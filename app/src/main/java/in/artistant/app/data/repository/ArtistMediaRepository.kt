package `in`.artistant.app.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.core.result.mapPostgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class ArtistMediaKind { photo, video }

enum class ArtistMediaAspect {
    square, portrait, landscape;

    companion object {
        fun classify(width: Int, height: Int): ArtistMediaAspect {
            if (height <= 0) return square
            val ratio = width.toFloat() / height
            return when {
                ratio > 1.2f -> landscape
                ratio < 0.85f -> portrait
                else -> square
            }
        }
    }
}

data class ArtistMediaItem(
    val id: String,
    val artistId: String,
    val kind: ArtistMediaKind,
    val aspect: ArtistMediaAspect,
    val position: Int,
    val storagePath: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
    /**
     * A ready-made URL for this item, used instead of deriving one from
     * [storagePath].
     *
     * Always null for a row that came off the server — that URL is a function of
     * the bucket and the path, and nothing should be able to override it. The
     * field exists for the repositories that are NOT Supabase-backed (the debug
     * harness, test doubles) and can hand back an address directly, such as a
     * local file. Without it, an item's URL is unavoidably an https address at
     * the configured project, which an offline fixture can never serve.
     */
    val directUrl: String? = null,
) {
    val publicUrl: String?
        get() {
            directUrl?.let { return it }
            val base = AppEnvironment.supabaseUrl.trimEnd('/')
            if (base.isBlank() || storagePath.isBlank()) return null
            return "$base/storage/v1/object/public/$BUCKET/$storagePath"
        }

    companion object {
        const val BUCKET = "artist-media"
    }
}

/**
 * `public.artist_media` + `artist-media` bucket — port of iOS ArtistMediaRepository.
 * Cover photo = kind=photo position=0. Cap enforced server-side (6 photos).
 */
interface ArtistMediaRepository {
    suspend fun list(artistId: String): List<ArtistMediaItem>
    suspend fun uploadPhoto(jpegFile: File, artistId: String, position: Int? = null): ArtistMediaItem
    suspend fun delete(item: ArtistMediaItem)
    /** Cover-first id order — RPC `reorder_artist_media(p_ids)`. */
    suspend fun reorder(ids: List<String>)
}

@Singleton
class SupabaseArtistMediaRepository @Inject constructor(
    private val client: SupabaseClient,
) : ArtistMediaRepository {

    override suspend fun list(artistId: String): List<ArtistMediaItem> =
        try {
            client.from("artist_media")
                .select {
                    filter { eq("artist_id", artistId.lowercase()) }
                    order("kind", Order.ASCENDING)
                    order("position", Order.ASCENDING)
                }
                .decodeList<MediaRow>()
                .mapNotNull { it.toItem() }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }

    override suspend fun uploadPhoto(
        jpegFile: File,
        artistId: String,
        position: Int?,
    ): ArtistMediaItem {
        val artist = artistId.lowercase()
        val normalized = normalizeJpeg(jpegFile)
        val aspect = ArtistMediaAspect.classify(normalized.width, normalized.height)
        val resolvedPosition = position ?: nextPosition(artist, ArtistMediaKind.photo)
        val fileUuid = UUID.randomUUID().toString().lowercase()
        val path = "$artist/photo/$fileUuid.jpg"
        try {
            client.storage.from(ArtistMediaItem.BUCKET).upload(path, normalized.bytes) {
                contentType = ContentType.Image.JPEG
                upsert = false
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
        return try {
            insertRow(
                artistId = artist,
                kind = ArtistMediaKind.photo,
                aspect = aspect,
                position = resolvedPosition,
                storagePath = path,
                mimeType = "image/jpeg",
                width = normalized.width,
                height = normalized.height,
                durationSeconds = null,
            )
        } catch (t: Throwable) {
            runCatching { client.storage.from(ArtistMediaItem.BUCKET).delete(path) }
            throw mapPostgrest(t)
        }
    }

    override suspend fun delete(item: ArtistMediaItem) {
        try {
            client.from("artist_media").delete {
                filter { eq("id", item.id.lowercase()) }
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
        runCatching { client.storage.from(ArtistMediaItem.BUCKET).delete(item.storagePath) }
    }

    override suspend fun reorder(ids: List<String>) {
        if (ids.isEmpty()) return
        val payload = ReorderParams(pIds = ids.map { it.lowercase() })
        try {
            client.postgrest.rpc("reorder_artist_media", payload)
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    private suspend fun insertRow(
        artistId: String,
        kind: ArtistMediaKind,
        aspect: ArtistMediaAspect,
        position: Int,
        storagePath: String,
        mimeType: String,
        width: Int?,
        height: Int?,
        durationSeconds: Double?,
    ): ArtistMediaItem {
        val row = client.from("artist_media")
            .insert(
                MediaInsert(
                    artistId = artistId,
                    kind = kind.name,
                    aspect = aspect.name,
                    position = position,
                    storagePath = storagePath,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    durationSeconds = durationSeconds,
                ),
            ) { select() }
            .decodeSingle<MediaRow>()
        return row.toItem() ?: error("artist_media insert returned unreadable row")
    }

    private suspend fun nextPosition(artistId: String, kind: ArtistMediaKind): Int {
        val rows = client.from("artist_media")
            .select(Columns.list("position")) {
                filter {
                    eq("artist_id", artistId)
                    eq("kind", kind.name)
                }
                order("position", Order.DESCENDING)
                limit(1)
            }
            .decodeList<PositionOnly>()
        return (rows.firstOrNull()?.position ?: -1) + 1
    }

    companion object {
        private const val MAX_DIM = 2048
        private const val JPEG_QUALITY = 85

        data class NormalizedJpeg(val bytes: ByteArray, val width: Int, val height: Int)

        fun normalizeJpeg(file: File): NormalizedJpeg {
            val original = BitmapFactory.decodeFile(file.absolutePath)
                ?: error("Could not decode image")
            val scale = maxOf(original.width, original.height).toFloat() / MAX_DIM
            val scaled = if (scale > 1f) {
                val w = (original.width / scale).toInt().coerceAtLeast(1)
                val h = (original.height / scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(original, w, h, true).also {
                    if (it !== original) original.recycle()
                }
            } else {
                original
            }
            // Turn AFTER the downscale: the scale factor is max(w, h), which no
            // rotation can change, so the result is identical either way — but
            // this turns a 2048px copy instead of holding two full 12MP bitmaps.
            val bitmap = uprighted(scaled, file)
            if (bitmap !== scaled) scaled.recycle()
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            val w = bitmap.width
            val h = bitmap.height
            bitmap.recycle()
            return NormalizedJpeg(out.toByteArray(), w, h)
        }

        /**
         * The source file's EXIF orientation, baked into the pixels.
         *
         * [BitmapFactory] decodes the stored pixels and ignores the Orientation
         * tag; [Bitmap.compress] then writes a JPEG carrying no EXIF at all — so
         * an un-applied hint isn't deferred to the viewer, it's destroyed. A
         * portrait phone shot (landscape pixels + `ORIENTATION_ROTATE_90`) would
         * upload sideways on the profile hero, the Discover tile and the EPK
         * grid, with no way to fix it in-app, and [ArtistMediaAspect.classify]
         * would measure the aspect on the swapped axis and store `landscape`.
         * iOS encodes through `UIImage`, which applies orientation first — this
         * is what keeps the two clients agreeing on the same source photo.
         *
         * Unreadable EXIF is treated as "already upright" rather than fatal: an
         * un-rotated upload beats a failed one.
         */
        private fun uprighted(bitmap: Bitmap, file: File): Bitmap {
            val orientation = runCatching {
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap
            }
            return runCatching {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }.getOrDefault(bitmap)
        }
    }
}

class FakeArtistMediaRepository : ArtistMediaRepository {
    private val byArtist = mutableMapOf<String, MutableList<ArtistMediaItem>>()
    val uploaded = mutableListOf<File>()

    override suspend fun list(artistId: String): List<ArtistMediaItem> =
        byArtist[artistId.lowercase()].orEmpty()

    override suspend fun uploadPhoto(
        jpegFile: File,
        artistId: String,
        position: Int?,
    ): ArtistMediaItem {
        uploaded.add(jpegFile)
        val item = ArtistMediaItem(
            id = UUID.randomUUID().toString().lowercase(),
            artistId = artistId.lowercase(),
            kind = ArtistMediaKind.photo,
            aspect = ArtistMediaAspect.square,
            position = position ?: byArtist[artistId.lowercase()].orEmpty().size,
            storagePath = "${artistId.lowercase()}/photo/${UUID.randomUUID()}.jpg",
            mimeType = "image/jpeg",
            width = 100,
            height = 100,
        )
        byArtist.getOrPut(artistId.lowercase()) { mutableListOf() }.add(item)
        return item
    }

    override suspend fun delete(item: ArtistMediaItem) {
        byArtist[item.artistId.lowercase()]?.removeAll { it.id == item.id }
    }

    override suspend fun reorder(ids: List<String>) {
        // no-op for tests
    }
}

@Serializable
private data class MediaInsert(
    @SerialName("artist_id") val artistId: String,
    val kind: String,
    val aspect: String,
    val position: Int,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("mime_type") val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
)

@Serializable
private data class MediaRow(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val kind: String,
    val aspect: String,
    val position: Int,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("mime_type") val mimeType: String = "image/jpeg",
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
) {
    fun toItem(): ArtistMediaItem? {
        val k = runCatching { ArtistMediaKind.valueOf(kind) }.getOrNull() ?: return null
        val a = runCatching { ArtistMediaAspect.valueOf(aspect) }.getOrNull()
            ?: ArtistMediaAspect.square
        return ArtistMediaItem(
            id = id.lowercase(),
            artistId = artistId.lowercase(),
            kind = k,
            aspect = a,
            position = position,
            storagePath = storagePath,
            mimeType = mimeType,
            width = width,
            height = height,
            durationSeconds = durationSeconds,
        )
    }
}

@Serializable
private data class ReorderParams(@SerialName("p_ids") val pIds: List<String>)

@Serializable
private data class PositionOnly(val position: Int)
