package `in`.artistant.app.feature.artist

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.PendingReport
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
     * The third stat cell: how fast this artist answers, as a bare duration.
     *
     * `artists.response_label` is a whole SENTENCE — the rows on dev read
     * "Replies in ~2h" — because it was written for a surface that printed it
     * on its own. This cell has its own label under it, so the raw column
     * rendered "Replies in ~2h" stacked over "Replies in". The design (screen
     * 04) puts the value above the label, so the label half is stripped here
     * and only the duration is drawn.
     *
     * Stripped by prefix rather than by a regex over the duration: the column is
     * free text a human may have written, and a pattern that hunts for "~2h"
     * inside it would silently drop anything it did not recognise. A prefix that
     * does not match leaves the string alone, so an unexpected phrasing still
     * shows up whole.
     *
     * Blank is a real state — the column defaults to `''` for an artist nobody
     * has messaged — and it renders as [UNKNOWN] rather than as an invented
     * "24h", because a slow-looking figure is a claim about the artist. So does
     * a label with no duration in it at all ("Replies quickly" → the prefix
     * eats "Replies", leaving a word that is not a time).
     */
    fun replyCell(artist: Artist): String {
        val raw = artist.response.trim()
        if (raw.isEmpty()) return UNKNOWN
        val prefix = REPLY_PREFIXES.firstOrNull { raw.startsWith(it, ignoreCase = true) }
            ?: return raw
        // Only whitespace and separators come off after the prefix — never the
        // "~". That tilde is part of the value: "~2h" is an estimate, and
        // printing "2h" would be a firmer claim than the server made.
        return raw.drop(prefix.length).trimStart(' ', ':', '·', ',').ifEmpty { UNKNOWN }
    }

    /**
     * Label halves of `response_label`, longest first so "usually replies in"
     * is matched before "replies".
     */
    private val REPLY_PREFIXES = listOf(
        "usually replies in",
        "usually responds in",
        "typically replies in",
        "typically responds in",
        "replies in",
        "responds in",
        "replies",
        "responds",
    )

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
     *
     * [ReportOutcome.Failed] gets no toast at all. A toast is momentary and
     * unrecoverable once it fades; "nothing is holding your report" is a state
     * with an action attached, so it is a banner with a retry instead
     * ([ArtistProfileUiState.failedReport]). Returning null here is what stops
     * the two paths from both firing.
     */
    fun reportToast(outcome: ReportOutcome?): String? = when (outcome) {
        ReportOutcome.Sent -> "Report sent to Artistant."
        ReportOutcome.Queued -> "Report queued on this device."
        ReportOutcome.Failed, null -> null
    }
}

/**
 * The state a report attempt STARTS from, or null when the tap must be swallowed.
 *
 * Null is the in-flight guard, and it is why this is a function rather than two
 * lines inside `submitReport`: the ViewModel cannot be built in a JVM test — it
 * reaches `SavedStore`, which reaches DataStore — so a guard written inline is a
 * guard nothing can hold to its promise. Here the double tap is a sequence two
 * calls long and the property is stated directly.
 *
 * [ArtistProfileUiState.failedReport] is deliberately carried through unchanged.
 * On a first submit it is already null; on a retry it is the banner the reader is
 * looking at, and dropping it for the length of the round trip made the banner
 * blink out and — when the retry failed too — come straight back.
 */
internal fun ArtistProfileUiState.startingReport(): ArtistProfileUiState? =
    if (isSubmittingReport) null else copy(showReportSheet = false, isSubmittingReport = true)

/**
 * The state a finished report attempt lands on.
 *
 * [superseded] is a later attempt having started, or the screen having gone. The
 * flag is released either way — it belongs to the attempt that is finishing, and
 * an early return that left it set would wedge the retry shut for good — but no
 * outcome is claimed, because claiming one for a report the reader has moved on
 * from is how a discarded banner comes back from the dead.
 *
 * A [ReportOutcome.Failed] is durable state with the reader's own words kept for
 * the retry, never a toast, so it is the only outcome that lands in the state at
 * all. The other two are momentary and toast-shaped: [ArtistProfileFacts.reportToast]
 * raises them on the app's single host and they leave nothing here. They must still
 * CLEAR a standing banner — a retry that lands is the end of the loss it retried —
 * which is what the last branch is for.
 */
internal fun ArtistProfileUiState.settlingReport(
    outcome: ReportOutcome,
    pending: PendingReport,
    superseded: Boolean,
): ArtistProfileUiState {
    val settled = copy(isSubmittingReport = false)
    return when {
        superseded -> settled
        outcome == ReportOutcome.Failed -> settled.copy(failedReport = pending)
        else -> settled.copy(failedReport = null)
    }
}
