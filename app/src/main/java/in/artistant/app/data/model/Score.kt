package `in`.artistant.app.data.model

import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import java.time.Instant

/**
 * Per-metric Bookability Score breakdown (iOS `ScoreBreakdown`). Each component
 * is a 0–100 percentage. [numericScore] is the composite, OR null when the artist
 * is "New" (< 5 completed gigs) — the view renders a "New" pill instead of a
 * misleading 0. [tier] is derived through the shared [ScoreBands] banding (reused,
 * not re-added) so tile / ring / explainer surfaces can never drift apart.
 */
data class ScoreBreakdown(
    val numericScore: Int?,
    val tier: ScoreTier,
    val showUpRate: Int,
    val reviewScore: Int,
    val replySpeed: Int,
    val cancellationRate: Int,
    val socialProof: Int,
    val totalGigs: Int,
) {
    companion object {
        /** Real-data-only fallback for a cold-launch / no-row / new artist. */
        val newArtist = ScoreBreakdown(
            numericScore = null,
            tier = ScoreTier.New,
            showUpRate = 0,
            reviewScore = 0,
            replySpeed = 0,
            cancellationRate = 0,
            socialProof = 0,
            totalGigs = 0,
        )

        /**
         * Builds a breakdown from raw metric values, deriving tier + the
         * numericScore-is-null-iff-New rule in one place (shared by the DTO
         * mapper and any synthetic breakdown). Mirrors iOS `DBScoreMetrics.toDomain`.
         */
        fun from(
            score: Int,
            showUpRate: Int,
            reviewScore: Int,
            replySpeed: Int,
            cancellationRate: Int,
            socialProof: Int,
            totalGigs: Int,
        ): ScoreBreakdown {
            val tier = ScoreBands.tier(score, totalGigs)
            return ScoreBreakdown(
                numericScore = if (tier == ScoreTier.New) null else score,
                tier = tier,
                showUpRate = showUpRate,
                reviewScore = reviewScore,
                replySpeed = replySpeed,
                cancellationRate = cancellationRate,
                socialProof = socialProof,
                totalGigs = totalGigs,
            )
        }
    }
}

/** One point in the score trajectory chart (iOS `ScoreHistoryPoint`). */
data class ScoreHistoryPoint(
    val score: Int,
    val computedAt: Instant,
)
