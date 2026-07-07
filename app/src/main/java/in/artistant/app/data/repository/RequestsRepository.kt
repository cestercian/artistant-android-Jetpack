package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.common.util.SupabaseISO8601
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.model.dto.DBGigRequestWithClient
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gig-request (negotiation) data boundary (port of iOS `RequestsRepository`). The
 * artist answers inbound requests (accept / decline / counter); the client composes
 * new ones. RLS gates the writes; the client only sanity-checks auth. A status
 * update that matches zero rows (RLS-filtered / deleted) surfaces as
 * [AppError.NotFoundOrUnauthorized] (via PGRST116) so the UI can roll back instead
 * of showing a fake success.
 */
interface RequestsRepository {
    /** Requests targeting the signed-in artist, newest-first (client name embed). */
    suspend fun listForArtist(): List<StoredRequest>

    /** Requests the signed-in client authored, newest-first. */
    suspend fun listForClient(): List<StoredRequest>

    /** Insert a new request the signed-in client is proposing to [artistId]. */
    suspend fun create(
        artistId: String,
        proposedAmountInr: Int,
        dateLabel: String,
        message: String?,
        venue: String?,
        crowdSize: Int?,
        expiresAt: Instant,
    ): StoredRequest

    /** Artist accepts → status=accepted. */
    suspend fun accept(id: String)

    /** Artist declines → status=declined. */
    suspend fun decline(id: String)

    /** Artist counters → status=countered + counter_amount_inr=[amount]. */
    suspend fun counter(id: String, amount: Int)
}

@Singleton
class SupabaseRequestsRepository @Inject constructor(
    private val client: SupabaseClient,
) : RequestsRepository {

    override suspend fun listForArtist(): List<StoredRequest> =
        listWhere("artist_id", currentUserId())

    override suspend fun listForClient(): List<StoredRequest> =
        listWhere("client_id", currentUserId())

    private suspend fun listWhere(column: String, userId: String): List<StoredRequest> =
        client.postgrest.from("gig_requests")
            .select(Columns.raw("$REQUEST_COLS, client:users!client_id(full_name)")) {
                filter { eq(column, userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<DBGigRequestWithClient>()
            .map { it.toStoredRequest() }

    override suspend fun create(
        artistId: String,
        proposedAmountInr: Int,
        dateLabel: String,
        message: String?,
        venue: String?,
        crowdSize: Int?,
        expiresAt: Instant,
    ): StoredRequest {
        val clientId = currentUserId()
        val row = Insert(
            client_id = clientId,
            artist_id = artistId.lowercaseUuid(),
            proposed_amount_inr = proposedAmountInr,
            date_label = dateLabel,
            message = message?.trim()?.takeIf { it.isNotEmpty() },
            venue = venue?.trim()?.takeIf { it.isNotEmpty() },
            crowd_size = crowdSize,
            expires_at = SupabaseISO8601.format(expiresAt),
        )
        return client.postgrest.from("gig_requests")
            .insert(row) { select(Columns.raw("$REQUEST_COLS, client:users!client_id(full_name)")) }
            .decodeSingle<DBGigRequestWithClient>()
            .toStoredRequest()
    }

    override suspend fun accept(id: String) = updateStatus(id, "accepted", null)
    override suspend fun decline(id: String) = updateStatus(id, "declined", null)
    override suspend fun counter(id: String, amount: Int) = updateStatus(id, "countered", amount)

    /**
     * Sets status (+ optional counter amount) and forces a single-row return so a
     * zero-row match (RLS-filtered / deleted) throws PGRST116 → NotFoundOrUnauthorized
     * — the caller rolls the optimistic flip back instead of showing a fake success.
     */
    private suspend fun updateStatus(id: String, status: String, counterAmount: Int?) {
        currentUserId() // ensure signed in
        val key = id.lowercaseUuid()
        try {
            // Two typed branches — `update(value)` is a reified generic, so the concrete
            // encoded shape (with or without counter_amount_inr) has to be chosen here.
            if (counterAmount != null) {
                client.postgrest.from("gig_requests")
                    .update(StatusWithCounter(status, counterAmount)) {
                        filter { eq("id", key) }
                        single()
                        select(Columns.list("id"))
                    }
                    .decodeSingle<IdOnly>()
            } else {
                client.postgrest.from("gig_requests")
                    .update(StatusOnly(status)) {
                        filter { eq("id", key) }
                        single()
                        select(Columns.list("id"))
                    }
                    .decodeSingle<IdOnly>()
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t) // PGRST116 → NotFoundOrUnauthorized; else typed/Unknown
        }
    }

    private fun currentUserId(): String =
        client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid()
            ?: throw AppError.NotFoundOrUnauthorized

    @Serializable
    private data class Insert(
        val client_id: String,
        val artist_id: String,
        val proposed_amount_inr: Int,
        val date_label: String,
        val message: String?,
        val venue: String?,
        val crowd_size: Int?,
        val expires_at: String,
    )

    @Serializable private data class StatusOnly(val status: String)
    @Serializable private data class StatusWithCounter(val status: String, val counter_amount_inr: Int)
    @Serializable private data class IdOnly(val id: String)

    companion object {
        private const val REQUEST_COLS =
            "id, artist_id, client_id, message, proposed_amount_inr, counter_amount_inr, " +
                "date_label, venue, crowd_size, status, created_at"
    }
}
