package `in`.artistant.app.feature.messages

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.FakeMessagesRepository
import `in`.artistant.app.state.BookingStore
import `in`.artistant.app.state.MessageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * ChatViewModel drives the store's reconcile through the send/retry/receive seam the
 * screen calls. The reconcile itself is proven in MessageStoreTest; here we prove the
 * VM wiring routes to it (and exposes the ordered message list the composer renders).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val threadId = "11111111-1111-1111-1111-111111111111"

    private fun thread() = Thread(id = threadId, artistId = "artist-1")

    private fun vm(store: MessageStore) = ChatViewModel(
        savedStateHandle = SavedStateHandle(mapOf("threadId" to threadId)),
        store = store,
        artists = FakeArtistsRepository(),
        bookings = BookingStore(FakeBookingsRepository(), FakeArtistsRepository()),
    )

    @Test
    fun `optimistic send settles to the confirmed server message`() = runTest(dispatcher) {
        val store = MessageStore(FakeMessagesRepository(threads = listOf(thread())))
        store.setThreads(listOf(thread()))
        val model = vm(store)

        model.send("hey").join()
        advanceUntilIdle()

        val msgs = model.messages.value
        assertEquals(1, msgs.size)
        assertEquals("server-1", msgs.single().id)
        assertEquals(MessageDelivery.Sent, msgs.single().delivery)
    }

    @Test
    fun `a realtime echo of the just-sent message does not duplicate it`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository(threads = listOf(thread()))
        val store = MessageStore(repo)
        store.setThreads(listOf(thread()))
        // Realtime delivers the server row BEFORE send() returns — the duplicate race.
        repo.beforeSendReturns = { store.receiveRealtimeMessage(threadId, it) }
        val model = vm(store)

        model.send("double me").join()
        advanceUntilIdle()

        assertEquals(1, model.messages.value.size)
        assertEquals("server-1", model.messages.value.single().id)
    }

    @Test
    fun `a failed send flips to Failed then retry commits it`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository(threads = listOf(thread()), failSend = true)
        val store = MessageStore(repo)
        store.setThreads(listOf(thread()))
        val model = vm(store)

        model.send("will fail").join()
        advanceUntilIdle()
        val failed = model.messages.value.single()
        assertEquals(MessageDelivery.Failed, failed.delivery)

        repo.failSend = false
        model.retry(failed.id).join()
        advanceUntilIdle()

        val msgs = model.messages.value
        assertEquals(1, msgs.size)
        assertEquals(MessageDelivery.Sent, msgs.single().delivery)
        assertEquals("server-1", msgs.single().id)
    }
}
