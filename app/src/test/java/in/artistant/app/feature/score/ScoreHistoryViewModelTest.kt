package `in`.artistant.app.feature.score

import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The score ledger, and the race a Retry button creates.
 *
 * Every one of these ViewModels loads on `init` and again on Retry, so two reads
 * can be in flight at once and can finish in either order. Nothing guarantees
 * the second call is the slower one — a retry after a hang is precisely the case
 * where the FIRST read is still outstanding — so the ViewModel has to decide
 * which result may commit. These are the tests that fail if it stops deciding.
 *
 * `ScoreHistoryViewModel` is the one that stands in for all four: it is the
 * smallest of them (one repository, one method) and the guard is the same
 * cancel-plus-generation pattern in each.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoreHistoryViewModelTest {

    // Standard, not Unconfined: the subject is the ORDER two coroutines resume
    // in, and an unconfined dispatcher runs each eagerly at its launch site —
    // the one schedule that cannot reproduce the race.
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private fun point(score: Int, iso: String) =
        ScoreHistoryPoint(score = score, computedAtIso = iso)

    private val stale = listOf(point(70, "2026-08-01T00:00:00Z"))
    private val fresh = listOf(
        point(70, "2026-08-01T00:00:00Z"),
        point(86, "2026-09-01T00:00:00Z"),
    )

    /**
     * A repository whose reads take a scripted amount of virtual time and give a
     * scripted answer, so a test can make the FIRST call the slow one.
     */
    private class ScriptedScoreRepository(
        private val script: MutableList<Pair<Long, Result<List<ScoreHistoryPoint>>>>,
    ) : ScoreRepository {
        var calls = 0
            private set

        override suspend fun historyForSelf(): List<ScoreHistoryPoint> {
            val (wait, answer) = script.removeAt(0)
            calls += 1
            delay(wait)
            return answer.getOrThrow()
        }

        override suspend fun breakdownForSelf(): ScoreBreakdown = ScoreBreakdown.NewArtist
        override suspend fun breakdown(artistId: String): ScoreBreakdown = ScoreBreakdown.NewArtist
    }

    @Test
    fun `a slow first read cannot overwrite the retry that replaced it`() = runTest {
        val repo = ScriptedScoreRepository(
            mutableListOf(
                // init: slow, and carrying the STALE answer.
                SLOW to Result.success(stale),
                // Retry: fast, and carrying the fresh one.
                FAST to Result.success(fresh),
            ),
        )

        val model = ScoreHistoryViewModel(repo)
        // Let the first read actually start and reach its suspension point. Skip
        // this and the retry cancels a job that never ran, which is the easy
        // half of the problem and not the one worth a test.
        advanceTimeBy(START_TICK)
        assertEquals("the first read must be in flight", 1, repo.calls)

        model.refresh()
        advanceUntilIdle()

        assertEquals(2, repo.calls)
        assertEquals(
            "the newer read must win regardless of which finished last",
            fresh,
            model.state.value.history,
        )
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun `a stale failure cannot resurrect the error state under a good read`() = runTest {
        // The nastier half of the same race: the first read is slow AND throws.
        // Left to land, it would flip `failed` back on over a ledger the screen
        // had already drawn — a "couldn't load your history" banner sitting above
        // the history.
        val repo = ScriptedScoreRepository(
            mutableListOf(
                SLOW to Result.failure(IllegalStateException("stale failure")),
                FAST to Result.success(fresh),
            ),
        )

        val model = ScoreHistoryViewModel(repo)
        advanceTimeBy(START_TICK)
        model.refresh()
        advanceUntilIdle()

        assertFalse("a stale throw must not overwrite a fresh success", model.state.value.failed)
        assertEquals(fresh, model.state.value.history)
    }

    @Test
    fun `a failed read is flagged rather than flattened into an empty ledger`() = runTest {
        val repo = ScriptedScoreRepository(
            mutableListOf(FAST to Result.failure(IllegalStateException("nope"))),
        )

        val model = ScoreHistoryViewModel(repo)
        advanceUntilIdle()

        assertTrue("empty and unreadable are opposite claims", model.state.value.failed)
        assertTrue(model.state.value.history.isEmpty())
        assertFalse(model.state.value.isLoading)
    }

    private companion object {
        /** Long enough that the retry always overtakes it. */
        const val SLOW = 1_000L
        const val FAST = 10L

        /** Enough virtual time for a queued coroutine to start and suspend. */
        const val START_TICK = 1L
    }
}
