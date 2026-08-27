package `in`.artistant.app.feature.search

import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Clearing the search WHILE a search is in flight.
 *
 * `runSearch` has an early return for "nothing to search" — no text, no filter —
 * which empties `results` and hands the screen back to the browse rails. That
 * return used to sit ABOVE `searchJob?.cancel()` and `++generation`, so the
 * request already in the air was neither cancelled nor retired: it kept
 * `gen == generation`, and when it landed it ran the ordinary success path and
 * wrote a dead query's page, `nextCursor` and `canLoadMore` back into state.
 *
 * The visible failure needs one more beat. `results` non-empty means the results
 * branch wins over `isLoading && results.isEmpty()` in SearchScreen, so the next
 * filter tap painted the CLEARED query's tiles under the NEW filter instead of a
 * spinner — with a `canLoadMore` describing a page nobody could still page.
 *
 * No `runTest`, matching the neighbouring search tests: [MainDispatcherRule]'s
 * dispatcher is unconfined, so `viewModelScope` work runs eagerly and a gate
 * completing resumes its awaiter inline. Advancing a scheduler would also fire
 * the 280ms query debounce and start a search these tests never asked for.
 */
class SearchClearedQueryRaceTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a page that lands after the search was cleared is discarded`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        assertTrue("fixture precondition: a search is in flight", vm.state.value.isLoading)

        // Everything unset — hasActiveQuery goes false and the rails come back.
        vm.clearFilters()
        assertFalse(vm.state.value.hasActiveQuery)

        // The request from before the clear finally answers.
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))

        assertEquals(
            "a cleared search has no results — these belong to a query that is gone",
            emptyList<String>(),
            vm.state.value.results.map { it.id },
        )
        assertFalse("nothing to page through either", vm.state.value.canLoadMore)
        assertFalse(vm.state.value.isLoading)
        assertNull("a cancelled request is not a failed one", vm.state.value.loadError)
    }

    @Test
    fun `the next filter after a clear shows a spinner, not the cleared results`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        vm.clearFilters()
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))

        // The reported repro's last beat: a category chip, tapped after the clear.
        vm.toggleCategory("DJ")

        val s = vm.state.value
        assertTrue(s.isLoading)
        assertEquals(
            "SearchScreen only spins while results are empty — stale tiles suppress it",
            emptyList<String>(),
            s.results.map { it.id },
        )
    }

    @Test
    fun `a cleared search cannot leave a cursor behind for loadMore`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        vm.clearFilters()
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))
        val requestsSoFar = repo.cursors.size

        vm.loadMore()

        assertEquals("no page 2 exists for a query that was cleared", requestsSoFar, repo.cursors.size)
    }

    /** The other side of the guard: clearing must not break the next real search. */
    @Test
    fun `a search started after the clear still lands`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        vm.clearFilters()
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))

        vm.selectCity("Mumbai")
        repo.settleLast(page(ids = NEW_PAGE, next = SearchCursor.End))

        assertEquals(NEW_PAGE, vm.state.value.results.map { it.id })
        assertFalse(vm.state.value.isLoading)
    }

    // --- fixtures ------------------------------------------------------------

    private companion object {
        val OLD_PAGE = (1..20).map { "a$it" }
        val NEW_PAGE = (1..20).map { "b$it" }
    }

    private fun page(ids: List<String>, next: SearchCursor) = SearchPage(
        artists = ids.map { FakeArtistsRepository.sample(id = it, name = "N$it") },
        nextCursor = next,
    )

    /** Holds every `search()` open until the test resolves it. */
    private class GatedSearchRepository : SearchRepository {
        /** One entry per request, in call order. */
        val cursors = mutableListOf<SearchCursor>()
        private val gates = mutableListOf<CompletableDeferred<SearchPage>>()

        override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage {
            cursors += cursor
            val gate = CompletableDeferred<SearchPage>()
            gates += gate
            return gate.await()
        }

        override suspend fun facets(): SearchFacets = SearchFacets.Empty

        override suspend fun priceHistogram(city: String?): List<PriceBucket> = listOf(
            PriceBucket(bucketMin = 5_000, bucketMax = 150_000, count = 20),
        )

        /** Resolve the newest request; any earlier one was cancelled by it. */
        fun settleLast(page: SearchPage) {
            gates.last().complete(page)
        }
    }

    private class NoopSearchRecents : SearchRecents {
        override suspend fun load(): List<String> = emptyList()
        override suspend fun save(terms: List<String>) = Unit
    }
}
