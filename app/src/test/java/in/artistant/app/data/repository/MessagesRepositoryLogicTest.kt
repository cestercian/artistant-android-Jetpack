package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pins the Fake seam used by message and chat ViewModel tests. */
class MessagesRepositoryLogicTest {
    @Test
    fun send_updatesThreadPreview_andListsMessage() = runTest {
        val repo = FakeMessagesRepository()
        val threadId = repo.findOrCreateThread("11111111-1111-1111-1111-111111111111")

        val sent = repo.send(threadId, "Meet at 8?")

        assertEquals("Meet at 8?", repo.listMessages(threadId).single().body)
        assertEquals("Meet at 8?", repo.listThreadsForUser().single().lastPreview)
        assertEquals(true, sent.isMine)
    }

    @Test
    fun findOrCreate_reusesSameArtistAndBookingPair() = runTest {
        val repo = FakeMessagesRepository()

        val first = repo.findOrCreateThread("11111111-1111-1111-1111-111111111111", "booking-a")
        val same = repo.findOrCreateThread("11111111-1111-1111-1111-111111111111", "booking-a")
        val otherBooking = repo.findOrCreateThread("11111111-1111-1111-1111-111111111111", "booking-b")

        assertEquals(first, same)
        assertNotEquals(first, otherBooking)
    }
}
