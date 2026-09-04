package `in`.artistant.app.feature.system

import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The gate policy behind design screens 120 and 121. */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemGateTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /**
     * Keep [SystemGateViewModel.state] hot.
     *
     * `WhileSubscribed` means an uncollected state never leaves `None`, which is
     * also the answer half these tests are checking for — so without this they
     * would pass on an empty flow and prove nothing.
     */
    private fun TestScope.subscribe(vm: SystemGateViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
    }

    private class FakeSource(initial: SystemStatus = SystemStatus.Normal) : SystemStatusSource {
        val flow = MutableStateFlow(initial)
        var refreshes = 0
            private set

        override val status: Flow<SystemStatus> = flow
        override suspend fun refresh() {
            refreshes += 1
        }
    }

    // ── resolve ──────────────────────────────────────────────────────────────

    @Test
    fun `normal shows the app`() {
        assertEquals(SystemGate.None, SystemGate.resolve(SystemStatus.Normal, false))
    }

    @Test
    fun `update required cannot be dismissed`() {
        // The design's own footnote: there is no dismiss on that screen. A
        // session dismissal must not reach it.
        val status = SystemStatus.UpdateRequired(installed = "0.1.0", minimum = "0.2.0")
        assertEquals(
            SystemGate.Update("0.1.0", "0.2.0"),
            SystemGate.resolve(status, outageDismissed = true),
        )
    }

    @Test
    fun `an outage can be stepped around`() {
        val status = SystemStatus.Outage(impact = "Bookings affected", startedLabel = null)
        assertTrue(SystemGate.resolve(status, outageDismissed = false) is SystemGate.Outage)
        assertEquals(SystemGate.None, SystemGate.resolve(status, outageDismissed = true))
    }

    @Test
    fun `the outage gate carries the scoped impact line through`() {
        val gate = SystemGate.resolve(
            SystemStatus.Outage("Bookings and messages affected", "Started 9:22 am"),
            outageDismissed = false,
        )
        assertEquals(
            SystemGate.Outage("Bookings and messages affected", "Started 9:22 am"),
            gate,
        )
    }

    // ── the ViewModel ────────────────────────────────────────────────────────

    @Test
    fun `production reports normal, because nothing can report otherwise`() = runTest {
        // `app_settings` is RLS default-deny and mig 0037 revoked the
        // `app_setting()` grant, so no client can read a minimum version.
        val vm = SystemGateViewModel(LiveSystemStatusSource())
        subscribe(vm)
        assertEquals(SystemGate.None, vm.state.value.gate)
    }

    @Test
    fun `dismissing the outage clears the gate`() = runTest {
        val source = FakeSource(SystemStatus.Outage("Bookings affected", null))
        val vm = SystemGateViewModel(source)
        subscribe(vm)
        assertTrue(vm.state.value.gate is SystemGate.Outage)
        vm.dismissOutage()
        assertEquals(SystemGate.None, vm.state.value.gate)
    }

    @Test
    fun `check again asks the source`() = runTest {
        val source = FakeSource(SystemStatus.Outage("Bookings affected", null))
        val vm = SystemGateViewModel(source)
        subscribe(vm)
        vm.checkAgain()
        assertEquals(1, source.refreshes)
        // The flag is released whatever the source did, or the CTA stays
        // disabled for the life of the screen.
        assertEquals(false, vm.state.value.checking)
    }
}
