package `in`.artistant.app.platform.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the runner does with a finished upload.
 *
 * `UploadQueue` itself needs a Context, but the decisions do not: whether an outcome
 * advances the queue, requeues its task or burns it, and — the part this was written
 * for — whether an outcome that lands AFTER a sign-out wipe is allowed to write
 * anything at all. Sign-out empties the queue while an upload may still be in flight;
 * a task that resurrects itself there is inherited by the next account on the phone,
 * which then sees a stalled upload it never started.
 */
class UploadOutcomeTest {

    private fun photo(id: String, attempts: Int = 1) = UploadQueue.Task.CoverPhoto(
        id = id,
        artistId = "11111111-1111-1111-1111-111111111111",
        filePath = "/tmp/$id.jpg",
        attempts = attempts,
    )

    private fun queued(vararg tasks: UploadQueue.Task) = UploadQueue.State(
        pending = tasks.toList(),
        isRunning = true,
        batchTotal = tasks.size,
    )

    @Test
    fun aSuccessDropsTheHeadAndCountsIt() {
        val state = queued(photo("a"), photo("b"))

        val next = uploadSucceeded(state, "a")

        assertEquals(listOf("b"), next.pending.map { it.id })
        assertEquals(1, next.batchCompleted)
    }

    @Test
    fun aFailureWithAttemptsLeftGoesToTheBackOfTheQueue() {
        val state = queued(photo("a", attempts = 1), photo("b"))

        val next = uploadFailed(state, photo("a", attempts = 1), UploadQueue.MAX_ATTEMPTS)

        assertEquals(listOf("b", "a"), next.pending.map { it.id })
        assertTrue(next.failed.isEmpty())
        // Not a completion: the artist's asset still hasn't arrived.
        assertEquals(0, next.batchCompleted)
    }

    @Test
    fun aFailureThatBurnsTheBudgetMovesToFailed() {
        val burned = photo("a", attempts = UploadQueue.MAX_ATTEMPTS)
        val state = queued(burned, photo("b"))

        val next = uploadFailed(state, burned, UploadQueue.MAX_ATTEMPTS)

        assertEquals(listOf("b"), next.pending.map { it.id })
        assertEquals(listOf("a"), next.failed.map { it.id })
    }

    /**
     * The sign-out race. `clearAll()` empties the queue while `execute` is still
     * awaiting its upload; a blind `drop(1)` would count a completion for whatever
     * the next account had put at the head — or, on an empty queue, for nothing.
     */
    @Test
    fun aSuccessThatLandsAfterTheQueueWasClearedChangesNothing() {
        val cleared = UploadQueue.State()

        val next = uploadSucceeded(cleared, "a")

        assertEquals(cleared, next)
    }

    @Test
    fun aFailureThatLandsAfterTheQueueWasClearedDoesNotResurrectTheTask() {
        val cleared = UploadQueue.State()
        val burned = photo("a", attempts = UploadQueue.MAX_ATTEMPTS)

        // Both halves of the failure branch: neither the requeue nor the burn may
        // write into a queue that has been wiped.
        assertEquals(
            cleared,
            uploadFailed(cleared, photo("a", attempts = 1), UploadQueue.MAX_ATTEMPTS),
        )
        assertEquals(cleared, uploadFailed(cleared, burned, UploadQueue.MAX_ATTEMPTS))
    }

    @Test
    fun anOutcomeForATaskThatNoLongerHeadsTheQueueIsIgnored() {
        val state = queued(photo("b"))

        assertEquals(state, uploadSucceeded(state, "a"))
        assertEquals(state, uploadFailed(state, photo("a"), UploadQueue.MAX_ATTEMPTS))
    }
}
