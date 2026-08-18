package `in`.artistant.app.platform.media

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wizard-media snapshot that survives a process kill.
 *
 * UploadQueue itself needs a Context, but the on-disk shape does not — and the
 * on-disk shape is where a stranded upload actually comes from. Two things must
 * hold across a restart: the attempt counter survives (that's the crash-loop
 * guard; resetting it would let a poison asset retry forever), and a row this
 * build doesn't recognise is dropped instead of blowing up the restore.
 */
class UploadQueuePersistenceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun aCoverPhotoTaskSurvivesASerializationRoundTrip() {
        val original = UploadQueue.Task.CoverPhoto(
            id = "task-1",
            artistId = "11111111-1111-1111-1111-111111111111",
            filePath = "/tmp/cover.jpg",
            attempts = 2,
        )

        val restored = json
            .decodeFromString<PersistedTask>(json.encodeToString(original.toPersisted()))
            .toTask()

        assertEquals(original, restored)
    }

    @Test
    fun anAudioSampleTaskKeepsItsTitleAndDuration() {
        val original = UploadQueue.Task.AudioSample(
            id = "task-2",
            artistId = "11111111-1111-1111-1111-111111111111",
            filePath = "/tmp/clip.m4a",
            title = "Rooftop set",
            durationSeconds = 92.5,
            attempts = 1,
        )

        val restored = json
            .decodeFromString<PersistedTask>(json.encodeToString(original.toPersisted()))
            .toTask()

        assertEquals(original, restored)
        assertEquals("Rooftop set", (restored as UploadQueue.Task.AudioSample).title)
        assertEquals(92.5, restored.durationSeconds, 0.0001)
    }

    @Test
    fun theAttemptCounterSurvivesTheRoundTrip() {
        // Crash-loop protection: attempts are bumped BEFORE execute, so losing
        // the counter on restore would let a poison asset retry forever.
        val burned = UploadQueue.Task.CoverPhoto(
            artistId = "a1",
            filePath = "/tmp/x.jpg",
            attempts = 3,
        )

        assertEquals(3, burned.toPersisted().toTask()?.attempts)
    }

    @Test
    fun withAttemptProducesTheSameTaskWithANewCount() {
        val task = UploadQueue.Task.CoverPhoto(id = "t", artistId = "a1", filePath = "/tmp/x.jpg")

        val bumped = task.withAttempt(1)

        assertEquals(1, bumped.attempts)
        assertEquals(task.id, bumped.id)
        assertEquals(task.artistId, bumped.artistId)
    }

    @Test
    fun anUnknownTaskKindIsDroppedRatherThanCrashingTheRestore() {
        val stale = PersistedTask(
            id = "task-3",
            kind = "video_reel", // a kind some future build wrote
            artistId = "a1",
            filePath = "/tmp/reel.mp4",
        )

        assertNull(stale.toTask())
    }

    @Test
    fun aWholeSnapshotDecodesWithPendingAndFailedSeparated() {
        val snapshot = PersistedSnapshot(
            pending = listOf(
                PersistedTask(id = "p1", kind = "cover_photo", artistId = "a1", filePath = "/tmp/a.jpg"),
            ),
            failed = listOf(
                PersistedTask(
                    id = "f1",
                    kind = "audio_sample",
                    artistId = "a1",
                    filePath = "/tmp/b.m4a",
                    title = "Take 1",
                    durationSeconds = 30.0,
                    attempts = 3,
                ),
            ),
        )

        val restored = json.decodeFromString<PersistedSnapshot>(json.encodeToString(snapshot))

        assertEquals(1, restored.pending.size)
        assertEquals(1, restored.failed.size)
        assertEquals("Take 1", restored.failed.single().title)
        assertEquals(3, restored.failed.single().attempts)
    }

    @Test
    fun anEmptySnapshotIsValid() {
        val restored = json.decodeFromString<PersistedSnapshot>("""{}""")

        assertTrue(restored.pending.isEmpty())
        assertTrue(restored.failed.isEmpty())
    }

    /**
     * The read MERGES into whatever the queue already holds. It used to run
     * synchronously in the constructor — main-thread file IO during the first frame —
     * and moving it to the IO scope means a caller can have enqueued by the time it
     * lands: the wizard's Publish is a frame or two after the EPK first injects the
     * queue. Replacing the state, as a constructor-time read could afford to, would
     * drop the cover that caller just staged.
     */
    @Test
    fun aRestoreThatLandsAfterAnEnqueueKeepsBoth() {
        val live = UploadQueue.State(
            pending = listOf(
                UploadQueue.Task.CoverPhoto(id = "live", artistId = "a1", filePath = "/tmp/live.jpg"),
            ),
            batchTotal = 1,
        )
        val snapshot = PersistedSnapshot(
            pending = listOf(
                PersistedTask(id = "disk", kind = "cover_photo", artistId = "a1", filePath = "/tmp/disk.jpg"),
            ),
        )

        val merged = restoredInto(live, snapshot, UploadQueue.MAX_ATTEMPTS)

        // Restored ahead of live: it was queued in an earlier session.
        assertEquals(listOf("disk", "live"), merged.pending.map { it.id })
        // And the progress denominator counts both, or the batch reads as over early.
        assertEquals(2, merged.batchTotal)
    }

    @Test
    fun aRestoredTaskWithASpentBudgetLandsInFailedRatherThanTheRunner() {
        val snapshot = PersistedSnapshot(
            pending = listOf(
                PersistedTask(
                    id = "poison",
                    kind = "cover_photo",
                    artistId = "a1",
                    filePath = "/tmp/poison.jpg",
                    attempts = 3,
                ),
                PersistedTask(
                    id = "live",
                    kind = "cover_photo",
                    artistId = "a1",
                    filePath = "/tmp/live.jpg",
                    attempts = 1,
                ),
            ),
            failed = listOf(
                PersistedTask(
                    id = "old",
                    kind = "cover_photo",
                    artistId = "a1",
                    filePath = "/tmp/old.jpg",
                    attempts = 3,
                ),
            ),
        )

        val restored = restoredInto(UploadQueue.State(), snapshot, UploadQueue.MAX_ATTEMPTS)

        assertEquals(listOf("live"), restored.pending.map { it.id })
        assertEquals(listOf("old", "poison"), restored.failed.map { it.id })
        assertEquals(1, restored.batchTotal)
    }

    @Test
    fun anEmptySnapshotLeavesALiveQueueUntouched() {
        val live = UploadQueue.State(
            pending = listOf(
                UploadQueue.Task.CoverPhoto(id = "live", artistId = "a1", filePath = "/tmp/live.jpg"),
            ),
            batchTotal = 1,
        )

        assertEquals(live, restoredInto(live, PersistedSnapshot(), UploadQueue.MAX_ATTEMPTS))
    }
}
