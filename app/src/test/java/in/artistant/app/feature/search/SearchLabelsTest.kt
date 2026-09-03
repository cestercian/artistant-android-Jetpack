package `in`.artistant.app.feature.search

import `in`.artistant.app.data.model.SearchFacet
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.repository.FakeArtistsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The claims Search makes on screen (SearchLabels.kt).
 *
 * Every one of these is a sentence a user reads and acts on — "40 acts free",
 * "three filters", "nothing matches X" — so each gets a test rather than a
 * comment. The cases that matter are the ones where the state is legal but the
 * naive string is wrong: a count that is really a floor, a date filter that turns
 * "acts" into "acts free", a chip row that has to agree with the badge.
 */
class SearchLabelsTest {

    private val loadedBounds = SearchUiState(
        priceBoundsLoaded = true,
        priceDataMin = 10_000,
        priceDataMax = 80_000,
        minPrice = 10_000,
        maxPrice = 80_000,
    )

    // ── results title / subtitle ─────────────────────────────────────────────

    @Test
    fun `the results title names what the user asked for, in the order they said it`() {
        val state = loadedBounds.copy(
            query = "sufi",
            categories = setOf("Bands"),
            city = "Bengaluru",
        )
        assertEquals("sufi · Bands · Bengaluru", searchResultsTitle(state))
    }

    /** Narrowing only by price is legal, and leaves nothing to name. */
    @Test
    fun `the results title falls back to the screen's own name`() {
        assertEquals("Search", searchResultsTitle(loadedBounds))
    }

    /**
     * "free" is a claim about a night. Without a date filter the count is of acts
     * that MATCH, which is a different thing, and a host books against the
     * difference.
     */
    @Test
    fun `the subtitle only says free when a date is actually posted`() {
        val undated = loadedBounds.copy(results = listOf(FakeArtistsRepository.sample()))
        assertEquals("1 act", searchResultsSubtitle(undated))

        val dated = undated.copy(dateIso = "2026-10-10")
        assertEquals("Sat 10 Oct · 1 act free", searchResultsSubtitle(dated))
    }

    /** A search in flight must not claim a night is free of nobody. */
    @Test
    fun `the subtitle drops the free claim while the search is in flight`() {
        val loading = loadedBounds.copy(dateIso = "2026-10-10", isLoading = true)
        assertEquals("Sat 10 Oct · Searching…", searchResultsSubtitle(loading))
    }

    @Test
    fun `an unreadable date filter is shown rather than hidden`() {
        assertEquals("not-a-date", searchDateLabel("not-a-date"))
    }

    // ── filter chips ─────────────────────────────────────────────────────────

    /**
     * The chip row, the "N filters active" line and the badge on the button are
     * one computation. When each surface counted for itself they disagreed — a
     * badge reading 1 over a sheet with nothing set.
     */
    @Test
    fun `the chip row is exactly as long as the active filter count`() {
        val state = loadedBounds.copy(
            city = "Bengaluru",
            dateIso = "2026-10-12",
            categories = setOf("DJ", "Band"),
            eventType = "Wedding",
            services = setOf("dj-set"),
            minPrice = 20_000,
            minScore = 75,
        )
        assertEquals(state.activeFilterCount, searchFilterChips(state).size)
    }

    /** Two act types are ONE chip: the thing undone is the narrowing, not a member. */
    @Test
    fun `a multi-value filter is a single chip`() {
        val state = loadedBounds.copy(categories = setOf("DJ", "Band"))
        val chips = searchFilterChips(state)
        assertEquals(1, chips.size)
        assertEquals(SearchFilterKind.Category, chips.single().kind)
        assertEquals("Band, DJ", chips.single().label)
    }

    @Test
    fun `a service chip carries the catalogue label, never the slug`() {
        val state = loadedBounds.copy(services = setOf("dj-set"))
        assertEquals("DJ set", searchFilterChips(state).single().label)
    }

    @Test
    fun `a date chip carries its flex window`() {
        val state = loadedBounds.copy(dateIso = "2026-10-12", flexDays = 2)
        assertEquals("12 Oct ±2d", searchFilterChips(state).single().label)
    }

    /**
     * An untouched price selection is not a filter, however far the placeholders
     * happen to sit from the roster — that is what `priceBoundsLoaded` is for.
     */
    @Test
    fun `an untouched budget produces no chip`() {
        assertTrue(searchFilterChips(loadedBounds).isEmpty())
        assertTrue(searchFilterChips(SearchUiState(minPrice = 20_000)).isEmpty())
    }

    @Test
    fun `the summary line is singular for one filter`() {
        assertEquals("1 filter active · tap the chip to drop it", searchFilterSummaryLine(1))
        assertTrue(searchFilterSummaryLine(6).startsWith("6 filters active"))
    }

    // ── empty copy ───────────────────────────────────────────────────────────

    /**
     * The body has to name whichever thing is narrowing the search, because the
     * two have different escapes and a sentence that mentions only one sends half
     * the users to the wrong control.
     */
    @Test
    fun `the empty body names the query and the filters separately`() {
        assertEquals(
            "Nothing matches \"throat singing\" with your three filters on.",
            searchNoResultsBody("throat singing", 3),
        )
        assertEquals("Nothing matches \"throat singing\".", searchNoResultsBody("throat singing", 0))
        assertEquals("Nothing matches your one filter.", searchNoResultsBody("  ", 1))
    }

    @Test
    fun `an empty state always offers something to do`() {
        assertEquals(
            SearchEmptyActions("Clear filters", "Clear search"),
            searchNoResultsActions("sufi", 2),
        )
        assertEquals(SearchEmptyActions("Clear filters", null), searchNoResultsActions("", 2))
        assertEquals(SearchEmptyActions("Clear search", null), searchNoResultsActions("sufi", 0))
    }

    // ── suggestions ──────────────────────────────────────────────────────────

    private val facets = SearchFacets(
        categories = listOf(SearchFacet("Sufi fusion", 31), SearchFacet("DJ", 86)),
        cities = listOf(SearchFacet("Sufiabad", 9)),
    )

    /**
     * The design's note is "counts sit next to every suggestion". The only counts
     * this backend publishes are `search_facets`' per-category and per-city rows,
     * so a term suggestion carries one and an act suggestion carries none — rather
     * than an invented number beside a name.
     */
    @Test
    fun `term suggestions carry the facet's own count`() {
        val out = searchSuggestions("sufi", facets, emptyList())
        val terms = out.filterIsInstance<SearchSuggestion.Term>()
        assertEquals(listOf("Sufi fusion", "Sufiabad"), terms.map { it.text })
        assertEquals("31 acts", terms.first().detail)
        assertEquals("9 acts in Sufiabad", terms.last().detail)
    }

    @Test
    fun `a blank query suggests nothing at all`() {
        assertTrue(searchSuggestions("   ", facets, listOf(FakeArtistsRepository.sample())).isEmpty())
    }

    /** Acts come from the live result page, so they are what pressing Search shows. */
    @Test
    fun `acts and terms interleave and respect the limit`() {
        val acts = (1..5).map { FakeArtistsRepository.sample(id = "a$it") }
        val out = searchSuggestions("sufi", facets, acts, limit = 4)
        assertEquals(4, out.size)
        assertTrue(out[0] is SearchSuggestion.Term)
        assertTrue(out[1] is SearchSuggestion.Act)
        assertTrue(out[2] is SearchSuggestion.Term)
        assertTrue(out[3] is SearchSuggestion.Act)
    }

    /** A facet with no artists behind it is not a suggestion, it is a dead end. */
    @Test
    fun `a zero-count facet is never suggested`() {
        val empty = SearchFacets(categories = listOf(SearchFacet("Sufi fusion", 0)))
        assertNull(
            searchSuggestions("sufi", empty, emptyList())
                .filterIsInstance<SearchSuggestion.Term>()
                .firstOrNull(),
        )
    }
}
