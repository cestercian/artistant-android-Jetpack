package `in`.artistant.app.domain.score

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreBandsTest {

    @Test
    fun `band boundaries`() {
        assertEquals(ScoreTier.New, ScoreBands.tier(59))
        assertEquals(ScoreTier.Rising, ScoreBands.tier(60))
        assertEquals(ScoreTier.Rising, ScoreBands.tier(74))
        assertEquals(ScoreTier.Trusted, ScoreBands.tier(75))
        assertEquals(ScoreTier.Trusted, ScoreBands.tier(89))
        assertEquals(ScoreTier.Elite, ScoreBands.tier(90))
        assertEquals(ScoreTier.Elite, ScoreBands.tier(100))
    }

    @Test
    fun `under 5 gigs is always New regardless of score`() {
        assertEquals(ScoreTier.New, ScoreBands.tier(95, totalGigs = 4))
        assertEquals(ScoreTier.New, ScoreBands.tier(60, totalGigs = 0))
    }

    @Test
    fun `5 or more gigs uses the raw band`() {
        assertEquals(ScoreTier.Elite, ScoreBands.tier(95, totalGigs = 5))
        assertEquals(ScoreTier.Rising, ScoreBands.tier(60, totalGigs = 12))
    }

    @Test
    fun `null gigs treated as ranked`() {
        assertEquals(ScoreTier.Elite, ScoreBands.tier(95, totalGigs = null))
    }
}
