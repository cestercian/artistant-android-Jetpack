package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageSender
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.FakeMessagesRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.state.BookingStore
import `in`.artistant.app.state.DeepLinkRouter
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
import java.time.Instant

/**
 * MessagesViewModel — the thread list hydrate + the role-aware counterpart-name
 * resolution (the two things that differ from the store's already-tested reconcile).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val artistId = "22222222-2222-2222-2222-222222222222"
    private val threadId = "11111111-1111-1111-1111-111111111111"

    private fun artist() = Artist(
        id = artistId, name = "Neon Ray", handle = "neon", category = "DJ", genre = "House", city = "Mumbai",
        price = 25000, duration = "2 hr", score = 90, gradient = ArtistGradient.palette(0), bio = "bio",
        followers = "", streams = "", response = "", onTime = 0, gigs = 12, rating = 4.9,
        packages = emptyList(), tech = emptyList(), samples = emptyList(), reviews = emptyList(),
    )

    private fun thread(clientName: String? = null) = Thread(
        id = threadId,
        artistId = artistId,
        clientName = clientName,
        messages = listOf(Message(id = "m1", sender = MessageSender.Them, body = "hi", sentAt = Instant.now())),
    )

    private fun vm(
        threads: List<Thread>,
        artists: FakeArtistsRepository = FakeArtistsRepository(),
    ): MessagesViewModel {
        val store = MessageStore(FakeMessagesRepository(threads = threads))
        return MessagesViewModel(
            store = store,
            artists = artists,
            bookings = BookingStore(FakeBookingsRepository(), FakeArtistsRepository()),
            deepLink = DeepLinkRouter(),
        )
    }

    @Test
    fun `loads the thread set from the store`() = runTest(dispatcher) {
        val model = vm(threads = listOf(thread()))
        advanceUntilIdle()
        assertEquals(1, model.threads.value.size)
        assertEquals(threadId, model.threads.value.single().id)
    }

    @Test
    fun `client viewer resolves the counterpart to the artist name`() = runTest(dispatcher) {
        val model = vm(threads = listOf(thread()), artists = FakeArtistsRepository(full = listOf(artist())))
        advanceUntilIdle()
        assertEquals("Neon Ray", model.counterpartName(model.threads.value.single(), AppRole.Client))
    }

    @Test
    fun `client viewer falls back to Artist when the name isn't cached`() = runTest(dispatcher) {
        val model = vm(threads = listOf(thread()))
        advanceUntilIdle()
        assertEquals("Artist", model.counterpartName(model.threads.value.single(), AppRole.Client))
    }

    @Test
    fun `artist viewer resolves the counterpart to the denormalized client name`() = runTest(dispatcher) {
        val model = vm(threads = listOf(thread(clientName = "Asha Client")))
        advanceUntilIdle()
        assertEquals("Asha Client", model.counterpartName(model.threads.value.single(), AppRole.Artist))
    }

    @Test
    fun `artist viewer falls back to Client when the thread has no client name`() = runTest(dispatcher) {
        val model = vm(threads = listOf(thread(clientName = null)))
        advanceUntilIdle()
        assertEquals("Client", model.counterpartName(model.threads.value.single(), AppRole.Artist))
    }
}
