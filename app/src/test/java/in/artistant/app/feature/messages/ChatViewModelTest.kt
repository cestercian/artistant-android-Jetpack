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

        fun emit(message: Message) = listener?.invoke(message)

        override suspend fun listThreadsForUser(): List<Thread> = listOfNotNull(thread)

        override suspend fun listMessages(threadId: String, limit: Int): List<Message> = seedMessages

        override suspend fun send(threadId: String, body: String): Message {
            sendCount++
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

    @Test
    fun aRealtimeEchoThatBeatsTheSendReturnDoesNotDuplicateTheBubble() = runTest {
        val repo = ScriptedMessages()
        val model = vm(repo)
        // Echo arrives first, carrying the id send() is about to return.
        repo.emit(serverMessage("server-1", "Meet at 8?"))

        model.send("Meet at 8?")

        assertEquals(listOf("server-1"), model.state.value.messages.map { it.id })
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

        // Counterpart happens to say the same words; the failed bubble must
        // survive so the user can still retry their own send.
        repo.emit(serverMessage("server-9", "Meet at 8?", at = 6_000L))

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
