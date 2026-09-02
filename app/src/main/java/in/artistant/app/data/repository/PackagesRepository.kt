package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.data.model.ArtistPackage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `public.packages` — port of iOS PackagesRepository.
 * Writes go through `replace_packages` (atomic wipe+insert); never DELETE+INSERT
 * from the client (Discover can observe the empty window).
 */
interface PackagesRepository {
    suspend fun list(artistId: String): List<ArtistPackage>

    /**
     * Wipe + re-insert the whole tier list for the signed-in artist.
     *
     * Guarded like `ArtistsRepository.patchSelf`: the write still runs as the
     * session (the RPC's `owns_artist` and the JWT agree on that), but the caller
     * names the account the edit was COMPOSED for and the seam refuses the write
     * unless the session IS that account. The EPK editor flushes owed saves from
     * a scope that outlives the screen (`EpkViewModel.onCleared`), so a replace
     * composed under one artist could otherwise run after somebody else signed in
     * on the same device and land THIS artist's pricing on the new user's row —
     * a write the server has no reason to refuse, since the JWT is theirs and the
     * row is theirs. Same hazard `patchSelf` closes, on a different seam.
     *
     * @param expectedOwner the account this edit was composed for, not whoever is
     *   signed in when it runs.
     */
    suspend fun replaceAll(expectedOwner: String, packages: List<PackageDraft>)
}

/** Wizard / EPK draft row — in-memory id is not the DB id. */
data class PackageDraft(
    val name: String,
    val durationLabel: String,
    val priceInr: Int,
    val includes: List<String> = emptyList(),
    val popular: Boolean = false,
)

@Singleton
class SupabasePackagesRepository @Inject constructor(
    private val client: SupabaseClient,
) : PackagesRepository {
    override suspend fun list(artistId: String): List<ArtistPackage> =
        try {
            client.from("packages")
                .select {
                    filter { eq("artist_id", artistId.lowercase()) }
                    order("position", Order.ASCENDING)
                }
                .decodeList<PackageRow>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }

    override suspend fun replaceAll(expectedOwner: String, packages: List<PackageDraft>) {
        // Same guard, same order, same error family as `patchSelf`: the target is
        // the session (which is what the RPC's owns_artist enforces), and the
        // require turns "whoever is signed in" into a checkable claim against the
        // account the drafts were composed for.
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized
        require(userId == expectedOwner.lowercase()) {
            "Self-row edit must target the account it was composed for."
        }
        val payload = ReplacePackagesParams(
            targetArtistId = userId,
            packagesJson = packages.mapIndexed { idx, pkg ->
                PackageJson(
                    position = idx,
                    name = pkg.name.trim(),
                    durationLabel = pkg.durationLabel.trim(),
                    priceInr = pkg.priceInr,
                    includes = pkg.includes,
                    popular = pkg.popular,
                )
            },
        )
        try {
            client.postgrest.rpc("replace_packages", payload)
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }
}

class FakePackagesRepository(
    /**
     * The signed-in artist — the one id [replaceAll] may target. Stands in for
     * the session the real repository resolves: named, the fake refuses a replace
     * composed for any other account in the same `require`/IllegalArgumentException
     * family the real seam uses; left null it models "no session", where every
     * write throws [AppError.NotFoundOrUnauthorized]. There is no single-row
     * fallback like [FakeArtistsRepository]'s, because `byArtist` is empty until
     * the first `replaceAll` seeds it — the seam has to know "self" independently
     * of what it has stored.
     */
    private val selfId: String? = null,
) : PackagesRepository {
    private val byArtist = mutableMapOf<String, List<ArtistPackage>>()
    var lastReplace: Pair<String, List<PackageDraft>>? = null
        private set
    var failReplace: Boolean = false

    override suspend fun list(artistId: String): List<ArtistPackage> =
        byArtist[artistId.lowercase()].orEmpty()

    override suspend fun replaceAll(expectedOwner: String, packages: List<PackageDraft>) {
        // Guard before the simulated network failure, exactly as the real repo
        // checks the session before issuing the RPC: a cross-account write is
        // refused whether or not [failReplace] is set.
        val self = selfId?.lowercase() ?: throw AppError.NotFoundOrUnauthorized
        require(self == expectedOwner.lowercase()) {
            "Self-row edit must target the account it was composed for."
        }
        if (failReplace) throw AppError.Unknown(IllegalStateException("fake packages failure"))
        lastReplace = self to packages
        byArtist[self] = packages.mapIndexed { idx, pkg ->
            ArtistPackage(
                id = "pkg-$idx",
                name = pkg.name,
                duration = pkg.durationLabel,
                price = pkg.priceInr,
                includes = pkg.includes,
                popular = pkg.popular,
            )
        }
    }
}

@Serializable
private data class ReplacePackagesParams(
    @SerialName("target_artist_id") val targetArtistId: String,
    @SerialName("packages_json") val packagesJson: List<PackageJson>,
)

@Serializable
private data class PackageJson(
    val position: Int,
    val name: String,
    @SerialName("duration_label") val durationLabel: String,
    @SerialName("price_inr") val priceInr: Int,
    val includes: List<String>,
    val popular: Boolean,
)

@Serializable
private data class PackageRow(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val name: String,
    @SerialName("duration_label") val durationLabel: String,
    @SerialName("price_inr") val priceInr: Int,
    val includes: List<String> = emptyList(),
    val popular: Boolean = false,
) {
    fun toDomain() = ArtistPackage(
        id = id,
        name = name,
        duration = durationLabel,
        price = priceInr,
        includes = includes,
        popular = popular,
    )
}
