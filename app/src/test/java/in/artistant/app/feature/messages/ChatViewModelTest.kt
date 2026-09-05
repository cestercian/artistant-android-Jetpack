package `in`.artistant.app.feature.messages

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeReportsRepository
import `in`.artistant.app.data.repository.FakeRequestsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.data.repository.PendingReport
import `in`.artistant.app.data.repository.ReportOutcome
import `in`.artistant.app.data.repository.ReportsRepository
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.CLIENT_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

        /**
         * What the server stamps on the row `send()` returns. Defaults to "now",
         * the way a real insert does; a test that races the RETURNING row against
         * an inbound message pins it so the two orders are comparable.
         */
        var nextServerSentAt: Long? = null

        /** What the receipts read answers. Null is also what a FAILED read gives. */
        var counterpartRead: Long? = 9_000L

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
                sentAtEpochMs = nextServerSentAt ?: System.currentTimeMillis(),
                isMine = true,
            )
        }

        override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String = threadId

        /** Which seat every mark-read named, in order — one entry per request. */
        val readSeats = mutableListOf<Boolean>()

        override suspend fun markThreadRead(threadId: String, viewerIsArtist: Boolean) {
            markedRead++
            readSeats += viewerIsArtist
        }
        /**
         * How many times the `mark_thread_read` RPC was called — the BROADCAST,
         * as distinct from [markedRead] above, which is the viewer's own badge.
         * The read-receipt preference gates exactly one of the two.
         */
        var receiptWrites: Int = 0

        override suspend fun markThreadReadReceipt(threadId: String) {
            receiptWrites++
        }
        override suspend fun counterpartLastRead(threadId: String): Long? = counterpartRead

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
        artists: FakeArtistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        requests: FakeRequestsRepository = FakeRequestsRepository(),
        reports: ReportsRepository = FakeReportsRepository(),
        readReceipts: ReadReceiptsPreference = ReadReceiptsPreference { true },
    ) = ChatViewModel(
        savedStateHandle = SavedStateHandle(mapOf("threadId" to threadId)),
        messagesRepository = messages,
        artistsRepository = artists,
        bookingsRepository = bookings,
        reports = reports,
        requests = requests,
        flagsStore = flags,
        blockedUsers = blockedUsers,
        readReceipts = readReceipts,
        viewer = { viewerId },
    )

    /**
     * A reports seam that can be held OPEN.
     *
     * `FakeReportsRepository` answers instantly, which is the one shape that
     * cannot reproduce a double tap: the window a second tap lands in is exactly
     * the round trip. [gate] holds the write there until the test releases it.
     */
    private class GatedReports(
        var outcome: ReportOutcome = ReportOutcome.Sent,
    ) : ReportsRepository {
        val gate = CompletableDeferred<Unit>()
        val conversation = mutableListOf<Triple<String, String, String?>>()

        override suspend fun reportConversation(
            threadId: String,
            reason: String,
            details: String?,
        ): ReportOutcome {
            conversation += Triple(threadId, reason, details)
            gate.await()
            return outcome
        }

        override suspend fun reportArtist(
            artistId: String,
            reason: String,
            details: String?,
        ): ReportOutcome = outcome
    }

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
        assertEquals(listOf(false), repo.readSeats)
        assertEquals(9_000L, model.state.value.counterpartLastReadAt)
    }

    /**
     * `threads` keeps ONE unread counter per side, and a PATCH aimed at the
     * counterparty's column matches zero rows — so the seat travels with the call
     * and exactly one request goes out. The seam used to fire both seat-filtered
     * PATCHes on every thread open AND every inbound Realtime row, one of them
     * guaranteed to be a no-op; probing client-first only moved that cost onto the
     * artist seat. A single entry per open, carrying the viewer's own side, is the
     * whole assertion: a regression to "write both" shows up as two entries, and a
     * regression to "always the client" shows up as `false` on the artist seat.
     */
    @Test
    fun markingReadNamesTheViewersOwnSeatAndAsksForOneWrite() = runTest {
        val seat = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID)

        val asClient = ScriptedMessages(thread = seat)
        vm(asClient, viewerId = CLIENT_ID)
        assertEquals(listOf(false), asClient.readSeats)

        val asArtist = ScriptedMessages(thread = seat)
        vm(asArtist, viewerId = ARTIST_ID)
        assertEquals(listOf(true), asArtist.readSeats)
    }

    /**
     * No thread row means no seat, and naming one would be a guess: half the time
     * it writes a column the viewer doesn't own, which the server silently drops.
     * There is nothing to clear either — the viewer is either not a participant or
     * still mid-load, and [ChatViewModel.refresh] marks read again when it lands.
     */
    @Test
    fun aThreadThatNeverLoadedAsksForNoUnreadWriteAtAll() = runTest {
        val repo = ScriptedMessages(thread = null)

        vm(repo)

        assertEquals(0, repo.markedRead)
    }

    // --- read receipts (Privacy → "Show when I've read messages", design 62) ---
    //
    // Two writes leave this screen when a thread is read and they mean opposite
    // things: `markThreadRead` zeroes the VIEWER's own badge, and
    // `markThreadReadReceipt` (the `mark_thread_read` RPC) writes the row the
    // COUNTERPARTY reads. Only the second is a broadcast, so only the second is
    // gated — and getting that backwards in either direction is a real bug:
    // gating the badge strands someone on an unread count they have read, and
    // not gating the RPC tells the other side something the switch promised it
    // wouldn't.

    @Test
    fun receiptsOnBroadcastsTheReadOnOpen() = runTest {
        val repo = ScriptedMessages()

        vm(repo, readReceipts = { true })

        assertTrue(repo.receiptWrites > 0)
    }

    @Test
    fun receiptsOffNeverCallsMarkThreadReadOnOpen() = runTest {
        val repo = ScriptedMessages()

        vm(repo, readReceipts = { false })

        assertEquals(0, repo.receiptWrites)
    }

    /**
     * The viewer's own badge is NOT the broadcast.
     *
     * Turning receipts off must still clear the unread counter, or the person who
     * asked for privacy is punished with a badge that never goes away for
     * conversations they have read.
     */
    @Test
    fun receiptsOffStillClearsTheViewersOwnUnreadCount() = runTest {
        val repo = ScriptedMessages()

        vm(repo, readReceipts = { false })

        assertTrue(repo.markedRead > 0)
        assertEquals(listOf(false), repo.readSeats)
    }

    /**
     * Every path through the choke point, not just the first.
     *
     * `markReadBestEffort` is reached on open, on an inbound realtime message,
     * and on a send that lands. A gate applied at only one of them would leak the
     * receipt the moment the conversation actually moved — which is exactly when
     * it matters.
     */
    @Test
    fun receiptsOffAlsoStaysQuietOnInboundAndOnSend() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo, readReceipts = { false })

        repo.emit(serverMessage("in-1", "Hello?", at = 6_000L, mine = false))
        model.send("On my way")
        advanceUntilIdle()

        assertEquals(0, repo.receiptWrites)
    }

    /**
     * A preference that cannot be READ fails closed — and takes nothing else
     * down with it.
     *
     * Two separate failures, and they want opposite answers.
     *
     * `enabled()` goes to DataStore, which throws on a corrupt or unreadable
     * file. Unwrapped, that throw escaped `markReadBestEffort` and killed
     * everything after it — the "mark as unread" flag was never retired and the
     * counterparty's receipt was never re-read, so a broken preference file
     * quietly disabled two unrelated behaviours. Everything outside the gate has
     * to survive.
     *
     * The gate itself is the opposite call. An absent key means enabled, but an
     * unreadable store means UNKNOWN, and among the people whose preference
     * cannot be read are the ones who turned it off — so the one
     * counterparty-visible write does not happen. Broadcasting an opted-out
     * user's read status cannot be taken back; a missing "Read by …" caption
     * costs nothing that the next successful read does not fix.
     */
    @Test
    fun anUnreadablePreferenceStaysQuietButStillClearsTheFlags() = runTest {
        val repo = ScriptedMessages()
        val flags = FakeThreadFlagsStore(ThreadFlags(markedUnread = setOf(threadId)))

        val model = vm(
            repo,
            flags = flags,
            readReceipts = { error("datastore: unreadable preferences file") },
        )
        advanceUntilIdle()

        assertEquals(
            "unknown is not consent: the broadcast must not go out",
            0,
            repo.receiptWrites,
        )
        assertTrue("the viewer's own badge must still clear", repo.markedRead > 0)
        assertTrue(
            "the explicit mark-as-unread must still be retired",
            flags.flags.first().markedUnread.isEmpty(),
        )
        assertEquals(
            "the counterparty's receipt must still be re-read",
            9_000L,
            model.state.value.counterpartLastReadAt,
        )
    }

    /** The same three paths, with the switch on, do broadcast. */
    @Test
    fun receiptsOnBroadcastsOnInboundAndOnSendToo() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo, readReceipts = { true })
        val afterOpen = repo.receiptWrites

        repo.emit(serverMessage("in-1", "Hello?", at = 6_000L, mine = false))
        model.send("On my way")
        advanceUntilIdle()

        assertTrue(repo.receiptWrites > afterOpen)
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

    /**
     * On the artist seat the thread's `artistId` IS the viewer, and no surface
     * here reads that profile: the header keeps the client's name and `artistId`
     * is nulled so the details sheet shows a plain participant row. Hydrating it
     * is a round trip bought for assignments nothing renders.
     */
    @Test
    fun theArtistSeatNeverFetchesTheViewersOwnProfile() = runTest {
        val artists = FakeArtistsRepository(remote = listOf(artist(name = "Nova Beats")))
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientName = "Asha Rao"),
        )

        val model = vm(repo, viewerId = ARTIST_ID, artists = artists)
        advanceUntilIdle()

        assertTrue(artists.fetchedIds.isEmpty())
        assertEquals("Asha Rao", model.state.value.title)
    }

    /**
     * The client seat still hydrates a cold cache — a chat opened from a push
     * never passed through the inbox, so this is what names the header at all.
     */
    @Test
    fun theClientSeatHydratesTheArtistWhenTheCacheIsCold() = runTest {
        val artists = FakeArtistsRepository(remote = listOf(artist(name = "Nova Beats")))
        val repo = ScriptedMessages(thread = Thread(id = threadId, artistId = ARTIST_ID))

        val model = vm(repo, viewerId = CLIENT_ID, artists = artists)
        advanceUntilIdle()

        assertEquals(listOf(ARTIST_ID.lowercase()), artists.fetchedIds)
        assertEquals("Nova Beats", model.state.value.title)
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

    /**
     * The other half of that same window: the counterparty's reply lands on the
     * socket while the viewer's own write is still waiting for its RETURNING row.
     * The server stamped the send FIRST, so the confirmed bubble belongs ABOVE
     * the reply — appending it at the tail put the viewer's message underneath a
     * message that answered it, and a pair straddling midnight printed the day
     * separator twice.
     */
    @Test
    fun aConfirmedSendSettlesInSentAtOrderEvenWhenAReplyBeatsItBack() = runTest {
        val now = System.currentTimeMillis()
        val repo = ScriptedMessages().apply {
            sendGate = CompletableDeferred()
            nextServerSentAt = now + 500
        }
        val model = vm(repo)

        model.send("On my way")
        repo.emit(serverMessage("them-1", "ok", at = now + 1_000, mine = false))
        repo.sendGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("server-1", "them-1"), model.state.value.messages.map { it.id })
    }

    /**
     * The transcript is keyed by id in a LazyColumn, which throws on a duplicate
     * key rather than degrading — so two bubbles minted in the same millisecond
     * would take the chat screen down.
     */
    @Test
    fun twoBubblesInFlightAtOnceCarryDistinctOptimisticIds() = runTest {
        val repo = ScriptedMessages().apply { sendGate = CompletableDeferred() }
        val model = vm(repo)

        model.send("first")
        model.send("second")

        val ids = model.state.value.messages.map { it.id }
        assertEquals(2, ids.size)
        assertEquals("optimistic ids must be unique", 2, ids.toSet().size)

        repo.sendGate!!.complete(Unit)
        advanceUntilIdle()
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

    /**
     * The failed bubble carried its own retry, so the strip above the composer
     * stays out of the way while it is on screen. Once the retry lands the bubble
     * is gone — and the strip, which speaks for the CONVERSATION failing to load,
     * would take over and report a refresh that never failed.
     */
    @Test
    fun aRetryThatLandsRetiresTheFailureItLeftBehind() = runTest {
        val repo = ScriptedMessages().apply { failSend = true }
        val model = vm(repo)
        model.send("Meet at 8?")
        val failedId = model.state.value.messages.single().id
        assertNotNull(model.state.value.error)

        repo.failSend = false
        model.retryFailedMessage(failedId)

        assertNull(model.state.value.error)
    }

    /**
     * The send-failure buzz.
     *
     * It comes off an EVENT rather than off a derived failed-count for a reason a
     * count can't cover: a retry that fails again leaves the count exactly where
     * it was, so a count-watcher goes silent on the second attempt — the one the
     * user most needs told about. Two failures, two events.
     */
    @Test
    fun everyFailedSendEmitsItsOwnFailureEvent() = runTest {
        val repo = ScriptedMessages().apply { failSend = true }
        val model = vm(repo)
        val events = mutableListOf<ChatEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events += it } }

        model.send("Meet at 8?")
        advanceUntilIdle()
        assertEquals(listOf(ChatEvent.SendFailed), events)

        val failedId = model.state.value.messages.single().id
        model.retryFailedMessage(failedId)
        advanceUntilIdle()
        assertEquals(listOf(ChatEvent.SendFailed, ChatEvent.SendFailed), events)
    }

    @Test
    fun aSendThatLandsEmitsNothing() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)
        val events = mutableListOf<ChatEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events += it } }

        model.send("Meet at 8?")
        advanceUntilIdle()

        assertTrue(events.isEmpty())
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
            requests = FakeRequestsRepository(),
            flagsStore = FakeThreadFlagsStore(),
            blockedUsers = FakeBlockedUsersStore(),
            readReceipts = { true },
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
            override suspend fun markThreadRead(threadId: String, viewerIsArtist: Boolean) = Unit
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

    /**
     * A receipts read answers null for three different reasons — no row, nothing
     * read yet, and a read that failed (the seam swallows its own throw). This
     * runs again on every inbound message, so writing null through made one
     * dropped request erase a receipt the counterparty had genuinely left: the
     * "Read" caption blinked out mid-conversation and came back on the next call.
     * Receipts only move forward, so the last known one is kept.
     */
    @Test
    fun aReceiptReadThatAnswersNothingKeepsTheLastKnownReceipt() = runTest {
        val repo = ScriptedMessages(
            seedMessages = listOf(serverMessage("s1", "Meet at 8?", at = 5_000L, mine = true)),
        )
        val model = vm(repo)
        assertEquals(9_000L, model.state.value.counterpartLastReadAt)

        repo.counterpartRead = null
        repo.emit(serverMessage("them-1", "ok", at = 10_000L, mine = false))
        advanceUntilIdle()

        assertEquals(9_000L, model.state.value.counterpartLastReadAt)
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

    // --- the in-thread quote (design 08) -------------------------------------

    private fun openQuote(id: String = "q-1", amount: Int = 48_000, clientId: String = CLIENT_ID) =
        StoredRequest(
            raw = GigRequest(
                id = id,
                client = "Rhea",
                message = "",
                date = "Sat 12 Oct",
                amount = amount,
                artistId = ARTIST_ID,
                clientId = clientId,
                expiresAtEpochMs = 4_102_444_800_000L,
            ),
            status = GigRequestStatus.Open,
        )

    /** The inquiry thread the gig-request loop actually lives in (mig 0047/0076). */
    private fun inquiryThread() = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID)

    @Test
    fun theChatShowsTheQuoteStandingBetweenThisPair() = runTest {
        val model = vm(
            ScriptedMessages(thread = inquiryThread()),
            viewerId = ARTIST_ID,
            requests = FakeRequestsRepository(listOf(openQuote())),
        )
        advanceUntilIdle()

        assertEquals(48_000, model.state.value.quote?.amountInr)
        assertTrue("open is the artist's to answer", model.state.value.quote!!.actionable)
    }

    /** The thread is about its booking; the quote loop lives in the bookingless one. */
    @Test
    fun aThreadWithABookingBehindItShowsNoQuoteCard() = runTest {
        val model = vm(
            ScriptedMessages(
                thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID, bookingId = "b-1"),
            ),
            viewerId = ARTIST_ID,
            requests = FakeRequestsRepository(listOf(openQuote())),
        )
        advanceUntilIdle()

        assertNull(model.state.value.quote)
    }

    /** Two live rows between one pair and nothing that says which: no card, no buttons. */
    @Test
    fun twoLiveQuotesBetweenThePairShowNoCard() = runTest {
        val model = vm(
            ScriptedMessages(thread = inquiryThread()),
            viewerId = ARTIST_ID,
            requests = FakeRequestsRepository(listOf(openQuote("q-1"), openQuote("q-2", 12_000))),
        )
        advanceUntilIdle()

        assertNull(model.state.value.quote)
    }

    /**
     * Accepting completes, and completing means the card becomes the record.
     *
     * There is nowhere else for it to go: `accept` is a status PATCH, and its
     * only server reaction (mig 0047, rewritten by 0076) is to open the
     * bookingless thread it is already in — that migration deliberately creates
     * no booking, and `bookings_insert_client` would refuse one from the artist
     * seat anyway. So the assertions are the whole outcome: the row is accepted,
     * the card is frozen and un-actionable, the narration has ended, and the
     * event carries no id because there is no destination.
     */
    @Test
    fun acceptingAQuoteFreezesTheCardAndEndsThere() = runTest {
        val requests = FakeRequestsRepository(listOf(openQuote()))
        val model = vm(
            ScriptedMessages(thread = inquiryThread()),
            viewerId = ARTIST_ID,
            requests = requests,
        )
        advanceUntilIdle()
        val events = mutableListOf<ChatEvent>()
        val collector = launch { model.events.collect { events += it } }

        model.acceptQuote()
        advanceUntilIdle()

        assertEquals(GigRequestStatus.Accepted, requests.listForArtist().single().status)
        val quote = model.state.value.quote
        assertTrue("the card is the record now", quote!!.frozen)
        assertFalse("a record has no buttons", quote.actionable)
        assertEquals(QuoteAction.Idle, model.state.value.quoteAction)
        assertEquals(listOf(ChatEvent.QuoteAccepted), events)
        collector.cancel()
    }

    /**
     * A write that didn't land must not leave a card claiming it did — and it
     * must not leave the narration running over a screen with no way out.
     */
    @Test
    fun anAcceptThatFailsSaysSoAndLeavesTheQuoteOpen() = runTest {
        val requests = FakeRequestsRepository(listOf(openQuote()))
        val model = vm(
            ScriptedMessages(thread = inquiryThread()),
            viewerId = ARTIST_ID,
            requests = requests,
        )
        advanceUntilIdle()
        // Fails only the WRITE: the card has already loaded off the read above.
        requests.signedIn = false

        model.acceptQuote()
        advanceUntilIdle()

        assertTrue(model.state.value.quoteAction is QuoteAction.Failed)
        assertFalse("nothing was agreed, so nothing is frozen", model.state.value.quote!!.frozen)
        assertTrue("and it is still the viewer's to answer", model.state.value.quote!!.actionable)
    }

    // --- report --------------------------------------------------------------

    /**
     * The repository soft-fails to an on-device log rather than throwing, and it
     * SAYS which of the three things happened — see the outcome tests below,
     * which are what stop this surface claiming a delivery it did not get.
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
            requests = FakeRequestsRepository(),
            flagsStore = FakeThreadFlagsStore(),
            blockedUsers = FakeBlockedUsersStore(),
            readReceipts = { true },
            viewer = { CLIENT_ID },
        )

        model.reportConversation("Scam or spam")

        assertEquals(ReportOutcome.Sent, model.state.value.report.outcome)
        assertNull(model.state.value.report.failed)
        assertEquals("Scam or spam", reports.conversation.single().second)
    }

    /**
     * A report that only reached THIS DEVICE is not a report the safety team has.
     *
     * `Queued` still lands in `ReportSubmission.outcome` — it is not a failure and the copy
     * must not call it one — but it is a different receipt and, on screen, a
     * different sentence. The failure this pins is the one where all three
     * outcomes flipped one boolean and printed "the report is with our safety
     * team" over every one of them.
     */
    @Test
    fun aQueuedReportIsAReceiptButNotAFailureAndNotADelivery() = runTest {
        val reports = FakeReportsRepository(outcome = ReportOutcome.Queued)
        val model = vm(ScriptedMessages(), reports = reports)

        model.reportConversation("Spam or a scam")
        advanceUntilIdle()

        assertEquals(ReportOutcome.Queued, model.state.value.report.outcome)
        assertNull("queued is not lost", model.state.value.report.failed)
    }

    /**
     * A report nothing is holding is not a receipt at all.
     *
     * Neither the insert nor the on-device log kept it, so there is nothing to
     * confirm — and the reader's own words have to come back with the retry,
     * because asking someone to write out a second time what upset them enough
     * to report is its own small harm.
     */
    @Test
    fun aFailedReportBecomesDurableStateCarryingTheReadersOwnWords() = runTest {
        val reports = FakeReportsRepository(outcome = ReportOutcome.Failed)
        val model = vm(ScriptedMessages(), reports = reports)

        model.reportConversation("Pressuring or aggressive messages", "he keeps calling")
        advanceUntilIdle()

        assertNull("nothing landed, so nothing may be confirmed", model.state.value.report.outcome)
        assertEquals(
            PendingReport("Pressuring or aggressive messages", "he keeps calling"),
            model.state.value.report.failed,
        )
    }

    /** Retry re-files what they already wrote, and a retry that lands clears the failure. */
    @Test
    fun retryingALostReportRefilesItAndClearsTheFailureWhenItLands() = runTest {
        val reports = FakeReportsRepository(outcome = ReportOutcome.Failed)
        val model = vm(ScriptedMessages(), reports = reports)
        model.reportConversation("Spam or a scam", "link in every message")
        advanceUntilIdle()

        reports.outcome = ReportOutcome.Sent
        model.retryReport()
        advanceUntilIdle()

        assertEquals(2, reports.conversation.size)
        assertEquals(
            "the retry must carry the same words, not an empty form",
            Triple(threadId, "Spam or a scam", "link in every message"),
            reports.conversation.last(),
        )
        assertEquals(ReportOutcome.Sent, model.state.value.report.outcome)
        assertNull(model.state.value.report.failed)
    }

    /**
     * Two taps on Submit file ONE report.
     *
     * The form does not close on submit — it stays up and becomes a receipt only
     * when the outcome lands — so the CTA is live for the whole round trip, and
     * the second tap used to file the same row again. Duplicates are not a
     * cosmetic problem: they are two rows in `public.reports` about one person
     * for one thing, which the moderation team reads as a pattern.
     */
    @Test
    fun aDoubleTapOnSubmitFilesTheReportOnce() = runTest {
        val reports = GatedReports()
        val model = vm(ScriptedMessages(), reports = reports)

        model.reportConversation("Spam or a scam", "same link twice")
        advanceUntilIdle()
        assertTrue(
            "the form has to lock itself while the write is out",
            model.state.value.report.inFlight,
        )

        model.reportConversation("Spam or a scam", "same link twice")
        advanceUntilIdle()

        assertEquals("the second tap must not reach the seam", 1, reports.conversation.size)

        reports.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, reports.conversation.size)
        assertEquals(ReportOutcome.Sent, model.state.value.report.outcome)
        assertFalse(
            "and the form unlocks when the answer lands",
            model.state.value.report.inFlight,
        )
    }

    /** The retry button is the same door: it must not open a second write either. */
    @Test
    fun retryCannotStartASecondReportWhileOneIsInFlight() = runTest {
        val reports = GatedReports(outcome = ReportOutcome.Failed)
        val model = vm(ScriptedMessages(), reports = reports)
        model.reportConversation("Pressuring or aggressive messages")
        advanceUntilIdle()

        model.retryReport()
        advanceUntilIdle()

        assertEquals(1, reports.conversation.size)

        reports.gate.complete(Unit)
        advanceUntilIdle()

        assertNotNull(model.state.value.report.failed)
        assertFalse(model.state.value.report.inFlight)
    }

    /**
     * Closing the sheet clears the RECEIPT but never the failure.
     *
     * A receipt is a momentary fact and the next open should start on the
     * actions. "Your safety report was lost" is a state: it goes away when it is
     * fixed or explicitly discarded, not because a sheet was dismissed.
     */
    @Test
    fun dismissingDetailsResetsTheReceiptButKeepsALostReport() = runTest {
        val model = vm(ScriptedMessages())
        model.reportConversation("Offensive")
        advanceUntilIdle()

        model.dismissDetails()

        assertNull(model.state.value.report.outcome)

        val lost = FakeReportsRepository(outcome = ReportOutcome.Failed)
        val second = vm(ScriptedMessages(), reports = lost)
        second.reportConversation("Offensive")
        advanceUntilIdle()

        second.dismissDetails()

        assertNotNull(
            "a lost report must survive the sheet closing",
            second.state.value.report.failed,
        )
        second.discardFailedReport()
        assertNull("and only an explicit discard clears it", second.state.value.report.failed)
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
        assertNotNull(model.state.value.actionError)
        // NOT `error`: that slot speaks for the transcript, and the two surfaces
        // reading it say "couldn't refresh this conversation" with a Retry that
        // reloads — over a conversation that loaded perfectly well. On a thread
        // with no messages yet it replaces the transcript outright.
        assertNull(model.state.value.error)
    }

    /** The next toggle that lands retires the line; nothing else was clearing it. */
    @Test
    fun aLaterSuccessfulToggleClearsTheFailureLine() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID),
        ).apply { failMute = true }
        val model = vm(repo)
        advanceUntilIdle()
        model.toggleMuted()
        advanceUntilIdle()
        assertNotNull(model.state.value.actionError)

        repo.failMute = false
        model.toggleMuted()
        advanceUntilIdle()

        assertNull(model.state.value.actionError)
        assertTrue(model.state.value.muted)
    }

    /** The line lives on the sheet, so closing the sheet takes it with it. */
    @Test
    fun closingTheDetailsSheetClearsTheFailureLine() = runTest {
        val repo = ScriptedMessages(
            thread = Thread(id = threadId, artistId = ARTIST_ID, clientId = CLIENT_ID),
        ).apply { failMute = true }
        val model = vm(repo)
        advanceUntilIdle()
        model.openDetails()
        model.toggleMuted()
        advanceUntilIdle()
        assertNotNull(model.state.value.actionError)

        model.dismissDetails()

        assertNull(model.state.value.actionError)
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
        assertNotNull(model.state.value.actionError)
        // Same split as the failed mute, and it matters most here: a block is
        // often attempted on a brand-new thread, where routing this into `error`
        // replaced the whole transcript with a load-failure screen.
        assertNull(model.state.value.error)
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
