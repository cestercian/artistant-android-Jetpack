package `in`.artistant.app.feature.system

import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
}
