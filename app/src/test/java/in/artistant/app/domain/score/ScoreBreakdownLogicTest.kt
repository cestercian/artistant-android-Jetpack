package `in`.artistant.app.domain.score

import `in`.artistant.app.data.repository.ScoreBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreBreakdownLogicTest {
    @Test
    fun newArtist_numericScoreNull() {
        val b = ScoreBreakdown.NewArtist
        assertEquals(ScoreTier.New, b.tier)
        assertNull(b.numericScore)
    }

    @Test
    fun eliteWithGigs_exposesNumeric() {
        val b = ScoreBreakdown(
            score = 92,
            showUpRate = 90,
            reviewScore = 88,
            replySpeed = 80,
            cancellationRate = 5,
            socialProof = 70,
            totalGigs = 20,
        )
        assertEquals(ScoreTier.Elite, b.tier)
        assertEquals(92, b.numericScore)
    }
}
