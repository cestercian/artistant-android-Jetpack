package `in`.artistant.app.feature.artist

import `in`.artistant.app.data.model.Review
import java.time.LocalDate
import java.util.Locale

/**
 * The three views screen 102 offers over one artist's reviews.
 *
 * "Recent" is a WINDOW, not a sort: the list already arrives newest-first from
 * `reviews.created_at DESC`, so a chip that only re-sorted it would do nothing
 * and say it did something. [RECENT_WINDOW_DAYS] is stated in the chip's own
 * empty copy, because a filter whose rule the reader cannot see is a filter they
 * cannot trust.
 */
enum class ReviewLens(val label: String) {
    All("All"),
    FiveStar("5 star"),
    Recent("Recent"),
}

/** How far back the Recent lens looks. Named so the copy can quote it. */
const val RECENT_WINDOW_DAYS = 90L

/** The headline and body a lens shows when it empties a non-empty corpus. */
data class EmptyLensCopy(val title: String, val body: String)

/**
 * Search and filter within one artist's reviews — the pure half of screen 102.
 *
 * Kept out of the Composable because the screen's whole point is a distinction
 * that has to be right: an empty result with a query is "no reviews mention
 * that, out of 121", and an empty result without one is "no reviews yet". The
 * screen renders whichever of those [apply] produces, and the corpus size it
 * quotes is the unfiltered list's size — never the filtered one.
 */
object ReviewSearch {

    fun apply(
        reviews: List<Review>,
        query: String,
        lens: ReviewLens,
        today: LocalDate,
    ): List<Review> {
        val lensed = when (lens) {
            ReviewLens.All -> reviews
            ReviewLens.FiveStar -> reviews.filter { it.rating >= 5 }
            ReviewLens.Recent -> {
                val cutoff = today.minusDays(RECENT_WINDOW_DAYS)
                reviews.filter { review ->
                    val on = reviewDate(review) ?: return@filter false
                    !on.isBefore(cutoff)
                }
            }
        }
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return lensed
        return lensed.filter { review ->
            review.body.lowercase(Locale.ROOT).contains(needle) ||
                review.name.lowercase(Locale.ROOT).contains(needle) ||
                review.org.lowercase(Locale.ROOT).contains(needle)
        }
    }

    /**
     * The chip's own label — "All 121", not "All".
     *
     * The count rides the chip because screen 102's note is that the reader must
     * know the corpus exists before they read "no match". Only the All chip
     * carries it: a count on "5 star" would have to be computed over a filter the
     * reader has not applied yet, which is a different and more confusing claim.
     */
    fun chipLabel(lens: ReviewLens, total: Int): String =
        if (lens == ReviewLens.All && total > 0) "${lens.label} $total" else lens.label

    /**
     * What the screen says when a lens empties a non-empty corpus and there is
     * no query to blame.
     *
     * Per-lens, because the copy has to name the rule the reader just hit. It
     * used to describe the Recent window unconditionally, so selecting "5 star"
     * on an artist with no five-star reviews told them "none of the 121 reviews
     * are in the last 90 days" — a false statement about the corpus, made in the
     * one place the reader has no way to check it.
     *
     * [ReviewLens.All] cannot reach this state (an empty result under All means
     * an empty corpus, which the screen answers earlier), but it gets honest
     * copy anyway rather than an `error()` — a crash is a worse answer to an
     * unreachable branch than a sentence is.
     */
    fun lensEmpty(lens: ReviewLens, total: Int): EmptyLensCopy = when (lens) {
        ReviewLens.All -> EmptyLensCopy(
            title = "Nothing to show",
            body = "None of the $total reviews are available right now.",
        )
        ReviewLens.FiveStar -> EmptyLensCopy(
            title = "No five-star reviews",
            body = "None of the $total reviews are 5 star. Switch back to All to " +
                "see every one of them.",
        )
        ReviewLens.Recent -> EmptyLensCopy(
            title = "Nothing recent",
            // The window is in the BODY, not only the title: the rule the reader
            // just hit is the thing they need in order to trust the count beside
            // it, and titles get skimmed.
            body = "All $total reviews are older than $RECENT_WINDOW_DAYS days. " +
                "Switch back to All to see every one of them.",
        )
    }

    /**
     * `reviews.created_at` as a date, or null when the row has none / an
     * unparseable one. A row we cannot date is excluded from a dated window
     * rather than assumed recent.
     */
    private fun reviewDate(review: Review): LocalDate? {
        val iso = review.createdAt?.takeIf { it.length >= 10 } ?: return null
        return runCatching { LocalDate.parse(iso.substring(0, 10)) }.getOrNull()
    }
}
