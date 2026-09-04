package `in`.artistant.app.feature.score

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.ScoreBreakdown

/**
 * Where an opportunity's tap lands.
 *
 * Not nullable, and there is no "no destination" member: screen 50's note is
 * that *each one opens the thing it edits*, so a row that cannot open anything
 * has no business rendering as a card with a chevron. The two score-moving rows
 * that have no editor in the usual sense still have a place to go — the inbox
 * you reply in, and the gig list you ask a host for a review from — which is
 * where they now send the reader.
 */
enum class ScoreEditor {
    /** The press kit tab: samples, photos, packages, bio. */
    PressKit,

    /** The wizard: tech rider, socials, Spotify. */
    Wizard,

    /** The inbox — where reply speed is actually earned. */
    Messages,

    /** The gig list — the completed shows a review can be asked for. */
    Gigs,
}

/**
 * One line of advice on screen 50, and the thing it opens.
 *
 * [points] is nullable and that is the whole point of the type. The design draws
 * a "+6" pill on every row; we draw it only where a real number exists — the
 * points still unearned on a published score factor. A profile row ("add a
 * sample") improves the listing but does not feed any `metric_*` column, so it
 * ships with no pill rather than with a plausible-looking one.
 *
 * [editor] is NOT nullable, for the reason on [ScoreEditor].
 */
data class ScoreOpportunity(
    val title: String,
    val detail: String,
    val editor: ScoreEditor,
    val points: Int? = null,
)

/**
 * Screen 50's "Small wins", derived from the artist's own record.
 *
 * Two kinds of row, in one list, because that is how the design reads:
 *
 *  - **score-moving**, from [ScoreFactors]. Offered only for the three factors
 *    an artist can still act on. Show-up and cancellation history are facts
 *    about gigs that already happened — "don't cancel" is the lecture the
 *    screen's note ("advice never dead-ends") is written against, so those two
 *    are never listed even when they have points left.
 *  - **profile completeness**, from the artist row. No points, a real editor,
 *    and only when the record actually lacks the thing.
 *
 * Every row is a fact about THIS artist: nothing is offered that they have
 * already done, and nothing carries a number the server did not supply.
 */
object ScoreOpportunities {

    fun of(breakdown: ScoreBreakdown, artist: Artist?): List<ScoreOpportunity> {
        val factors = ScoreFactors.of(breakdown).associateBy { it.label }
        val out = mutableListOf<ScoreOpportunity>()

        factors[ScoreFactors.REPLY]?.takeIf { it.remaining > 0 }?.let { f ->
            val average = replyDurationLabel(f.metric)
            out += ScoreOpportunity(
                title = "Reply faster",
                detail = if (average != null) {
                    "You answer in $average on average. Opens your inbox."
                } else {
                    "Reply speed counts from your first request. Opens your inbox."
                },
                // Messages, not an editor: reply speed is not a field anyone can
                // fill in, it is earned in the thread. Sending the reader to the
                // place the metric is actually moved keeps the row's promise.
                editor = ScoreEditor.Messages,
                points = f.remaining,
            )
        }
        factors[ScoreFactors.REVIEWS]?.takeIf { it.remaining > 0 }?.let { f ->
            out += ScoreOpportunity(
                title = "Ask your hosts for a review",
                detail = "Only a host you actually played for can leave one. " +
                    "Opens your completed gigs.",
                // The gig list is where the hosts are. Nothing in the app can
                // write a review on the artist's behalf, so the useful
                // destination is the one that names who to ask.
                editor = ScoreEditor.Gigs,
                points = f.remaining,
            )
        }
        factors[ScoreFactors.SOCIAL]?.takeIf { it.remaining > 0 }?.let { f ->
            val spotify = artist?.spotifyArtistUrl.isNullOrBlank()
            out += ScoreOpportunity(
                title = if (spotify) "Connect Spotify" else "Refresh your socials",
                detail = "Social proof is ${ScoreFactors.SOCIAL_WEIGHT}% of the score.",
                points = f.remaining,
                editor = ScoreEditor.Wizard,
            )
        }

        if (artist != null) {
            if (artist.samples.isEmpty()) {
                out += ScoreOpportunity(
                    title = "Add an audio sample",
                    detail = "A client who can hear you decides faster.",
                    editor = ScoreEditor.PressKit,
                )
            }
            if (artist.gallery.isEmpty()) {
                out += ScoreOpportunity(
                    title = "Add photos from a real show",
                    detail = "The gallery is the second thing a host scrolls to.",
                    editor = ScoreEditor.PressKit,
                )
            }
            if (artist.packages.isEmpty()) {
                out += ScoreOpportunity(
                    title = "Publish a package",
                    detail = "Without one your profile can only quote on request.",
                    editor = ScoreEditor.PressKit,
                )
            }
            if (artist.bio.isBlank()) {
                out += ScoreOpportunity(
                    title = "Write your bio",
                    detail = "Two lines about the act, in your own words.",
                    editor = ScoreEditor.PressKit,
                )
            }
            if (artist.tech.isEmpty()) {
                out += ScoreOpportunity(
                    title = "Fill your tech rider",
                    detail = "Fewer load-in surprises, fewer cancellations.",
                    editor = ScoreEditor.Wizard,
                )
            }
        }
        return out
    }
}
