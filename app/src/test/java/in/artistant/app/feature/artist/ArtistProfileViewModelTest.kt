package `in`.artistant.app.feature.artist

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeReviewsRepository
import `in`.artistant.app.data.repository.FakeSavedArtistsRepository
import `in`.artistant.app.data.repository.FakeScoreRepository
import `in`.artistant.app.state.SavedStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun artist(id: String) = Artist(
        id = id, name = "Neon", handle = "neon", category = "DJ", genre = "House", city = "Mumbai",
        price = 25000, duration = "2 hr", score = 90, gradient = ArtistGradient.palette(0), bio = "real bio",
        followers = "", streams = "", response = "", onTime = 0, gigs = 12, rating = 4.9,
        packages = emptyList(), tech = emptyList(), samples = emptyList(), reviews = emptyList(),
    )

    private fun vm(
        id: String,
        artists: FakeArtistsRepository,
        reviews: FakeReviewsRepository = FakeReviewsRepository(),
    ) = ArtistProfileViewModel(
        savedStateHandle = SavedStateHandle(mapOf("artistId" to id)),
        artists = artists,
        reviewsRepo = reviews,
        scoreRepo = FakeScoreRepository(),
        saved = SavedStore(FakeSavedArtistsRepository()),
    )

    @Test
    fun `resolves the full artist and its reviews into Loaded`() = runTest(dispatcher) {
        val a = artist("a1")
        val model = vm(
            id = "a1",
            artists = FakeArtistsRepository(full = listOf(a)),
            reviews = FakeReviewsRepository(
                mapOf("a1" to listOf(Review(id = "r1", name = "Asha", org = "", rating = 5, body = "great"))),
            ),
        )
        advanceUntilIdle()

        val s = model.state.value
        assertTrue(s is ArtistProfileUiState.Loaded)
        s as ArtistProfileUiState.Loaded
        assertEquals("a1", s.artist.id)
        assertEquals("real bio", s.artist.bio)
        assertEquals(1, s.reviews.size)
        assertTrue(!s.reviewsFailed)
    }

    @Test
    fun `an unknown id resolves to NotFound`() = runTest(dispatcher) {
        val model = vm(id = "ghost", artists = FakeArtistsRepository(full = emptyList()))
        advanceUntilIdle()

        assertEquals(ArtistProfileUiState.NotFound, model.state.value)
    }
}
