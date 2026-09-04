package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.common.util.SupabaseISO8601
import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gig-request (quote) seam — port of iOS `RequestsRepository`.
 * Table `gig_requests`; status mutations are status-only PATCH (+ counter amount).
 */

sealed class RequestsRepositoryError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object NotSignedIn : RequestsRepositoryError("Sign in to accept or decline gig requests.")
    class NotFoundOrUnauthorized(id: String) :
        RequestsRepositoryError("Couldn't update this request — it may have been removed ($id).")
    class Underlying(cause: Throwable) : RequestsRepositoryError(cause.message ?: "Request failed", cause)
}

interface RequestsRepository {
    suspend fun listForArtist(): List<StoredRequest>
    suspend fun listForClient(): List<StoredRequest>
    suspend fun create(
        artistId: String,
        proposedAmountInr: Int,
        dateLabel: String,
        message: String?,
        venue: String?,
        crowdSize: Int?,
        expiresAtEpochMs: Long,
    ): StoredRequest
    suspend fun accept(id: String)
    suspend fun decline(id: String)
    suspend fun counter(id: String, amount: Int)
}

@Singleton
class SupabaseRequestsRepository @Inject constructor(
    private val client: SupabaseClient,
) : RequestsRepository {

    override suspend fun listForArtist(): List<StoredRequest> {
        val userId = currentUserId() ?: throw RequestsRepositoryError.NotSignedIn
        return try {
            client.from("gig_requests")
                .select(Columns.raw("*, client:users!client_id(full_name)")) {
                    filter { eq("artist_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<DbGigRequestWithClient>()
                .map { it.toStoredRequest() }
        } catch (t: Throwable) {
            throw RequestsRepositoryError.Underlying(t)
        }
    }

    override suspend fun listForClient(): List<StoredRequest> {
        val clientId = currentUserId() ?: throw RequestsRepositoryError.NotSignedIn
        return try {
            client.from("gig_requests")
                .select(Columns.raw("*, client:users!client_id(full_name)")) {
                    filter { eq("client_id", clientId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<DbGigRequestWithClient>()
                .map { it.toStoredRequest() }
        } catch (t: Throwable) {
            throw RequestsRepositoryError.Underlying(t)
        }
    }

    override suspend fun create(
        artistId: String,
        proposedAmountInr: Int,
        dateLabel: String,
        message: String?,
        venue: String?,
        crowdSize: Int?,
        expiresAtEpochMs: Long,
    ): StoredRequest {
        val clientId = currentUserId() ?: throw RequestsRepositoryError.NotSignedIn
        val artistKey = artistId.lowercase()
        if (runCatching { UUID.fromString(artistKey) }.isFailure) {
            throw RequestsRepositoryError.Underlying(
                IllegalArgumentException("Internal: artist_id is not a UUID ($artistId)."),
            )
        }
        val row = GigRequestInsert(
            clientId = clientId,
            artistId = artistKey,
            proposedAmountInr = proposedAmountInr,
            dateLabel = dateLabel,
            message = nilIfBlank(message),
            venue = nilIfBlank(venue),
            crowdSize = crowdSize,
            expiresAt = isoUtc(expiresAtEpochMs),
        )
        return try {
            client.from("gig_requests")
                .insert(row) {
                    select(Columns.raw("*, client:users!client_id(full_name)"))
                }
                .decodeSingle<DbGigRequestWithClient>()
                .toStoredRequest()
        } catch (t: Throwable) {
            throw RequestsRepositoryError.Underlying(t)
        }
    }

    override suspend fun accept(id: String) = updateStatus(id, "accepted", null)
    override suspend fun decline(id: String) = updateStatus(id, "declined", null)
    override suspend fun counter(id: String, amount: Int) = updateStatus(id, "countered", amount)

    private suspend fun updateStatus(id: String, status: String, counterAmount: Int?) {
        if (currentUserId() == null) throw RequestsRepositoryError.NotSignedIn
        if (runCatching { UUID.fromString(id) }.isFailure) {
            throw RequestsRepositoryError.NotFoundOrUnauthorized(id)
        }
        try {
            val rows = if (counterAmount != null) {
                client.from("gig_requests")
                    .update(StatusWithCounter(status = status, counterAmountInr = counterAmount)) {
                        filter { eq("id", id.lowercase()) }
                        select(Columns.list("id"))
                    }
                    .decodeList<IdOnly>()
            } else {
                client.from("gig_requests")
                    .update(StatusOnly(status = status)) {
                        filter { eq("id", id.lowercase()) }
                        select(Columns.list("id"))
                    }
                    .decodeList<IdOnly>()
            }
            if (rows.isEmpty()) throw RequestsRepositoryError.NotFoundOrUnauthorized(id)
        } catch (e: RequestsRepositoryError) {
            throw e
        } catch (t: Throwable) {
            throw RequestsRepositoryError.Underlying(t)
        }
    }

    private fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id?.lowercase()

    private fun nilIfBlank(s: String?): String? =
        s?.trim()?.takeIf { it.isNotEmpty() }

    private fun isoUtc(epochMs: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(epochMs))
    }
}

@Serializable
private data class StatusOnly(val status: String)

@Serializable
private data class StatusWithCounter(
    val status: String,
    @SerialName("counter_amount_inr") val counterAmountInr: Int,
)

@Serializable
private data class IdOnly(val id: String)

@Serializable
private data class GigRequestInsert(
    @SerialName("client_id") val clientId: String,
    @SerialName("artist_id") val artistId: String,
    @SerialName("proposed_amount_inr") val proposedAmountInr: Int,
    @SerialName("date_label") val dateLabel: String,
    val message: String?,
    val venue: String?,
    @SerialName("crowd_size") val crowdSize: Int?,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
private data class DbGigRequestWithClient(
    val id: String,
    @SerialName("artist_id") val artistId: String = "",
    @SerialName("client_id") val clientId: String = "",
    val message: String? = null,
    @SerialName("proposed_amount_inr") val proposedAmountInr: Int = 0,
    @SerialName("counter_amount_inr") val counterAmountInr: Int? = null,
    @SerialName("date_label") val dateLabel: String = "",
    val venue: String? = null,
    @SerialName("crowd_size") val crowdSize: Int? = null,
    val status: String = "open",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    val client: ClientEmbed? = null,
) {
    @Serializable
    data class ClientEmbed(@SerialName("full_name") val fullName: String? = null)

    fun toStoredRequest(): StoredRequest {
        // Denormalized column first, embed second, null last — never a literal.
        //
        // The embed only resolves on the CLIENT's own path (`users_select_self`);
        // on the artist's path RLS nulls it, which is why every inbound quote
        // used to render under the same hardcoded name — a fact about nobody.
        // Migration 0100 adds `client_name`, filled by a SECURITY DEFINER
        // trigger exactly as 0080 does for bookings, so the artist can finally
        // tell requesters apart. The embed stays as the fallback for rows
        // written before 0100's backfill, and null still means null: the
        // renderers show the request's own date and offer rather than inventing
        // an identity.
        val resolvedName = clientName?.trim()?.takeIf { it.isNotEmpty() }
            ?: client?.fullName?.trim()?.takeIf { it.isNotEmpty() }
        return StoredRequest(
            raw = GigRequest(
                id = id,
                client = resolvedName,
                message = message.orEmpty(),
                date = dateLabel,
                amount = proposedAmountInr,
                packageLabel = "Custom",
                timeAgo = relativeTimeAgo(createdAt),
                artistId = artistId.lowercase(),
                // Canonical parser, not the lenient ladder below it: `expires_at`
                // is a deadline the UI states in words ("holds till Fri"), so a
                // misread is a lie about how long an offer stands. Null when it
                // will not parse, and every caller drops the line rather than
                // guessing.
                expiresAtEpochMs = expiresAt
                    ?.let { SupabaseISO8601.parse(it)?.toEpochMilli() },
            ),
            status = GigRequestStatus.fromDb(status),
            counterAmount = counterAmountInr,
        )
    }

    private fun relativeTimeAgo(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return ""
        // Best-effort: parse ISO prefix; fall back empty.
        val parsedMs = runCatching {
            val cleaned = createdAt.replace("Z", "+0000")
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
            )
            for (fmt in formats) {
                val f = SimpleDateFormat(fmt, Locale.US)
                f.isLenient = true
                val d = runCatching { f.parse(cleaned) }.getOrNull()
                if (d != null) return@runCatching d.time
            }
            null
        }.getOrNull() ?: return ""
        val diff = System.currentTimeMillis() - parsedMs
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        return when {
            hours < 1 -> "just now"
            hours < 24 -> "${hours}h ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        }
    }
}

class FakeRequestsRepository(
    seed: List<StoredRequest> = emptyList(),
) : RequestsRepository {
    private val rows = seed.toMutableList()
    var signedIn: Boolean = true

    override suspend fun listForArtist(): List<StoredRequest> {
        if (!signedIn) throw RequestsRepositoryError.NotSignedIn
        return rows.toList()
    }

    override suspend fun listForClient(): List<StoredRequest> = listForArtist()

    override suspend fun create(
        artistId: String,
        proposedAmountInr: Int,
        dateLabel: String,
        message: String?,
        venue: String?,
        crowdSize: Int?,
        expiresAtEpochMs: Long,
    ): StoredRequest {
        if (!signedIn) throw RequestsRepositoryError.NotSignedIn
        val req = StoredRequest(
            raw = GigRequest(
                id = UUID.randomUUID().toString(),
                client = "You",
                message = message.orEmpty(),
                date = dateLabel,
                amount = proposedAmountInr,
                // Carried through, like the real seam: a fake that drops the two
                // fields the chat matches a quote on would make the chat's own
                // tests pass against a repository that answers nothing.
                artistId = artistId.lowercase(),
                expiresAtEpochMs = expiresAtEpochMs,
            ),
            status = GigRequestStatus.Open,
        )
        rows.add(0, req)
        return req
    }

    override suspend fun accept(id: String) = setStatus(id, GigRequestStatus.Accepted)
    override suspend fun decline(id: String) = setStatus(id, GigRequestStatus.Declined)
    override suspend fun counter(id: String, amount: Int) {
        val idx = rows.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        if (idx < 0) throw RequestsRepositoryError.NotFoundOrUnauthorized(id)
        rows[idx] = rows[idx].copy(status = GigRequestStatus.Countered, counterAmount = amount)
    }

    private fun setStatus(id: String, status: GigRequestStatus) {
        if (!signedIn) throw RequestsRepositoryError.NotSignedIn
        val idx = rows.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        if (idx < 0) throw RequestsRepositoryError.NotFoundOrUnauthorized(id)
        rows[idx] = rows[idx].copy(status = status)
    }
}
