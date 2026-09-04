package `in`.artistant.app.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The results header's count copy.
 *
 * Small surface, but every branch is one a user reads, and two of them are the
 * classic ways this string ships wrong: "1 acts", and a flat total over a
 * list that pages (so the number on screen is a floor, not a count).
 */
class SearchResultCountLabelTest {

    @Test
    fun `a cold load says searching rather than zero results`() {
        assertEquals(
            "Searching…",
            searchResultCountLabel(count = 0, isLoading = true, canLoadMore = false),
        )
    }

    @Test
    fun `a load with results already on screen keeps counting them`() {
        // Paging keeps isLoading true while rows are already visible; falling back
        // to "Searching…" there would blank a count the user is looking at.
        assertEquals(
            "12 acts+",
            searchResultCountLabel(count = 12, isLoading = true, canLoadMore = true),
        )
    }

    @Test
    fun `an empty result set is reported, not hidden`() {
        assertEquals(
            "0 acts",
            searchResultCountLabel(count = 0, isLoading = false, canLoadMore = false),
        )
    }

    @Test
    fun `one result is singular`() {
        assertEquals(
            "1 act",
            searchResultCountLabel(count = 1, isLoading = false, canLoadMore = false),
        )
    }

    @Test
    fun `more pages available marks the count as a floor`() {
        assertEquals(
            "1 act+",
            searchResultCountLabel(count = 1, isLoading = false, canLoadMore = true),
        )
    }

    @Test
    fun `a complete result set carries no plus`() {
        assertEquals(
            "24 acts",
            searchResultCountLabel(count = 24, isLoading = false, canLoadMore = false),
        )
    }
}
