package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import `in`.artistant.app.common.util.SupabaseISO8601
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.data.model.ArtistMediaItem
import `in`.artistant.app.data.model.MediaAspect
import `in`.artistant.app.data.model.MediaKind
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads + writes `public.artist_media` and the matching objects in the public
 * `artist-media` bucket (port of iOS `ArtistMediaRepository`). Bucket layout is
 * `<artistId>/<photo|video>/<uuid>.<ext>`; storage RLS gates the write on the
 * leading artist-uuid path segment.
 *
 * Caps (6 photos / 1 video) are enforced SERVER-SIDE by the `tg_artist_media_cap`
 * trigger; a rejected insert surfaces as [ArtistMediaError.CapReached]. On any
 * insert failure the just-uploaded object is rolled back so the bucket stays
 * consistent with the table.
 */
interface ArtistMediaRepository {
    suspend fun list(artistId: String): List<ArtistMediaItem>

    /** Upload an already-normalized JPEG. [position] null → next free slot. */
    suspend fun uploadPhoto(
        jpegBytes: ByteArray,
        width: Int,
        height: Int,
        artistId: String,
        position: Int? = null,
    ): ArtistMediaItem

    /** Upload an already-trimmed MP4 (trim happens upstream in VideoTrimmer). */
    suspend fun uploadVideo(
        mp4Bytes: ByteArray,
        durationSeconds: Double,
        width: Int,
        height: Int,
        artistId: String,
    ): ArtistMediaItem

    suspend fun delete(item: ArtistMediaItem)
}

@Singleton
class SupabaseArtistMediaRepository @Inject constructor(
    private val client: SupabaseClient,
) : ArtistMediaRepository {

    override suspend fun list(artistId: String): List<ArtistMediaItem> =
        client.postgrest.from("artist_media")
            .select(MEDIA_COLUMNS) {
                filter { eq("artist_id", artistId.lowercaseUuid()) }
                order("kind", Order.ASCENDING)
                order("position", Order.ASCENDING)
            }
            .decodeList<DBArtistMedia>()
            .mapNotNull { it.toItem() }

    override suspend fun uploadPhoto(
        jpegBytes: ByteArray,
        width: Int,
        height: Int,
        artistId: String,
        position: Int?,
    ): ArtistMediaItem {
        val artist = artistId.lowercaseUuid()
        val resolvedPosition = position ?: nextPosition(artist, MediaKind.Photo)
        val path = storagePath(artist, MediaKind.Photo, "jpg")

        client.storage.from(BUCKET).upload(path, jpegBytes) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        return insertRowOrRollback(
            path = path,
            artistId = artist,
            kind = MediaKind.Photo,
            aspect = MediaAspect.classify(width, height),
            position = resolvedPosition,
            mimeType = "image/jpeg",
            width = width,
            height = height,
            durationSeconds = null,
        )
    }

    override suspend fun uploadVideo(
        mp4Bytes: ByteArray,
        durationSeconds: Double,
        width: Int,
        height: Int,
        artistId: String,
    ): ArtistMediaItem {
        val artist = artistId.lowercaseUuid()
        val position = nextPosition(artist, MediaKind.Video)
        val path = storagePath(artist, MediaKind.Video, "mp4")

        client.storage.from(BUCKET).upload(path, mp4Bytes) {
            upsert = false
            contentType = ContentType.Video.MP4
        }
        return insertRowOrRollback(
            path = path,
            artistId = artist,
            kind = MediaKind.Video,
            aspect = MediaAspect.classify(width, height),
            position = position,
            mimeType = "video/mp4",
            width = width,
            height = height,
            durationSeconds = durationSeconds,
        )
    }

    override suspend fun delete(item: ArtistMediaItem) {
        // Row first so no live reference points at the file; if the storage
        // delete then fails the object is a cleanable orphan, not a broken row.
        client.postgrest.from("artist_media")
            .delete { filter { eq("id", item.id.lowercaseUuid()) } }
        runCatching { client.storage.from(BUCKET).delete(item.storagePath) }
    }

    /** Insert the row; on ANY failure roll the uploaded object back and rethrow. */
    private suspend fun insertRowOrRollback(
        path: String,
        artistId: String,
        kind: MediaKind,
        aspect: MediaAspect,
        position: Int,
        mimeType: String,
        width: Int?,
        height: Int?,
        durationSeconds: Double?,
    ): ArtistMediaItem {
        try {
            return client.postgrest.from("artist_media")
                .insert(
                    MediaInsert(
                        artist_id = artistId,
                        kind = kind.db,
                        aspect = aspect.db,
                        position = position,
                        storage_path = path,
                        mime_type = mimeType,
                        width = width,
                        height = height,
                        duration_seconds = durationSeconds,
                    ),
                ) { select(MEDIA_COLUMNS) }
                .decodeSingle<DBArtistMedia>()
                .toItem()
                ?: throw ArtistMediaError.InsertReturnedEmpty
        } catch (t: Throwable) {
            runCatching { client.storage.from(BUCKET).delete(path) }
            // The cap trigger raises a check_violation carrying "cap reached".
            if (t.message?.contains("cap reached", ignoreCase = true) == true) {
                throw ArtistMediaError.CapReached(kind)
            }
            if (t is ArtistMediaError) throw t
            throw ArtistMediaError.Underlying(t)
        }
    }

    private suspend fun nextPosition(artistId: String, kind: MediaKind): Int {
        val rows = client.postgrest.from("artist_media")
            .select(Columns.list("position")) {
                filter { eq("artist_id", artistId); eq("kind", kind.db) }
                order("position", Order.DESCENDING)
                limit(1)
            }
            .decodeList<PositionOnly>()
        return (rows.firstOrNull()?.position ?: -1) + 1
    }

    private fun storagePath(artistId: String, kind: MediaKind, ext: String): String =
        "$artistId/${kind.db}/${UUID.randomUUID().toString().lowercaseUuid()}.$ext"

    @Serializable
    private data class MediaInsert(
        val artist_id: String,
        val kind: String,
        val aspect: String,
        val position: Int,
        val storage_path: String,
        val mime_type: String,
        val width: Int?,
        val height: Int?,
        val duration_seconds: Double?,
    )

    @Serializable private data class PositionOnly(val position: Int)

    @Serializable
    private data class DBArtistMedia(
        val id: String,
        val artist_id: String,
        val kind: String,
        val aspect: String,
        val position: Int,
        val storage_path: String,
        val mime_type: String,
        val width: Int? = null,
        val height: Int? = null,
        val duration_seconds: Double? = null,
        val created_at: String? = null,
    ) {
        fun toItem(): ArtistMediaItem? {
            val k = MediaKind.fromDb(kind) ?: return null
            val a = MediaAspect.fromDb(aspect) ?: return null
            return ArtistMediaItem(
                id = id,
                artistId = artist_id,
                kind = k,
                aspect = a,
                position = position,
                storagePath = storage_path,
                mimeType = mime_type,
                width = width,
                height = height,
                durationSeconds = duration_seconds,
                createdAt = created_at?.let { SupabaseISO8601.parse(it) } ?: Instant.EPOCH,
            )
        }
    }

    companion object {
        const val BUCKET = "artist-media"
        private val MEDIA_COLUMNS = Columns.list(
            "id", "artist_id", "kind", "aspect", "position", "storage_path",
            "mime_type", "width", "height", "duration_seconds", "created_at",
        )
    }
}

/** Typed media-write failures (iOS `ArtistMediaError`). */
sealed class ArtistMediaError(message: String) : Exception(message) {
    class CapReached(val kind: MediaKind) : ArtistMediaError(
        when (kind) {
            MediaKind.Photo -> "You can have up to 6 photos. Remove one first."
            MediaKind.Video -> "You can have one short video. Remove it first to replace it."
        },
    )
    data object InsertReturnedEmpty : ArtistMediaError("Upload finished but the server returned no record.")
    class Underlying(cause: Throwable) : ArtistMediaError(cause.message ?: "Upload failed.")
}

/** In-memory twin — enforces the 6-photo / 1-video caps like the server trigger. */
class FakeArtistMediaRepository : ArtistMediaRepository {
    private val store = mutableMapOf<String, MutableList<ArtistMediaItem>>()

    override suspend fun list(artistId: String): List<ArtistMediaItem> =
        store[artistId].orEmpty().sortedWith(compareBy({ it.kind.db }, { it.position }))

    override suspend fun uploadPhoto(
        jpegBytes: ByteArray,
        width: Int,
        height: Int,
        artistId: String,
        position: Int?,
    ): ArtistMediaItem {
        val list = store.getOrPut(artistId) { mutableListOf() }
        if (list.count { it.kind == MediaKind.Photo } >= 6) throw ArtistMediaError.CapReached(MediaKind.Photo)
        val pos = position ?: ((list.filter { it.kind == MediaKind.Photo }.maxOfOrNull { it.position } ?: -1) + 1)
        return item(artistId, MediaKind.Photo, MediaAspect.classify(width, height), pos, "image/jpeg", width, height, null)
            .also { list.add(it) }
    }

    override suspend fun uploadVideo(
        mp4Bytes: ByteArray,
        durationSeconds: Double,
        width: Int,
        height: Int,
        artistId: String,
    ): ArtistMediaItem {
        val list = store.getOrPut(artistId) { mutableListOf() }
        if (list.any { it.kind == MediaKind.Video }) throw ArtistMediaError.CapReached(MediaKind.Video)
        val pos = (list.filter { it.kind == MediaKind.Video }.maxOfOrNull { it.position } ?: -1) + 1
        return item(artistId, MediaKind.Video, MediaAspect.classify(width, height), pos, "video/mp4", width, height, durationSeconds)
            .also { list.add(it) }
    }

    override suspend fun delete(item: ArtistMediaItem) {
        store[item.artistId]?.removeAll { it.id == item.id }
    }

    private fun item(
        artistId: String, kind: MediaKind, aspect: MediaAspect, position: Int,
        mime: String, width: Int?, height: Int?, duration: Double?,
    ) = ArtistMediaItem(
        id = UUID.randomUUID().toString(),
        artistId = artistId,
        kind = kind,
        aspect = aspect,
        position = position,
        storagePath = "$artistId/${kind.db}/$position.${if (kind == MediaKind.Photo) "jpg" else "mp4"}",
        mimeType = mime,
        width = width,
        height = height,
        durationSeconds = duration,
        createdAt = Instant.now(),
    )
}
