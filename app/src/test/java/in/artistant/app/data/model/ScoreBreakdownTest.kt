package `in`.artistant.app.data.model

import `in`.artistant.app.data.model.dto.DBScoreMetrics
import `in`.artistant.app.domain.score.ScoreTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the newArtist fallback + the numericScore-is-null-iff-New rule. */
class ScoreBreakdownTest {

    @Test
    fun `newArtist is the zeroed New zero-state`() {
        val b = ScoreBreakdown.newArtist
        assertNull(b.numericScore)
        assertEquals(ScoreTier.New, b.tier)
        assertEquals(0, b.totalGigs)
        assertEquals(0, b.showUpRate)
    }

    @Test
    fun `an established high score reads its tier with a numeric score`() {
        val b = ScoreBreakdown.from(
            score = 95, showUpRate = 90, reviewScore = 88, replySpeed = 80,
            cancellationRate = 2, socialProof = 70, totalGigs = 40,
        )
        assertEquals(ScoreTier.Elite, b.tier)
        assertEquals(95, b.numericScore)
    }

    @Test
    fun `a high score with under 5 gigs is New with no numeric score`() {
        val b = ScoreBreakdown.from(
            score = 95, showUpRate = 90, reviewScore = 88, replySpeed = 80,
            cancellationRate = 2, socialProof = 70, totalGigs = 2,
        )
        assertEquals(ScoreTier.New, b.tier)
        assertNull("a <5-gig artist must never show a number", b.numericScore)
    }

    @Test
    fun `DBScoreMetrics maps real column names onto the domain`() {
        val dto = DBScoreMetrics(
            score = 78, metric_show_up = 91, metric_review_score = 85,
            metric_reply_speed = 60, metric_cancellations = 5, metric_social_proof = 40,
            total_gigs = 12,
        )
        val b = dto.toDomain()
        assertEquals(ScoreTier.Trusted, b.tier)
        assertEquals(78, b.numericScore)
        assertEquals(91, b.showUpRate)
        assertEquals(5, b.cancellationRate)
        assertEquals(12, b.totalGigs)
    }
}
