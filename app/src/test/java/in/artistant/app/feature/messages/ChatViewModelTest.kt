package `in`.artistant.app.feature.messages

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeReportsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

        /**
         * When set, `send()` parks here until the test completes it. That's the
         * only way to hold the write genuinely in flight — optimistic bubble on
         * screen, server row not back yet — which is the window the Realtime
         * echo actually races.
         */
        var sendGate: CompletableDeferred<Unit>? = null

        fun emit(message: Message) = listener?.invoke(message)

        override suspend fun listThreadsForUser(): List<Thread> = listOfNotNull(thread)

        override suspend fun listMessages(threadId: String, limit: Int): List<Message> = seedMessages

        override suspend fun send(threadId: String, body: String): Message {
            sendCount++
            sendGate?.await()
            if (failSend) throw IllegalStateException("network down")
            return Message(
                id = nextServerId,
                threadId = threadId,
                senderId = "me",
                body = body,
                sentAtEpochMs = 5_000L,
                isMine = true,
            )
        }

        override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String = threadId
        override suspend fun markThreadRead(threadId: String) { markedRead++ }
        override suspend fun markThreadReadReceipt(threadId: String) = Unit
        override suspend fun counterpartLastRead(threadId: String): Long? = 9_000L

        override suspend fun subscribeMessages(
            threadId: String,
            onInsert: (Message) -> Unit,
        ): MessagesSubscription {
            listener = onInsert
            return MessagesSubscription { cancelCount++ }
        }
    }

    private fun vm(messages: MessagesRepository) = ChatViewModel(
        savedStateHandle = SavedStateHandle(mapOf("threadId" to threadId)),
        messagesRepository = messages,
        artistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        reports = FakeReportsRepository(),
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
            reports = reports,
        )

        model.reportConversation("Harassment")

        assertEquals(threadId, reports.conversation.single().first)
        assertEquals("Harassment", reports.conversation.single().second)
    }

    @Test
    fun aLoadFailureSurfacesAnErrorInsteadOfAnEmptyTranscript() = runTest {
        val failing = object : MessagesRepository {
            override suspend fun listThreadsForUser(): List<Thread> = error("offline")
            override suspend fun listMessages(threadId: String, limit: Int): List<Message> = emptyList()
            override suspend fun send(threadId: String, body: String): Message = error("offline")
            override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String = error("offline")
            override suspend fun markThreadRead(threadId: String) = Unit
            override suspend fun markThreadReadReceipt(threadId: String) = Unit
            override suspend fun counterpartLastRead(threadId: String): Long? = null
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
}
