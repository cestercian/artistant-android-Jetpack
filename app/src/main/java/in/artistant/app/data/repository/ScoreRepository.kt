package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bookability Score reads — port of iOS ScoreRepository.
 * Metrics live denormalized on `artists` (written by compute-score EF);
 * history from `score_history` (self only).
 */
data class ScoreBreakdown(
    val score: Int,
    val showUpRate: Int,
    val reviewScore: Int,
    val replySpeed: Int,
    val cancellationRate: Int,
    val socialProof: Int,
    val totalGigs: Int,
) {
    val tier: ScoreTier get() = ScoreBands.tier(score, totalGigs)

    /** Null ring for New tier (<5 gigs) — matches ScoreRing nil-handling. */
    val numericScore: Int? get() = if (tier == ScoreTier.New) null else score

    companion object {
        val NewArtist = ScoreBreakdown(
            score = 0,
            showUpRate = 0,
            reviewScore = 0,
            replySpeed = 0,
            cancellationRate = 0,
            socialProof = 0,
            totalGigs = 0,
        )
    }
}

data class ScoreHistoryPoint(val score: Int, val computedAtIso: String)

interface ScoreRepository {
    suspend fun breakdownForSelf(): ScoreBreakdown
    suspend fun breakdown(artistId: String): ScoreBreakdown
    suspend fun historyForSelf(): List<ScoreHistoryPoint>
}

@Singleton
class SupabaseScoreRepository @Inject constructor(
    private val client: SupabaseClient,
) : ScoreRepository {

    override suspend fun breakdownForSelf(): ScoreBreakdown {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        return fetchBreakdown(userId)
    }

    override suspend fun breakdown(artistId: String): ScoreBreakdown {
        val id = artistId.lowercase()
        if (runCatching { java.util.UUID.fromString(id) }.isFailure) {
            return ScoreBreakdown.NewArtist
        }
        return fetchBreakdown(id)
    }

    override suspend fun historyForSelf(): List<ScoreHistoryPoint> {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        val cutoff = java.time.Instant.now().minusSeconds(365L * 24 * 3600).toString()
        return try {
            client.from("score_history")
                .select(Columns.list("score", "computed_at")) {
                    filter {
                        eq("artist_id", userId)
                        gte("computed_at", cutoff)
                    }
                    order("computed_at", Order.ASCENDING)
                    limit(5000)
                }
                .decodeList<HistoryRow>()
                .map { ScoreHistoryPoint(it.score, it.computedAt) }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    private suspend fun fetchBreakdown(artistId: String): ScoreBreakdown =
        try {
            client.from("artists")
                .select(
                    Columns.list(
                        "score",
                        "metric_show_up",
                        "metric_review_score",
                        "metric_reply_speed",
                        "metric_cancellations",
                        "metric_social_proof",
                        "total_gigs",
                    ),
                ) {
                    filter { eq("id", artistId) }
                    limit(1)
                }
                .decodeList<MetricsRow>()
                .firstOrNull()
                ?.toDomain()
                ?: ScoreBreakdown.NewArtist
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
}

class FakeScoreRepository(
    var self: ScoreBreakdown = ScoreBreakdown.NewArtist,
    var byId: Map<String, ScoreBreakdown> = emptyMap(),
    var history: List<ScoreHistoryPoint> = emptyList(),
    var failSelf: Boolean = false,
    /**
     * The history read throws, like the Supabase twin does on any transport/RLS
     * failure. Its own flag rather than an empty [history], because "no rows" and
     * "couldn't ask" are the distinction the explainer has to render differently.
     */
    var failHistory: Boolean = false,
) : ScoreRepository {
    override suspend fun breakdownForSelf(): ScoreBreakdown {
        if (failSelf) throw AppError.Unknown(IllegalStateException("score fail"))
        return self
    }

    override suspend fun breakdown(artistId: String): ScoreBreakdown =
        byId[artistId.lowercase()] ?: ScoreBreakdown.NewArtist

    override suspend fun historyForSelf(): List<ScoreHistoryPoint> {
        if (failHistory) throw AppError.Unknown(IllegalStateException("history fail"))
        return history
    }
}

@Serializable
private data class MetricsRow(
    val score: Int = 0,
    @SerialName("metric_show_up") val metricShowUp: Int = 0,
    @SerialName("metric_review_score") val metricReviewScore: Int = 0,
    @SerialName("metric_reply_speed") val metricReplySpeed: Int = 0,
    @SerialName("metric_cancellations") val metricCancellations: Int = 0,
    @SerialName("metric_social_proof") val metricSocialProof: Int = 0,
    @SerialName("total_gigs") val totalGigs: Int = 0,
) {
    fun toDomain() = ScoreBreakdown(
        score = score,
        showUpRate = metricShowUp,
        reviewScore = metricReviewScore,
        replySpeed = metricReplySpeed,
        cancellationRate = metricCancellations,
        socialProof = metricSocialProof,
        totalGigs = totalGigs,
    )
}

@Serializable
private data class HistoryRow(
    val score: Int,
    @SerialName("computed_at") val computedAt: String,
)
