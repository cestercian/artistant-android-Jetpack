package `in`.artistant.app.feature.score

import `in`.artistant.app.data.repository.FakeScoreRepository
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Bookability Score explainer.
 *
 * The invariant worth a test: a failed load must become a VISIBLE error, never
 * a silently-rendered zero. A "0" ring reads as "you're a terrible artist" —
 * the exact wrong message to show because a network call dropped.
 */
class ScoreExplainerViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun breakdown(score: Int, gigs: Int) = ScoreBreakdown(
        score = score,
        showUpRate = 96,
        reviewScore = 90,
        replySpeed = 80,
        cancellationRate = 2,
        socialProof = 55,
        totalGigs = gigs,
    )

    @Test
    fun loadsTheBreakdownAndHistory() = runTest {
        val model = ScoreExplainerViewModel(
            FakeScoreRepository(
                self = breakdown(score = 82, gigs = 12),
                history = listOf(ScoreHistoryPoint(score = 79, computedAtIso = "2026-05-01T00:00:00Z")),
            ),
        )

        val s = model.state.value
        assertEquals(82, s.breakdown.score)
        assertEquals(1, s.history.size)
        assertFalse(s.isLoading)
        assertNull(s.error)
    }

    @Test
    fun aThrownBreakdownBecomesAnExplicitError_notAFakeZero() = runTest {
        val model = ScoreExplainerViewModel(FakeScoreRepository(failSelf = true))

        val s = model.state.value
        assertNotNull("the UI must be able to say the score didn't load", s.error)
        assertFalse(s.isLoading)
    }

    @Test
    fun refreshRecoversAfterATransientFailure() = runTest {
        val repo = FakeScoreRepository(self = breakdown(score = 82, gigs = 12), failSelf = true)
        val model = ScoreExplainerViewModel(repo)
        assertNotNull(model.state.value.error)

        repo.failSelf = false
        model.refresh()

        assertNull(model.state.value.error)
        assertEquals(82, model.state.value.breakdown.score)
    }

    @Test
    fun aNewArtistRingIsNullRatherThanZero() {
        // <5 gigs is the "New" tier — the ring must render blank, not a 0 score.
        val fresh = breakdown(score = 40, gigs = 2)

        assertEquals(ScoreTier.New, fresh.tier)
        assertNull(fresh.numericScore)
    }

    @Test
    fun anEstablishedArtistExposesANumericScore() {
        val seasoned = breakdown(score = 82, gigs = 12)

        assertTrue(seasoned.tier != ScoreTier.New)
        assertEquals(82, seasoned.numericScore)
    }
}
