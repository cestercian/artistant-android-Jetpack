package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeMessagesRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.CLIENT_ID
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
        viewerId: String? = CLIENT_ID,
    ) = MessagesViewModel(
        messagesRepository = messages,
        artistsRepository = artists,
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
