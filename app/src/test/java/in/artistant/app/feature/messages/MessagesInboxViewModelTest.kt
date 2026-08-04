package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeMessagesRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Inbox segmentation + the counterpart-name ladder.
 *
 * The name shown per row is `client_name` (server-stamped, artist side) →
 * cached artist name (client side) → "Artist". Getting that ladder wrong is how
 * an artist ends up looking at their own name in their own inbox.
 */
class MessagesInboxViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /**
     * Serves a fixed thread list (or throws). The shipped FakeMessagesRepository
     * can't stamp `client_name` or fail a list call, which is what these rows need.
     */
    private class StaticThreads(
        private val threads: List<Thread> = emptyList(),
        private val failList: Boolean = false,
    ) : MessagesRepository {
        override suspend fun listThreadsForUser(): List<Thread> {
            if (failList) throw IllegalStateException("offline")
            return threads
        }
        override suspend fun listMessages(threadId: String, limit: Int): List<Message> = emptyList()
        override suspend fun send(threadId: String, body: String): Message = error("unused")
        override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String =
            threads.firstOrNull()?.id ?: "t-new"
        override suspend fun markThreadRead(threadId: String) = Unit
        override suspend fun markThreadReadReceipt(threadId: String) = Unit
        override suspend fun counterpartLastRead(threadId: String): Long? = null
        override suspend fun subscribeMessages(
            threadId: String,
            onInsert: (Message) -> Unit,
        ): MessagesSubscription = MessagesSubscription {}
    }

    private fun vm(
        messages: MessagesRepository,
        artists: FakeArtistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
    ) = MessagesViewModel(messagesRepository = messages, artistsRepository = artists)

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

    @Test
    fun aServerStampedClientNameWinsOverTheArtistCache() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientName = "Asha Rao"))),
        )

        assertEquals("Asha Rao", model.state.value.threads.single().counterpartName)
    }

    @Test
    fun aBlankClientNameIsIgnoredAndTheLadderContinues() = runTest {
        val model = vm(
            StaticThreads(listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientName = "  "))),
        )

        assertEquals("Nova Beats", model.state.value.threads.single().counterpartName)
    }

    @Test
    fun findOrCreateThreadHandsBackAServerId() = runTest {
        val model = vm(FakeMessagesRepository())
        var opened: String? = null

        model.findOrCreateThread(ARTIST_ID) { opened = it }

        assertNotNull(opened)
    }

    @Test
    fun aFailedInboxLoadSurfacesAnError() = runTest {
        val model = vm(StaticThreads(failList = true))

        assertNotNull(model.state.value.error)
        assertTrue(model.state.value.threads.isEmpty())
        assertFalse(model.state.value.isLoading)
    }
}
