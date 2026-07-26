package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.core.result.mapPostgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ArtistLink(
    val id: String,
    val artistId: String,
    val label: String,
    val url: String,
    val position: Int,
)

/**
 * `public.artist_links` CRUD — port of iOS ArtistLinksRepository.
 * No RPC; direct insert/update/delete under owns_artist RLS.
 */
interface ArtistLinksRepository {
    suspend fun list(artistId: String): List<ArtistLink>
    suspend fun upsert(artistId: String, label: String, url: String, id: String? = null, position: Int? = null): ArtistLink
    suspend fun delete(id: String)
}

@Singleton
class SupabaseArtistLinksRepository @Inject constructor(
    private val client: SupabaseClient,
) : ArtistLinksRepository {
    override suspend fun list(artistId: String): List<ArtistLink> =
        try {
            client.from("artist_links")
                .select {
                    filter { eq("artist_id", artistId.lowercase()) }
                    order("position", Order.ASCENDING)
                }
                .decodeList<LinkRow>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }

    override suspend fun upsert(
        artistId: String,
        label: String,
        url: String,
        id: String?,
        position: Int?,
    ): ArtistLink {
        val artist = artistId.lowercase()
        val resolvedId = id?.lowercase() ?: UUID.randomUUID().toString().lowercase()
        val resolvedPosition = position ?: nextPosition(artist)
        val row = LinkInsert(
            id = resolvedId,
            artistId = artist,
            label = label.trim(),
            url = url.trim(),
            position = resolvedPosition,
        )
        return try {
            client.from("artist_links")
                .upsert(row) { onConflict = "id"; select() }
                .decodeSingle<LinkRow>()
                .toDomain()
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    override suspend fun delete(id: String) {
        try {
            client.from("artist_links").delete {
                filter { eq("id", id.lowercase()) }
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    private suspend fun nextPosition(artistId: String): Int {
        val rows = client.from("artist_links")
            .select {
                filter { eq("artist_id", artistId) }
                order("position", Order.DESCENDING)
                limit(1)
            }
            .decodeList<LinkRow>()
        return (rows.firstOrNull()?.position ?: -1) + 1
    }
}

class FakeArtistLinksRepository : ArtistLinksRepository {
    private val byArtist = mutableMapOf<String, MutableList<ArtistLink>>()

    override suspend fun list(artistId: String): List<ArtistLink> =
        byArtist[artistId.lowercase()].orEmpty()

    override suspend fun upsert(
        artistId: String,
        label: String,
        url: String,
        id: String?,
        position: Int?,
    ): ArtistLink {
        val list = byArtist.getOrPut(artistId.lowercase()) { mutableListOf() }
        val resolvedId = id?.lowercase() ?: UUID.randomUUID().toString().lowercase()
        list.removeAll { it.id == resolvedId }
        val link = ArtistLink(
            id = resolvedId,
            artistId = artistId.lowercase(),
            label = label,
            url = url,
            position = position ?: list.size,
        )
        list.add(link)
        return link
    }

    override suspend fun delete(id: String) {
        byArtist.values.forEach { it.removeAll { link -> link.id == id.lowercase() } }
    }
}

@Serializable
private data class LinkInsert(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val label: String,
    val url: String,
    val position: Int,
)

@Serializable
private data class LinkRow(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val label: String,
    val url: String,
    val position: Int = 0,
) {
    fun toDomain() = ArtistLink(
        id = id.lowercase(),
        artistId = artistId.lowercase(),
        label = label,
        url = url,
        position = position,
    )
}
