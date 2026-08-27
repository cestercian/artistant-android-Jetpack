package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeMessagesRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.CLIENT_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.OTHER_ARTIST_ID
import `in`.artistant.app.testsupport.artist
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Inbox segmentation + the counterpart-name resolution.
 *
 * A thread has a client side and an artist side, and the row must name *the other
 * one from the viewer's seat*: a CLIENT viewer sees the artist, an ARTIST viewer
 * sees `client_name` (the denormalized column migration 0080 added precisely
 * because the users embed is RLS-nulled for the counterparty). Resolving without
 * consulting the viewer is how every client ended up reading their own name as
 * the person they were talking to.
 */
class MessagesInboxViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /**
     * Serves a fixed thread list (or throws). The shipped FakeMessagesRepository
     * can't stamp `client_name` or fail a list call, which is what these rows need.
     */
    private class StaticThreads(
        /** Swappable, so a test can model the server changing between two loads. */
        var threads: List<Thread> = emptyList(),
        private val failList: Boolean = false,
    ) : MessagesRepository {
        /** Load count — proves a re-projection didn't secretly go back to the network. */
        var calls = 0
            private set

        /** When set, a load parks here until the test completes it — in-flight state. */
        var listGate: CompletableDeferred<Unit>? = null

        override suspend fun listThreadsForUser(): List<Thread> {
            calls++
            listGate?.await()
            if (failList) throw IllegalStateException("offline")
            return threads
        }
        override suspend fun listMessages(threadId: String, limit: Int, before: Long?): List<Message> =
            emptyList()
        override suspend fun send(threadId: String, body: String): Message = error("unused")
        override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String =
            threads.firstOrNull()?.id ?: "t-new"
        override suspend fun markThreadRead(threadId: String, viewerIsArtist: Boolean) = Unit
        override suspend fun markThreadReadReceipt(threadId: String) = Unit
        override suspend fun counterpartLastRead(threadId: String): Long? = null
        override suspend fun setMuted(threadId: String, muted: Boolean) = Unit
        override suspend fun subscribeMessages(
            threadId: String,
            onInsert: (Message) -> Unit,
        ): MessagesSubscription = MessagesSubscription {}
    }

    private fun vm(
        messages: MessagesRepository,
        artists: FakeArtistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        viewerId: String? = CLIENT_ID,
        bookings: StubBookings = StubBookings(),
        flags: FakeThreadFlagsStore = FakeThreadFlagsStore(),
        blockedUsers: FakeBlockedUsersStore = FakeBlockedUsersStore(),
    ) = MessagesViewModel(
        messagesRepository = messages,
        artistsRepository = artists,
        bookingsRepository = bookings,
        flagsStore = flags,
        blockedUsers = blockedUsers,
        viewer = { viewerId },
    )

    /** Seeds two threads: one attached to a booking, one bare inquiry. */
    private suspend fun seeded(): FakeMessagesRepository {
        val repo = FakeMessagesRepository()
        repo.findOrCreateThread(ARTIST_ID, bookingId = "b-1")
        repo.findOrCreateThread(ARTIST_ID, bookingId = null)
        return repo
    }

    @Test
    fun allFilterShowsEveryThread() = runTest {
        val model = vm(seeded())

        assertEquals(MessagesFilter.All, model.state.value.filter)
        assertEquals(2, model.state.value.visibleThreads.size)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun bookingsFilterKeepsOnlyThreadsBoundToABooking() = runTest {
        val model = vm(seeded())

        model.setFilter(MessagesFilter.Bookings)

        assertEquals(listOf("b-1"), model.state.value.visibleThreads.map { it.thread.bookingId })
    }

    @Test
    fun inquiriesFilterKeepsOnlyBookinglessThreads() = runTest {
        val model = vm(seeded())

        model.setFilter(MessagesFilter.Inquiries)

        assertEquals(1, model.state.value.visibleThreads.size)
        assertEquals(null, model.state.value.visibleThreads.single().thread.bookingId)
    }

    @Test
    fun filterChangesDoNotRefetch_theyJustReprojectTheLoadedRows() = runTest {
        val model = vm(seeded())
        val loaded = model.state.value.threads

        model.setFilter(MessagesFilter.Bookings)
        model.setFilter(MessagesFilter.All)

        assertEquals(loaded, model.state.value.threads)
    }

    @Test
    fun rowNameFallsBackToTheCachedArtistName() = runTest {
        val model = vm(seeded())

        assertTrue(model.state.value.threads.all { it.counterpartName == "Nova Beats" })
    }

    @Test
    fun rowNameFallsBackToArtistWhenTheCacheIsCold() = runTest {
        val model = vm(seeded(), FakeArtistsRepository(emptyList()))

        assertTrue(model.state.value.threads.all { it.counterpartName == "Artist" })
    }

    // --- counterpart resolution, from the viewer's side ----------------------

    /**
     * The reported bug, exactly: signed in as a client, on a thread the server has
     * stamped with the client's OWN name. Preferring `client_name` unconditionally
     * makes the row say "you are talking to yourself".
     */
    @Test
    fun aClientViewerSeesTheArtistNotTheirOwnStampedClientName() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientName = "Asha Rao"))),
            viewerId = CLIENT_ID,
        )

        assertEquals("Nova Beats", model.state.value.threads.single().counterpartName)
    }

    /** The mirror seat: the artist's counterpart IS the client, so client_name wins. */
    @Test
    fun anArtistViewerSeesTheClientName() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientName = "Asha Rao"))),
            viewerId = ARTIST_ID,
        )

        assertEquals("Asha Rao", model.state.value.threads.single().counterpartName)
    }

    /**
     * An artist viewer with no `client_name` must NOT fall through to the artist
     * cache — that cache entry is the artist themself. Placeholder, never a name
     * belonging to the viewer and never a raw uuid.
     */
    @Test
    fun anArtistViewerWithNoClientNameGetsThePlaceholderNotTheirOwnName() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientName = null))),
            viewerId = ARTIST_ID,
        )

        val name = model.state.value.threads.single().counterpartName
        assertEquals("Client", name)
        assertFalse("must never render a raw id", name.contains(ARTIST_ID))
    }

    /** Blank is as good as missing on the artist seat (server writes "" not null). */
    @Test
    fun anArtistViewerTreatsABlankClientNameAsMissing() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientName = "  "))),
            viewerId = ARTIST_ID,
        )

        assertEquals("Client", model.state.value.threads.single().counterpartName)
    }

    /**
     * Signed out mid-list (session dropped between load and render): we cannot
     * prove the viewer is the artist, so degrade to the client seat — that shows
     * the artist's name, which is never the viewer's own.
     */
    @Test
    fun anUnknownViewerDegradesToTheArtistSeatRatherThanGuessing() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientName = "Asha Rao"))),
            viewerId = null,
        )

        assertEquals("Nova Beats", model.state.value.threads.single().counterpartName)
    }

    @Test
    fun aFailedInboxLoadSurfacesAnError() = runTest {
        val model = vm(StaticThreads(failList = true))

        assertNotNull(model.state.value.error)
        assertTrue(model.state.value.threads.isEmpty())
        assertFalse(model.state.value.isLoading)
    }

    // --- coming back to the inbox -------------------------------------------

    /**
     * Reading a conversation happens on ANOTHER screen. The chat zeroes the
     * server's unread count and the reply moves the row up the `last_message_at`
     * order, but this ViewModel is retained while the chat sits on top of it, so
     * on the way back it is still holding the payload from before the thread was
     * opened — an unread rail and a stale preview on a conversation the user has
     * just read and answered. Nothing else can correct that: the flags store
     * only clears a device-local "mark as unread", never `unreadCount`.
     *
     * So the screen hands EVERY ON_RESUME to [MessagesViewModel.onResumed] and
     * the return re-reads. Driven through `onResumed` rather than `refresh`
     * because the trigger is the half that was broken: the screen's composition
     * is disposed behind the chat and rebuilt on the way back, so a latch living
     * over there would reset and swallow the resume that arrives with it.
     */
    @Test
    fun returningFromAChatRereadsTheInbox() = runTest {
        val stale = Thread(
            id = "t-1",
            artistId = ARTIST_ID,
            clientId = CLIENT_ID,
            lastPreview = "Are you free on the 16th?",
            lastMessageAtEpochMs = 1_000L,
            unreadCount = 2,
        )
        val repo = StaticThreads(listOf(stale))
        val model = vm(repo)
        assertTrue(model.state.value.threads.single().unread)
        assertEquals(1, repo.calls)

        // The resume that arrives with the first paint: `init` just loaded.
        model.onResumed()
        assertEquals("entering the inbox must not load it twice", 1, repo.calls)

        // The chat marked it read and sent a reply, and the screen composes
        // fresh on the way back — but this ViewModel, and its latch, survived.
        repo.threads = listOf(
            stale.copy(
                lastPreview = "Yes — 8pm works.",
                lastMessageAtEpochMs = 2_000L,
                unreadCount = 0,
            ),
        )
        model.onResumed()

        assertEquals("returning must re-read the inbox", 2, repo.calls)
        val row = model.state.value.threads.single()
        assertFalse("a row the server says was read must stop claiming unread", row.unread)
        assertEquals("Yes — 8pm works.", row.thread.lastPreview)
    }

    /**
     * Backgrounding and returning is the same event as coming back from a chat,
     * and every one after the first counts — the inbox is stale after each.
     */
    @Test
    fun everyLaterResumeRereadsToo() = runTest {
        val repo = StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID)))
        val model = vm(repo)

        model.onResumed()
        model.onResumed()
        model.onResumed()

        assertEquals(3, repo.calls)
    }

    /**
     * Nobody asked for the resync, so it must not raise the pull-to-refresh
     * indicator — [MessagesScreen] drives that off `isLoading` once the first
     * load has landed, and backing out of a chat should update the row, not
     * animate a spinner at it. The gesture and the retry taps still do.
     */
    @Test
    fun theResyncDoesNotRaiseTheRefreshIndicator() = runTest {
        val repo = StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID)))
        val model = vm(repo)
        model.onResumed() // the first paint's resume
        repo.listGate = CompletableDeferred()

        model.onResumed()

        assertEquals("the resync must be in flight", 2, repo.calls)
        assertFalse("a re-read nobody asked for must not spin", model.state.value.isLoading)
        repo.listGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.state.value.isLoading)
    }

    /** The gesture still owns its own signal: a pull shows the indicator. */
    @Test
    fun aPullToRefreshStillShowsTheIndicator() = runTest {
        val repo = StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID)))
        val model = vm(repo)
        repo.listGate = CompletableDeferred()

        model.refresh()

        assertTrue(model.state.value.isLoading)
        repo.listGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.state.value.isLoading)
    }

    // --- gig context ---------------------------------------------------------

    /**
     * The row's whole point: it should name the gig's state, not just the person.
     * The booking is read by id and matched back onto its thread.
     */
    @Test
    fun aBookedThreadCarriesItsGigStatusDateAndVenue() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, bookingId = "b-1"))),
            bookings = StubBookings(
                listOf(booking(id = "b-1", status = BookingStatus.Confirmed, venue = "Rooftop")),
            ),
        )

        val context = model.state.value.threads.single().context
        assertEquals(BookingStatus.Confirmed, context.status)
        assertEquals("Rooftop", context.venue)
        assertEquals("Sat, May 16, 2026", context.dateLabel)
    }

    /** A thread with no booking must not borrow one — it is genuinely an inquiry. */
    @Test
    fun aBookinglessThreadStaysAnInquiry() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID))),
            bookings = StubBookings(listOf(booking(id = "b-1"))),
        )

        assertEquals(ThreadContext.INQUIRY, model.state.value.threads.single().context)
    }

    /**
     * A booking the viewer can't read (RLS, or outside the loaded window) leaves
     * the status unresolved rather than guessing one — but keeps the id so the
     * row can still deep-link.
     */
    @Test
    fun anUnreadableBookingLeavesTheStatusUnresolved() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, bookingId = "b-missing"))),
            bookings = StubBookings(),
        )

        val context = model.state.value.threads.single().context
        assertEquals("b-missing", context.bookingId)
        assertNull(context.status)
    }

    /** A bookings outage costs the context line, never the inbox itself. */
    @Test
    fun aBookingsFailureDoesNotTakeTheInboxDown() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, bookingId = "b-1"))),
            bookings = StubBookings(failFetch = true),
        )

        assertNull(model.state.value.error)
        assertEquals(1, model.state.value.threads.size)
    }

    /**
     * Only the ids the rows actually carry, once each, in one query. Asking for
     * the seat's own list instead pulls every booking the account has ever had
     * on every inbox load — and drags the calendar mirror along behind it, which
     * is the surface Bookings/Gigs owns, not this one.
     */
    @Test
    fun onlyTheBookingsTheRowsReferenceAreFetched() = runTest {
        val bookings = StubBookings(listOf(booking(id = "b-1")))
        vm(
            StaticThreads(
                listOf(
                    Thread(id = "t-1", artistId = ARTIST_ID, bookingId = "b-1"),
                    // The same gig, negotiated in two threads, plus an inquiry
                    // that has no gig at all.
                    Thread(id = "t-2", artistId = OTHER_ARTIST_ID, bookingId = "b-1"),
                    Thread(id = "t-3", artistId = ARTIST_ID),
                ),
            ),
            viewerId = CLIENT_ID,
            bookings = bookings,
        )

        assertEquals(listOf(listOf("b-1")), bookings.fetchedIds)
    }

    /** No booked threads at all means there is nothing to ask for. */
    @Test
    fun anInboxOfPureInquiriesSkipsTheBookingsFetchEntirely() = runTest {
        val bookings = StubBookings()
        vm(StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID))), bookings = bookings)

        assertTrue(bookings.fetchedIds.isEmpty())
    }

    /**
     * `pending_confirm` is the artist's decision, so only the artist seat gets
     * the accelerator. Offering the client a "review" button for a call that
     * isn't theirs is the same class of bug as showing them their own name.
     */
    @Test
    fun onlyTheArtistSeatIsToldARequestIsWaitingOnThem() = runTest {
        val threads = listOf(Thread(id = "t-1", artistId = ARTIST_ID, bookingId = "b-1"))
        val pending = listOf(booking(id = "b-1", status = BookingStatus.PendingConfirm))

        val asArtist = vm(
            StaticThreads(threads),
            viewerId = ARTIST_ID,
            bookings = StubBookings(pending),
        )
        val asClient = vm(
            StaticThreads(threads),
            viewerId = CLIENT_ID,
            bookings = StubBookings(pending),
        )

        assertTrue(asArtist.state.value.threads.single().context.awaitingViewer)
        assertFalse(asClient.state.value.threads.single().context.awaitingViewer)
    }

    // --- archive, star, search ----------------------------------------------

    @Test
    fun archivedThreadsLeaveTheInboxAndAppearInTheArchive() = runTest {
        val flags = FakeThreadFlagsStore(ThreadFlags(archived = setOf("t-2")))
        val model = vm(
            StaticThreads(
                listOf(
                    Thread(id = "t-1", artistId = ARTIST_ID),
                    Thread(id = "t-2", artistId = ARTIST_ID),
                ),
            ),
            flags = flags,
        )

        assertEquals(listOf("t-1"), model.state.value.activeThreads.map { it.thread.id })
        assertEquals(listOf("t-2"), model.state.value.archivedThreads.map { it.thread.id })
        assertEquals(listOf("t-1"), model.state.value.visibleThreads.map { it.thread.id })
    }

    /** Archived rows must not be counted by the chips they no longer appear under. */
    @Test
    fun archivedThreadsAreNotCountedByTheChips() = runTest {
        val model = vm(
            StaticThreads(
                listOf(
                    Thread(id = "t-1", artistId = ARTIST_ID, bookingId = "b-1"),
                    Thread(id = "t-2", artistId = ARTIST_ID, bookingId = "b-2"),
                ),
            ),
            flags = FakeThreadFlagsStore(ThreadFlags(archived = setOf("t-2"))),
        )

        assertEquals(1, model.state.value.counts[MessagesFilter.Bookings])
    }

    /** Toggling a flag re-projects the loaded rows; it must not refetch. */
    @Test
    fun starringReProjectsWithoutRefetching() = runTest {
        val repo = StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID)))
        val model = vm(repo, bookings = StubBookings())

        model.toggleStarred("t-1")

        assertTrue(model.state.value.threads.single().starred)
    }

    @Test
    fun archivingIsReversible() = runTest {
        val model = vm(StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID))))

        model.toggleArchived("t-1")
        assertTrue(model.state.value.activeThreads.isEmpty())

        model.toggleArchived("t-1")
        assertEquals(1, model.state.value.activeThreads.size)
    }

    // --- blocking (migration 0087) -------------------------------------------
    //
    // 0087 is enforced CLIENT-SIDE in v1: the table records the block, and the
    // inbox filter is what makes it mean anything. These pin that the filter
    // reaches every inbox surface, not just the visible list.

    @Test
    fun aBlockedCounterpartsThreadsLeaveTheInboxEntirely() = runTest {
        val model = vm(
            StaticThreads(
                listOf(
                    Thread(id = "t-1", artistId = ARTIST_ID, clientId = CLIENT_ID),
                    Thread(id = "t-2", artistId = OTHER_ARTIST_ID, clientId = CLIENT_ID),
                ),
            ),
            blockedUsers = FakeBlockedUsersStore(setOf(ARTIST_ID.lowercase())),
        )

        // Not just `visibleThreads`: a block that left the row in `threads`
        // would keep feeding the chip counts and the archive list, so the inbox
        // would still advertise a conversation with no way to open it.
        assertEquals(listOf("t-2"), model.state.value.threads.map { it.thread.id })
        assertEquals(listOf("t-2"), model.state.value.visibleThreads.map { it.thread.id })
        assertEquals(1, model.state.value.counts[MessagesFilter.All])
    }

    @Test
    fun blockingAnArtistFromAnotherScreenUpdatesTheInboxWithoutARefetch() = runTest {
        // The block happens in the chat's details sheet, not here. The inbox
        // observes the same store, so it must react on its own — otherwise
        // blocking someone appears to do nothing until a pull-to-refresh.
        val repo = StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientId = CLIENT_ID)))
        val blocked = FakeBlockedUsersStore()
        val model = vm(repo, blockedUsers = blocked)
        val loadsBefore = repo.calls

        blocked.toggle(ARTIST_ID)

        assertTrue(model.state.value.threads.isEmpty())
        assertEquals("the inbox must re-project, not reload", loadsBefore, repo.calls)
    }

    @Test
    fun unblockingPutsTheConversationBackInTheInbox() = runTest {
        // The other half of the loop, and the reason the blocked-accounts screen
        // can exist at all: it unblocks through this same store, so the inbox
        // has to re-project a hidden thread back into every surface — rows,
        // counts, badge — without being told to reload.
        val repo = StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientId = CLIENT_ID)))
        val blocked = FakeBlockedUsersStore(setOf(ARTIST_ID.lowercase()))
        val model = vm(repo, blockedUsers = blocked)
        assertTrue("precondition: blocked, so hidden", model.state.value.threads.isEmpty())
        val loadsBefore = repo.calls

        blocked.toggle(ARTIST_ID)

        assertEquals(listOf("t-1"), model.state.value.threads.map { it.thread.id })
        assertEquals(1, model.state.value.counts[MessagesFilter.All])
        assertEquals("the inbox must re-project, not reload", loadsBefore, repo.calls)
    }

    @Test
    fun anArtistViewerBlockingAClientHidesThatConversation() = runTest {
        // The mirror case, from the other seat: `artist_id` is the artist's own
        // uid here, so only the `client_id` leg can be what matches.
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientId = CLIENT_ID))),
            viewerId = ARTIST_ID,
            blockedUsers = FakeBlockedUsersStore(setOf(CLIENT_ID.lowercase())),
        )

        assertTrue(model.state.value.threads.isEmpty())
    }

    @Test
    fun searchMatchesTheCounterpartNameAndThePreview() = runTest {
        val model = vm(
            StaticThreads(
                listOf(
                    Thread(id = "t-1", artistId = ARTIST_ID, lastPreview = "See you Saturday"),
                    Thread(id = "t-2", artistId = OTHER_ARTIST_ID, lastPreview = "Rates?"),
                ),
            ),
            artists = FakeArtistsRepository(
                listOf(
                    artist(id = ARTIST_ID, name = "Nova Beats"),
                    artist(id = OTHER_ARTIST_ID, name = "Kabir Sen Trio"),
                ),
            ),
        )

        model.setQuery("kabir")
        assertEquals(listOf("t-2"), model.state.value.visibleThreads.map { it.thread.id })

        model.setQuery("saturday")
        assertEquals(listOf("t-1"), model.state.value.visibleThreads.map { it.thread.id })
    }

    /** A live query must not leave the chips advertising rows it filtered out. */
    @Test
    fun chipCountsFollowTheActiveSearch() = runTest {
        val model = vm(
            StaticThreads(
                listOf(
                    Thread(id = "t-1", artistId = ARTIST_ID, lastPreview = "See you Saturday"),
                    Thread(id = "t-2", artistId = ARTIST_ID, lastPreview = "Rates?"),
                ),
            ),
        )

        model.setQuery("saturday")

        assertEquals(1, model.state.value.counts[MessagesFilter.All])
    }

    /** Support holds no conversations — a count there would read as an empty inbox. */
    @Test
    fun theSupportSegmentNeverMatchesAThread() = runTest {
        val model = vm(seeded())

        model.setFilter(MessagesFilter.Support)

        assertTrue(model.state.value.visibleThreads.isEmpty())
        assertEquals(0, model.state.value.counts[MessagesFilter.Support])
    }
}
