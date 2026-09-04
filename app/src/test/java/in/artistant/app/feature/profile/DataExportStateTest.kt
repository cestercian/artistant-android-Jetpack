package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.repository.ExportResult
import `in`.artistant.app.data.repository.FakeAccountRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The DPDP export state machine (design 81 → 82 → 49 / 113).
 *
 * The property this suite exists to pin is the design's own note on screen 113: **a failed
 * export produces no file at all**. [ExportState] makes "failed with a file" unrepresentable in
 * the type; these tests check the ViewModel's transitions actually land in the states the type
 * allows — including the case that motivated the shape, a successful export followed by a
 * failed re-request, where a bag-of-booleans implementation keeps the stale file on screen
 * beside the new error and hands somebody half their data believing it is all of it.
 */
class DataExportStateTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `starts idle`() {
        assertEquals(ExportState.Idle, DataExportViewModel(FakeAccountRepository()).state.value.export)
    }

    @Test
    fun `a successful request ends ready with the server's own result`() = runTest {
        val result = ExportResult.SignedUrl(url = "https://example.test/x.json", expiresInSeconds = 3600)
        val vm = DataExportViewModel(FakeAccountRepository(exportResult = result))
        vm.request()
        advanceUntilIdle()
        assertEquals(ExportState.Ready(result), vm.state.value.export)
    }

    @Test
    fun `a failed request ends failed and carries NO file`() = runTest {
        val vm = DataExportViewModel(FakeAccountRepository(failExport = true))
        vm.request()
        advanceUntilIdle()
        val state = vm.state.value.export
        assertTrue("expected Failed, was $state", state is ExportState.Failed)
        assertNull(vm.state.value.pendingShare)
    }

    @Test
    fun `a failed RE-request discards the file the previous one produced`() = runTest {
        // The regression this shape exists to prevent. Export once, get a file; ask again and
        // have it fail. Nothing shareable may survive into the failed state.
        val repo = FakeAccountRepository()
        val vm = DataExportViewModel(repo)
        vm.request()
        advanceUntilIdle()
        assertTrue(vm.state.value.export is ExportState.Ready)

        repo.failExport = true
        vm.request()
        advanceUntilIdle()
        assertTrue(vm.state.value.export is ExportState.Failed)
    }

    @Test
    fun `share only arms a handoff from Ready`() = runTest {
        val vm = DataExportViewModel(FakeAccountRepository(failExport = true))
        vm.request()
        advanceUntilIdle()
        vm.share()
        assertNull("a failed export must have nothing to share", vm.state.value.pendingShare)
    }

    @Test
    fun `share from Ready arms exactly the file the server returned`() = runTest {
        val result = ExportResult.Inline("""{"user":"fixture"}""")
        val vm = DataExportViewModel(FakeAccountRepository(exportResult = result))
        vm.request()
        advanceUntilIdle()
        vm.share()
        assertEquals(result, vm.state.value.pendingShare)
        vm.clearPendingShare()
        assertNull(vm.state.value.pendingShare)
    }

    @Test
    fun `stopping the wait returns to idle, not to a file nobody built`() = runTest {
        val vm = DataExportViewModel(FakeAccountRepository())
        vm.stopWaiting()
        assertEquals(ExportState.Idle, vm.state.value.export)
    }

    // ── The header subtitle: one line per state, never a shared one ─────────────────────

    @Test
    fun `each state names itself in the header`() {
        val subtitles = listOf(
            exportSubtitle(ExportState.Idle),
            exportSubtitle(ExportState.Requested),
            exportSubtitle(ExportState.Ready(ExportResult.Inline("{}"))),
            exportSubtitle(ExportState.Failed("Stopped at bookings")),
        )
        // "loading, empty and failed are three different screens and say which one they are"
        // (REDESIGN_2026-09 §2) — only true if no two of them say the same thing.
        assertEquals(subtitles.size, subtitles.toSet().size)
    }

    // ── The expiry the design asks to be STATED ─────────────────────────────────────────

    @Test
    fun `a signed url states the server's expiry`() {
        assertEquals(
            "Ready · link expires in 1 hour",
            exportReadyDetail(ExportResult.SignedUrl("https://x.test", 3600)),
        )
    }

    @Test
    fun `an inline payload states what is true of it instead of borrowing an expiry`() {
        assertTrue(
            "must not invent an expiry",
            !exportReadyDetail(ExportResult.Inline("{}")).contains("expires"),
        )
    }

    @Test
    fun `expiry reads as a duration a person would say`() {
        assertEquals("1 minute", expiryLabel(60))
        assertEquals("45 minutes", expiryLabel(45 * 60))
        assertEquals("1 hour", expiryLabel(3600))
        assertEquals("3 hours", expiryLabel(3 * 3600))
        assertEquals("1 day", expiryLabel(24 * 3600))
        assertEquals("7 days", expiryLabel(7 * 24 * 3600))
    }

    @Test
    fun `a nonsense expiry degrades rather than printing a negative`() {
        // The server is not ours to trust blindly; a zero or a negative must not render
        // "expires in -1 hours" on a privacy screen.
        assertEquals("under a minute", expiryLabel(30))
        assertEquals("under a minute", expiryLabel(0))
        assertEquals("under a minute", expiryLabel(-500))
    }
}
