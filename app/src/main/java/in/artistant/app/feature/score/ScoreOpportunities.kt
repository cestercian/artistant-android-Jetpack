package `in`.artistant.app.feature.score

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.ScoreBreakdown

/** Where an opportunity's "fix it" tap lands. Null = there is no editor for it. */
enum class ScoreEditor { PressKit, Wizard }

/**
 * One line of advice on screen 50, and the thing it opens.
 *
 * [points] is nullable and that is the whole point of the type. The design draws
 * a "+6" pill on every row; we draw it only where a real number exists — the
 * points still unearned on a published score factor. A profile row ("add a
 * sample") improves the listing but does not feed any `metric_*` column, so it
 * ships with no pill rather than with a plausible-looking one.
 */
data class ScoreOpportunity(
    val title: String,
    val detail: String,
    val points: Int? = null,
    val editor: ScoreEditor? = null,
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
                    "You answer in $average on average. Reply speed is " +
                        "${ScoreFactors.REPLY_WEIGHT}% of the score."
                } else {
                    "Reply speed is ${ScoreFactors.REPLY_WEIGHT}% of the score and " +
                        "starts counting from your first request."
                },
                points = f.remaining,
            )
        }
        factors[ScoreFactors.REVIEWS]?.takeIf { it.remaining > 0 }?.let { f ->
            out += ScoreOpportunity(
                title = "Ask your hosts for a review",
                detail = "Reviews are ${ScoreFactors.REVIEWS_WEIGHT}% of the score. " +
                    "Only a host you actually played for can leave one.",
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
