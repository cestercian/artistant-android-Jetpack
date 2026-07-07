package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.ScoreHistoryPoint

/** In-memory [ScoreRepository]. Defaults to the New zero-state (iOS fixture posture). */
class FakeScoreRepository(
    private val self: ScoreBreakdown = ScoreBreakdown.newArtist,
    private val byArtist: Map<String, ScoreBreakdown> = emptyMap(),
    private val history: List<ScoreHistoryPoint> = emptyList(),
) : ScoreRepository {
    override suspend fun breakdownForSelf(): ScoreBreakdown = self
    override suspend fun breakdown(forArtist: String): ScoreBreakdown =
        byArtist[forArtist] ?: ScoreBreakdown.newArtist
    override suspend fun historyForSelf(): List<ScoreHistoryPoint> = history
}
