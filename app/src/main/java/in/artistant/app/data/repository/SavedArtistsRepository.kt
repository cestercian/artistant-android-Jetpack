package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

sealed class SavedArtistsRepositoryError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    data object NotSignedIn : SavedArtistsRepositoryError("Sign in to save artists.")
    class Underlying(cause: Throwable) :
        SavedArtistsRepositoryError(cause.message ?: "Saved artists request failed", cause)
}

/**
 * `public.saved_artists` CRUD — port of iOS SavedArtistsRepository.
 * No RPC; RLS `saved_artists_owner` gates on `auth.uid() = client_id`.
 */
interface SavedArtistsRepository {
    suspend fun add(artistId: String)
    suspend fun remove(artistId: String)
    /** Artist UUIDs. Throws [SavedArtistsRepositoryError.NotSignedIn] — never empty-on-unsigned. */
    suspend fun list(): List<String>
}

@Singleton
class SupabaseSavedArtistsRepository @Inject constructor(
    private val client: SupabaseClient,
) : SavedArtistsRepository {
    override suspend fun add(artistId: String) {
        val userId = currentUserId() ?: throw SavedArtistsRepositoryError.NotSignedIn
        try {
            client.from("saved_artists").upsert(
                SavedRow(clientId = userId, artistId = artistId.lowercase()),
            ) {
                onConflict = "client_id,artist_id"
            }
        } catch (t: Throwable) {
            throw SavedArtistsRepositoryError.Underlying(t)
        }
    }

    override suspend fun remove(artistId: String) {
        val userId = currentUserId() ?: throw SavedArtistsRepositoryError.NotSignedIn
        try {
            client.from("saved_artists").delete {
                filter {
                    eq("client_id", userId)
                    eq("artist_id", artistId.lowercase())
                }
            }
        } catch (t: Throwable) {
            throw SavedArtistsRepositoryError.Underlying(t)
        }
    }

    override suspend fun list(): List<String> {
        val userId = currentUserId() ?: throw SavedArtistsRepositoryError.NotSignedIn
        return try {
            client.from("saved_artists")
                .select(Columns.list("artist_id")) {
                    filter { eq("client_id", userId) }
                }
                .decodeList<ArtistIdRow>()
                .map { it.artistId.lowercase() }
        } catch (t: Throwable) {
            throw SavedArtistsRepositoryError.Underlying(t)
        }
    }

    private fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id?.lowercase()
}

@Serializable
private data class SavedRow(
    @SerialName("client_id") val clientId: String,
    @SerialName("artist_id") val artistId: String,
)

@Serializable
private data class ArtistIdRow(@SerialName("artist_id") val artistId: String)

/** In-memory twin for ViewModel tests. */
class FakeSavedArtistsRepository : SavedArtistsRepository {
    private val ids = linkedSetOf<String>()
    var signedIn: Boolean = true

    override suspend fun add(artistId: String) {
        if (!signedIn) throw SavedArtistsRepositoryError.NotSignedIn
        ids.add(artistId.lowercase())
    }

    override suspend fun remove(artistId: String) {
        if (!signedIn) throw SavedArtistsRepositoryError.NotSignedIn
        ids.remove(artistId.lowercase())
    }

    override suspend fun list(): List<String> {
        if (!signedIn) throw SavedArtistsRepositoryError.NotSignedIn
        return ids.toList()
    }
}
