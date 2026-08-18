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

    /**
     * "Back of the queue" is the head again when the queue holds one task — which is
     * the wizard's cover, on its own, more often than not. The drain loop picks the
     * head up on its very next iteration, so this is the shape that makes the retry
     * gap load-bearing rather than decorative.
     */
    @Test
    fun aRequeuedSoleTaskComesStraightBackToTheHead() {
        val only = photo("a", attempts = 1)

        val next = uploadFailed(queued(only), only, UploadQueue.MAX_ATTEMPTS)

        assertEquals(listOf("a"), next.pending.map { it.id })
        assertEquals(backoffMillisFor(1), retryDelayMillis(next, only))
    }

    /**
     * The gap itself. With none, all three attempts burned inside milliseconds of the
     * first failure — and the dominant failure is "no network", which needs seconds:
     * a cell→wifi handover during Publish permanently failed the artist's cover.
     */
    @Test
    fun theRetryGapGrowsWithEachAttemptAndIsMeasuredInSeconds() {
        assertTrue(backoffMillisFor(1) >= 1_000L)
        assertTrue(backoffMillisFor(2) > backoffMillisFor(1))
        // A budget of three attempts spans both gaps before the task is burned.
        assertTrue(
            "the whole attempt budget must outlast a network handover",
            backoffMillisFor(1) + backoffMillisFor(2) >= 5_000L,
        )
    }

    /**
     * The multi-task shape — and it is the shape the wizard actually produces: Publish
     * enqueues the staged cover AND every pending sample in one loop. A gap conditioned
     * on "the requeued task came back to the HEAD" fires only for a queue of one; with
     * four queued the head after any failure is always a different task, so the gap
     * never fired and the drain rotated the whole batch through every attempt back to
     * back. The gap is owed to the task that FAILED, wherever the requeue put it.
     */
    @Test
    fun aRequeuedTaskIsOwedItsGapEvenWithOthersAheadOfIt() {
        val cover = photo("cover", attempts = 1)
        val state = queued(cover, photo("s1"), photo("s2"), photo("s3"))

        val next = uploadFailed(state, cover, UploadQueue.MAX_ATTEMPTS)

        assertEquals(listOf("s1", "s2", "s3", "cover"), next.pending.map { it.id })
        assertEquals(backoffMillisFor(1), retryDelayMillis(next, cover))
    }

    /** Nothing is owed a gap that isn't coming back: this one moved to `failed`. */
    @Test
    fun aBurnedTaskIsOwedNoGap() {
        val burned = photo("a", attempts = UploadQueue.MAX_ATTEMPTS)

        val next = uploadFailed(queued(burned, photo("b")), burned, UploadQueue.MAX_ATTEMPTS)

        assertEquals(0L, retryDelayMillis(next, burned))
    }

    /** Nor is a failure that landed after a sign-out wipe — that queue is gone. */
    @Test
    fun aFailureThatLandsAfterTheQueueWasClearedIsOwedNoGap() {
        assertEquals(0L, retryDelayMillis(UploadQueue.State(), photo("a", attempts = 1)))
    }

    /**
     * The property the head-keyed gap did not have, walked over the real drain order:
     * an offline publish of a whole batch cannot spend its attempt budget in a tight
     * loop. Three tasks × three attempts used to burn in the ~200ms it takes three
     * `uploadPhoto` calls to fail with no network, killing the artist's cover and
     * samples together; every attempt after the first now sits behind a gap.
     */
    @Test
    fun anOfflinePublishOfAWholeBatchCannotBurnItsBudgetInATightLoop() {
        var state = queued(
            photo("cover", attempts = 0),
            photo("s1", attempts = 0),
            photo("s2", attempts = 0),
        )
        var waited = 0L
        var guard = 0
        // Mirrors `runDrain`: bump the head's attempt, fail it, wait what it's owed.
        while (state.pending.isNotEmpty() && guard++ < 50) {
            val head = state.pending.first()
            val marked = head.withAttempt(head.attempts + 1)
            state = state.copy(pending = listOf(marked) + state.pending.drop(1))
            state = uploadFailed(state, marked, UploadQueue.MAX_ATTEMPTS)
            waited += retryDelayMillis(state, marked)
        }

        assertEquals(3, state.failed.size)
        assertTrue(
            "a whole batch must not burn its budget faster than a handover recovers",
            waited >= 30_000L,
        )
    }
}
