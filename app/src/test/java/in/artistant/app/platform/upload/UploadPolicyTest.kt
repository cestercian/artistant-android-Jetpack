package `in`.artistant.app.platform.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two load-bearing upload rules: cross-account purge + the publish gate. */
class UploadPolicyTest {

    private fun task(id: String, artist: String, file: String? = null, kind: UploadKind = UploadKind.PHOTO) =
        UploadTaskInfo(id, artist, kind, file)

    @Test
    fun `purge cancels tasks that belong to a different user`() {
        val tasks = listOf(
            task("t1", "me", "me.jpg"),
            task("t2", "someone-else", "foreign.jpg"),
            task("t3", "ME", "me2.jpg"),   // case-insensitive match to current user
        )
        val purge = UploadPolicy.tasksToPurge(tasks, currentUserId = "me")
        assertEquals(listOf("t2"), purge.map { it.taskId })
        // Fix 2: the purge set carries the staged filename so resumeAfterLaunch can
        // delete the foreign cache file (WizardMediaCache.delete), not just cancel it.
        assertEquals(listOf("foreign.jpg"), purge.map { it.cacheFilename })
    }

    @Test
    fun `purge keeps everything when no user is signed in yet`() {
        val tasks = listOf(task("t1", "a"), task("t2", "b"))
        assertTrue(UploadPolicy.tasksToPurge(tasks, currentUserId = null).isEmpty())
    }

    @Test
    fun `publish allowed only when all media succeeded`() {
        assertTrue(UploadPolicy.canPublish(emptyList()))
        assertTrue(
            UploadPolicy.canPublish(listOf(MediaTaskState.SUCCEEDED, MediaTaskState.SUCCEEDED)),
        )
    }

    @Test
    fun `publish blocked while any media is pending or failed`() {
        assertFalse(
            UploadPolicy.canPublish(listOf(MediaTaskState.SUCCEEDED, MediaTaskState.PENDING)),
        )
        assertFalse(
            UploadPolicy.canPublish(listOf(MediaTaskState.SUCCEEDED, MediaTaskState.FAILED)),
        )
    }
}
