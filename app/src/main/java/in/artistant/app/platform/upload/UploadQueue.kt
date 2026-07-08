package `in`.artistant.app.platform.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.platform.media.PendingAudioRef
import `in`.artistant.app.platform.media.PendingMediaRef
import `in`.artistant.app.platform.media.WizardMediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues wizard media uploads onto WorkManager (port of the iOS `UploadQueue`,
 * but delegating persistence + retry to WorkManager instead of the hand-rolled
 * JSON snapshot). Each task is one [MediaUploadWorker] with:
 *  - a NetworkType.CONNECTED constraint (don't burn retries offline),
 *  - exponential backoff (the worker caps itself at 3 attempts),
 *  - tags for the owning artist + kind so the publish gate and the cross-account
 *    purge can reason over the queue.
 *
 * The load-bearing decisions (purge on account switch, publish-gate) live in the
 * pure [UploadPolicy] so they're unit-tested; this class is just the wiring.
 */
@Singleton
class UploadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: WizardMediaCache,
) : MediaUploadEnqueuer, UploadBannerSource {
    private val wm: WorkManager get() = WorkManager.getInstance(context)

    // `override`d from MediaUploadEnqueuer (no default on `position` — an interface method's
    // override can't redeclare defaults; every caller passes it explicitly).
    override fun enqueuePhoto(ref: PendingMediaRef, artistId: String, position: Int?): UUID {
        val data = baseData(UploadKind.PHOTO, artistId)
            .putString(MediaUploadWorker.KEY_FILENAME, ref.cacheFilename)
            .putInt(MediaUploadWorker.KEY_POSITION, position ?: -1)
            .putInt(MediaUploadWorker.KEY_WIDTH, ref.width ?: 0)
            .putInt(MediaUploadWorker.KEY_HEIGHT, ref.height ?: 0)
            .build()
        return enqueue(UploadKind.PHOTO, artistId, data, ref.cacheFilename)
    }

    override fun enqueueVideo(ref: PendingMediaRef, artistId: String): UUID {
        val data = baseData(UploadKind.VIDEO, artistId)
            .putString(MediaUploadWorker.KEY_FILENAME, ref.cacheFilename)
            .putDouble(MediaUploadWorker.KEY_DURATION, ref.durationSeconds ?: 0.0)
            .putInt(MediaUploadWorker.KEY_WIDTH, ref.width ?: 0)
            .putInt(MediaUploadWorker.KEY_HEIGHT, ref.height ?: 0)
            .build()
        return enqueue(UploadKind.VIDEO, artistId, data, ref.cacheFilename)
    }

    override fun enqueueAudioSample(ref: PendingAudioRef, artistId: String): UUID {
        val data = baseData(UploadKind.AUDIO, artistId)
            .putString(MediaUploadWorker.KEY_FILENAME, ref.cacheFilename)
            .putString(MediaUploadWorker.KEY_TITLE, ref.title)
            .putDouble(MediaUploadWorker.KEY_DURATION, ref.durationSeconds)
            .putString(MediaUploadWorker.KEY_EXT, ref.ext)
            .putString(MediaUploadWorker.KEY_MIME, ref.mimeType)
            .build()
        return enqueue(UploadKind.AUDIO, artistId, data, ref.cacheFilename)
    }

    /** Flip published=true LAST — the worker gates on media completion. */
    fun enqueuePublishFlag(artistId: String): UUID {
        val data = baseData(UploadKind.PUBLISH_FLAG, artistId).build()
        return enqueue(UploadKind.PUBLISH_FLAG, artistId, data)
    }

    /**
     * Cross-account purge on launch (see [UploadPolicy.tasksToPurge]): cancel any
     * queued task that doesn't belong to the signed-in user. Blocking WorkManager
     * read, so runs off the main thread.
     */
    suspend fun resumeAfterLaunch(currentUserId: String?) = withContext(Dispatchers.IO) {
        val infos = wm.getWorkInfosByTag(ARTIST_UPLOAD_TAG).get()
        val tasks = infos.mapNotNull { info ->
            val artist = info.tags.firstOrNull { it.startsWith(ARTIST_TAG_PREFIX) }
                ?.removePrefix(ARTIST_TAG_PREFIX) ?: return@mapNotNull null
            val kind = info.tags.firstOrNull { it.startsWith(KIND_TAG_PREFIX) }
                ?.removePrefix(KIND_TAG_PREFIX)
                ?.let { runCatching { UploadKind.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            // Staged filename rides a tag (WorkInfo doesn't expose input Data), so the
            // purge can delete the foreign cache file. Null for the publish-flag task.
            val file = info.tags.firstOrNull { it.startsWith(FILE_TAG_PREFIX) }
                ?.removePrefix(FILE_TAG_PREFIX)
            UploadTaskInfo(info.id.toString(), artist, kind, file)
        }
        UploadPolicy.tasksToPurge(tasks, currentUserId?.lowercaseUuid()).forEach { purged ->
            // iOS parity: delete the stranded staged file too, don't just cancel the
            // task — else a cross-account switch leaks the previous user's media in
            // cacheDir/artist-wizard/ until the next full deleteAll().
            purged.cacheFilename?.let { cache.delete(it) }
            wm.cancelWorkById(UUID.fromString(purged.taskId))
        }
    }

    /**
     * Live banner state for [UploadProgressBanner], derived from the WorkManager
     * queue (the iOS `UploadQueue.batchTotal/batchCompleted/failedTasks` analogue).
     * We don't hold our own snapshot the way iOS does — WorkManager owns the queue —
     * so the "batch" is APPROXIMATED from the current WorkInfos:
     *   total     = still-running/enqueued + already-succeeded (the visible batch),
     *   completed = succeeded,
     *   failed    = failed.
     * Idle/uploading are decided from IN-FLIGHT work; once a batch fully drains we
     * prune the finished infos (iOS resets its batch counters on drain) so a lingering
     * SUCCEEDED row can't keep the banner spinning or inflate the next batch's count.
     */
    override fun bannerStateFlow(): Flow<UploadBannerState> =
        wm.getWorkInfosByTagFlow(ARTIST_UPLOAD_TAG).map { infos ->
            var inFlight = 0
            var succeeded = 0
            var failed = 0
            for (i in infos) when (i.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> inFlight++
                WorkInfo.State.SUCCEEDED -> succeeded++
                WorkInfo.State.FAILED -> failed++
                WorkInfo.State.CANCELLED -> {} // dropped from the batch
            }
            // Batch fully drained (nothing running, nothing failed) → prune the leftover
            // SUCCEEDED infos so `total` returns to 0. Idempotent: the next emission has
            // succeeded=0 and stays idle. Gated on failed==0 so a failed batch's banner
            // (which the user must dismiss) is never pruned out from under them.
            if (inFlight == 0 && failed == 0 && succeeded > 0) wm.pruneWork()
            UploadBannerState(inFlight = inFlight, total = inFlight + succeeded, completed = succeeded, failed = failed)
        }

    /**
     * Clear the failed banner. True re-enqueue isn't possible here: WorkInfo doesn't
     * retain a task's input Data, so the media ref needed to replay a permanently-
     * failed upload lives with the wizard/EPK that still holds it — not this queue.
     * The most we can honestly do from the banner is prune the finished WorkInfos so
     * a stalled row stops nagging; the EPK re-add flow is the real retry surface.
     * ponytail: prune-not-replay; wire a real retry when the EPK holds the refs (part 2).
     */
    override fun clearFinished() {
        wm.pruneWork()
    }

    /** Cancel a single task (banner "give up"). */
    fun cancel(taskId: UUID) = wm.cancelWorkById(taskId)

    /**
     * Hard reset on sign-out / delete-account: cancel every upload task and wipe
     * the staged files so the next user inherits nothing (iOS `cancelAll`).
     */
    fun cancelAll() {
        wm.cancelAllWorkByTag(ARTIST_UPLOAD_TAG)
        cache.deleteAll()
    }

    // Re-run a permanently-failed task = call the matching enqueue* again with the
    // ref the wizard/banner still holds; WorkManager already retries 3x internally,
    // so no separate "retry(id)" replay of lost input data is needed here.

    private fun baseData(kind: UploadKind, artistId: String): Data.Builder =
        Data.Builder()
            .putString(MediaUploadWorker.KEY_KIND, kind.name)
            .putString(MediaUploadWorker.KEY_ARTIST, artistId.lowercaseUuid())

    private fun enqueue(kind: UploadKind, artistId: String, data: Data, cacheFilename: String? = null): UUID {
        val builder = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(ARTIST_UPLOAD_TAG)
            .addTag(artistTag(artistId))
            .addTag(KIND_TAG_PREFIX + kind.name)
        // Tag the staged filename so the cross-account purge (which only sees
        // WorkInfo tags, not input Data) can delete the file, not just cancel.
        if (cacheFilename != null) builder.addTag(FILE_TAG_PREFIX + cacheFilename)
        val request = builder.build()
        wm.enqueue(request)
        return request.id
    }

    companion object {
        const val ARTIST_UPLOAD_TAG = "artist-upload"

        const val ARTIST_TAG_PREFIX = "artist:"
        const val KIND_TAG_PREFIX = "kind:"
        const val FILE_TAG_PREFIX = "file:"

        fun artistTag(artistId: String): String = ARTIST_TAG_PREFIX + artistId.lowercaseUuid()
    }
}

/**
 * Snapshot of the upload queue for the banner. [isIdle] = nothing to show (queue
 * empty, no failures); [isUploading] = an in-flight batch; else a failed batch.
 */
data class UploadBannerState(
    val inFlight: Int = 0,
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
) {
    // Idle/uploading key on IN-FLIGHT work, NOT `total` — `total` includes SUCCEEDED
    // WorkInfos WorkManager keeps until pruned, so keying idle on `total` left the
    // banner stuck on a "Saving 1 of 1…" spinner forever after a successful upload.
    // inFlight is 0 the instant nothing is enqueued/running.
    val isIdle: Boolean get() = inFlight == 0 && failed == 0
    val isUploading: Boolean get() = inFlight > 0
}
