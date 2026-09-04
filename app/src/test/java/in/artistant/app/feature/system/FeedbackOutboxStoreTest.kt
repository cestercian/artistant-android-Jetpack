package `in`.artistant.app.feature.system

import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.feature.messages.ViewerIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DataStoreFeedbackOutbox] against an in-memory store.
 *
 * Two properties, and both of them are about a queue outliving the session that
 * filled it: a note is submitted as the account that WROTE it, and an enqueue
 * racing a drain neither loses a note nor sends one twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackOutboxStoreTest {

    private val me = "11111111-1111-1111-1111-111111111111"
    private val someoneElse = "22222222-2222-2222-2222-222222222222"

    /**
     * Records what actually reached `app_feedback`, and suspends on the way —
     * an insert is a network hop, and the window it opens is the whole point.
     */
    private class RecordingBookings(
        delegate: BookingsRepository = FakeBookingsRepository(),
        var accepts: Boolean = true,
    ) : BookingsRepository by delegate {
        val submitted = mutableListOf<String>()

        override suspend fun submitFeedback(body: String, isBug: Boolean): Boolean {
            yield()
            if (!accepts) return false
            submitted += body
            return true
        }
    }

    private fun note(body: String) =
        PendingFeedback(body = body, isBug = false, writtenAtMs = 0L)

    private fun outbox(
        store: SlowKeyValueStore,
        bookings: BookingsRepository,
        viewer: String?,
    ) = DataStoreFeedbackOutbox(store, bookings, ViewerIdentity { viewer }, FeedbackDrainScheduler {})

    // ── who wrote it ─────────────────────────────────────────────────────────

    @Test
    fun `a queued note records the account that wrote it`() = runTest {
        val store = SlowKeyValueStore()
        val box = outbox(store, RecordingBookings(), viewer = me)
        box.enqueue(note("The availability strip is the best thing in the app."))
        assertEquals(me, box.pending().single().userId)
    }

    @Test
    fun `a queued note gets an id of its own`() = runTest {
        val store = SlowKeyValueStore()
        val box = outbox(store, RecordingBookings(), viewer = me)
        box.enqueue(note("one"))
        box.enqueue(note("two"))
        val ids = box.pending().map { it.id }
        assertTrue(ids.none { it.isBlank() })
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun `nothing is queued with nobody signed in`() = runTest {
        // `app_feedback` is insert-only for `authenticated`, so an unattributable
        // note could never land — queueing it would only defer the lie.
        val store = SlowKeyValueStore()
        val box = outbox(store, RecordingBookings(), viewer = null)
        box.enqueue(note("into the void"))
        assertTrue(box.pending().isEmpty())
    }

    @Test
    fun `the drain never submits a note written by another account`() = runTest {
        val store = SlowKeyValueStore()
        val bookings = RecordingBookings()
        outbox(store, bookings, viewer = someoneElse).enqueue(note("their note"))

        // A different account signs in and the OS wakes the worker.
        val drained = outbox(store, bookings, viewer = me).drain()

        assertTrue(bookings.submitted.isEmpty())
        assertTrue("a foreign note is dropped, not carried", drained)
        assertTrue(outbox(store, bookings, viewer = me).pending().isEmpty())
    }

    @Test
    fun `a drain with nobody signed in leaves the queue where it is`() = runTest {
        val store = SlowKeyValueStore()
        val bookings = RecordingBookings()
        outbox(store, bookings, viewer = me).enqueue(note("keep me"))

        val drained = outbox(store, bookings, viewer = null).drain()

        assertFalse("undelivered, so the worker retries", drained)
        assertTrue(bookings.submitted.isEmpty())
        assertEquals(listOf("keep me"), outbox(store, bookings, viewer = me).pending().map { it.body })
    }

    @Test
    fun `a note that lands leaves the queue empty`() = runTest {
        val store = SlowKeyValueStore()
        val bookings = RecordingBookings()
        val box = outbox(store, bookings, viewer = me)
        box.enqueue(note("sendable"))
        assertTrue(box.drain())
        assertEquals(listOf("sendable"), bookings.submitted)
        assertTrue(box.pending().isEmpty())
    }

    @Test
    fun `a drain that cannot send keeps the note`() = runTest {
        val store = SlowKeyValueStore()
        val bookings = RecordingBookings(accepts = false)
        val box = outbox(store, bookings, viewer = me)
        box.enqueue(note("offline"))
        assertFalse(box.drain())
        assertEquals(listOf("offline"), box.pending().map { it.body })
    }

    // ── two writers, one string ──────────────────────────────────────────────

    @Test
    fun `an enqueue racing a drain loses nothing and sends nothing twice`() = runTest {
        // Both halves replace the whole queue. Unserialized, the drain's closing
        // write either erases the note the enqueue just added, or the enqueue's
        // write restores a note the drain has already delivered.
        val store = SlowKeyValueStore()
        val bookings = RecordingBookings()
        val box = outbox(store, bookings, viewer = me)
        box.enqueue(note("first"))

        val draining = launch { box.drain() }
        val writing = launch { box.enqueue(note("second")) }
        draining.join()
        writing.join()

        val accountedFor = bookings.submitted + box.pending().map { it.body }
        assertEquals(listOf("first", "second"), accountedFor.sorted())
        assertEquals("nothing was sent twice", accountedFor.size, accountedFor.toSet().size)
    }

    @Test
    fun `two drains cannot both send the same note`() = runTest {
        // The ViewModel drains on open and the OS drains on connectivity; they
        // are not ordered relative to each other.
        val store = SlowKeyValueStore()
        val bookings = RecordingBookings()
        val box = outbox(store, bookings, viewer = me)
        box.enqueue(note("once"))

        val a = launch { box.drain() }
        val b = launch { box.drain() }
        a.join()
        b.join()

        assertEquals(listOf("once"), bookings.submitted)
        assertTrue(box.pending().isEmpty())
    }
}
