package `in`.artistant.app.feature.search

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.repository.FakeSearchRepository
import `in`.artistant.app.data.repository.SearchRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeRecents : SearchRecents {
        var stored: List<String> = emptyList()
        override suspend fun load() = stored
        override suspend fun save(terms: List<String>) { stored = terms }
    }

    private fun artist(id: String, name: String, score: Int) = Artist(
        id = id, name = name, handle = name.lowercase(), category = "DJ", genre = "House",
        city = "Mumbai", price = 20000, duration = "2 hr", score = score, gradient = ArtistGradient.palette(0),
        bio = "", followers = "", streams = "", response = "", onTime = 0, gigs = 0, rating = 0.0,
        packages = emptyList(), tech = emptyList(), samples = emptyList(), reviews = emptyList(),
    )

    private fun roster(n: Int) = (1..n).map { artist("id$it", "Act$it", score = it) }

    private fun vm(repo: SearchRepository, recents: SearchRecents = FakeRecents()) =
        SearchViewModel(repo, recents)

    @Test
    fun `first-page search then loadMore paginates to the end`() = runTest(dispatcher) {
        val model = vm(FakeSearchRepository(roster(25)))
        model.onQueryChange("act")          // 25 names contain "act"
        advanceUntilIdle()

        assertEquals(20, model.state.value.results.size)
        assertTrue(model.state.value.canLoadMore)

        model.loadMore()
        advanceUntilIdle()

        assertEquals(25, model.state.value.results.size)
        assertFalse(model.state.value.canLoadMore)
    }

    @Test
    fun `rapid keystrokes debounce to a single first-page search`() = runTest(dispatcher) {
        val counter = CountingSearch(FakeSearchRepository(roster(5)))
        val model = vm(counter)
        // Three synchronous edits inside the debounce window collapse to one run.
        model.onQueryChange("a")
        model.onQueryChange("ac")
        model.onQueryChange("act")
        advanceUntilIdle()

        // Only the last query ran a first-page search (the empty initial browse
        // key clears results without a repository call).
        assertEquals(1, counter.firstPageCalls)
        assertEquals(5, model.state.value.results.size)
    }

    @Test
    fun `a stale loadMore page is dropped when a new search bumps the generation`() = runTest(dispatcher) {
        // 40 acts so the broad-"act" page 2 (Act21..40) contains rows that do NOT
        // match "act2" (Act30..40) — if the stale page leaked, the assertion below
        // would catch it.
        val gated = GatedSearch(FakeSearchRepository(roster(40)))
        val model = vm(gated)

        model.onQueryChange("act")          // page1 of the broad "act" query
        advanceUntilIdle()
        assertEquals(20, model.state.value.results.size)

        // Hold the next loadMore page in flight.
        gated.holdNextPage = true
        model.loadMore()
        advanceUntilIdle()                  // suspends inside the gated page fetch

        // A new query supersedes it (bumps the generation) and lands its results.
        model.onQueryChange("act2")         // matches Act2, Act20..Act29
        advanceUntilIdle()

        // Release the stale page — it must NOT be appended to the act2 results.
        gated.release()
        advanceUntilIdle()

        val results = model.state.value.results
        assertTrue(results.isNotEmpty())
        assertTrue("stale page leaked", results.all { it.name.lowercase().contains("act2") })
    }

    /** Counts only first-page (cursor == Start) searches. */
    private class CountingSearch(private val backing: SearchRepository) : SearchRepository {
        var firstPageCalls = 0
        override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage {
            if (cursor == SearchCursor.Start) firstPageCalls++
            return backing.search(filters, cursor)
        }
        override suspend fun facets() = backing.facets()
    }

    /** Suspends a NON-first page until [release] is called, to interleave a stale page. */
    private class GatedSearch(private val backing: SearchRepository) : SearchRepository {
        var holdNextPage = false
        private var gate: CompletableDeferred<Unit>? = null
        fun release() { gate?.complete(Unit) }
        override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage {
            if (cursor != SearchCursor.Start && holdNextPage) {
                holdNextPage = false
                gate = CompletableDeferred()
                gate!!.await()
            }
            return backing.search(filters, cursor)
        }
        override suspend fun facets() = backing.facets()
    }
}
