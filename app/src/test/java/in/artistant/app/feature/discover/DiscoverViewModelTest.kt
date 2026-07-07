package `in`.artistant.app.feature.discover

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeSavedArtistsRepository
import `in`.artistant.app.data.repository.FakeSearchRepository
import `in`.artistant.app.data.repository.SearchRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun artist(id: String, name: String, score: Int, city: String, category: String) = Artist(
        id = id, name = name, handle = name.lowercase(), category = category, genre = "House",
        city = city, price = 20000, duration = "2 hr", score = score, gradient = ArtistGradient.palette(0),
        bio = "bio", followers = "", streams = "", response = "", onTime = 0, gigs = 9, rating = 4.5,
        packages = emptyList(), tech = emptyList(), samples = emptyList(), reviews = emptyList(),
    )

    private fun vm(search: SearchRepository) = DiscoverViewModel(
        search = search,
        artists = FakeArtistsRepository(),
        saved = SavedStore(FakeSavedArtistsRepository()),
    )

    @Test
    fun `rails load and slice correctly, with city and comedy filters honoured`() = runTest(dispatcher) {
        val roster = buildList {
            // Bangalore acts across scores.
            for (i in 1..8) add(artist("blr$i", "Blr$i", score = 50 + i, city = "Bangalore", category = "DJ"))
            // A couple of comedians.
            add(artist("com1", "Comic One", score = 88, city = "Mumbai", category = "Stand-up"))
            add(artist("com2", "Comic Two", score = 71, city = "Delhi", category = "Stand-up"))
        }
        val model = vm(FakeSearchRepository(roster))
        advanceUntilIdle()

        val s = model.state.value
        assertNull(s.loadError)
        assertTrue(s.hasContent)
        assertEquals(5, s.hero.size)                       // top page sliced to 5
        assertEquals(8, s.featured.size)                   // …to 8
        assertTrue(s.topIndia.size <= 10)
        assertTrue(s.topBangalore.all { it.city == "Bangalore" })
        assertTrue(s.comedy.all { it.category == "Stand-up" })
        assertEquals(2, s.comedy.size)
    }

    @Test
    fun `a repository failure surfaces a load error and no content`() = runTest(dispatcher) {
        val throwing = object : SearchRepository {
            override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage =
                throw IllegalStateException("network down")
            override suspend fun facets() = throw IllegalStateException("network down")
        }
        val model = vm(throwing)
        advanceUntilIdle()

        val s = model.state.value
        assertNotNull(s.loadError)
        assertTrue(s.hero.isEmpty())
        assertTrue(!s.hasContent)
    }
}
