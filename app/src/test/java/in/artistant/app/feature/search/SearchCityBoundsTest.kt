package `in`.artistant.app.feature.search

import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.model.SearchTuning
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Price facet bounds across a city switch.
 *
 * `price_histogram` is scoped by city, so `priceDataMin`/`priceDataMax` describe
 * ONE city's roster. `priceBoundsLoaded == true` is a claim that those two
 * numbers describe the roster currently being searched — and the moment the city
 * changes, that claim is about the city we just left.
 *
 * When the next city's histogram comes back empty (no priced artists there) or
 * its RPC fails, `applyHistogram` returned early after blanking the bars and left
 * the previous city's bounds and `priceBoundsLoaded` standing. Consequences:
 *
 *  - a narrowing made in city A kept posting `p_min_price`/`p_max_price` while
 *    searching city B, cutting B's results against A's price distribution, under
 *    a badge whose "1" pointed at a budget section showing no bars at all;
 *  - `SearchFilterSheet` hands the RangeSlider `value = minPrice..maxPrice` and
 *    `valueRange = priceDataMin..priceDataMax`, so the track described city A
 *    while the label under it read a selection the user could no longer relate
 *    to anything on screen.
 *
 * The contract pinned here: bounds are only ever the CURRENT scope's, so when the
 * new scope has no facet data the state reverts to exactly its cold-start shape —
 * not loaded, placeholder span, placeholder selection. "We don't know this city's
 * price range" and "we haven't looked yet" are the same epistemic state and must
 * therefore be the same state in the model.
 */
class SearchCityBoundsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `switching to a city with no histogram drops the previous city's bounds`() {
        val vm = viewModel()
        assertTrue("fixture precondition: all-cities bounds load", vm.state.value.priceBoundsLoaded)

        vm.selectCity(CITY_WITHOUT_PRICES)

        val s = vm.state.value
        assertFalse("bounds describe a city we are no longer searching", s.priceBoundsLoaded)
        assertEquals(SearchTuning.PRICE_FLOOR, s.priceDataMin)
        assertEquals(SearchTuning.PRICE_CEILING, s.priceDataMax)
    }

    @Test
    fun `a failed histogram RPC drops the bounds too`() {
        // Same handling as "no data": we don't know this city's span either way,
        // and a load that throws must not leave the app asserting stale facts.
        val vm = viewModel()

        vm.selectCity(CITY_THAT_FAILS)

        assertFalse(vm.state.value.priceBoundsLoaded)
        assertEquals(SearchTuning.PRICE_CEILING, vm.state.value.priceDataMax)
    }

    @Test
    fun `a narrowing from the previous city stops filtering the new one`() {
        val vm = viewModel()
        vm.setPriceRange(20_000, 40_000)
        assertTrue("fixture precondition: that is a real narrowing", vm.state.value.isPriceNarrowed)

        vm.selectCity(CITY_WITHOUT_PRICES)

        val s = vm.state.value
        // Both the badge and the RPC arguments read these — see `activePriceFloor`.
        assertNull(s.activePriceFloor)
        assertNull(s.activePriceCeiling)
        assertFalse(s.isPriceNarrowed)
        // The badge still counts the city itself: 1, and price is not part of it.
        assertEquals(1, s.activeFilterCount)
    }

    @Test
    fun `the selection returns to the placeholders so the slider cannot render off its own track`() {
        // The sheet's RangeSlider takes value = minPrice..maxPrice inside
        // valueRange = priceDataMin..priceDataMax. Reverting the span without
        // reverting the selection would put the thumbs outside the track.
        val vm = viewModel()
        vm.setPriceRange(20_000, 40_000)

        vm.selectCity(CITY_WITHOUT_PRICES)

        val s = vm.state.value
        assertEquals(SearchTuning.PRICE_FLOOR, s.minPrice)
        assertEquals(SearchTuning.PRICE_CEILING, s.maxPrice)
        assertTrue(s.minPrice >= s.priceDataMin && s.maxPrice <= s.priceDataMax)
    }

    @Test
    fun `a city that does have bounds adopts its own span`() {
        // The revert must not over-correct into "never trust a city switch".
        val vm = viewModel()

        vm.selectCity(CITY_WITH_NARROW_PRICES)

        val s = vm.state.value
        assertTrue(s.priceBoundsLoaded)
        assertEquals(8_000, s.priceDataMin)
        assertEquals(30_000, s.priceDataMax)
        assertEquals(8_000, s.minPrice)
        assertEquals(30_000, s.maxPrice)
        assertEquals(1, s.activeFilterCount) // the city, not the price
    }

    @Test
    fun `bounds come back when a city with data is selected again`() {
        // The revert is a statement about the current scope, not a latch.
        val vm = viewModel()

        vm.selectCity(CITY_WITHOUT_PRICES)
        vm.selectCity(CITY_WITH_NARROW_PRICES)

        val s = vm.state.value
        assertTrue(s.priceBoundsLoaded)
        assertEquals(8_000, s.priceDataMin)
        assertEquals(30_000, s.priceDataMax)
    }

    @Test
    fun `clearing the city restores the all-cities bounds`() {
        val vm = viewModel()

        vm.selectCity(CITY_WITHOUT_PRICES)
        vm.selectCity(null)

        val s = vm.state.value
        assertTrue(s.priceBoundsLoaded)
        assertEquals(5_000, s.priceDataMin)
        assertEquals(150_000, s.priceDataMax)
    }

    // --- fixtures ------------------------------------------------------------

    private companion object {
        const val CITY_WITHOUT_PRICES = "Goa"
        const val CITY_WITH_NARROW_PRICES = "Jaipur"
        const val CITY_THAT_FAILS = "Kochi"
    }

    private fun viewModel() = SearchViewModel(PerCitySearchRepository(), NoopSearchRecents())

    /**
     * Histogram varies by city, which is the whole point of the RPC taking one.
     * [CITY_THAT_FAILS] throws rather than returning empty: the real repository
     * swallows RPC errors into an empty list today, but the ViewModel talks to the
     * interface, and a seam that can throw must not take the scope down with it.
     */
    private class PerCitySearchRepository : SearchRepository {
        override suspend fun search(filters: SearchFilters, cursor: SearchCursor) =
            SearchPage(artists = emptyList(), nextCursor = SearchCursor.End)

        override suspend fun facets(): SearchFacets = SearchFacets.Empty

        override suspend fun priceHistogram(city: String?): List<PriceBucket> = when (city) {
            null -> listOf(
                PriceBucket(bucketMin = 5_000, bucketMax = 20_000, count = 4),
                PriceBucket(bucketMin = 20_000, bucketMax = 150_000, count = 9),
            )
            CITY_WITH_NARROW_PRICES -> listOf(
                PriceBucket(bucketMin = 8_000, bucketMax = 30_000, count = 3),
            )
            CITY_THAT_FAILS -> throw IllegalStateException("price_histogram unavailable")
            else -> emptyList()
        }
    }

    private class NoopSearchRecents : SearchRecents {
        override suspend fun load(): List<String> = emptyList()
        override suspend fun save(terms: List<String>) = Unit
    }
}
