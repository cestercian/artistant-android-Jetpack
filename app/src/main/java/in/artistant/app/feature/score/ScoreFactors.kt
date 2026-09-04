package `in`.artistant.app.feature.score

import `in`.artistant.app.data.repository.ScoreBreakdown
import kotlin.math.roundToInt

/**
 * The Bookability Score, itemised — the arithmetic screens 16 / 50 / 99 exist to
 * put on the page.
 *
 * Every figure here is derived from the five `metric_*` columns the
 * `compute-score` Edge Function writes onto `artists`, and from the published
 * weights those columns carry. Nothing is invented: a factor's [earned] is its
 * 0–100 metric scaled by its [weight], which is exactly what the server does
 * before summing.
 *
 * The one thing this deliberately does NOT claim is that [total] equals the
 * artist's `score`. The server owns that number and may round, floor or clamp it
 * differently; screen 99 states the same thing in words ("The total is still the
 * server's number"). So the screens render the rows and the server's score side
 * by side, and never present the sum as the score.
 */
data class ScoreFactor(
    /** The client-facing name, as the design words it. */
    val label: String,
    /** Points earned, 0..[weight]. */
    val earned: Int,
    /** Points available — the factor's share of 100. */
    val weight: Int,
    /**
     * The raw 0–100 metric this was scaled from, kept so the screens can phrase
     * an opportunity ("you're at 62 of a possible 100 on reply speed") without
     * re-deriving it out of [earned].
     */
    val metric: Int,
) {
    /** 0f..1f, for [in.artistant.app.designsystem.component.Meter]. */
    val fraction: Float get() = if (weight <= 0) 0f else earned.toFloat() / weight
    /** "28 / 30" — the fraction the reader is invited to add up. */
    val display: String get() = "$earned / $weight"
    /** Points still on the table. What an opportunity is worth. */
    val remaining: Int get() = (weight - earned).coerceAtLeast(0)
}

object ScoreFactors {

    /**
     * The published weights. They sum to 100 and mirror the Edge Function's; the
     * self-facing explainer has rendered these same five since M5.
     */
    const val SHOW_UP_WEIGHT = 30
    const val REVIEWS_WEIGHT = 25
    const val REPLY_WEIGHT = 20
    const val RELIABILITY_WEIGHT = 15
    const val SOCIAL_WEIGHT = 10

    const val SHOW_UP = "Showed up on time"
    const val REVIEWS = "Host reviews"
    const val REPLY = "Reply speed"
    const val RELIABILITY = "Kept the booking"
    const val SOCIAL = "Social proof"

    /** Every factor name, in render order — used by the *unavailable* screens. */
    val labels: List<String> = listOf(SHOW_UP, REVIEWS, REPLY, RELIABILITY, SOCIAL)

    /**
     * Itemise a loaded breakdown.
     *
     * Cancellations invert on the way in: the column counts a bad thing (percent
     * of bookings cancelled) and every other metric counts a good one, so a
     * cancellation column rendered raw would draw a full accent bar for the
     * artist who cancels most. `100 - rate` is the same fact stated the way the
     * rest of the list is stated, which is why the row is called "Kept the
     * booking" rather than "Cancellations".
     */
    fun of(breakdown: ScoreBreakdown): List<ScoreFactor> = listOf(
        factor(SHOW_UP, breakdown.showUpRate, SHOW_UP_WEIGHT),
        factor(REVIEWS, breakdown.reviewScore, REVIEWS_WEIGHT),
        factor(REPLY, breakdown.replySpeed, REPLY_WEIGHT),
        factor(RELIABILITY, 100 - breakdown.cancellationRate, RELIABILITY_WEIGHT),
        factor(SOCIAL, breakdown.socialProof, SOCIAL_WEIGHT),
    )

    /** Sum of the itemised points. See the class doc — this is not the score. */
    fun total(factors: List<ScoreFactor>): Int = factors.sumOf { it.earned }

    private fun factor(label: String, metric: Int, weight: Int): ScoreFactor {
        val clamped = metric.coerceIn(0, 100)
        return ScoreFactor(
            label = label,
            earned = (clamped / 100.0 * weight).roundToInt().coerceIn(0, weight),
            weight = weight,
            metric = clamped,
        )
    }
}

/**
 * Approximate reply time from `metric_reply_speed`, for client-facing copy.
 *
 * The metric is an invertible linear map (≤5 min → 100, ≥24 h → 0), so this
 * inverts it. Returns null for 0, which is "never measured" rather than "replies
 * in a day" — a brand-new artist has no reply history and must not be described
 * as slow.
 */
fun replyDurationLabel(speed: Int): String? {
    if (speed <= 0) return null
    val minutes = ((100 - speed.coerceIn(0, 100)) / 100.0 * (24 * 60 - 5) + 5)
        .toInt()
        .coerceAtLeast(1)
    return when {
        minutes < 60 -> "~${minutes}m"
        minutes < 24 * 60 -> "~${minutes / 60}h"
        else -> "~1d"
    }
}
