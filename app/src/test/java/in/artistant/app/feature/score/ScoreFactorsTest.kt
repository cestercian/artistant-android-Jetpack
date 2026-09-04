package `in`.artistant.app.feature.score

import `in`.artistant.app.data.repository.ScoreBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic screens 16 / 50 / 99 put on the page.
 *
 * Two things must hold or the screens are lying: the weights are the published
 * ones and sum to 100, and the cancellation column — which counts a BAD thing —
 * is inverted before it is drawn beside four columns that count good ones.
 */
class ScoreFactorsTest {

    private fun breakdown(
        score: Int = 86,
        showUp: Int = 100,
        reviews: Int = 100,
        reply: Int = 100,
        cancellations: Int = 0,
        social: Int = 100,
        gigs: Int = 128,
    ) = ScoreBreakdown(
        score = score,
        showUpRate = showUp,
        reviewScore = reviews,
        replySpeed = reply,
        cancellationRate = cancellations,
        socialProof = social,
        totalGigs = gigs,
    )

    @Test
    fun `the published weights sum to one hundred`() {
        val total = ScoreFactors.SHOW_UP_WEIGHT + ScoreFactors.REVIEWS_WEIGHT +
            ScoreFactors.REPLY_WEIGHT + ScoreFactors.RELIABILITY_WEIGHT +
            ScoreFactors.SOCIAL_WEIGHT
        assertEquals(100, total)
    }

    @Test
    fun `a perfect record earns every point`() {
        val factors = ScoreFactors.of(breakdown())
        assertEquals(100, ScoreFactors.total(factors))
        assertTrue(factors.all { it.earned == it.weight })
        assertTrue(factors.all { it.remaining == 0 })
    }

    @Test
    fun `cancellations invert so the row states a good thing`() {
        // The column counts the percentage of bookings cancelled. Drawn raw, the
        // artist who cancels most would get a full accent bar.
        val clean = ScoreFactors.of(breakdown(cancellations = 0))
            .first { it.label == ScoreFactors.RELIABILITY }
        val messy = ScoreFactors.of(breakdown(cancellations = 100))
            .first { it.label == ScoreFactors.RELIABILITY }

        assertEquals(ScoreFactors.RELIABILITY_WEIGHT, clean.earned)
        assertEquals(0, messy.earned)
    }

    @Test
    fun `points are the metric scaled by the weight`() {
        val reply = ScoreFactors.of(breakdown(reply = 50))
            .first { it.label == ScoreFactors.REPLY }
        // 50 of 100 on a 20-point factor.
        assertEquals(10, reply.earned)
        assertEquals(20, reply.weight)
        assertEquals("10 / 20", reply.display)
        assertEquals(10, reply.remaining)
        assertEquals(0.5f, reply.fraction, 0.001f)
    }

    @Test
    fun `an out-of-range metric is clamped rather than overflowing its weight`() {
        val over = ScoreFactors.of(breakdown(showUp = 140))
            .first { it.label == ScoreFactors.SHOW_UP }
        val under = ScoreFactors.of(breakdown(showUp = -20))
            .first { it.label == ScoreFactors.SHOW_UP }

        assertEquals(ScoreFactors.SHOW_UP_WEIGHT, over.earned)
        assertEquals(0, under.earned)
        assertEquals(0f, under.fraction, 0.001f)
    }

    @Test
    fun `every factor is listed, in the order the label table declares`() {
        assertEquals(
            ScoreFactors.labels,
            ScoreFactors.of(breakdown()).map { it.label },
        )
    }

    @Test
    fun `a brand-new artist itemises to zero without any factor being negative`() {
        val fresh = ScoreFactors.of(ScoreBreakdown.NewArtist)
        // NewArtist has cancellationRate 0, so reliability is the one factor a
        // no-history artist starts full on — nothing has gone wrong yet.
        assertEquals(ScoreFactors.RELIABILITY_WEIGHT, ScoreFactors.total(fresh))
        assertTrue(fresh.all { it.earned >= 0 })
    }

    // ── reply duration ──────────────────────────────────────────────────────

    @Test
    fun `an unmeasured reply speed has no duration, not a slow one`() {
        // 0 is "nobody has messaged this artist yet". Rendering it as "~1d"
        // would describe a brand-new artist as slow.
        assertNull(replyDurationLabel(0))
        assertNull(replyDurationLabel(-5))
    }

    @Test
    fun `the fastest band reads in minutes and the slowest in hours`() {
        assertEquals("~5m", replyDurationLabel(100))
        assertTrue(replyDurationLabel(50)!!.endsWith("h"))
        assertTrue(replyDurationLabel(1)!!.endsWith("h"))
    }
}
