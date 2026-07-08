package `in`.artistant.app.feature.score

import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.ScoreHistoryPoint
import `in`.artistant.app.data.repository.FakeScoreRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.domain.score.ScoreTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** ScoreExplainer VM distinguishes a real breakdown from a fetch failure; the history VM slices 30 days. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoreScreensTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `explainer loads the breakdown with its tier`() = runTest(dispatcher) {
        val repo = FakeScoreRepository(self = ScoreBreakdown.from(78, 90, 80, 70, 5, 40, 22))
        val vm = ScoreExplainerViewModel(repo)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s is ScoreExplainerUiState.Loaded)
        assertEquals(78, (s as ScoreExplainerUiState.Loaded).breakdown.numericScore)
        assertEquals(ScoreTier.Trusted, s.breakdown.tier)
    }

    @Test
    fun `a thrown breakdown becomes an explicit error, not a fake zero`() = runTest(dispatcher) {
        val throwing = object : ScoreRepository {
            override suspend fun breakdownForSelf(): ScoreBreakdown = throw RuntimeException("boom")
            override suspend fun breakdown(forArtist: String): ScoreBreakdown = ScoreBreakdown.newArtist
            override suspend fun historyForSelf(): List<ScoreHistoryPoint> = emptyList()
        }
        val vm = ScoreExplainerViewModel(throwing)
        advanceUntilIdle()
        assertEquals(ScoreExplainerUiState.Error, vm.state.value)
    }

    @Test
    fun `history VM keeps only the last 30 days`() {
        val now = Instant.parse("2026-07-08T00:00:00Z")
        val points = listOf(
            ScoreHistoryPoint(60, now.minus(Duration.ofDays(45))), // dropped
            ScoreHistoryPoint(70, now.minus(Duration.ofDays(20))),
            ScoreHistoryPoint(75, now.minus(Duration.ofDays(1))),
        )
        val window = ScoreHistoryViewModel.last30Days(points, now)
        assertEquals(listOf(70, 75), window.map { it.score })
    }
}
