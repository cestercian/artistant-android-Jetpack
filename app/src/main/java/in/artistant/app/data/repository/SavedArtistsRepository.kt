package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import `in`.artistant.app.common.util.lowercaseUuid
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved-artists sync against `public.saved_artists` (iOS `SavedArtistsRepository`).
 * Signed-in-only — un-authenticated users keep the toggle in local prefs, so
 * [list] returns `[]` (not an error) when there's no session and the caller drives
 * the local-only path. [add]/[remove] require a session.
 */
interface SavedArtistsRepository {
    /** Upsert the (client_id, artist_id) row. Idempotent. Throws when not signed in. */
    suspend fun add(artistId: String)

    /** Delete the row. Idempotent — removing a non-existent row is a no-op. */
    suspend fun remove(artistId: String)

    /** Saved artist ids for the signed-in user; `[]` when not signed in. */
    suspend fun list(): List<String>
}

@Singleton
class SupabaseSavedArtistsRepository @Inject constructor(
    private val client: SupabaseClient,
) : SavedArtistsRepository {

    @Serializable
    private data class Row(val client_id: String, val artist_id: String)

    @Serializable
    private data class ArtistIdRow(val artist_id: String)

    override suspend fun add(artistId: String) {
        val clientId = requireClientId()
        client.postgrest.from("saved_artists")
            // Upsert on the composite PK so a double-tap doesn't 23505.
            .upsert(Row(clientId, artistId.lowercaseUuid())) { onConflict = "client_id,artist_id" }
    }

    override suspend fun remove(artistId: String) {
        val clientId = requireClientId()
        client.postgrest.from("saved_artists").delete {
            filter { eq("client_id", clientId); eq("artist_id", artistId.lowercaseUuid()) }
        }
    }

    override suspend fun list(): List<String> {
        val clientId = selfId() ?: return emptyList()
        return client.postgrest.from("saved_artists")
            .select(Columns.list("artist_id")) { filter { eq("client_id", clientId) } }
            .decodeList<ArtistIdRow>()
            .map { it.artist_id }
    }

    private fun selfId(): String? = client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid()
    private fun requireClientId(): String = selfId()
        ?: throw IllegalStateException("Sign in to sync saved artists across devices.")
}
