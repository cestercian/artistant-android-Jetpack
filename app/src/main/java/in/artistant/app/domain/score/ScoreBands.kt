package `in`.artistant.app.domain.score

/**
 * Port of the iOS ScoreTier single-source-of-truth banding
 * (ScoreRepository.swift). Bands at 60 / 75 / 90; a `<5 completed gigs`
 * artist is always "New" regardless of raw score (that drift was a real iOS
 * bug — a <5-gig artist scoring ≥60 must not read "Rising").
 *
 *   new      0–60   (or < 5 completed gigs)
 *   rising   60–75
 *   trusted  75–90
 *   elite    90+
 */
enum class ScoreTier(val label: String) {
    New("New"),
    Rising("Rising"),
    Trusted("Trusted"),
    Elite("Elite"),
}

object ScoreBands {
    const val MIN_GIGS_FOR_RANK = 5

    /** [totalGigs] null = unknown; treated as ranked (matches iOS optional). */
    fun tier(score: Int, totalGigs: Int? = null): ScoreTier {
        if (totalGigs != null && totalGigs < MIN_GIGS_FOR_RANK) return ScoreTier.New
        return when {
            score >= 90 -> ScoreTier.Elite
            score >= 75 -> ScoreTier.Trusted
            score >= 60 -> ScoreTier.Rising
            else -> ScoreTier.New
        }
    }
}
