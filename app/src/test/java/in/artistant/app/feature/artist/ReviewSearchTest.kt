package `in`.artistant.app.feature.artist

import `in`.artistant.app.data.model.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Screen 102's arithmetic.
 *
 * The screen's whole argument is a distinction between two empty lists — "this
 * artist has no reviews" and "none of the 121 reviews match what you typed" —
 * so what is worth pinning is which inputs produce which empty.
 */
class ReviewSearchTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 4)

    private fun review(
        id: String,
        rating: Int = 5,
        body: String = "",
        name: String = "Rhea Menon",
        org: String = "",
        createdAt: String? = "2026-09-01T10:00:00Z",
    ) = Review(
        id = id,
        name = name,
        org = org,
        rating = rating,
        body = body,
        createdAt = createdAt,
    )

    // ── lenses ──────────────────────────────────────────────────────────────

    @Test
    fun `All returns the corpus untouched and in order`() {
        val corpus = listOf(review("a"), review("b", rating = 3), review("c"))
        val out = ReviewSearch.apply(corpus, query = "", lens = ReviewLens.All, today = today)
        assertEquals(corpus, out)
    }

    @Test
    fun `5 star keeps only the fives`() {
        val corpus = listOf(review("a", rating = 5), review("b", rating = 4), review("c", rating = 5))
        val out = ReviewSearch.apply(corpus, query = "", lens = ReviewLens.FiveStar, today = today)
        assertEquals(listOf("a", "c"), out.map { it.id })
    }

    @Test
    fun `Recent is a real window, not a re-sort`() {
        // The list already arrives newest-first from `created_at DESC`, so a chip
        // that only re-ordered it would do nothing and claim to have done
        // something. It filters to the stated window instead.
        val corpus = listOf(
            review("fresh", createdAt = "2026-08-30T00:00:00Z"),
            review("stale", createdAt = "2025-01-05T00:00:00Z"),
        )
        val out = ReviewSearch.apply(corpus, query = "", lens = ReviewLens.Recent, today = today)
        assertEquals(listOf("fresh"), out.map { it.id })
    }

    @Test
    fun `a row we cannot date is left out of a dated window rather than assumed recent`() {
        val corpus = listOf(
            review("dateless", createdAt = null),
            review("garbled", createdAt = "not-a-date"),
            review("fresh", createdAt = "2026-09-02T00:00:00Z"),
        )
        val out = ReviewSearch.apply(corpus, query = "", lens = ReviewLens.Recent, today = today)
        assertEquals(listOf("fresh"), out.map { it.id })
    }

    @Test
    fun `the window boundary is inclusive`() {
        val onTheEdge = today.minusDays(RECENT_WINDOW_DAYS).toString() + "T12:00:00Z"
        val out = ReviewSearch.apply(
            listOf(review("edge", createdAt = onTheEdge)),
            query = "",
            lens = ReviewLens.Recent,
            today = today,
        )
        assertEquals(1, out.size)
    }

    // ── query ───────────────────────────────────────────────────────────────

    @Test
    fun `the query matches body, reviewer and organisation`() {
        val corpus = listOf(
            review("body", body = "They played bharatanatyam pieces"),
            review("name", name = "Bharat Kumar"),
            review("org", org = "Bharatiya Vidya Bhavan"),
            review("miss", body = "Great jazz set"),
        )
        val out = ReviewSearch.apply(corpus, query = "bharat", lens = ReviewLens.All, today = today)
        assertEquals(listOf("body", "name", "org"), out.map { it.id })
    }

    @Test
    fun `the query is case and whitespace insensitive`() {
        val corpus = listOf(review("a", body = "Warm four-part HARMONIES"))
        val out = ReviewSearch.apply(corpus, query = "  harmonies ", lens = ReviewLens.All, today = today)
        assertEquals(1, out.size)
    }

    @Test
    fun `a query that matches nothing empties a non-empty corpus`() {
        // This is the state screen 102 renders, and it is NOT the same state as
        // an artist with no reviews — the corpus is still 3.
        val corpus = listOf(review("a"), review("b"), review("c"))
        val out = ReviewSearch.apply(corpus, query = "tuba", lens = ReviewLens.All, today = today)
        assertTrue(out.isEmpty())
        assertEquals(3, corpus.size)
    }

    @Test
    fun `the lens applies before the query`() {
        val corpus = listOf(
            review("four", rating = 4, body = "encore"),
            review("five", rating = 5, body = "encore"),
        )
        val out = ReviewSearch.apply(corpus, query = "encore", lens = ReviewLens.FiveStar, today = today)
        assertEquals(listOf("five"), out.map { it.id })
    }

    // ── chip labels ─────────────────────────────────────────────────────────

    @Test
    fun `only the All chip carries the corpus size`() {
        // A count on "5 star" would have to be computed over a filter the reader
        // has not applied yet — a different and more confusing claim.
        assertEquals("All 121", ReviewSearch.chipLabel(ReviewLens.All, total = 121))
        assertEquals("5 star", ReviewSearch.chipLabel(ReviewLens.FiveStar, total = 121))
        assertEquals("Recent", ReviewSearch.chipLabel(ReviewLens.Recent, total = 121))
    }

    @Test
    fun `an empty corpus does not print All 0`() {
        assertEquals("All", ReviewSearch.chipLabel(ReviewLens.All, total = 0))
    }

    // ── empty-lens copy ─────────────────────────────────────────────────────

    @Test
    fun `each lens explains its own rule when it empties the list`() {
        // The bug this pins: the blank-query empty state used to describe the
        // Recent window whatever lens was selected, so picking "5 star" on an
        // artist with none told the reader "none of the 121 reviews are in the
        // last 90 days" — a false statement about the corpus, made where they
        // cannot check it.
        val five = ReviewSearch.lensEmpty(ReviewLens.FiveStar, total = 121)
        assertTrue(five.body.contains("5 star"))
        assertFalse(five.body.contains(RECENT_WINDOW_DAYS.toString()))

        val recent = ReviewSearch.lensEmpty(ReviewLens.Recent, total = 121)
        assertTrue(recent.body.contains(RECENT_WINDOW_DAYS.toString()))
        assertFalse(recent.body.contains("5 star"))
    }

    @Test
    fun `every lens quotes the unfiltered corpus size`() {
        // The reader's real question is whether reviews exist at all, so the
        // number in the sentence is always the whole corpus — never the filtered
        // count, which would make it circular.
        ReviewLens.entries.forEach { lens ->
            assertTrue(
                "$lens must state the corpus size",
                ReviewSearch.lensEmpty(lens, total = 121).body.contains("121"),
            )
        }
    }
}
