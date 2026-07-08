package `in`.artistant.app.platform.upload

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.platform.media.WizardMediaCache
import timber.log.Timber

/**
 * The single worker that drains any one upload task (photo / video / audio /
 * publish-flag), injected via Hilt-Work so it reaches the Supabase repositories.
 * WorkManager owns persistence + retry + the NetworkType.CONNECTED constraint, so
 * this is just: read the staged file, call the repo, translate the outcome.
 *
 * Retry budget is 3 attempts (mirrors the iOS UploadQueue): a transient failure
 * returns [Result.retry] until [MAX_ATTEMPTS], after which it's [Result.failure]
 * (terminal — the artist's row stays unpublished, which the publish gate honours).
 * A missing staged file (OS cache eviction) is terminal immediately — retrying
 * can't bring the bytes back.
 */
@HiltWorker
class MediaUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cache: WizardMediaCache,
    private val samples: SamplesRepository,
    private val media: ArtistMediaRepository,
    private val artists: ArtistsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND)?.let { runCatching { UploadKind.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val artistId = inputData.getString(KEY_ARTIST) ?: return Result.failure()

        return try {
            when (kind) {
                UploadKind.PHOTO -> {
                    val bytes = readStaged() ?: return Result.failure() // evicted → terminal
                    val position = inputData.getInt(KEY_POSITION, -1).takeIf { it >= 0 }
                    media.uploadPhoto(
                        jpegBytes = bytes,
                        width = inputData.getInt(KEY_WIDTH, 0),
                        height = inputData.getInt(KEY_HEIGHT, 0),
                        artistId = artistId,
                        position = position,
                    )
                    Result.success()
                }

                UploadKind.VIDEO -> {
                    val bytes = readStaged() ?: return Result.failure()
                    media.uploadVideo(
                        mp4Bytes = bytes,
                        durationSeconds = inputData.getDouble(KEY_DURATION, 0.0),
                        width = inputData.getInt(KEY_WIDTH, 0),
                        height = inputData.getInt(KEY_HEIGHT, 0),
                        artistId = artistId,
                    )
                    Result.success()
                }

                UploadKind.AUDIO -> {
                    val bytes = readStaged() ?: return Result.failure()
                    samples.upload(
                        audioBytes = bytes,
                        ext = inputData.getString(KEY_EXT) ?: "m4a",
                        mime = inputData.getString(KEY_MIME) ?: "audio/m4a",
                        title = inputData.getString(KEY_TITLE) ?: "Untitled sample",
                        durationSeconds = inputData.getDouble(KEY_DURATION, 0.0),
                        artistId = artistId,
                    )
                    Result.success()
                }

                UploadKind.PUBLISH_FLAG -> {
                    // Publish gate: only flip published=true once every media task for
                    // this artist has SUCCEEDED. Keep retrying while media is still in
                    // flight; give up (don't publish) if any media task FAILED.
                    when (mediaOutcome(artistId)) {
                        MediaTaskState.SUCCEEDED -> {
                            artists.publish(artistId)
                            Result.success()
                        }
                        MediaTaskState.PENDING -> Result.retry()
                        MediaTaskState.FAILED -> Result.failure()
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "upload task failed (kind=%s attempt=%d)", kind, runAttemptCount)
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private fun readStaged(): ByteArray? {
        val filename = inputData.getString(KEY_FILENAME) ?: return null
        val file = cache.resolve(filename)
        return if (file.exists()) file.readBytes() else null
    }

    /**
     * Collapses this artist's media work into one gate state via [UploadPolicy].
     * Uses the app's WorkManager to read the media tasks tagged for the artist.
     */
    private fun mediaOutcome(artistId: String): MediaTaskState {
        val infos = androidx.work.WorkManager.getInstance(applicationContext)
            .getWorkInfosByTag(UploadQueue.artistTag(artistId)).get()
        val mediaStates = infos
            .filter { it.tags.contains(UploadQueue.KIND_TAG_PREFIX + UploadKind.PHOTO.name) ||
                it.tags.contains(UploadQueue.KIND_TAG_PREFIX + UploadKind.VIDEO.name) ||
                it.tags.contains(UploadQueue.KIND_TAG_PREFIX + UploadKind.AUDIO.name) }
            .map {
                when (it.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> MediaTaskState.SUCCEEDED
                    androidx.work.WorkInfo.State.FAILED, androidx.work.WorkInfo.State.CANCELLED -> MediaTaskState.FAILED
                    else -> MediaTaskState.PENDING
                }
            }
        if (mediaStates.any { it == MediaTaskState.FAILED }) return MediaTaskState.FAILED
        return if (UploadPolicy.canPublish(mediaStates)) MediaTaskState.SUCCEEDED else MediaTaskState.PENDING
    }

    companion object {
        const val MAX_ATTEMPTS = 3

        const val KEY_KIND = "kind"
        const val KEY_ARTIST = "artist_id"
        const val KEY_FILENAME = "filename"
        const val KEY_POSITION = "position"
        const val KEY_TITLE = "title"
        const val KEY_DURATION = "duration"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_EXT = "ext"
        const val KEY_MIME = "mime"
    }
}
