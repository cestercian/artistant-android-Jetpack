package `in`.artistant.app.feature.system

import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Screen 123's state: the chips, the unread rule and the single accented row. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /**
     * Keep [ActivityViewModel.state] hot for the length of a test.
     *
     * The flow is `WhileSubscribed`, so without a collector it never leaves its
     * initial value and every assertion below would be reading an empty state
     * that says nothing about the ViewModel.
     */
    private fun TestScope.subscribe(vm: ActivityViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
    }

    private fun entry(id: String, event: String?, read: Boolean = false, at: Long = 0L) =
        ActivityEntry(id = id, event = event, title = id, body = "", receivedAtMs = at, read = read)

    private val seeded = listOf(
        entry("confirmed", "booking_confirmed_client", at = 300),
        entry("quote", "gig_request", at = 200),
        entry("chat", "message", read = true, at = 100),
    )

    @Test
    fun `All is the opening chip and admits everything`() = runTest {
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        assertEquals(ActivityFilter.All, vm.state.value.filter)
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test
    fun `a chip narrows to its own category`() = runTest {
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        vm.select(ActivityFilter.Quotes)
        assertEquals(listOf("quote"), vm.state.value.visible.map { it.id })
    }

    @Test
    fun `a chat push is reachable only under All`() = runTest {
        // The design draws four chips and none of them is Messages; a row that
        // no chip admits must still be findable.
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        vm.select(ActivityFilter.Bookings)
        assertFalse(vm.state.value.visible.any { it.id == "chat" })
        vm.select(ActivityFilter.All)
        assertTrue(vm.state.value.visible.any { it.id == "chat" })
    }

    @Test
    fun `exactly one row is accented, and it is the newest unread`() = runTest {
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        assertEquals("confirmed", vm.state.value.accentedId)
    }

    @Test
    fun `nothing is accented once everything is read`() = runTest {
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        vm.markAllRead()
        assertNull(vm.state.value.accentedId)
        assertFalse(vm.state.value.hasUnread)
    }

    @Test
    fun `mark all read leaves the rows in place`() = runTest {
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        vm.markAllRead()
        assertEquals(3, vm.state.value.all.size)
        assertTrue(vm.state.value.all.all { it.read })
    }

    @Test
    fun `an empty log has nothing unread and nothing accented`() = runTest {
        val vm = ActivityViewModel(FakeActivityLog())
        subscribe(vm)
        assertFalse(vm.state.value.hasUnread)
        assertNull(vm.state.value.accentedId)
        assertTrue(vm.state.value.visible.isEmpty())
    }

    @Test
    fun `an arriving push appears without a refresh`() = runTest {
        val log = FakeActivityLog(seeded)
        val vm = ActivityViewModel(log)
        subscribe(vm)
        log.record(entry("newest", "booking_reminder_24h", at = 400))
        assertEquals("newest", vm.state.value.all.first().id)
        assertEquals("newest", vm.state.value.accentedId)
    }

    // ── seen-on-open ─────────────────────────────────────────────────────────

    @Test
    fun `opening the screen marks the log read`() = runTest {
        // The bug: unread could only be cleared from this screen's own header,
        // so the bell on Discover kept its dot after the user had read every
        // row it stood for.
        val log = FakeActivityLog(seeded)
        val vm = ActivityViewModel(log)
        subscribe(vm)
        vm.markSeen()
        assertTrue(vm.state.value.all.all { it.read })
        assertEquals(0, unreadActivityCount(log.entries.first()))
    }

    @Test
    fun `a push landing between the snapshot and the mark stays unread`() = runTest {
        // Opening the screen does two things in a row — read the unread ids, then mark them —
        // and both are suspending store operations, so a push CAN land in between. It is not
        // in the snapshot, so `unreadOnArrival` never draws it bold; marking it read anyway
        // (which `markAllRead()` did, because it speaks for rows it has never seen) retired a
        // notification the account demonstrably has not seen. The bell would go quiet for a
        // gig request nobody ever showed them.
        val log = InterleavingActivityLog(seeded)
        val vm = ActivityViewModel(log)
        subscribe(vm)

        // `markSeen` runs eagerly under the unconfined dispatcher until the read of the
        // unread ids parks in the window — which is where the push arrives.
        vm.markSeen()
        log.landPush(entry("landed", "gig_request", at = 400))
        advanceUntilIdle()

        assertFalse("markSeen must not speak for rows it never saw", log.markedEverything)
        assertEquals(setOf("confirmed", "quote"), log.markedIds)
        val stored = log.entries.first()
        assertTrue(stored.first { it.id == "landed" }.read.not())
        assertEquals("the bell still has something to say", 1, unreadActivityCount(stored))
        // And the snapshot is what it always was, so the two rows that WERE there still read
        // as unread for the rest of the visit.
        assertTrue(vm.state.value.showsUnread(vm.state.value.all.first { it.id == "confirmed" }))
    }

    @Test
    fun `the rows that were unread on arrival still draw as unread`() = runTest {
        // Marking the STORE read must not take away the one thing the screen
        // answers: which of these had I not seen.
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        vm.markSeen()
        val state = vm.state.value
        assertTrue(state.showsUnread(state.all.first { it.id == "confirmed" }))
        assertTrue(state.showsUnread(state.all.first { it.id == "quote" }))
        // Already read before the visit; nothing about arriving changes that.
        assertFalse(state.showsUnread(state.all.first { it.id == "chat" }))
        assertEquals("confirmed", state.accentedId)
    }

    @Test
    fun `mark all read is offered only once something lands while the screen is open`() = runTest {
        val log = FakeActivityLog(seeded)
        val vm = ActivityViewModel(log)
        subscribe(vm)
        vm.markSeen()
        assertFalse("nothing left to mark straight after opening", vm.state.value.hasUnread)
        log.record(entry("landed", "booking_reminder_24h", at = 400))
        assertTrue("a push arriving mid-visit brings the action back", vm.state.value.hasUnread)
    }

    @Test
    fun `the button clears the arrival snapshot too`() = runTest {
        // Pressing it is a statement about every row on screen, not just the
        // ones that arrived since — so they all stop drawing as unread.
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        vm.markSeen()
        vm.markAllRead()
        val state = vm.state.value
        assertTrue(state.all.none { state.showsUnread(it) })
        assertNull(state.accentedId)
    }

    @Test
    fun `the arrival snapshot is taken once, not again after a recomposition`() = runTest {
        // `markSeen` runs from a composition effect, and the ViewModel outlives
        // a configuration change. A second snapshot would be taken after the
        // first marked everything read — every row on screen would lose its
        // unread treatment mid-visit.
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        vm.markSeen()
        vm.markSeen()
        val state = vm.state.value
        assertTrue(state.showsUnread(state.all.first { it.id == "confirmed" }))
    }

    @Test
    fun `nothing is offered to mark before the visit has resolved`() = runTest {
        // Between the log landing and `markSeen` answering, the action must not
        // flash up and vanish.
        val vm = ActivityViewModel(FakeActivityLog(seeded))
        subscribe(vm)
        assertFalse(vm.state.value.hasUnread)
    }

    /**
     * A log whose read of the unread ids happens BEFORE the window and whose answer arrives
     * after it — which is what a DataStore read is: the blob is decoded at one instant and
     * handed back at another, and a push recorded in between is in the store but not in the
     * decoded list.
     *
     * [landPush] is deliberately non-suspending so a test can drop the push into that window
     * without needing a second coroutine to interleave with.
     */
    private class InterleavingActivityLog(seed: List<ActivityEntry>) : ActivityLog {
        private val rows = MutableStateFlow(seed)

        var markedIds: Set<String> = emptySet()
            private set
        var markedEverything = false
            private set

        override val entries: Flow<List<ActivityEntry>> = flow {
            val captured = rows.value
            // The window. Under `UnconfinedTestDispatcher` this hands control back to the
            // test body, which lands the push before the emission below resumes.
            yield()
            emit(captured)
            emitAll(rows)
        }

        fun landPush(entry: ActivityEntry) {
            rows.value = appendActivity(rows.value, entry)
        }

        override suspend fun record(entry: ActivityEntry) = landPush(entry)

        override suspend fun markAllRead() {
            markedEverything = true
            rows.value = rows.value.map { it.copy(read = true) }
        }

        override suspend fun markRead(ids: Set<String>) {
            markedIds = markedIds + ids
            rows.value = rows.value.map { if (it.id in ids) it.copy(read = true) else it }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }
}
