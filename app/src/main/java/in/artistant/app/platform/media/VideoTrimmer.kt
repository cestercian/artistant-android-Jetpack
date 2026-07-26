package `in`.artistant.app.platform.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cover-video trimmer — port of iOS VideoTrimmer.
 * Takes the first [maxDurationMs] of [input] and writes an MP4 under the wizard cache.
 * Soft-fails to copying the original when Transformer isn't viable on-device.
 */
@Singleton
class VideoTrimmer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun trimToMax(input: Uri, maxDurationMs: Long = MAX_MS): File {
        val out = File(context.cacheDir, "artist-wizard/video-${UUID.randomUUID()}.mp4").also {
            it.parentFile?.mkdirs()
        }
        return try {
            transform(input, out, maxDurationMs)
            out
        } catch (t: Throwable) {
            // Fallback: copy bytes so publish still works; length may exceed 10s.
            context.contentResolver.openInputStream(input)?.use { src ->
                out.outputStream().use { dst -> src.copyTo(dst) }
            } ?: throw t
            out
        }
    }

    private suspend fun transform(input: Uri, out: File, maxDurationMs: Long): Unit =
        suspendCancellableCoroutine { cont ->
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(0)
                .setEndPositionMs(maxDurationMs)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(input)
                .setClippingConfiguration(clipping)
                .build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), emptyList()))
                .build()
            val composition = Composition.Builder(
                EditedMediaItemSequence(edited),
            ).build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()
            cont.invokeOnCancellation { transformer.cancel() }
            transformer.start(composition, out.absolutePath)
        }

    companion object {
        const val MAX_MS = 10_000L
    }
}
