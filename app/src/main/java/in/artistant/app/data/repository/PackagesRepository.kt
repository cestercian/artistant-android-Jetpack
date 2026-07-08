package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.dto.DBPackage
import `in`.artistant.app.data.model.dto.replacePackagesParams
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read + atomic-replace helpers for `public.packages` (port of iOS
 * `PackagesRepository`). The wizard/EPK keeps pricing tiers in memory; on save we
 * REPLACE all of the artist's rows with the fresh set via the `replace_packages`
 * RPC (SECURITY DEFINER + `owns_artist`), so the DB is the single source of truth
 * and a Discover-side reader never sees the half-applied DELETE→INSERT gap.
 */
interface PackagesRepository {
    /** Packages for a visible artist, position-ordered. */
    suspend fun list(artistId: String): List<ArtistPackage>

    /** Atomically replace ALL of the artist's packages with [packages]. */
    suspend fun replaceAll(artistId: String, packages: List<ArtistPackage>)
}

@Singleton
class SupabasePackagesRepository @Inject constructor(
    private val client: SupabaseClient,
) : PackagesRepository {

    override suspend fun list(artistId: String): List<ArtistPackage> =
        client.postgrest.from("packages")
            .select(PACKAGE_COLUMNS) {
                filter { eq("artist_id", artistId.lowercaseUuid()) }
                order("position", Order.ASCENDING)
            }
            .decodeList<DBPackage>()
            .map { it.toPackage() }

    override suspend fun replaceAll(artistId: String, packages: List<ArtistPackage>) {
        // One round-trip, atomic in Postgres — ownership enforced inside the RPC.
        client.postgrest.rpc("replace_packages", replacePackagesParams(artistId, packages))
    }

    companion object {
        private val PACKAGE_COLUMNS = Columns.list(
            "artist_id", "position", "name", "duration_label", "price_inr", "includes", "popular",
        )
    }
}

/** In-memory twin — the replace overwrites the artist's list wholesale. */
class FakePackagesRepository(
    seed: Map<String, List<ArtistPackage>> = emptyMap(),
) : PackagesRepository {
    private val store = seed.toMutableMap()

    override suspend fun list(artistId: String): List<ArtistPackage> = store[artistId].orEmpty()

    override suspend fun replaceAll(artistId: String, packages: List<ArtistPackage>) {
        store[artistId] = packages.toList()
    }
}
