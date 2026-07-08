package `in`.artistant.app.platform.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Trims a picked video to a short clip (<= [maxSeconds]) and re-encodes it to MP4
 * on-device via Media3 [Transformer] (port of iOS `VideoTrimmer`, which uses
 * AVAssetExportSession). We want short cinematic loops, not full performances, and
 * doing it client-side avoids a server ffmpeg Edge Function.
 *
 * DEVICE-DEPENDENT: the actual encode depends on the phone's codecs, so only
 * [clampDuration] is unit-tested; the transform + [firstFrame] are verified
 * on-device. Transformer requires a Looper thread, so [trim] runs on Main.
 */
@Singleton
class VideoTrimmer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Output(
        val file: File,
        val durationSeconds: Double,
        val width: Int,
        val height: Int,
    )

    suspend fun trim(source: Uri, maxSeconds: Double = 10.0): Output = withContext(Dispatchers.Main) {
        val outFile = File(context.cacheDir, "trim-${UUID.randomUUID()}.mp4")
        // Clip to the first maxSeconds; the export re-encodes to an MP4/H264 clip.
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setEndPositionMs((maxSeconds * 1000).toLong())
            .build()
        val mediaItem = MediaItem.Builder().setUri(source).setClippingConfiguration(clip).build()

        // iOS parity (VideoTrimmer.swift): AVAssetExportPreset1920x1080 fits the clip
        // inside a 1920×1080 box, so a 4K capture uploads at ~1080p, not 4K. Media3
        // Transformer has no export "preset", so we cap explicitly with a Presentation
        // video effect sized to make the source's SHORT side 1080 (long side → 1920 for
        // 16:9), preserving aspect. Downscale-only: [capSize] returns null for a clip
        // already <= 1080p (or unreadable dims) so we never upscale a smaller source.
        val editedBuilder = EditedMediaItem.Builder(mediaItem)
        capSize(source)?.let { (w, h) ->
            // device: 1.4.1 has no Presentation.createForShortSide, so we pass explicit
            // even dims (H264 encoders reject odd width/height on many devices) built
            // from the source aspect; LAYOUT_SCALE_TO_FIT keeps aspect within the box.
            val presentation = Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)
            editedBuilder.setEffects(Effects(emptyList(), listOf<Effect>(presentation)))
        }
        val edited = editedBuilder.build()

        val outPath: String = suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        cont.resume(outFile.path)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        outFile.delete()
                        cont.resumeWithException(exception)
                    }
                })
                .build()
            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
                outFile.delete()
            }
            transformer.start(edited, outFile.path)
        }

        val output = File(outPath)
        val (w, h, durMs) = probe(output)
        Output(
            file = output,
            // Clamp into the DB-legal [1, 10] window (artist_media.duration_seconds
            // CHECK) so a sub-1s clip or a rounding overshoot can't fail the insert.
            durationSeconds = clampDuration(durMs / 1000.0, maxSeconds),
            width = w,
            height = h,
        )
    }

    /** First frame for the wizard preview still; null if the asset is unreadable. */
    fun firstFrame(source: Uri): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, source)
            mmr.getFrameAtTime(0)
        } catch (_: Throwable) {
            null
        } finally {
            mmr.release()
        }
    }

    /**
     * Target (width, height) for the 1080p cap, or null when no cap is needed:
     * the source is already <= 1080 on its short side (downscale-only — never
     * upscale a smaller clip) or its dimensions are unreadable. Scales the source
     * dims so min(w,h) == 1080, rounding to even (H264 wants even dimensions), which
     * mirrors iOS's 1920×1080 export box (short side 1080).
     */
    private fun capSize(source: Uri): Pair<Int, Int>? {
        val (w, h) = sourceDimensions(source) ?: return null
        if (w <= 0 || h <= 0 || minOf(w, h) <= MAX_SHORT_SIDE) return null
        val scale = MAX_SHORT_SIDE.toDouble() / minOf(w, h)
        fun even(v: Double) = (v.toInt() and 1.inv()).coerceAtLeast(2)
        return even(w * scale) to even(h * scale)
    }

    /** Raw pixel dimensions of the source clip, or null if unreadable. */
    private fun sourceDimensions(source: Uri): Pair<Int, Int>? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, source)
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (w != null && h != null) w to h else null
        } catch (_: Throwable) {
            null
        } finally {
            mmr.release()
        }
    }

    private fun probe(file: File): Triple<Int, Int, Long> {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.path)
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(w, h, d)
        } catch (_: Throwable) {
            Triple(0, 0, 0L)
        } finally {
            mmr.release()
        }
    }

    companion object {
        /** iOS caps at 1920×1080 (short side 1080); we match by short side. */
        private const val MAX_SHORT_SIDE = 1080

        /**
         * Pin a measured clip duration into the DB-legal [1, maxSeconds] window.
         * Pure so it's unit-testable off any looper. Port of iOS
         * `VideoTrimmer.clampDuration`.
         */
        fun clampDuration(seconds: Double, maxSeconds: Double = 10.0): Double =
            seconds.coerceIn(1.0, maxSeconds)
    }
}
