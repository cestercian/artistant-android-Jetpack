package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
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
    suspend fun replaceAll(artistId: String, packages: List<PackageDraft>)
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

    override suspend fun replaceAll(artistId: String, packages: List<PackageDraft>) {
        val payload = ReplacePackagesParams(
            targetArtistId = artistId.lowercase(),
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

class FakePackagesRepository : PackagesRepository {
    private val byArtist = mutableMapOf<String, List<ArtistPackage>>()
    var lastReplace: Pair<String, List<PackageDraft>>? = null
        private set
    var failReplace: Boolean = false

    override suspend fun list(artistId: String): List<ArtistPackage> =
        byArtist[artistId.lowercase()].orEmpty()

    override suspend fun replaceAll(artistId: String, packages: List<PackageDraft>) {
        if (failReplace) throw AppError.Unknown(IllegalStateException("fake packages failure"))
        lastReplace = artistId.lowercase() to packages
        byArtist[artistId.lowercase()] = packages.mapIndexed { idx, pkg ->
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
