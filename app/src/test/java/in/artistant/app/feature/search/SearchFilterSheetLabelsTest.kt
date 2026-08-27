package `in`.artistant.app.feature.search

import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * The two strings the filter sheet says about state the user can't otherwise see:
 * the collapsed Budget summary, and the primary CTA.
 *
 * Both shipped claiming something that wasn't true on a COLD sheet — the first
 * open, before anything is typed or picked:
 *
 *  - Budget compared the selection against the `SearchTuning` ₹10k/₹80k
 *    constants, but `applyHistogram` snaps an untouched selection onto the span
 *    `price_histogram` actually returned. Any roster wider or narrower than that
 *    window (i.e. essentially all of them) therefore failed the comparison and
 *    the row announced a price range nobody had set — under a filter badge that
 *    correctly read 0.
 *  - The CTA ran `results.size.coerceAtLeast(1)`, added to dodge "Show 0
 *    artists", which turned the zero into a claim: with nothing set there are no
 *    results AND no active query, so the "No matches" branch was skipped and the
 *    button read "Show 1 artist".
 *
 * Both now read the same predicates the badge and the RPC arguments read.
 */
class SearchFilterSheetLabelsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // --- Budget summary -------------------------------------------------------

    @Test
    fun `an untouched cold sheet says Any`() {
        assertEquals("Any", searchBudgetSummary(SearchUiState()))
    }

    @Test
    fun `a selection made before the facet bounds land is still Any`() {
        // Same rule the badge follows: numbers compared against placeholders
        // describe nothing, so they are not a filter and must not read as one.
        assertEquals("Any", searchBudgetSummary(SearchUiState(minPrice = 20_000)))
    }

    @Test
    fun `a range sitting on the loaded data span says Any, not the span`() {
        // The reported bug. The roster spans ₹5,000–₹2,00,000, the histogram
        // snapped the untouched selection onto it, and the row read
        // "₹5,000–₹2,00,000" as though the user had dragged both thumbs there.
        assertEquals("Any", searchBudgetSummary(loadedBounds))
    }

    @Test
    fun `narrowing an end names the range`() {
        assertEquals(
            "₹20,000–₹2,00,000",
            searchBudgetSummary(loadedBounds.copy(minPrice = 20_000)),
        )
        assertEquals(
            "₹5,000–₹90,000",
            searchBudgetSummary(loadedBounds.copy(maxPrice = 90_000)),
        )
        assertEquals(
            "₹20,000–₹90,000",
            searchBudgetSummary(loadedBounds.copy(minPrice = 20_000, maxPrice = 90_000)),
        )
    }

    @Test
    fun `the summary agrees with the badge on every state it can be in`() {
        // The point of the fix: one predicate, so the row and the badge can never
        // disagree about whether a budget is set.
        listOf(
            SearchUiState(),
            SearchUiState(minPrice = 20_000),
            loadedBounds,
            loadedBounds.copy(minPrice = 20_000),
            loadedBounds.copy(maxPrice = 90_000),
        ).forEach { state ->
            val summary = searchBudgetSummary(state)
            if (state.activeFilterCount > 0) {
                assertNotEquals("badge counts a budget but the row says Any: $state", "Any", summary)
            } else {
                assertEquals("badge counts nothing but the row names a range: $state", "Any", summary)
            }
        }
    }

    @Test
    fun `a real histogram load leaves the cold sheet reading Any`() {
        // Through the ViewModel, because the bug only appeared once
        // `applyHistogram` had snapped the selection onto the learned span.
        val vm = SearchViewModel(HistogramSearchRepository(wideHistogram), NoopSearchRecents())

        assertEquals("Any", searchBudgetSummary(vm.state.value))
    }

    // --- Apply CTA ------------------------------------------------------------

    @Test
    fun `a cold sheet does not claim one artist`() {
        assertEquals(
            "Show artists",
            searchApplyLabel(
                resultCount = 0,
                hasActiveQuery = false,
                isLoading = false,
                canLoadMore = false,
            ),
        )
    }

    @Test
    fun `an active query with nothing matching says so`() {
        assertEquals(
            "No matches",
            searchApplyLabel(
                resultCount = 0,
                hasActiveQuery = true,
                isLoading = false,
                canLoadMore = false,
            ),
        )
    }

    @Test
    fun `one result is singular`() {
        assertEquals(
            "Show 1 artist",
            searchApplyLabel(
                resultCount = 1,
                hasActiveQuery = true,
                isLoading = false,
                canLoadMore = false,
            ),
        )
    }

    @Test
    fun `a full page marks the count as a floor`() {
        assertEquals(
            "Show 20+ artists",
            searchApplyLabel(
                resultCount = 20,
                hasActiveQuery = true,
                isLoading = false,
                canLoadMore = true,
            ),
        )
    }

    @Test
    fun `a complete result set carries no plus`() {
        assertEquals(
            "Show 12 artists",
            searchApplyLabel(
                resultCount = 12,
                hasActiveQuery = true,
                isLoading = false,
                canLoadMore = false,
            ),
        )
    }

    @Test
    fun `a search in flight outranks every count`() {
        assertEquals(
            "Searching…",
            searchApplyLabel(
                resultCount = 0,
                hasActiveQuery = true,
                isLoading = true,
                canLoadMore = false,
            ),
        )
    }

    // --- fixtures ------------------------------------------------------------

    /** Post-histogram: real roster span, selection snapped onto both ends. */
    private val loadedBounds = SearchUiState(
        minPrice = 5_000,
        maxPrice = 200_000,
        priceDataMin = 5_000,
        priceDataMax = 200_000,
        priceBoundsLoaded = true,
    )

    /** Reaches below the ₹10k placeholder floor and above the ₹80k ceiling. */
    private val wideHistogram = listOf(
        PriceBucket(bucketMin = 5_000, bucketMax = 20_000, count = 4),
        PriceBucket(bucketMin = 20_000, bucketMax = 200_000, count = 9),
    )

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
