package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.data.model.dto.DBTechItem
import `in`.artistant.app.data.model.dto.replaceTechRiderParams
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read + atomic-replace helpers for `public.tech_rider` (port of iOS
 * `TechRiderRepository`). Stored as ordered rows; the app keeps them as a plain
 * list. Same replace-all-via-RPC semantics as packages — trimming + empty-filter
 * happen in [replaceTechRiderParams] before the round trip.
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
        client.postgrest.from("tech_rider")
            // Order by position server-side (works without selecting it); the
            // DTO only carries artist_id + item.
            .select(Columns.list("artist_id", "item")) {
                filter { eq("artist_id", artistId.lowercaseUuid()) }
                order("position", Order.ASCENDING)
            }
            .decodeList<DBTechItem>()
            .map { it.item }

    override suspend fun replaceAll(artistId: String, items: List<String>) {
        client.postgrest.rpc("replace_tech_rider", replaceTechRiderParams(artistId, items))
    }
}

class FakeTechRiderRepository(
    seed: Map<String, List<String>> = emptyMap(),
) : TechRiderRepository {
    private val store = seed.toMutableMap()

    override suspend fun list(artistId: String): List<String> = store[artistId].orEmpty()

    override suspend fun replaceAll(artistId: String, items: List<String>) {
        // Match the RPC contract: trim + drop empties so a Fake-backed test sees
        // the same shape the server would persist.
        store[artistId] = items.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
