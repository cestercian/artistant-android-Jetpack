package `in`.artistant.app.feature.search

import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacet
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Getting back OUT of a category picked from the "Browse by category" rail.
 *
 * The bug this pins (audit p12): `clearFilters()` copied eight fields and not
 * `categories`, and `activeFilterCount` deliberately excluded them. Between those
 * two facts a category was a one-way door:
 *
 *  - tapping a chip sets `categories`, which makes `hasActiveQuery` true, and the
 *    screen swaps the rail — the only category toggle that exists anywhere in the
 *    app — for the results grid, so the chip you'd tap again is now off-screen;
 *  - the results header's "Clear filters" is gated on `activeFilterCount`, which
 *    didn't count categories, so it never appeared;
 *  - `SearchFilterSheet` has no category section, and its "Clear" called the
 *    method that didn't clear them.
 *
 * Search is reached through `navigateToTab`, which restores the destination's
 * `NavBackStackEntry` (`popUpTo { saveState = true }` + `restoreState = true`), so
 * the ViewModel — and the stuck category with it — survived every tab switch for
 * the rest of the session. Every artist the user could reach was a comedian.
 *
 * iOS never had the hole: `SearchStore.clearFilters()` sets `categories = []` and
 * both `hasFilters` and `activeFilterCount` count `!categories.isEmpty`, which is
 * what gates its own "Clear filters" button.
 */
class SearchCategoryEscapeTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /** The escape hatch is only rendered when the count is non-zero. */
    @Test
    fun `a railed category counts as an active filter`() {
        val vm = viewModel()

        vm.toggleCategory(COMEDY)

        assertEquals(1, vm.state.value.activeFilterCount)
    }

    /** The failure scenario, undone. */
    @Test
    fun `clearing filters releases a category so the browse rail comes back`() {
        val vm = viewModel()
        vm.toggleCategory(COMEDY)
        assertTrue("fixture precondition: the rail is swapped for results", vm.state.value.hasActiveQuery)

        vm.clearFilters()

        assertTrue(vm.state.value.categories.isEmpty())
        // False is what puts FacetBrowse — and the category chip — back on screen.
        assertFalse(vm.state.value.hasActiveQuery)
        assertEquals(0, vm.state.value.activeFilterCount)
    }

    /** Clearing must not become "clear categories only". */
    @Test
    fun `clearing filters still clears every sheet filter alongside the category`() {
        val vm = viewModel()
        vm.toggleCategory(COMEDY)
        vm.selectCity("Bangalore")
        vm.setEventType("Wedding")
        vm.toggleService("dj")
        vm.setDate("2026-09-12")
        vm.setFlexDays(3)
        vm.setMinScore(70)
        vm.setPriceRange(20_000, 90_000)
        assertEquals(7, vm.state.value.activeFilterCount)

        vm.clearFilters()

        val s = vm.state.value
        assertTrue(s.categories.isEmpty())
        assertNull(s.city)
        assertNull(s.eventType)
        assertTrue(s.services.isEmpty())
        assertNull(s.dateIso)
        assertEquals(0, s.flexDays)
        assertEquals(0, s.minScore)
        assertFalse(s.isPriceNarrowed)
        assertEquals(0, s.activeFilterCount)
    }

    /**
     * The rail's own toggle keeps working — the fix adds an exit, it doesn't
     * replace the one that was already there (for as long as it's reachable).
     */
    @Test
    fun `tapping the same chip twice still un-picks it`() {
        val vm = viewModel()

        vm.toggleCategory(COMEDY)
        vm.toggleCategory(COMEDY)

        assertTrue(vm.state.value.categories.isEmpty())
        assertFalse(vm.state.value.hasActiveQuery)
    }

    /** A second category is additive, and one clear releases both. */
    @Test
    fun `clearing filters releases every picked category`() {
        val vm = viewModel()
        vm.toggleCategory(COMEDY)
        vm.toggleCategory(DJ)
        assertEquals(setOf(COMEDY, DJ), vm.state.value.categories)
        // Still ONE filter — it's one control, same rule the price range follows.
        assertEquals(1, vm.state.value.activeFilterCount)

        vm.clearFilters()

        assertTrue(vm.state.value.categories.isEmpty())
    }

    /**
     * A category is a real search predicate, so releasing it must re-run the
     * search rather than leave the narrowed grid standing under a cleared badge.
     */
    @Test
    fun `clearing filters drops the narrowed results`() {
        val vm = viewModel()
        vm.toggleCategory(COMEDY)
        assertFalse("fixture precondition: the category search returned rows", vm.state.value.results.isEmpty())

        vm.clearFilters()

        // Nothing left to search — `runSearch` empties the grid and the screen
        // renders the browse rails instead.
        assertTrue(vm.state.value.results.isEmpty())
    }

    // --- fixtures ------------------------------------------------------------

    private companion object {
        const val COMEDY = "Comedy"
        const val DJ = "DJ"
    }

    private fun viewModel() = SearchViewModel(CategorySearchRepository(), NoopSearchRecents())

    /** Answers any category query with one row, so "results are narrowed" is observable. */
    private class CategorySearchRepository : SearchRepository {
        override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage =
            SearchPage(
                artists = listOf(FakeArtistsRepository.sample(id = "a1")),
                nextCursor = SearchCursor.End,
            )

        override suspend fun facets(): SearchFacets =
            SearchFacets(categories = listOf(SearchFacet(COMEDY, 12), SearchFacet(DJ, 8)))

        override suspend fun priceHistogram(city: String?): List<PriceBucket> = listOf(
            PriceBucket(bucketMin = 5_000, bucketMax = 150_000, count = 20),
        )
    }

    private class NoopSearchRecents : SearchRecents {
        override suspend fun load(): List<String> = emptyList()
        override suspend fun save(terms: List<String>) = Unit
    }
}
