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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `loadMore()` firing while a reset search is still in the air.
 *
 * The bug this pins (audit p12): a reset search rewound `nextCursor` to `Start`
 * but left `canLoadMore` describing the page it had just thrown away, and left the
 * previous query's `results` painted while it flew. `loadMore()` guarded only on
 * `isLoadingMore || !canLoadMore`, so a near-end scroll during that window passed,
 * cancelled the reset job, read the rewound cursor, re-fetched page 1 of the NEW
 * filters — and merged it onto page 1 of the OLD ones, because `reset` was false.
 *
 * Result: one list holding two different filter sets. And because SearchScreen keys
 * its grid rows by the artist ids inside them, an artist matching both queries put
 * the same key on two rows, which is a `LazyColumn`
 * `IllegalArgumentException: Key was already used` rather than a cosmetic glitch.
 *
 * Two guards close it, and both are asserted here: a reset drops `canLoadMore` with
 * the cursor, and `loadMore()` refuses to run while `isLoading` is true. The merge
 * also dedupes on artist id, so a roster whose scores move under a keyset cursor
 * can't reproduce the duplicate key from ordinary paging either.
 *
 * No `runTest` here, matching the neighbouring search tests: the rule's
 * [MainDispatcherRule] dispatcher is unconfined, so `viewModelScope` work runs
 * eagerly and a gate completing resumes its awaiter inline. Advancing a scheduler
 * would additionally fire the 280ms query debounce and start a search this test
 * never asked for.
 */
class SearchPagingRaceTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /**
     * The window itself: while a reset search is in flight there is no known "next
     * page", so nothing may claim there is one.
     */
    @Test
    fun `a reset search stops claiming another page is available`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))
        assertTrue("fixture precondition: page 1 has a successor", vm.state.value.canLoadMore)

        // A filter change kicks off a reset while page 1 is still painted.
        vm.selectCity("Mumbai")

        assertTrue(vm.state.value.isLoading)
        assertFalse("canLoadMore described the page we just discarded", vm.state.value.canLoadMore)
    }

    /** The reported failure: the scroll lands mid-reset. */
    @Test
    fun `loadMore during an in-flight reset does not append page 1 onto the old results`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))
        assertEquals(OLD_PAGE, vm.state.value.results.map { it.id })
        val requestsSoFar = repo.cursors.size

        vm.selectCity("Mumbai")
        // The old list is still on screen, so the near-end snapshotFlow fires.
        vm.loadMore()
        // Whatever is still in flight resolves with the NEW city's first page.
        repo.settleLast(page(ids = NEW_PAGE, next = SearchCursor.End))

        assertEquals(
            "the reset's own page must replace the list, not extend it",
            NEW_PAGE,
            vm.state.value.results.map { it.id },
        )
        // One request for the reset, and none for the refused loadMore.
        assertEquals(1, repo.cursors.size - requestsSoFar)
    }

    /** No duplicate key can reach the grid: ids across the list stay unique. */
    @Test
    fun `loadMore during an in-flight reset cannot duplicate an artist id`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))

        vm.selectCity("Mumbai")
        vm.loadMore()
        // "roh" → "roha": the second query's page repeats artists from the first.
        repo.settleLast(page(ids = OLD_PAGE.take(4), next = SearchCursor.End))

        val ids = vm.state.value.results.map { it.id }
        assertEquals("row keys are built from these — duplicates crash LazyColumn", ids.distinct(), ids)
        assertEquals(OLD_PAGE.take(4), ids)
    }

    /**
     * The other side of the guard: real paging still works. Closing the race must
     * not close the feature.
     */
    @Test
    fun `loadMore appends the next page once the first one has landed`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))

        vm.loadMore()
        assertEquals(
            "page 2 must be asked for from where page 1 stopped",
            SearchCursor.Offset(20),
            repo.cursors.last(),
        )
        repo.settleLast(page(ids = NEW_PAGE, next = SearchCursor.End))

        assertEquals(OLD_PAGE + NEW_PAGE, vm.state.value.results.map { it.id })
        assertFalse(vm.state.value.canLoadMore)
    }

    /**
     * An artist can appear on two pages of one query — `compute-score` rewrites the
     * column the keyset cursor orders by. That is a duplicate tile AND a duplicate
     * row key, so the merge drops the repeat rather than the grid throwing.
     */
    @Test
    fun `a page that repeats an artist from the previous page is deduped`() {
        val repo = GatedSearchRepository()
        val vm = SearchViewModel(repo, NoopSearchRecents())
        vm.selectCity("Bangalore")
        repo.settleLast(page(ids = OLD_PAGE, next = SearchCursor.Offset(20)))

        vm.loadMore()
        repo.settleLast(page(ids = listOf("a20", "b1", "b2"), next = SearchCursor.End))

        val ids = vm.state.value.results.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(OLD_PAGE + listOf("b1", "b2"), ids)
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

    /**
     * Holds every `search()` call open until the test resolves it, so a reset and a
     * page load can be interleaved deliberately. Records the cursor each call asked
     * for, which is how "it re-fetched page 1" is observable at all.
     */
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
