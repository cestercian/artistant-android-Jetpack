package `in`.artistant.app.platform.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.data.model.MediaAspect
import `in`.artistant.app.data.model.MediaKind
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk staging for wizard media (port of iOS `WizardMediaCache`). Picked photos
 * are normalized to JPEG, trimmed videos are adopted, audio samples are copied and
 * duration-probed — all landing under `cacheDir/artist-wizard/`. The upload queue
 * later streams these files to Storage; keeping only a filename + metadata off the
 * UI state graph is what keeps a 12MP pick from ballooning resident memory.
 *
 * `cacheDir` is OS-managed and CAN be evicted under storage pressure, so callers
 * that read a ref must tolerate a missing file (the queue surfaces that as a
 * re-pick). [deleteAll] runs on publish + sign-out so the next user starts clean.
 */
/**
 * The narrow media-staging seam the EPK editor's ViewModel depends on (implemented by
 * [WizardMediaCache]). Extracted for the SAME single reason as [MediaUploadEnqueuer]:
 * [WizardMediaCache] needs a Context so it can't be constructed in a plain-JVM unit
 * test, and the EpkViewModel must be. Only the four methods the EPK add/remove paths
 * touch are on it.
 */
interface EpkMediaStager {
    fun writePhoto(source: Uri): PendingMediaRef
    fun adoptAudio(source: Uri, displayName: String?): PendingAudioRef
    /** Raw bytes of a staged file — the EPK sample add reads them for the immediate upload. */
    fun bytesOf(cacheFilename: String): ByteArray
    fun delete(cacheFilename: String)
}

@Singleton
class WizardMediaCache @Inject constructor(
    @ApplicationContext private val context: Context,
) : EpkMediaStager {
    private val dir: File
        get() = File(context.cacheDir, "artist-wizard").apply { mkdirs() }

    /** Absolute file for a ref's [PendingMediaRef.cacheFilename]. */
    fun resolve(cacheFilename: String): File = File(dir, cacheFilename)

    /**
     * Decodes [source] to a Bitmap, downscales so the longest edge is <= 2048px,
     * re-encodes JPEG (q=85), writes it, and returns a photo ref. Throws
     * [WizardMediaError.EncodingFailed] if the pick can't be decoded/encoded.
     */
    override fun writePhoto(source: Uri): PendingMediaRef {
        val bitmap = context.contentResolver.openInputStream(source).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw WizardMediaError.EncodingFailed
        val scaled = downscale(bitmap, maxDimension = 2048)
        val id = UUID.randomUUID().toString().lowercase()
        val filename = "$id.jpg"
        val file = File(dir, filename)
        val ok = file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        if (!ok) {
            file.delete()
            throw WizardMediaError.EncodingFailed
        }
        val (w, h) = scaled.width to scaled.height
        if (scaled != bitmap) bitmap.recycle()
        return PendingMediaRef(
            id = id,
            kind = MediaKind.Photo,
            aspect = MediaAspect.classify(w, h),
            cacheFilename = filename,
            mimeType = "image/jpeg",
            width = w,
            height = h,
            durationSeconds = null,
        )
    }

    /**
     * Adopts a freshly-trimmed video (VideoTrimmer writes to a temp file) by moving
     * it into the wizard cache so its lifetime is decoupled from temp eviction.
     */
    fun adoptVideo(output: VideoTrimmer.Output): PendingMediaRef {
        val id = UUID.randomUUID().toString().lowercase()
        val filename = "$id.mp4"
        val dest = File(dir, filename)
        if (dest.exists()) dest.delete()
        // rename is atomic on the same volume; fall back to copy across volumes.
        if (!output.file.renameTo(dest)) {
            output.file.copyTo(dest, overwrite = true)
            output.file.delete()
        }
        return PendingMediaRef(
            id = id,
            kind = MediaKind.Video,
            aspect = MediaAspect.classify(output.width, output.height),
            cacheFilename = filename,
            mimeType = "video/mp4",
            width = output.width,
            height = output.height,
            durationSeconds = output.durationSeconds,
        )
    }

    /**
     * Copies a picked audio file into the cache (preserving its extension so the
     * Storage Content-Type stays honest) and probes duration via
     * [MediaMetadataRetriever]. Throws [WizardMediaError.AudioProbeFailed] on an
     * unreadable/zero-length clip (the copied bytes are cleaned up first).
     */
    override fun adoptAudio(source: Uri, displayName: String?): PendingAudioRef {
        val srcExt = (displayName ?: source.lastPathSegment ?: "").substringAfterLast('.', "")
        val ext = WizardMediaFormats.normalizedAudioExt(srcExt)
        val mime = WizardMediaFormats.audioMime(srcExt)
        val id = UUID.randomUUID().toString().lowercase()
        val filename = "$id.$ext"
        val file = File(dir, filename)

        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "audio source not readable" }
            file.outputStream().use { input.copyTo(it) }
        }

        // MediaMetadataRetriever only implements AutoCloseable on API 29+; minSdk
        // is 26, so release() explicitly in a finally rather than use {}.
        val durationMs = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.path)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            } finally {
                mmr.release()
            }
        }.getOrNull()
        val seconds = (durationMs ?: 0L) / 1000.0
        if (seconds <= 0.0) {
            file.delete()
            throw WizardMediaError.AudioProbeFailed
        }

        val stem = (displayName ?: filename).substringBeforeLast('.').replace('_', ' ').trim()
        return PendingAudioRef(
            id = id,
            cacheFilename = filename,
            title = stem.ifEmpty { "Untitled sample" },
            durationSeconds = seconds,
            ext = ext,
            mimeType = mime,
        )
    }

    override fun bytesOf(cacheFilename: String): ByteArray = File(dir, cacheFilename).readBytes()

    override fun delete(cacheFilename: String) {
        runCatching { File(dir, cacheFilename).delete() }
    }

    /** Nukes the whole wizard cache (publish / sign-out). */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun downscale(src: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(src.width, src.height)
        if (largest <= maxDimension || largest == 0) return src
        val scale = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}

/**
 * Ref to a picked photo/video staged on disk. Only the filename + metadata cross
 * layer boundaries; [PendingMediaRef.cacheFilename] resolves lazily through
 * [WizardMediaCache.resolve] because the absolute path isn't stable.
 */
data class PendingMediaRef(
    val id: String,
    val kind: MediaKind,
    val aspect: MediaAspect,
    val cacheFilename: String,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Double?,
)

/** Ref to a picked audio sample (targets `public.samples`, carries a title). */
data class PendingAudioRef(
    val id: String,
    val cacheFilename: String,
    val title: String,
    val durationSeconds: Double,
    val ext: String,
    val mimeType: String,
) {
    val durationLabel: String get() = WizardMediaFormats.durationLabel(durationSeconds)
}

/**
 * Pure format rules for the wizard cache — extracted so the audio MIME/extension
 * mapping and the duration label are unit-testable without Android graphics/media.
 * Ports the switch in iOS `WizardMediaCache.adoptAudio` + `PendingAudioRef`.
 */
object WizardMediaFormats {
    fun normalizedAudioExt(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "mp3"
        "wav" -> "wav"
        "aiff", "aif" -> "aiff"
        "m4a", "" -> "m4a"
        else -> ext.lowercase()
    }

    fun audioMime(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "aiff", "aif" -> "audio/aiff"
        "m4a", "" -> "audio/m4a"
        else -> "audio/${ext.lowercase()}"
    }

    /** `30s` / `1:24` — mono-numeral duration label, same shape as the DB row. */
    fun durationLabel(seconds: Double): String {
        val secs = Math.round(seconds).toInt()
        return if (secs < 60) "${secs}s" else "%d:%02d".format(secs / 60, secs % 60)
    }
}

sealed class WizardMediaError(message: String) : Exception(message) {
    data object EncodingFailed : WizardMediaError("Couldn't process that image — try a different photo.")
    data object AudioProbeFailed : WizardMediaError("Couldn't read that audio file — try a different clip.")
}
