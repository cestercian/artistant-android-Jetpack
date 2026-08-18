package `in`.artistant.app.feature.messages

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeReportsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.CLIENT_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
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
 * Optimistic send ↔ server RETURNING ↔ Realtime echo — the 3-way race the iOS
 * MessageStore tests used to cover. `ChatRealtimeLogic` is already unit-tested
 * as a pure function; what's untested is that ChatViewModel wires it correctly,
 * i.e. that a bubble actually settles, fails, and retries in the real state flow.
 *
 * The shipped FakeMessagesRepository can't fail a send or push a Realtime row,
 * so this file carries its own scriptable double.
 */
class ChatViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val threadId = "44444444-4444-4444-4444-444444444444"

    /**
     * Scriptable messages seam: [failSend] flips the write path, [emit] pushes a
     * row through the Realtime callback the ViewModel registered.
     *
     * [seedMessages] is the whole server-side transcript, and it is PAGED here
     * the way the real seam pages it — newest `limit` rows no newer than the
     * cursor — so scroll-back is exercised against the same shape production
     * gets rather than against a double that always returns everything.
     */
    private inner class ScriptedMessages(
        private val seedMessages: List<Message> = emptyList(),
        private val thread: Thread? = Thread(id = "44444444-4444-4444-4444-444444444444", artistId = ARTIST_ID),
    ) : MessagesRepository {
        var failSend: Boolean = false
        var sendCount: Int = 0
        var markedRead: Int = 0
        var nextServerId: String = "server-1"
        private var listener: ((Message) -> Unit)? = null
        var cancelCount: Int = 0
        var subscribeCount: Int = 0
        var listCount: Int = 0

        /** Every body handed to the write path, in order. */
        val sentBodies = mutableListOf<String>()

        /** Every `before` cursor asked for, in order — null is the newest page. */
        val cursors = mutableListOf<Long?>()

        /** Fails only the SCROLL-BACK fetches, leaving the open's page alone. */
        var failOlder: Boolean = false

        /** When set, a scroll-back page parks here until the test completes it. */
        var olderGate: CompletableDeferred<Unit>? = null

        /**
         * When set, `send()` parks here until the test completes it. That's the
         * only way to hold the write genuinely in flight — optimistic bubble on
         * screen, server row not back yet — which is the window the Realtime
         * echo actually races.
         */
        var sendGate: CompletableDeferred<Unit>? = null

        fun emit(message: Message) = listener?.invoke(message)

        override suspend fun listThreadsForUser(): List<Thread> = listOfNotNull(thread)

        override suspend fun listMessages(threadId: String, limit: Int, before: Long?): List<Message> {
            listCount++
            cursors += before
            if (before != null) {
                olderGate?.await()
                if (failOlder) throw IllegalStateException("network down")
            }
            return seedMessages
                .filter { before == null || it.sentAtEpochMs <= before }
                .takeLast(limit)
        }

        override suspend fun send(threadId: String, body: String): Message {
            sendCount++
            sentBodies += body
            sendGate?.await()
            if (failSend) throw IllegalStateException("network down")
            return Message(
                id = nextServerId,
                threadId = threadId,
                senderId = "me",
                // The real seam trims before it inserts, so both the RETURNING
                // row and the row Postgres broadcasts carry the trimmed text.
                body = body.trim(),
                sentAtEpochMs = 5_000L,
                isMine = true,
            )
        }

        override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String = threadId
        override suspend fun markThreadRead(threadId: String) { markedRead++ }
        override suspend fun markThreadReadReceipt(threadId: String) = Unit
        override suspend fun counterpartLastRead(threadId: String): Long? = 9_000L

        /** Every mute value the ViewModel asked the server to write, in order. */
        val muteWrites = mutableListOf<Boolean>()
        var failMute: Boolean = false

        override suspend fun setMuted(threadId: String, muted: Boolean) {
            muteWrites += muted
            if (failMute) throw IllegalStateException("network down")
        }

        override suspend fun subscribeMessages(
            threadId: String,
            onInsert: (Message) -> Unit,
        ): MessagesSubscription {
            listener = onInsert
            subscribeCount++
            return MessagesSubscription { cancelCount++ }
        }
    }

    private fun vm(
        messages: MessagesRepository,
        viewerId: String? = CLIENT_ID,
        bookings: StubBookings = StubBookings(),
        flags: FakeThreadFlagsStore = FakeThreadFlagsStore(),
        blockedUsers: FakeBlockedUsersStore = FakeBlockedUsersStore(),
    ) = ChatViewModel(
        savedStateHandle = SavedStateHandle(mapOf("threadId" to threadId)),
        messagesRepository = messages,
        artistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        bookingsRepository = bookings,
        reports = FakeReportsRepository(),
        flagsStore = flags,
        blockedUsers = blockedUsers,
        viewer = { viewerId },
    )

    private fun serverMessage(id: String, body: String, at: Long = 5_000L, mine: Boolean = true) =
        Message(
            id = id,
            threadId = threadId,
            senderId = if (mine) "me" else "them",
            body = body,
            sentAtEpochMs = at,
            isMine = mine,
        )

    // --- load ---------------------------------------------------------------

    @Test
    fun openingAThreadLoadsHistoryAndMarksItRead() = runTest {
        val repo = ScriptedMessages(seedMessages = listOf(serverMessage("s1", "Hi", at = 1_000L, mine = false)))

        val model = vm(repo)

        assertEquals(listOf("s1"), model.state.value.messages.map { it.id })
        assertEquals("Nova Beats", model.state.value.title)
        assertTrue(repo.markedRead > 0)
        assertEquals(9_000L, model.state.value.counterpartLastReadAt)
    }

    // --- title: the counterpart, from the viewer's seat ----------------------

    /**
     * Same bug as the inbox row, on the second surface: the chat header must name
     * the other party. A client viewer whose own name is stamped on the thread was
     * seeing themself in the title bar (and, via `counterpartLabel`, in the details
     * sheet's Participants list right above "You").
     */
    @Test
    fun theTitleShowsTheArtistToAClientViewerEvenWhenClientNameIsStamped() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientName = "Asha Rao"),
        )

        val model = vm(repo, viewerId = CLIENT_ID)

        assertEquals("Nova Beats", model.state.value.title)
        assertEquals(false, model.state.value.viewerIsArtist)
    }

    @Test
    fun theTitleShowsTheClientToAnArtistViewer() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientName = "Asha Rao"),
        )

        val model = vm(repo, viewerId = ARTIST_ID)

        assertEquals("Asha Rao", model.state.value.title)
        assertEquals(true, model.state.value.viewerIsArtist)
    }

    /** No client_name on the artist seat → placeholder, never the artist's own name. */
    @Test
    fun anArtistViewerWithNoClientNameGetsThePlaceholderTitle() = runTest {
        val repo = ScriptedMessages(thread = Thread(id = threadId, artistId = ARTIST_ID))

        val model = vm(repo, viewerId = ARTIST_ID)

        assertEquals("Client", model.state.value.title)
    }

    // --- optimistic send ----------------------------------------------------

    @Test
    fun sendSettlesToExactlyOneBubbleCarryingTheServerId() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)

        model.send("Meet at 8?")

        val messages = model.state.value.messages
        assertEquals(1, messages.size)
        assertEquals("server-1", messages.single().id)
        assertEquals(MessageDelivery.Sent, messages.single().delivery)
        assertTrue(messages.single().isMine)
    }

    /**
     * The real race: the Realtime INSERT lands while `send()` is still in flight,
     * so the echo has to collapse into the `.sending` placeholder rather than
     * append beside it.
     *
     * The assertion that matters is the one taken BEFORE the gate opens. If the
     * in-flight collapse in `receiveRealtimeMessage` regressed, the echo would
     * append and the user would see a duplicated bubble for the whole network
     * round-trip — but `reconcileSendSuccess` cleans the list up afterwards, so
     * a final-state-only assertion stays green through that bug. This is
     * exactly what the earlier version of this test missed.
     */
    @Test
    fun aRealtimeEchoDuringAnInFlightSendCollapsesIntoTheOptimisticBubble() = runTest {
        val repo = ScriptedMessages().apply { sendGate = CompletableDeferred() }
        val model = vm(repo)
        val now = System.currentTimeMillis()

        model.send("Meet at 8?")

        // In flight: one optimistic bubble, write parked inside the repository.
        val inFlight = model.state.value.messages.single()
        assertTrue(inFlight.id.startsWith("optimistic-"))
        assertEquals(MessageDelivery.Sending, inFlight.delivery)
        assertEquals(1, repo.sendCount)

        // Echo arrives before the write returns.
        repo.emit(serverMessage("server-1", "Meet at 8?", at = now))

        val duringFlight = model.state.value.messages
        assertEquals("echo must collapse in place, not append", 1, duringFlight.size)
        assertEquals("server-1", duringFlight.single().id)
        assertEquals(MessageDelivery.Sent, duringFlight.single().delivery)

        // Now let the write return; the RETURNING row must not re-add itself.
        repo.sendGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("server-1"), model.state.value.messages.map { it.id })
        assertEquals(MessageDelivery.Sent, model.state.value.messages.single().delivery)
    }

    /**
     * The same race, with the whitespace a soft keyboard leaves behind after a
     * word suggestion — and the reason this is a separate test: the seam TRIMS
     * before it inserts, so the row Postgres broadcasts back carries the trimmed
     * body, while the collapse only fires when the bodies match. Holding the raw
     * draft locally meant the echo appended instead: two bubbles for the whole
     * round trip, and — when the insert lands but its response is lost — a
     * permanent "Not sent · Tap to retry" beside a message that was delivered,
     * whose retry posts a real duplicate.
     */
    @Test
    fun anEchoCollapsesIntoABubbleSentWithTrailingWhitespace() = runTest {
        val repo = ScriptedMessages().apply { sendGate = CompletableDeferred() }
        val model = vm(repo)

        model.send("Meet at 8? ")

        // Local bubble and the write both carry what the server will store.
        val inFlight = model.state.value.messages.single()
        assertEquals("Meet at 8?", inFlight.body)
        assertEquals(listOf("Meet at 8?"), repo.sentBodies)

        repo.emit(serverMessage("server-1", "Meet at 8?", at = System.currentTimeMillis()))

        val duringFlight = model.state.value.messages
        assertEquals("the echo of the trimmed row must collapse, not append", 1, duringFlight.size)
        assertEquals("server-1", duringFlight.single().id)
        assertEquals(MessageDelivery.Sent, duringFlight.single().delivery)

        repo.sendGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("server-1"), model.state.value.messages.map { it.id })
    }

    @Test
    fun anOlderEchoWithTheSameTextDoesNotSwallowTheInFlightBubble() = runTest {
        // Same body, but far outside the 15s collapse window — a *different*
        // message that happens to repeat the text. Stealing the placeholder here
        // would lose the user's send.
        val repo = ScriptedMessages().apply { sendGate = CompletableDeferred() }
        val model = vm(repo)

        model.send("Meet at 8?")
        repo.emit(serverMessage("server-old", "Meet at 8?", at = System.currentTimeMillis() - 60_000))

        val duringFlight = model.state.value.messages
        assertEquals(2, duringFlight.size)
        assertTrue(duringFlight.any { it.delivery == MessageDelivery.Sending })

        repo.sendGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("server-old", "server-1"), model.state.value.messages.map { it.id })
    }

    @Test
    fun anIncomingMessageFromTheCounterpartAppends() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)

        repo.emit(serverMessage("them-1", "On my way", at = 2_000L, mine = false))

        assertEquals(listOf("them-1"), model.state.value.messages.map { it.id })
        assertTrue(model.state.value.messages.single().isMine.not())
    }

    @Test
    fun emptyOrWhitespaceOnlyTextIsNeverSent() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)

        model.send("")
        model.send("    ")

        assertEquals(0, repo.sendCount)
        assertTrue(model.state.value.messages.isEmpty())
    }

    @Test
    fun bodiesAreCappedAt4000Chars() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)

        model.send("x".repeat(5_000))

        assertEquals(4_000, model.state.value.messages.single().body.length)
    }

    // --- failure + retry ----------------------------------------------------

    @Test
    fun aFailedSendLeavesAFailedBubbleAndAnError() = runTest {
        val repo = ScriptedMessages().apply { failSend = true }
        val model = vm(repo)

        model.send("Meet at 8?")

        val bubble = model.state.value.messages.single()
        assertEquals(MessageDelivery.Failed, bubble.delivery)
        assertTrue(bubble.id.startsWith("optimistic-"))
        assertNotNull(model.state.value.error)
    }

    @Test
    fun retryCommitsTheFailedBubble() = runTest {
        val repo = ScriptedMessages().apply { failSend = true }
        val model = vm(repo)
        model.send("Meet at 8?")
        val failedId = model.state.value.messages.single().id

        repo.failSend = false
        model.retryFailedMessage(failedId)

        val messages = model.state.value.messages
        assertEquals(1, messages.size)
        assertEquals("server-1", messages.single().id)
        assertEquals(MessageDelivery.Sent, messages.single().delivery)
        assertEquals(2, repo.sendCount)
    }

    @Test
    fun retryIsANoOpForABubbleThatIsNotFailed() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)
        model.send("Meet at 8?")
        val sendsAfterFirst = repo.sendCount

        model.retryFailedMessage("server-1")

        assertEquals(sendsAfterFirst, repo.sendCount)
    }

    @Test
    fun aFailedBubbleIsNotStolenByAnUnrelatedRealtimeEchoOfTheSameText() = runTest {
        val repo = ScriptedMessages().apply { failSend = true }
        val model = vm(repo)
        model.send("Meet at 8?")

        // Same body AND inside the 15s collapse window, so the ONLY thing
        // standing between the echo and the failed bubble is the `.sending`
        // predicate. The bubble must survive, or the user loses their retry.
        repo.emit(serverMessage("server-9", "Meet at 8?", at = System.currentTimeMillis()))

        assertEquals(2, model.state.value.messages.size)
        assertTrue(model.state.value.messages.any { it.delivery == MessageDelivery.Failed })
    }

    // --- refresh ------------------------------------------------------------

    @Test
    fun refreshKeepsInFlightBubblesAndOlderScrollback() = runTest {
        val repo = ScriptedMessages(
            seedMessages = listOf(serverMessage("s2", "later", at = 2_000L, mine = false)),
        ).apply { failSend = true }
        val model = vm(repo)
        model.send("Meet at 8?") // fails → stays as an in-flight/failed bubble

        model.refresh()

        val ids = model.state.value.messages.map { it.id }
        assertTrue("server page kept", "s2" in ids)
        assertTrue("failed bubble kept", ids.any { it.startsWith("optimistic-") })
    }

    // --- scroll-back ---------------------------------------------------------
    //
    // Opening a thread fetches ONE page. Without a cursor the messages before it
    // were unreachable in this client — scrolling up stopped at the 50th-newest
    // and everything earlier in a long negotiation was simply gone. iOS has
    // shipped the cursor (`listMessages(threadID:limit:before:)` + `loadOlder`)
    // since the store was written.

    /** 120 messages, one per second, oldest first — a thread with real history. */
    private fun history(count: Int) = (1..count).map { n ->
        serverMessage("m$n", "line $n", at = n * 1_000L, mine = false)
    }

    @Test
    fun openingAThreadLoadsOnlyTheNewestPage() = runTest {
        val model = vm(ScriptedMessages(seedMessages = history(120)))

        assertEquals(50, model.state.value.messages.size)
        assertEquals("m71", model.state.value.messages.first().id)
        assertEquals("m120", model.state.value.messages.last().id)
    }

    @Test
    fun scrollingBackPagesInTheHistoryBehindTheNewestPage() = runTest {
        val repo = ScriptedMessages(seedMessages = history(120))
        val model = vm(repo)

        model.loadOlder()
        advanceUntilIdle()

        val ids = model.state.value.messages.map { it.id }
        // Paged from the oldest row in memory, and the older page landed AHEAD
        // of the window rather than replacing it. The open itself carries no
        // cursor — it is always the newest page.
        assertEquals(listOf(null, 71_000L), repo.cursors)
        assertEquals("m22", ids.first())
        assertEquals("m120", ids.last())
        // The cursor is inclusive, so the boundary row comes back on the wire —
        // and must collapse by id rather than render twice.
        assertEquals(1, ids.count { it == "m71" })
        assertEquals(99, ids.size)
    }

    @Test
    fun aShortPageMeansTheStartOfTheThreadAndRetiresPaging() = runTest {
        val repo = ScriptedMessages(seedMessages = history(60))
        val model = vm(repo)

        model.loadOlder()
        advanceUntilIdle()
        assertEquals(60, model.state.value.messages.size)
        val fetches = repo.listCount

        model.loadOlder()
        advanceUntilIdle()

        assertEquals("the beginning only has to be found once", fetches, repo.listCount)
    }

    @Test
    fun aFailedPageIsRetriedRatherThanTakenForTheStartOfTheThread() = runTest {
        // A dropped request says nothing about history. Treating it as "you've
        // reached the beginning" would kill scroll-back for the whole session.
        val repo = ScriptedMessages(seedMessages = history(120)).apply { failOlder = true }
        val model = vm(repo)

        model.loadOlder()
        advanceUntilIdle()
        assertEquals("a failed page must not disturb the window", 50, model.state.value.messages.size)

        repo.failOlder = false
        model.loadOlder()
        advanceUntilIdle()

        assertEquals(99, model.state.value.messages.size)
    }

    @Test
    fun onlyOnePageIsInFlightAtATime() = runTest {
        // The trigger is "a scroll settled at the top", which fires again on
        // every settle while the page is still loading.
        val repo = ScriptedMessages(seedMessages = history(120)).apply {
            olderGate = CompletableDeferred()
        }
        val model = vm(repo)
        val fetchesAfterOpen = repo.listCount

        model.loadOlder()
        model.loadOlder()
        model.loadOlder()

        assertEquals(fetchesAfterOpen + 1, repo.listCount)

        repo.olderGate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun anEmptyTranscriptHasNothingToPageBefore() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)
        val fetches = repo.listCount

        model.loadOlder()
        advanceUntilIdle()

        assertEquals(fetches, repo.listCount)
    }

    // --- details + report ---------------------------------------------------

    @Test
    fun detailsSheetTogglesWithoutTouchingTheTranscript() = runTest {
        val model = vm(ScriptedMessages())

        model.openDetails()
        assertTrue(model.state.value.showDetails)
        model.dismissDetails()
        assertTrue(!model.state.value.showDetails)
    }

    @Test
    fun reportingAConversationForwardsTheThreadIdAndReason() = runTest {
        val reports = FakeReportsRepository()
        val model = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("threadId" to threadId)),
            messagesRepository = ScriptedMessages(),
            artistsRepository = FakeArtistsRepository(),
            bookingsRepository = StubBookings(),
            reports = reports,
            flagsStore = FakeThreadFlagsStore(),
            blockedUsers = FakeBlockedUsersStore(),
            viewer = { CLIENT_ID },
        )

        model.reportConversation("Harassment")

        assertEquals(threadId, reports.conversation.single().first)
        assertEquals("Harassment", reports.conversation.single().second)
    }

    @Test
    fun aLoadFailureSurfacesAnErrorInsteadOfAnEmptyTranscript() = runTest {
        val failing = object : MessagesRepository {
            override suspend fun listThreadsForUser(): List<Thread> = error("offline")
            override suspend fun listMessages(threadId: String, limit: Int, before: Long?): List<Message> =
                emptyList()
            override suspend fun send(threadId: String, body: String): Message = error("offline")
            override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String = error("offline")
            override suspend fun markThreadRead(threadId: String) = Unit
            override suspend fun markThreadReadReceipt(threadId: String) = Unit
            override suspend fun counterpartLastRead(threadId: String): Long? = null
            override suspend fun setMuted(threadId: String, muted: Boolean) = error("offline")
            override suspend fun subscribeMessages(
                threadId: String,
                onInsert: (Message) -> Unit,
            ): MessagesSubscription = MessagesSubscription {}
        }

        val model = vm(failing)

        assertNotNull(model.state.value.error)
        assertTrue(model.state.value.messages.isEmpty())
        assertNull(model.state.value.thread)
    }

    // --- gig context ---------------------------------------------------------

    /**
     * The header's whole job beyond the name: say which gig is being negotiated.
     * Fetched as ONE row by id — pulling the seat's entire booking list to render
     * a status line would drag its calendar side effect along with it.
     */
    @Test
    fun theGigBehindTheThreadFeedsTheContextStrip() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, bookingId = "b-1"),
        )
        val model = vm(
            repo,
            bookings = StubBookings(
                one = booking(id = "b-1", status = BookingStatus.Confirmed, venue = "Rooftop"),
            ),
        )

        val context = model.state.value.context
        assertEquals(BookingStatus.Confirmed, context.status)
        assertEquals("Rooftop", context.venue)
        assertEquals("b-1", context.bookingId)
    }

    /** Unreadable booking: no invented status, but the id survives so Details can route. */
    @Test
    fun anUnreadableBookingKeepsTheIdWithoutAStatus() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, bookingId = "b-gone"),
        )
        val model = vm(repo, bookings = StubBookings(one = null))

        assertEquals("b-gone", model.state.value.context.bookingId)
        assertNull(model.state.value.context.status)
    }

    @Test
    fun aBookinglessThreadStaysAnInquiry() = runTest {
        val model = vm(ScriptedMessages())

        assertEquals(ThreadContext.INQUIRY, model.state.value.context)
    }

    // --- read receipts -------------------------------------------------------

    /**
     * The counterparty read up to 9s; the viewer's message went at 5s, so it is
     * the one that carries the caption.
     */
    @Test
    fun theReceiptMarksTheNewestOwnMessageTheCounterpartHasRead() = runTest {
        val repo = ScriptedMessages(
            seedMessages = listOf(serverMessage("s1", "Meet at 8?", at = 5_000L, mine = true)),
        )
        val model = vm(repo)

        assertEquals("s1", model.state.value.lastReadOwnMessageId)
    }

    // --- per-thread flags ----------------------------------------------------

    /**
     * Dismissal is per thread and persisted. Hiding the notice everywhere after
     * one dismissal would silently opt the reader out on conversations they have
     * never opened.
     */
    @Test
    fun dismissingTheSafetyNoticeIsScopedToThisThread() = runTest {
        val flags = FakeThreadFlagsStore()
        val model = vm(ScriptedMessages(), flags = flags)
        assertTrue(model.state.value.safetyBannerVisible)

        model.dismissSafetyBanner()
        assertFalse(model.state.value.safetyBannerVisible)

        // Exactly one thread was recorded — a different conversation still shows it.
        assertEquals(setOf(threadId), flags.flags.first().safetyDismissed)
    }

    @Test
    fun aPreviouslyDismissedNoticeStaysDismissedOnReopen() = runTest {
        val flags = FakeThreadFlagsStore(ThreadFlags(safetyDismissed = setOf(threadId)))

        val model = vm(ScriptedMessages(), flags = flags)

        assertFalse(model.state.value.safetyBannerVisible)
    }

    @Test
    fun starAndArchiveRoundTripThroughTheFlagsStore() = runTest {
        val model = vm(ScriptedMessages())

        model.toggleStarred()
        model.toggleArchived()
        assertTrue(model.state.value.starred)
        assertTrue(model.state.value.archived)

        model.toggleStarred()
        assertFalse(model.state.value.starred)
    }

    /**
     * Opening a thread retires an explicit "mark as unread" — the reader has now
     * demonstrably read it, so leaving the flag set would make the inbox argue
     * with what just happened.
     */
    @Test
    fun openingAThreadClearsAnExplicitMarkAsUnread() = runTest {
        val flags = FakeThreadFlagsStore(ThreadFlags(markedUnread = setOf(threadId)))

        vm(ScriptedMessages(), flags = flags)

        assertTrue(flags.flags.first().markedUnread.isEmpty())
    }

    // --- report --------------------------------------------------------------

    /**
     * The repository soft-fails to an on-device log rather than throwing, so this
     * surface cannot tell delivered from queued — it records that the report was
     * filed and the copy is worded to be true either way.
     */
    @Test
    fun reportingFlipsToTheReceiptState() = runTest {
        val reports = FakeReportsRepository()
        val model = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("threadId" to threadId)),
            messagesRepository = ScriptedMessages(),
            artistsRepository = FakeArtistsRepository(),
            bookingsRepository = StubBookings(),
            reports = reports,
            flagsStore = FakeThreadFlagsStore(),
            blockedUsers = FakeBlockedUsersStore(),
            viewer = { CLIENT_ID },
        )

        model.reportConversation("Scam or spam")

        assertTrue(model.state.value.reportSubmitted)
        assertEquals("Scam or spam", reports.conversation.single().second)
    }

    /** Closing the sheet resets the receipt so the next open starts on the actions. */
    @Test
    fun dismissingDetailsResetsTheReportReceipt() = runTest {
        val model = vm(ScriptedMessages())
        model.reportConversation("Offensive")

        model.dismissDetails()

        assertFalse(model.state.value.reportSubmitted)
    }

    // --- foreground resync ---------------------------------------------------

    /**
     * The socket is suspended while backgrounded, so returning has to do both:
     * pull what was missed AND rejoin. Refresh first, because it fills the gap
     * even if the channel is slow to come back.
     *
     * The screen delivers every ON_RESUME, so the first call here is the one
     * that arrives with the first paint — see the test below.
     */
    @Test
    fun comingBackToTheForegroundRefreshesAndReSubscribes() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)
        model.onResumed() // the first paint's resume
        advanceUntilIdle()
        val listsBefore = repo.listCount
        val subscribesBefore = repo.subscribeCount

        model.onResumed()
        advanceUntilIdle()

        assertTrue(repo.listCount > listsBefore)
        assertTrue(repo.subscribeCount > subscribesBefore)
        assertTrue("the superseded channel must be torn down", repo.cancelCount > 0)
    }

    /**
     * Opening a chat must cost ONE load and ONE join: `init` does them, and the
     * resume that comes with the first paint has to be swallowed. The latch is
     * on the ViewModel because it is the half that survives a push — the screen
     * is disposed behind a booking pushed on top and composed again on the way
     * back, and that return genuinely is a resync.
     */
    @Test
    fun theFirstPaintsResumeDoesNotReloadTheThread() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)
        advanceUntilIdle()
        val listsAfterInit = repo.listCount
        val subscribesAfterInit = repo.subscribeCount

        model.onResumed()
        advanceUntilIdle()

        assertEquals(listsAfterInit, repo.listCount)
        assertEquals(subscribesAfterInit, repo.subscribeCount)
    }

    // --- per-thread mute (mig 0091) ------------------------------------------

    @Test
    fun theMuteStateIsReadFromTheViewersOwnSideOfTheThread() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID, muted = true),
        )

        val model = vm(repo)
        advanceUntilIdle()

        assertTrue(model.state.value.muted)
    }

    @Test
    fun mutingWritesOnceAndFlipsTheState() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID),
        )
        val model = vm(repo)
        advanceUntilIdle()

        model.toggleMuted()
        advanceUntilIdle()

        assertEquals(listOf(true), repo.muteWrites)
        assertTrue(model.state.value.muted)
    }

    @Test
    fun aFailedMuteRevertsRatherThanClaimingSilence() = runTest {
        // The control promises no lock-screen notifications. A toggle that stuck
        // in the UI but never reached the server would be a promise the app
        // cannot keep, so the state goes back and the failure is surfaced.
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID),
        ).apply { failMute = true }
        val model = vm(repo)
        advanceUntilIdle()

        model.toggleMuted()
        advanceUntilIdle()

        assertFalse(model.state.value.muted)
        assertNotNull(model.state.value.error)
    }

    // --- blocking (mig 0087) -------------------------------------------------

    @Test
    fun theClientSeatBlocksTheArtist() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID),
        )
        val blocked = FakeBlockedUsersStore()
        val model = vm(repo, viewerId = CLIENT_ID, blockedUsers = blocked)
        advanceUntilIdle()

        model.toggleBlocked()
        advanceUntilIdle()

        // The id blocked is the COUNTERPARTY's, never the viewer's own — 0087's
        // no-self check would reject that outright.
        assertEquals(setOf(ARTIST_ID.lowercase()), blocked.blocked.value)
        assertTrue(model.state.value.blocked)
    }

    @Test
    fun theArtistSeatBlocksTheClient() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID),
        )
        val blocked = FakeBlockedUsersStore()
        val model = vm(repo, viewerId = ARTIST_ID, blockedUsers = blocked)
        advanceUntilIdle()

        model.toggleBlocked()
        advanceUntilIdle()

        assertEquals(setOf(CLIENT_ID.lowercase()), blocked.blocked.value)
    }

    @Test
    fun anAlreadyBlockedCounterpartRendersAsBlocked() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID),
        )

        val model = vm(repo, blockedUsers = FakeBlockedUsersStore(setOf(ARTIST_ID.lowercase())))
        advanceUntilIdle()

        assertTrue(model.state.value.blocked)
    }

    @Test
    fun aFailedBlockSurfacesAnErrorAndDoesNotClaimTheBlock() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID),
        )
        val blocked = FakeBlockedUsersStore().apply { failWrites = true }
        val model = vm(repo, blockedUsers = blocked)
        advanceUntilIdle()

        model.toggleBlocked()
        advanceUntilIdle()

        assertFalse(model.state.value.blocked)
        assertNotNull(model.state.value.error)
    }

    @Test
    fun aThreadWithNoResolvableCounterpartOffersNoBlock() = runTest {
        // No `client_id` on the row and the viewer sits in the artist seat, so
        // there is no id to block. The state carries null, which is what hides
        // the action rather than letting it aim at the viewer themselves.
        val repo = ScriptedMessages(thread = Thread(id = threadId, artistId = ARTIST_ID))
        val blocked = FakeBlockedUsersStore()
        val model = vm(repo, viewerId = ARTIST_ID, blockedUsers = blocked)
        advanceUntilIdle()

        model.toggleBlocked()
        advanceUntilIdle()

        assertNull(model.state.value.counterpartId)
        assertTrue(blocked.blocked.value.isEmpty())
    }
}
