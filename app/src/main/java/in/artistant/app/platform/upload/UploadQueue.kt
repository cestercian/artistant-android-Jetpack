package `in`.artistant.app.platform.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.platform.media.PendingAudioRef
import `in`.artistant.app.platform.media.PendingMediaRef
import `in`.artistant.app.platform.media.WizardMediaCache
import kotlinx.coroutines.Dispatchers
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
) : MediaUploadEnqueuer {
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
