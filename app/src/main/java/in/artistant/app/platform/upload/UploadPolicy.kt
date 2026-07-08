package `in`.artistant.app.platform.upload

/** The four kinds of upload task the queue runs. */
enum class UploadKind { PHOTO, VIDEO, AUDIO, PUBLISH_FLAG }

/**
 * Minimal, WorkManager-agnostic view of a queued task (id + owner + kind + the
 * staged cache filename, if any). [cacheFilename] lets the cross-account purge
 * delete the foreign staged file, not just cancel the WorkManager task — iOS
 * `resumeAfterLaunch` calls `cleanupLocalFile` on every purged task. Null for the
 * publish-flag task (it stages no file).
 */
data class UploadTaskInfo(
    val taskId: String,
    val artistId: String,
    val kind: UploadKind,
    val cacheFilename: String? = null,
)

/** WorkManager states collapsed to what the publish gate cares about. */
enum class MediaTaskState { PENDING, SUCCEEDED, FAILED }

/**
 * Pure policy for the upload queue — extracted from the WorkManager plumbing so the
 * two load-bearing rules are unit-testable in plain JVM (ports of the iOS
 * `UploadQueue.resumeAfterLaunch` cross-account guard + the runLoop publish gate).
 */
object UploadPolicy {

    /**
     * Cross-account purge on resume. `artists.id == users.id` (migration 0027), so
     * each task's [UploadTaskInfo.artistId] IS its owning user id — anything not
     * belonging to the signed-in user is a stranded task from a previous account
     * (killed mid-publish, then a different user signed in) and must be cancelled
     * rather than replayed into guaranteed RLS failures. Null user → purge nothing
     * (can't attribute ownership yet).
     */
    fun tasksToPurge(tasks: List<UploadTaskInfo>, currentUserId: String?): List<UploadTaskInfo> {
        if (currentUserId == null) return emptyList()
        val me = currentUserId.lowercase()
        return tasks.filter { it.artistId.lowercase() != me }
    }

    /**
     * Publish gate: only flip `published=true` once EVERY media task has SUCCEEDED.
     * A FAILED media task blocks publish (the artist would otherwise go live with a
     * missing cover); a still-PENDING one blocks it too (publish runs last). No
     * media (empty) → publish immediately.
     */
    fun canPublish(mediaStates: List<MediaTaskState>): Boolean =
        mediaStates.all { it == MediaTaskState.SUCCEEDED }
}
