package `in`.artistant.app.state

import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.MessageSender
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeMessagesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 3-way reconcile across {optimistic, send-echo, realtime-echo} — the core of
 * the chat store. Each test pins one race the reconcile has to survive without
 * duplicating or losing a message (ports of the iOS MessageStore audit cases).
 */
class MessageStoreTest {

    private val threadId = "11111111-1111-1111-1111-111111111111"

    private fun thread() = Thread(id = threadId, artistId = "artist-1")

    @Test
    fun `optimistic then send-echo leaves exactly one message with the server id`() = runTest {
        val repo = FakeMessagesRepository(threads = listOf(thread()))
        val store = MessageStore(repo)
        store.setThreads(listOf(thread()))

        store.send(threadId, "hey there").join()

        val msgs = store.thread(threadId)!!.messages
        assertEquals(1, msgs.size)
        assertEquals("server-1", msgs.single().id)          // optimistic replaced, not stacked
        assertEquals(MessageDelivery.Sent, msgs.single().delivery)
    }

    @Test
    fun `realtime echo that beats the send-return does not duplicate the message`() = runTest {
        val repo = FakeMessagesRepository(threads = listOf(thread()))
        val store = MessageStore(repo)
        store.setThreads(listOf(thread()))
        // Simulate the realtime channel delivering the server row BEFORE send() returns:
        // the fake fires this with the exact message it is about to return.
        repo.beforeSendReturns = { store.receiveRealtimeMessage(threadId, it) }

        store.send(threadId, "double me").join()

        val msgs = store.thread(threadId)!!.messages
        assertEquals(1, msgs.size)                           // NOT two
        assertEquals("server-1", msgs.single().id)
    }

    @Test
    fun `failed send flips to Failed then a retry commits it`() = runTest {
        val repo = FakeMessagesRepository(threads = listOf(thread()), failSend = true)
        val store = MessageStore(repo)
        store.setThreads(listOf(thread()))

        store.send(threadId, "will fail first").join()

        val failed = store.thread(threadId)!!.messages.single()
        assertEquals(MessageDelivery.Failed, failed.delivery)

        // Recover the network and retry the SAME optimistic bubble.
        repo.failSend = false
        store.retryFailedMessage(threadId, failed.id).join()

        val msgs = store.thread(threadId)!!.messages
        assertEquals(1, msgs.size)
        assertEquals(MessageDelivery.Sent, msgs.single().delivery)
        assertEquals("server-1", msgs.single().id)
    }

    @Test
    fun `receiveRealtimeMessage dedups a message already present by id`() = runTest {
        val repo = FakeMessagesRepository(threads = listOf(thread()))
        val store = MessageStore(repo)
        store.setThreads(listOf(thread()))
        store.send(threadId, "once").join()

        val server = store.thread(threadId)!!.messages.single()
        // A late realtime echo of the SAME row must not re-append it.
        store.receiveRealtimeMessage(threadId, server)

        assertEquals(1, store.thread(threadId)!!.messages.size)
    }

    @Test
    fun `redaction gate lifts only when the thread booking is confirmed`() {
        val store = MessageStore(FakeMessagesRepository())
        val inquiry = Thread(id = threadId, artistId = "a", bookingId = null)
        val booked = Thread(id = threadId, artistId = "a", bookingId = "b1")

        // Bookingless inquiry always redacts.
        assertTrue(store.shouldRedact(inquiry, bookingConfirmed = false))
        // Pre-confirm booking still redacts; confirmed booking lifts.
        assertTrue(store.shouldRedact(booked, bookingConfirmed = false))
        assertFalse(store.shouldRedact(booked, bookingConfirmed = true))

        val body = "call me on 98765 43210"
        assertTrue("un-confirmed body is redacted", store.displayBody(body, booked, false).contains("hidden"))
        assertEquals("confirmed body is raw", body, store.displayBody(body, booked, true))
    }

    @Test
    fun `setThreads normalizes a lingering Sending message to Failed on cold load`() {
        val store = MessageStore(FakeMessagesRepository())
        val stuck = thread().copy(
            messages = listOf(
                `in`.artistant.app.data.model.Message(
                    id = "opt-1",
                    sender = MessageSender.Me,
                    body = "sent while dying",
                    sentAt = java.time.Instant.now(),
                    delivery = MessageDelivery.Sending,
                ),
            ),
        )
        store.setThreads(listOf(stuck))
        assertEquals(MessageDelivery.Failed, store.thread(threadId)!!.messages.single().delivery)
    }
}
