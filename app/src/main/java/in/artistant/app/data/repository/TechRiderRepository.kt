package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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

    /**
     * Wipe + re-insert the whole rider for the signed-in artist.
     *
     * Guarded like `ArtistsRepository.patchSelf` and the sibling
     * `PackagesRepository.replaceAll`: the write runs as the session (the RPC's
     * `owns_artist` enforces that), but the caller names the account the edit was
     * COMPOSED for so a replace flushed from `EpkViewModel.onCleared` after an
     * account switch can never land this artist's rider on the new user's row.
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun replaceAll(expectedOwner: String, items: List<String>)
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

    override suspend fun replaceAll(expectedOwner: String, items: List<String>) {
        // Same guard, order and error family as `patchSelf`: the target is the
        // session, and the require checks it against the account the rider was
        // composed for before anything reaches the RPC.
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        require(userId == expectedOwner.lowercase()) {
            "Self-row edit must target the account it was composed for."
        }
        val trimmed = items.map { it.trim() }.filter { it.isNotEmpty() }
        val payload = ReplaceTechParams(
            targetArtistId = userId,
            items = trimmed,
        )
        try {
            client.postgrest.rpc("replace_tech_rider", payload)
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }
}

class FakeTechRiderRepository(
    /**
     * The signed-in artist — the one id [replaceAll] may target. Same role as
     * [FakePackagesRepository]'s: named, it refuses a replace composed for another
     * account in the `require`/IllegalArgumentException family; null, it models
     * "no session" and throws [AppError.NotFoundOrUnauthorized]. No single-row
     * fallback, because `byArtist` is empty until the first write seeds it.
     */
    private val selfId: String? = null,
) : TechRiderRepository {
    private val byArtist = mutableMapOf<String, List<String>>()
    var lastReplace: Pair<String, List<String>>? = null
        private set
    var failReplace: Boolean = false

    override suspend fun list(artistId: String): List<String> =
        byArtist[artistId.lowercase()].orEmpty()

    override suspend fun replaceAll(expectedOwner: String, items: List<String>) {
        // Guard before the simulated failure, exactly as the real repo checks the
        // session before the RPC — a cross-account write is refused regardless of
        // [failReplace].
        val self = selfId?.lowercase() ?: throw AppError.NotFoundOrUnauthorized
        require(self == expectedOwner.lowercase()) {
            "Self-row edit must target the account it was composed for."
        }
        if (failReplace) throw AppError.Unknown(IllegalStateException("fake tech failure"))
        val trimmed = items.map { it.trim() }.filter { it.isNotEmpty() }
        lastReplace = self to trimmed
        byArtist[self] = trimmed
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
