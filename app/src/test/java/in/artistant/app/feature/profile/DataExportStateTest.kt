package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.data.repository.ExportResult
import `in`.artistant.app.data.repository.FakeAccountRepository
import `in`.artistant.app.platform.storage.KeyValueStore
import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
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
 *
 * Since the review the request lives in [DataExportStore] rather than the ViewModel, so the
 * second property here is the one that motivated the move: **leaving the screen does not cancel
 * the request, and coming back does not re-enter at Idle.**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataExportStateTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /** In-memory [KeyValueStore] — the same string in, the same string out. */
    private class FakeKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
        private val values = MutableStateFlow(initial)
        override fun getString(key: String): Flow<String?> = values.map { it[key] }
        override suspend fun setString(key: String, value: String) {
            values.value = values.value + (key to value)
        }

        fun raw(key: String): String? = values.value[key]

        /** `AppPreferences.wipeAll()` — what sign-out does to this store. */
        fun wipe() {
            values.value = emptyMap()
        }
    }

    /**
     * A store on the test's own scheduler, so `advanceUntilIdle()` drives its job the way it
     * drives a `viewModelScope` one.
     */
    private fun TestScope.store(
        account: AccountRepository = FakeAccountRepository(),
        prefs: KeyValueStore = FakeKeyValueStore(),
        now: () -> Long = { 0L },
    ) = DataExportStore(account, prefs, CoroutineScope(coroutineContext), now)

    @Test
    fun `starts idle`() = runTest {
        assertEquals(ExportState.Idle, DataExportViewModel(store()).state.value.export)
    }

    @Test
    fun `a successful request ends ready with the server's own result`() = runTest {
        val result = ExportResult.SignedUrl(url = "https://example.test/x.json", expiresInSeconds = 3600)
        val vm = DataExportViewModel(store(FakeAccountRepository(exportResult = result)))
        vm.request()
        advanceUntilIdle()
        assertEquals(ExportState.Ready(result), vm.state.value.export)
    }

    @Test
    fun `a failed request ends failed and carries NO file`() = runTest {
        val vm = DataExportViewModel(store(FakeAccountRepository(failExport = true)))
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
        val vm = DataExportViewModel(store(repo))
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
        val vm = DataExportViewModel(store(FakeAccountRepository(failExport = true)))
        vm.request()
        advanceUntilIdle()
        vm.share()
        assertNull("a failed export must have nothing to share", vm.state.value.pendingShare)
    }

    @Test
    fun `share from Ready arms exactly the file the server returned`() = runTest {
        val result = ExportResult.Inline("""{"user":"fixture"}""")
        val vm = DataExportViewModel(store(FakeAccountRepository(exportResult = result)))
        vm.request()
        advanceUntilIdle()
        vm.share()
        assertEquals(result, vm.state.value.pendingShare)
        vm.clearPendingShare()
        assertNull(vm.state.value.pendingShare)
    }

    @Test
    fun `stopping the wait returns to idle, not to a file nobody built`() = runTest {
        val vm = DataExportViewModel(store())
        vm.stopWaiting()
        assertEquals(ExportState.Idle, vm.state.value.export)
    }

    // ── The request outlives the screen (review finding 2) ─────────────────────────────

    /** An export that does not answer until the test says so. */
    private class GatedAccountRepository(
        private val gate: CompletableDeferred<ExportResult>,
    ) : AccountRepository {
        var calls: Int = 0
            private set

        override suspend fun requestDataExport(): ExportResult {
            calls++
            return gate.await()
        }

        override suspend fun deleteAccount() = Unit
        override suspend fun signOutOtherDevices() = Unit
    }

    @Test
    fun `a second ViewModel over the same store sees the request the first one started`() = runTest {
        // Leaving the screen destroys the ViewModel; the request is not the ViewModel's.
        val gate = CompletableDeferred<ExportResult>()
        val shared = store(GatedAccountRepository(gate))
        val first = DataExportViewModel(shared)
        first.request()
        advanceUntilIdle()
        assertEquals(ExportState.Requested, first.state.value.export)

        val reopened = DataExportViewModel(shared)
        assertEquals(
            "reopening must not re-enter at Idle over a running request",
            ExportState.Requested,
            reopened.state.value.export,
        )

        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
        assertTrue(reopened.state.value.export is ExportState.Ready)
    }

    @Test
    fun `an outstanding request is written down, and cleared when it settles`() = runTest {
        val prefs = FakeKeyValueStore()
        val gate = CompletableDeferred<ExportResult>()
        val s = store(GatedAccountRepository(gate), prefs, now = { 1_000L })
        s.request()
        advanceUntilIdle()
        assertEquals("1000", prefs.raw(DataExportStore.KEY_REQUESTED_AT))

        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
        assertEquals("", prefs.raw(DataExportStore.KEY_REQUESTED_AT))
    }

    @Test
    fun `a young outstanding request comes back as Requested and issues NOTHING on its own`() =
        runTest {
            // The store is a @Singleton built the first time anything touches the graph, which
            // is before supabase-kt has restored the session off disk. Re-issuing from `init`
            // sent a DPDP export out with no JWT, 401'd, and left screen 113 waiting with
            // "couldn't build your export" for somebody who had opened the app and touched
            // nothing.
            val prefs = FakeKeyValueStore(mapOf(DataExportStore.KEY_REQUESTED_AT to "1000"))
            val repo = FakeAccountRepository()
            val s = store(repo, prefs, now = { 1000L + EXPORT_REQUEST_TTL_MILLIS / 2 })
            advanceUntilIdle()
            assertEquals("construction must not call the server", 0, repo.exportCallCount)
            assertEquals(ExportState.Requested, s.state.value)
        }

    @Test
    fun `the screen re-issues the restored request`() = runTest {
        // `data-export` is synchronous: the call IS the job, so one the process death
        // interrupted left nothing behind to poll. A restored "Requested" that is never
        // re-issued would be a spinner over a job that no longer exists — so the screen does
        // it, by which point there is a session and somebody waiting on the answer.
        val prefs = FakeKeyValueStore(mapOf(DataExportStore.KEY_REQUESTED_AT to "1000"))
        val repo = FakeAccountRepository()
        val s = store(repo, prefs, now = { 1000L + EXPORT_REQUEST_TTL_MILLIS / 2 })
        s.resumeRestored()
        advanceUntilIdle()
        assertEquals(1, repo.exportCallCount)
        assertTrue(s.state.value is ExportState.Ready)
    }

    @Test
    fun `resuming is idempotent and never doubles a live request`() = runTest {
        val gate = CompletableDeferred<ExportResult>()
        val repo = GatedAccountRepository(gate)
        val s = store(repo)
        s.request()
        advanceUntilIdle()
        s.resumeRestored()
        s.resumeRestored()
        advanceUntilIdle()
        assertEquals(1, repo.calls)
        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
    }

    @Test
    fun `a sign-out that lands mid-restore is not undone by it`() = runTest {
        // The restore reads preferences across a suspension point, so a sign-out can happen
        // inside it. Publishing the departed account's "Requested" afterwards would hand the
        // next person a spinner over an export nobody asked for.
        val prefs = FakeKeyValueStore(mapOf(DataExportStore.KEY_REQUESTED_AT to "1000"))
        val repo = FakeAccountRepository()
        val s = store(repo, prefs, now = { 1000L + EXPORT_REQUEST_TTL_MILLIS / 2 })
        // No advanceUntilIdle: the restore is queued and has not read a line.
        s.reset()
        advanceUntilIdle()
        assertEquals(ExportState.Idle, s.state.value)
        assertEquals(0, repo.exportCallCount)
    }

    @Test
    fun `two concurrent resumes issue ONE request`() = runTest {
        // The check-and-launch was a `@Volatile` read followed by a write, which makes each half
        // atomic and the sequence not: two resumes could both find the store idle and both
        // assign. On the DPDP path that is two full copies of somebody's account built and
        // handed out for one request.
        val prefs = FakeKeyValueStore(mapOf(DataExportStore.KEY_REQUESTED_AT to "1000"))
        val gate = CompletableDeferred<ExportResult>()
        val repo = GatedAccountRepository(gate)
        val s = store(repo, prefs, now = { 1000L + EXPORT_REQUEST_TTL_MILLIS / 2 })

        // Both queued before either runs a line — the screen calls this on every composition.
        s.resumeRestored()
        s.resumeRestored()
        advanceUntilIdle()

        assertEquals("one request, not two", 1, repo.calls)
        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
        assertTrue(s.state.value is ExportState.Ready)
    }

    @Test
    fun `a resume racing a tap still issues once`() = runTest {
        val prefs = FakeKeyValueStore(mapOf(DataExportStore.KEY_REQUESTED_AT to "1000"))
        val gate = CompletableDeferred<ExportResult>()
        val repo = GatedAccountRepository(gate)
        val s = store(repo, prefs, now = { 1000L + EXPORT_REQUEST_TTL_MILLIS / 2 })

        s.resumeRestored()
        s.request()
        advanceUntilIdle()

        assertEquals(1, repo.calls)
        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
    }

    @Test
    fun `a reset that interleaves a resume leaves nothing running`() = runTest {
        val prefs = FakeKeyValueStore(mapOf(DataExportStore.KEY_REQUESTED_AT to "1000"))
        val repo = FakeAccountRepository()
        val s = store(repo, prefs, now = { 1000L + EXPORT_REQUEST_TTL_MILLIS / 2 })

        s.resumeRestored()
        s.reset()
        advanceUntilIdle()

        // Whichever order they land in, the store ends signed-out: Idle, and with no timestamp
        // for the next account's launch to restore.
        assertEquals(ExportState.Idle, s.state.value)
        assertEquals("", prefs.raw(DataExportStore.KEY_REQUESTED_AT))
    }

    @Test
    fun `resuming does nothing when nothing was outstanding`() = runTest {
        val repo = FakeAccountRepository()
        val s = store(repo)
        s.resumeRestored()
        advanceUntilIdle()
        assertEquals(0, repo.exportCallCount)
        assertEquals(ExportState.Idle, s.state.value)
    }

    @Test
    fun `a resume that beats the restore read still lands`() = runTest {
        // The screen can be built before the store has read a byte. A resume that observed Idle
        // and gave up would leave a restored request sitting there forever, which is why
        // `resumeRestored` waits on the restore job rather than sampling the state.
        val prefs = FakeKeyValueStore(mapOf(DataExportStore.KEY_REQUESTED_AT to "1000"))
        val repo = FakeAccountRepository()
        val s = store(repo, prefs, now = { 1000L + EXPORT_REQUEST_TTL_MILLIS / 2 })
        // Deliberately NO advanceUntilIdle first: the restore has not run a line.
        s.resumeRestored()
        advanceUntilIdle()
        assertEquals(1, repo.exportCallCount)
    }

    @Test
    fun `a stale outstanding request is dropped, not resumed`() = runTest {
        val prefs = FakeKeyValueStore(mapOf(DataExportStore.KEY_REQUESTED_AT to "1000"))
        val repo = FakeAccountRepository()
        val s = store(repo, prefs, now = { 1000L + EXPORT_REQUEST_TTL_MILLIS + 1 })
        advanceUntilIdle()
        assertEquals(0, repo.exportCallCount)
        assertEquals(ExportState.Idle, s.state.value)
        assertEquals("", prefs.raw(DataExportStore.KEY_REQUESTED_AT))
    }

    @Test
    fun `nothing outstanding starts nothing`() = runTest {
        val repo = FakeAccountRepository()
        val s = store(repo)
        advanceUntilIdle()
        assertEquals(0, repo.exportCallCount)
        assertEquals(ExportState.Idle, s.state.value)
    }

    @Test
    fun `the restore window is 24 hours, and a backwards clock is young rather than stale`() {
        assertEquals(ExportRestore.None, exportRestoreFor(null, 0))
        assertEquals(ExportRestore.Resume, exportRestoreFor(0, EXPORT_REQUEST_TTL_MILLIS))
        assertEquals(ExportRestore.Expired, exportRestoreFor(0, EXPORT_REQUEST_TTL_MILLIS + 1))
        // Timezone edit or an NTP correction. Dropping somebody's export request because their
        // phone disagrees about the date is the wrong way to be wrong.
        assertEquals(ExportRestore.Resume, exportRestoreFor(1_000, 0))
    }

    // ── Stopping stops WATCHING (review finding 4) ──────────────────────────────────────

    @Test
    fun `a completion that lands after Stop waiting cannot drag the screen back`() = runTest {
        // The bug: "Stop waiting here" only set Idle, and the still-running request overwrote
        // it a minute later with a Ready the user had explicitly walked away from.
        val gate = CompletableDeferred<ExportResult>()
        val prefs = FakeKeyValueStore()
        val s = store(GatedAccountRepository(gate), prefs)
        s.request()
        advanceUntilIdle()
        s.stopWaiting()
        advanceUntilIdle()
        assertEquals(ExportState.Idle, s.state.value)
        assertEquals("", prefs.raw(DataExportStore.KEY_REQUESTED_AT))

        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
        assertEquals("a stale completion must not write state", ExportState.Idle, s.state.value)
    }

    @Test
    fun `stopping does not cancel the server call — it is doing the work`() = runTest {
        val gate = CompletableDeferred<ExportResult>()
        val repo = GatedAccountRepository(gate)
        val s = store(repo)
        s.request()
        advanceUntilIdle()
        s.stopWaiting()
        advanceUntilIdle()
        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
        assertEquals("the call was issued exactly once and never aborted", 1, repo.calls)
    }

    @Test
    fun `a fresh request after stopping still lands`() = runTest {
        val s = store()
        s.request()
        s.stopWaiting()
        advanceUntilIdle()
        s.request()
        advanceUntilIdle()
        assertTrue(s.state.value is ExportState.Ready)
    }

    @Test
    fun `sign-out clears a finished export — it is the departing account's whole record`() =
        runTest {
            val s = store()
            s.request()
            advanceUntilIdle()
            assertTrue(s.state.value is ExportState.Ready)
            s.reset()
            assertEquals(ExportState.Idle, s.state.value)
        }

    @Test
    fun `reset during an in-flight export persists nothing afterwards`() = runTest {
        // Review round 2. The job had not reached its timestamp write when sign-out wiped
        // preferences; it then wrote that timestamp into the store the NEXT account inherits,
        // and skipped its own cleanup on the stale-generation check. The next person to open
        // the export screen had it restored as an outstanding request and a DPDP export issued
        // in their name that nobody asked for.
        val prefs = FakeKeyValueStore()
        val repo = FakeAccountRepository()
        val s = store(repo, prefs, now = { 5_000L })

        s.request()
        // Deliberately NO advanceUntilIdle: the coroutine is queued and has not run a line,
        // which is exactly the window the finding is about.
        prefs.wipe()
        s.reset()
        advanceUntilIdle()

        assertEquals("nothing may be left for the next account", "", prefs.raw(DataExportStore.KEY_REQUESTED_AT))
        assertEquals("the cancelled request never reached the server", 0, repo.exportCallCount)
        assertEquals(ExportState.Idle, s.state.value)
    }

    @Test
    fun `reset cancels the request rather than disowning it`() = runTest {
        val gate = CompletableDeferred<ExportResult>()
        val prefs = FakeKeyValueStore()
        val s = store(GatedAccountRepository(gate), prefs, now = { 5_000L })
        s.request()
        advanceUntilIdle()
        assertEquals("5000", prefs.raw(DataExportStore.KEY_REQUESTED_AT))

        prefs.wipe()
        s.reset()
        advanceUntilIdle()
        assertEquals("", prefs.raw(DataExportStore.KEY_REQUESTED_AT))

        // Whatever the (now cancelled) call does next, it cannot write state or a timestamp.
        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
        assertEquals(ExportState.Idle, s.state.value)
        assertEquals("", prefs.raw(DataExportStore.KEY_REQUESTED_AT))
    }

    @Test
    fun `a request issued after a reset is unaffected by it`() = runTest {
        // The guard must stop the OLD request, not the store.
        val prefs = FakeKeyValueStore()
        val s = store(FakeAccountRepository(), prefs, now = { 7_000L })
        s.request()
        s.reset()
        advanceUntilIdle()

        s.request()
        advanceUntilIdle()
        assertTrue(s.state.value is ExportState.Ready)
        assertEquals("a settled request leaves nothing outstanding", "", prefs.raw(DataExportStore.KEY_REQUESTED_AT))
    }

    @Test
    fun `stopping does not leave a timestamp that would resume it on the next launch`() = runTest {
        val gate = CompletableDeferred<ExportResult>()
        val prefs = FakeKeyValueStore()
        val s = store(GatedAccountRepository(gate), prefs, now = { 5_000L })
        s.request()
        advanceUntilIdle()
        s.stopWaiting()
        advanceUntilIdle()
        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
        // Otherwise the next cold start restores a request the user explicitly walked away from.
        assertEquals("", prefs.raw(DataExportStore.KEY_REQUESTED_AT))
    }

    @Test
    fun `a request already in flight is not started twice`() = runTest {
        val gate = CompletableDeferred<ExportResult>()
        val repo = GatedAccountRepository(gate)
        val s = store(repo)
        s.request()
        advanceUntilIdle()
        s.request()
        advanceUntilIdle()
        assertEquals(1, repo.calls)
        // Release it: the one call is genuinely still in flight, and `runTest` waits for the
        // scope's children — which is itself the proof that nothing cancelled it.
        gate.complete(ExportResult.Inline("{}"))
        advanceUntilIdle()
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
