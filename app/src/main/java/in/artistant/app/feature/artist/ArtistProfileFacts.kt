package `in`.artistant.app.feature.artist

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.ReportOutcome
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import java.util.Locale

/**
 * The strings screen 04's identity block and stat strip are made of.
 *
 * Pure, and out of the Composable, because every one of them is a place the page
 * could quietly state something it does not know: an artist with no genre must
 * not render "Indie folk band ·  · Bengaluru", a New-tier artist's score cell
 * must not read "0", and the rating pill must not appear at all for an artist
 * whose reviews failed to load. Those are the cases the tests cover.
 */
object ArtistProfileFacts {

    /** The em dash a cell renders when the fact behind it is unknown. */
    const val UNKNOWN = "—"

    /**
     * "Indie folk band · 5 pc · Bengaluru" — category, genre, city, with the
     * blanks dropped rather than rendered as empty segments.
     */
    fun subtitle(artist: Artist): String =
        listOf(artist.category, artist.genre, artist.city)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" · ")

    /**
     * "4.92 (128)" for the accent pill beside the name, or null when there is
     * nothing to average.
     *
     * Computed from the SAME list the Reviews section renders, so the pill and
     * the section can never disagree — and null on an empty list, which is also
     * what a failed read arrives as. A failed read therefore shows no pill
     * instead of "0.0 (0)".
     */
    fun ratingLabel(reviews: List<Review>): String? {
        if (reviews.isEmpty()) return null
        val average = reviews.sumOf { it.rating }.toDouble() / reviews.size
        return String.format(Locale.US, "%.2f (%d)", average, reviews.size)
    }

    /**
     * The middle stat cell: the score, or the word "New".
     *
     * A New-tier artist has a `score` column like anyone else and it is usually
     * 0 — the compute job has had under five gigs to work with. Printing that 0
     * in a cell labelled "Bookability" is the exact misreading screen 79 exists
     * to prevent, so the band decides what the cell says.
     */
    fun scoreCell(artist: Artist): String =
        if (ScoreBands.tier(artist.score, artist.gigs) == ScoreTier.New) {
            "New"
        } else {
            artist.score.toString()
        }

    /**
     * The third stat cell: `artists.response_label`, the server's own words for
     * how fast this artist answers.
     *
     * Blank is a real state — the column defaults to `''` for an artist nobody
     * has messaged — and it renders as [UNKNOWN] rather than as an invented
     * "24h", because a slow-looking figure is a claim about the artist.
     */
    fun replyCell(artist: Artist): String =
        artist.response.trim().ifEmpty { UNKNOWN }

    /**
     * The first stat cell: completed gigs on Artistant.
     *
     * Zero is a fact here, not an absence — a new artist has played none — so it
     * prints as "0" rather than as a dash.
     */
    fun showsCell(artist: Artist): String = artist.gigs.coerceAtLeast(0).toString()

    /**
     * Is the reader looking at their own act? (Screen 103.)
     *
     * Case-folded on both sides. Postgres hands UUIDs back lowercase and the
     * auth session hands them back lowercase, but the id on this route arrives
     * from a deep link, a share URL or another screen's `navigate("artist/$id")`
     * — any of which can carry the upper-case form. Comparing raw would silently
     * show a client the bookable view of their own profile, and let them file a
     * request the server's self-booking guard then rejects.
     *
     * Null viewer is "signed out", which is never self.
     */
    fun isSelfProfile(viewerId: String?, artistId: String): Boolean {
        val viewer = viewerId?.trim()?.lowercase() ?: return false
        return viewer.isNotEmpty() && viewer == artistId.trim().lowercase()
    }

    /**
     * What the toast says after a report.
     *
     * "Queued", never "received": the insert soft-fails into a local log on this
     * device, and telling a reporter their report reached Artistant when it is
     * sitting in DataStore is the overclaim screen 56's note is written against.
     */
    fun reportToast(outcome: ReportOutcome?): String? = when (outcome) {
        ReportOutcome.Sent -> "Report sent to Artistant."
        ReportOutcome.Queued -> "Report queued on this device."
        null -> null
    }
}
