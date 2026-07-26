package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `public.tech_rider` — port of iOS TechRiderRepository.
 * Writes via `replace_tech_rider` RPC (atomic, owns_artist).
 */
interface TechRiderRepository {
    suspend fun list(artistId: String): List<String>
    suspend fun replaceAll(artistId: String, items: List<String>)
}

@Singleton
class SupabaseTechRiderRepository @Inject constructor(
    private val client: SupabaseClient,
) : TechRiderRepository {
    override suspend fun list(artistId: String): List<String> =
        try {
            client.from("tech_rider")
                .select {
                    filter { eq("artist_id", artistId.lowercase()) }
                    order("position", Order.ASCENDING)
                }
                .decodeList<TechRow>()
                .map { it.item }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }

    override suspend fun replaceAll(artistId: String, items: List<String>) {
        val trimmed = items.map { it.trim() }.filter { it.isNotEmpty() }
        val payload = ReplaceTechParams(
            targetArtistId = artistId.lowercase(),
            items = trimmed,
        )
        try {
            client.postgrest.rpc("replace_tech_rider", payload)
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }
}

class FakeTechRiderRepository : TechRiderRepository {
    private val byArtist = mutableMapOf<String, List<String>>()
    var lastReplace: Pair<String, List<String>>? = null
        private set
    var failReplace: Boolean = false

    override suspend fun list(artistId: String): List<String> =
        byArtist[artistId.lowercase()].orEmpty()

    override suspend fun replaceAll(artistId: String, items: List<String>) {
        if (failReplace) throw AppError.Unknown(IllegalStateException("fake tech failure"))
        val trimmed = items.map { it.trim() }.filter { it.isNotEmpty() }
        lastReplace = artistId.lowercase() to trimmed
        byArtist[artistId.lowercase()] = trimmed
    }
}

@Serializable
private data class ReplaceTechParams(
    @SerialName("target_artist_id") val targetArtistId: String,
    val items: List<String>,
)

@Serializable
private data class TechRow(
    @SerialName("artist_id") val artistId: String,
    val item: String,
)
