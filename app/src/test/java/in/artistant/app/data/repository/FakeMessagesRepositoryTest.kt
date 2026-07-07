package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.MessageSender
import `in`.artistant.app.data.model.Thread
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Fake stand-in the store tests bind — pin its send/list/find-or-create contract. */
class FakeMessagesRepositoryTest {

    private val threadId = "22222222-2222-2222-2222-222222222222"

    @Test
    fun `send appends a server message and listMessages returns it`() = runTest {
        val repo = FakeMessagesRepository(threads = listOf(Thread(id = threadId, artistId = "a")))

        val sent = repo.send(threadId, "hi")

        assertEquals(MessageSender.Me, sent.sender)
        assertEquals("hi", sent.body)
        assertEquals(listOf(sent), repo.listMessages(threadId))
    }

    @Test
    fun `send throws when failSend is set`() = runTest {
        val repo = FakeMessagesRepository(threads = listOf(Thread(id = threadId, artistId = "a")), failSend = true)
        assertThrows(AppError.Unknown::class.java) { runTestSend(repo) }
    }

    @Test
    fun `findOrCreateThread reuses the same id for the same artist+booking`() = runTest {
        val repo = FakeMessagesRepository()
        val first = repo.findOrCreateThread(artistId = "artist-9", bookingId = null)
        val again = repo.findOrCreateThread(artistId = "artist-9", bookingId = null)
        assertEquals(first, again)
        assertNotNull(repo.storedThreads.firstOrNull { it.id == first })
    }

    @Test
    fun `markThreadRead zeroes the unread counter`() = runTest {
        val repo = FakeMessagesRepository(threads = listOf(Thread(id = threadId, artistId = "a", unread = 3)))
        repo.markThreadRead(threadId)
        assertEquals(0, repo.storedThreads.single().unread)
    }

    @Test
    fun `beforeSendReturns fires with the message before send returns`() = runTest {
        val repo = FakeMessagesRepository(threads = listOf(Thread(id = threadId, artistId = "a")))
        var seen = false
        repo.beforeSendReturns = { seen = true }
        repo.send(threadId, "x")
        assertTrue(seen)
    }

    // suspend send wrapped so assertThrows (which takes a non-suspend lambda) can drive it.
    private fun runTestSend(repo: FakeMessagesRepository) = kotlinx.coroutines.runBlocking {
        repo.send(threadId, "hi")
    }
}
