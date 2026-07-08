package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.ArtistLink
import `in`.artistant.app.data.model.dto.DBArtistLink
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CRUD for the EPK Links section (`public.artist_links`; port of iOS
 * `ArtistLinksRepository`). Every write is scoped to the signed-in artist's own
 * rows by RLS; the DB CHECK constraints (label length, url shape) surface bad
 * input rather than a client-side regex. [add] packs the new link at MAX+1.
 */
interface ArtistLinksRepository {
    suspend fun list(artistId: String): List<ArtistLink>
    suspend fun add(label: String, url: String, artistId: String): ArtistLink
    suspend fun update(link: ArtistLink, label: String, url: String): ArtistLink
    suspend fun delete(link: ArtistLink)
}

@Singleton
class SupabaseArtistLinksRepository @Inject constructor(
    private val client: SupabaseClient,
) : ArtistLinksRepository {

    override suspend fun list(artistId: String): List<ArtistLink> =
        client.postgrest.from("artist_links")
            .select(LINK_COLUMNS) {
                filter { eq("artist_id", artistId.lowercaseUuid()) }
                order("position", Order.ASCENDING)
            }
            .decodeList<DBArtistLink>()
            .map { it.toDomain() }

    override suspend fun add(label: String, url: String, artistId: String): ArtistLink {
        requireSignedIn()
        val position = nextPosition(artistId)
        return client.postgrest.from("artist_links")
            .insert(
                Insert(
                    artist_id = artistId.lowercaseUuid(),
                    label = label.trim(),
                    url = url.trim(),
                    position = position,
                ),
            ) { select(LINK_COLUMNS) }
            .decodeSingle<DBArtistLink>()
            .toDomain()
    }

    override suspend fun update(link: ArtistLink, label: String, url: String): ArtistLink =
        client.postgrest.from("artist_links")
            .update(Patch(label = label.trim(), url = url.trim())) {
                filter { eq("id", link.id.lowercaseUuid()) }
                select(LINK_COLUMNS)
            }
            .decodeSingle<DBArtistLink>()
            .toDomain()

    override suspend fun delete(link: ArtistLink) {
        client.postgrest.from("artist_links")
            .delete { filter { eq("id", link.id.lowercaseUuid()) } }
    }

    private suspend fun nextPosition(artistId: String): Int {
        val rows = client.postgrest.from("artist_links")
            .select(Columns.list("position")) {
                filter { eq("artist_id", artistId.lowercaseUuid()) }
                order("position", Order.DESCENDING)
                limit(1)
            }
            .decodeList<PositionOnly>()
        return (rows.firstOrNull()?.position ?: -1) + 1
    }

    private fun requireSignedIn() {
        client.auth.currentSessionOrNull()?.user?.id ?: throw AppError.NotFoundOrUnauthorized
    }

    @Serializable
    private data class Insert(
        val artist_id: String,
        val label: String,
        val url: String,
        val position: Int,
    )

    @Serializable private data class Patch(val label: String, val url: String)
    @Serializable private data class PositionOnly(val position: Int)

    companion object {
        private val LINK_COLUMNS = Columns.list("id", "artist_id", "label", "url", "position")
    }
}

/** In-memory twin; assigns sequential positions on [add]. */
class FakeArtistLinksRepository : ArtistLinksRepository {
    private val store = mutableMapOf<String, MutableList<ArtistLink>>()
    private var counter = 0

    override suspend fun list(artistId: String): List<ArtistLink> =
        store[artistId].orEmpty().sortedBy { it.position }

    override suspend fun add(label: String, url: String, artistId: String): ArtistLink {
        val list = store.getOrPut(artistId) { mutableListOf() }
        val link = ArtistLink(
            id = "link-${counter++}",
            artistId = artistId,
            label = label.trim(),
            url = url.trim(),
            position = (list.maxOfOrNull { it.position } ?: -1) + 1,
        )
        list.add(link)
        return link
    }

    override suspend fun update(link: ArtistLink, label: String, url: String): ArtistLink {
        val updated = link.copy(label = label.trim(), url = url.trim())
        store[link.artistId]?.let { list ->
            val idx = list.indexOfFirst { it.id == link.id }
            if (idx >= 0) list[idx] = updated
        }
        return updated
    }

    override suspend fun delete(link: ArtistLink) {
        store[link.artistId]?.removeAll { it.id == link.id }
    }
}
