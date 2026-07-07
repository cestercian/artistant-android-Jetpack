package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.ScoreHistoryPoint
import `in`.artistant.app.data.model.dto.DBScoreHistoryRow
import `in`.artistant.app.data.model.dto.DBScoreMetrics
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bookability Score reads (iOS `ScoreRepository`). The score + metric_* columns
 * live on `public.artists`, RLS-readable for any published artist, so
 * [breakdown] needs no auth session; the self surfaces do. A missing row / new
 * artist yields [ScoreBreakdown.newArtist] (the truthful zero-state).
 */
interface ScoreRepository {
    /** Per-metric breakdown for the signed-in artist. Throws when not signed in. */
    suspend fun breakdownForSelf(): ScoreBreakdown

    /** Per-metric breakdown for an arbitrary published artist (client viewing a profile). */
    suspend fun breakdown(forArtist: String): ScoreBreakdown

    /** Signed-in artist's last 12 months of score-history points, oldest-first. */
    suspend fun historyForSelf(): List<ScoreHistoryPoint>
}

@Singleton
class SupabaseScoreRepository @Inject constructor(
    private val client: SupabaseClient,
) : ScoreRepository {

    override suspend fun breakdownForSelf(): ScoreBreakdown {
        val userId = selfId() ?: throw AppError.NotFoundOrUnauthorized
        return fetchBreakdown(userId)
    }

    override suspend fun breakdown(forArtist: String): ScoreBreakdown {
        // Non-UUID ids (fixtures) can't have a row — return the New zero-state
        // rather than letting PostgREST 400 on a malformed uuid literal.
        val id = runCatching { UUID.fromString(forArtist) }.getOrNull() ?: return ScoreBreakdown.newArtist
        return fetchBreakdown(id.toString().lowercaseUuid())
    }

    override suspend fun historyForSelf(): List<ScoreHistoryPoint> {
        val userId = selfId() ?: throw AppError.NotFoundOrUnauthorized
        // Push the 12-month window into the DB filter (not a client row cap) so an
        // active artist's earliest baseline isn't silently truncated.
        val cutoff = Instant.now().minus(Duration.ofDays(365)).toString()
        return client.postgrest.from("score_history")
            .select(Columns.list("score", "computed_at")) {
                filter { eq("artist_id", userId); gte("computed_at", cutoff) }
                order("computed_at", Order.DESCENDING)
                limit(5000)  // payload ceiling; not the time window (that's gte above)
            }
            .decodeList<DBScoreHistoryRow>()
            .mapNotNull { it.toDomain() }
            .sortedBy { it.computedAt }
    }

    private suspend fun fetchBreakdown(artistId: String): ScoreBreakdown {
        val rows = client.postgrest.from("artists")
            .select(METRIC_COLUMNS) {
                filter { eq("id", artistId) }
                limit(1)
            }
            .decodeList<DBScoreMetrics>()
        return rows.firstOrNull()?.toDomain() ?: ScoreBreakdown.newArtist
    }

    private fun selfId(): String? = client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid()

    companion object {
        private val METRIC_COLUMNS = Columns.list(
            "score", "metric_show_up", "metric_review_score", "metric_reply_speed",
            "metric_cancellations", "metric_social_proof", "total_gigs",
        )
    }
}
