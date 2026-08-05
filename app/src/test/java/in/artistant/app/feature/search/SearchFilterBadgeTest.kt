package `in`.artistant.app.feature.search

import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.model.SearchTuning
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The filter button's badge count.
 *
 * Reported symptom: a fresh Search tab — City "Any", every accordion collapsed,
 * nothing touched — showed a "1" badge. The badge is the user's only signal that
 * results are being narrowed, so a lying badge is worse than no badge: it sends
 * people into the sheet hunting for a filter that was never set.
 *
 * Root cause is the price predicate. `priceDataMin`/`priceDataMax` start at the
 * `SearchTuning.PRICE_FLOOR`/`PRICE_CEILING` PLACEHOLDERS and are only replaced
 * once the `price_histogram` load lands. Two things then went wrong:
 *
 *  1. Before the load, the predicate compared the selection against placeholders
 *     that describe no real data at all.
 *  2. After the load, `applyHistogram` kept the untouched placeholder selection
 *     instead of snapping it to the freshly-learned span — so any roster whose
 *     real range is wider than ₹10k–₹80k (artists under the floor or above the
 *     ceiling, both ordinary) left `maxPrice < priceDataMax` true forever. That
 *     also silently posted a real `p_max_price` to `search_artists`, so results
 *     WERE being cut while the UI claimed no filters.
 *
 * The contract these tests pin: price counts as a filter only when facet bounds
 * have actually loaded AND the user has moved an end off the full data span.
 */
class SearchFilterBadgeTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // --- pure state: activeFilterCount is a computed property on the state ----

    @Test
    fun `an untouched cold state counts no filters`() {
        assertEquals(0, SearchUiState().activeFilterCount)
    }

    @Test
    fun `a price selection made before the facet bounds land is not a filter`() {
        // Nothing has loaded, so priceDataMin/Max are still placeholders. A
        // comparison against a placeholder can't tell "narrowed" from "default",
        // so the price predicate must stay silent until real bounds arrive.
        assertEquals(0, SearchUiState(minPrice = 20_000).activeFilterCount)
        assertEquals(0, SearchUiState(maxPrice = 40_000).activeFilterCount)
    }

    @Test
    fun `an unloaded bound never narrows the search either`() {
        // The badge and the RPC arguments must agree — a filter the badge refuses
        // to count must not reach `search_artists` as a price predicate.
        val cold = SearchUiState(minPrice = 20_000, maxPrice = 40_000)
        assertNull(cold.activePriceFloor)
        assertNull(cold.activePriceCeiling)
        assertFalse(cold.isPriceNarrowed)
    }

    @Test
    fun `a range equal to the loaded data span is not a filter`() {
        assertEquals(0, loadedBounds.activeFilterCount)
    }

    @Test
    fun `narrowing either end counts as exactly one filter`() {
        val loaded = loadedBounds
        assertEquals(1, loaded.copy(minPrice = 20_000).activeFilterCount)
        assertEquals(1, loaded.copy(maxPrice = 90_000).activeFilterCount)
        // Both ends moved is still ONE filter — it's one control.
        assertEquals(1, loaded.copy(minPrice = 20_000, maxPrice = 90_000).activeFilterCount)
    }

    @Test
    fun `the other filters each still count as before`() {
        assertEquals(1, SearchUiState(city = "Bangalore").activeFilterCount)
        assertEquals(1, SearchUiState(dateIso = "2026-09-12").activeFilterCount)
        assertEquals(1, SearchUiState(eventType = "Wedding").activeFilterCount)
        assertEquals(1, SearchUiState(services = setOf("dj")).activeFilterCount)
        assertEquals(1, SearchUiState(minScore = 70).activeFilterCount)
        // Categories are the Discover rails, NOT sheet filters — never counted.
        assertEquals(0, SearchUiState(categories = setOf("DJ")).activeFilterCount)
    }

    @Test
    fun `every sheet filter at once counts them all including price`() {
        val state = SearchUiState(
            city = "Bangalore",
            dateIso = "2026-09-12",
            eventType = "Wedding",
            services = setOf("dj"),
            minScore = 70,
            minPrice = 20_000,
            maxPrice = 90_000,
            priceDataMin = 5_000,
            priceDataMax = 150_000,
            priceBoundsLoaded = true,
        )
        assertEquals(6, state.activeFilterCount)
    }

    /** Post-histogram state: real roster span, selection sitting on both ends. */
    private val loadedBounds = SearchUiState(
        minPrice = 5_000,
        maxPrice = 150_000,
        priceDataMin = 5_000,
        priceDataMax = 150_000,
        priceBoundsLoaded = true,
    )

    // --- ViewModel: the reported device state ---------------------------------

    @Test
    fun `a cold Search tab shows no badge once the histogram lands`() = runTest {
        // Real-shaped roster: cheapest artist under the ₹10k placeholder floor,
        // priciest well over the ₹80k placeholder ceiling.
        val vm = viewModel(wideHistogram)

        assertEquals(0, vm.state.value.activeFilterCount)
    }

    @Test
    fun `the histogram load snaps an untouched range onto the full data span`() {
        // The selection the sheet slider renders must BE the data span when the
        // user hasn't touched it — otherwise the slider shows a narrowed thumb
        // and `runSearch` posts a p_max_price that quietly drops artists.
        val vm = viewModel(wideHistogram)

        assertEquals(5_000, vm.state.value.minPrice)
        assertEquals(150_000, vm.state.value.maxPrice)
        assertEquals(5_000, vm.state.value.priceDataMin)
        assertEquals(150_000, vm.state.value.priceDataMax)
    }

    @Test
    fun `narrowing the range after the load does raise the badge`() {
        val vm = viewModel(wideHistogram)

        vm.setPriceRange(20_000, 90_000)

        assertEquals(1, vm.state.value.activeFilterCount)
    }

    @Test
    fun `clearing filters drops the badge back to zero`() {
        val vm = viewModel(wideHistogram)
        vm.setPriceRange(20_000, 90_000)

        vm.clearFilters()

        assertEquals(0, vm.state.value.activeFilterCount)
    }

    @Test
    fun `an empty histogram leaves the badge at zero`() {
        // `price_histogram` returns nothing on a fresh/empty project. Bounds never
        // load, so the price predicate must stay silent rather than compare
        // placeholders to placeholders and guess.
        val vm = viewModel(emptyList())

        assertEquals(0, vm.state.value.activeFilterCount)
        assertEquals(SearchTuning.PRICE_FLOOR, vm.state.value.minPrice)
        assertEquals(SearchTuning.PRICE_CEILING, vm.state.value.maxPrice)
    }

    // --- fixtures ------------------------------------------------------------

    /** Spans below the placeholder floor and above the placeholder ceiling. */
    private val wideHistogram = listOf(
        PriceBucket(bucketMin = 5_000, bucketMax = 20_000, count = 4),
        PriceBucket(bucketMin = 20_000, bucketMax = 150_000, count = 9),
    )

    private fun viewModel(buckets: List<PriceBucket>) =
        SearchViewModel(HistogramSearchRepository(buckets), NoopSearchRecents())

    /** Minimal seam — only the histogram matters here; search returns nothing. */
    private class HistogramSearchRepository(
        private val buckets: List<PriceBucket>,
    ) : SearchRepository {
        override suspend fun search(filters: SearchFilters, cursor: SearchCursor) =
            SearchPage(artists = emptyList(), nextCursor = SearchCursor.End)

        override suspend fun facets(): SearchFacets = SearchFacets.Empty

        override suspend fun priceHistogram(city: String?): List<PriceBucket> = buckets
    }

    private class NoopSearchRecents : SearchRecents {
        override suspend fun load(): List<String> = emptyList()
        override suspend fun save(terms: List<String>) = Unit
    }
}
