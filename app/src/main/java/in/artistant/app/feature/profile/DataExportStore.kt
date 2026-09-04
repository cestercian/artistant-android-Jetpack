package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What to do with a request that was outstanding when the process died.
 *
 * Pure, and separate from the store, because it is the one piece of this that is a decision
 * rather than plumbing — and because a clock and a coroutine scope are exactly what a unit test
 * should not have to own to check it.
 */
enum class ExportRestore {
    /** Nothing was outstanding. */
    None,

    /** A request was outstanding and is young enough to still mean something. */
    Resume,

    /** A request was outstanding, and it is old enough that claiming it is still running lies. */
    Expired,
}

/**
 * How long an outstanding request stays meaningful — the same 24 hours screen 81 promises
 * ("requests take up to 24 hours to assemble"). Past it, the screen returns to Idle rather than
 * showing a spinner over a job nobody can account for.
 */
const val EXPORT_REQUEST_TTL_MILLIS = 24L * 60 * 60 * 1000

/** @see ExportRestore */
fun exportRestoreFor(requestedAtMillis: Long?, nowMillis: Long): ExportRestore = when {
    requestedAtMillis == null -> ExportRestore.None
    // A clock that moved backwards (timezone edit, NTP correction) makes the age negative.
    // Treat it as young rather than expired: the user asked for their data, and dropping the
    // request because their phone disagrees about the date is the wrong way to be wrong.
    nowMillis - requestedAtMillis > EXPORT_REQUEST_TTL_MILLIS -> ExportRestore.Expired
    else -> ExportRestore.Resume
}

/**
 * The DPDP export request, held above the screen that starts it.
 *
 * **Why this is not in the ViewModel.** It was, and that made the screen's own copy false. The
 * ViewModel is scoped to the `data_export` NavBackStackEntry, so `viewModelScope` is cancelled
 * the moment the user leaves — the in-flight call to the Edge Function died with it, and coming
 * back re-entered at [ExportState.Idle] while screen 82 was saying "the job is on the server and
 * does not depend on the app staying open". A singleton with its own scope is what that sentence
 * needs to be true; [SavedStore] is the same pattern for the same reason.
 *
 * **What "requested" survives.** Two different lifetimes, and they need two different answers:
 *
 *  - *Navigation and backgrounding* are covered by this object being a `@Singleton` — the job
 *    runs on [scope], which nothing but sign-out cancels, and the state is here waiting when the
 *    screen is rebuilt.
 *  - *Process death* is covered by the timestamp in [KeyValueStore]. On the next construction
 *    [exportRestoreFor] decides whether that request still means anything, and a young one is
 *    **re-issued** rather than merely re-displayed. That is not a shortcut: `data-export` is a
 *    synchronous Edge Function that BUILDS the export in the call and returns it (inline JSON or
 *    a one-hour signed URL). There is no job id to poll and no server-side job to poll for, so a
 *    call the process death interrupted produced nothing and left nothing behind — restoring
 *    "Requested" without re-issuing would be a spinner over a job that no longer exists.
 *
 * **Stopping is not cancelling.** "Stop waiting here" says exactly what it does: [stopWaiting]
 * orphans the in-flight call rather than aborting it, because the call is doing the work and
 * killing it mid-flight is how you get a half-written export in a bucket. The generation counter
 * is what makes that safe — a completion whose generation is stale cannot write state, so the
 * screen the user sent back to Idle stays Idle instead of flipping to Ready under them a minute
 * later.
 */
@Singleton
class DataExportStore internal constructor(
    private val account: AccountRepository,
    private val prefs: KeyValueStore,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    @Inject constructor(account: AccountRepository, prefs: KeyValueStore) : this(
        account = account,
        prefs = prefs,
        // SupervisorJob so a failed export cannot take the scope (and every later request)
        // down with it; IO because the only thing that runs here is one network call.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        now = System::currentTimeMillis,
    )

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    /**
     * Which request the screen is currently watching.
     *
     * Bumped by [request] and by [stopWaiting] and by [reset], and read by an in-flight job
     * before it writes. Atomic because the write happens on [scope] and the bumps happen on
     * whatever thread tapped a button.
     */
    private val generation = AtomicInteger(0)

    init {
        scope.launch {
            val requestedAt = runCatching { prefs.getString(KEY_REQUESTED_AT).first() }
                .getOrNull()
                ?.toLongOrNull()
            when (exportRestoreFor(requestedAt, now())) {
                ExportRestore.Resume -> request()
                ExportRestore.Expired -> clearOutstanding()
                ExportRestore.None -> Unit
            }
        }
    }

    /**
     * Ask the server to build the export. A no-op while one is already in flight.
     *
     * Failure produces [ExportState.Failed] and **no file**: there is deliberately no path that
     * keeps a previous [ExportState.Ready] alongside a failure, because a stale file beside a
     * fresh error is how someone shares half their data believing it is all of it.
     */
    fun request() {
        if (_state.value is ExportState.Requested) return
        val mine = generation.incrementAndGet()
        _state.value = ExportState.Requested
        scope.launch {
            runCatching { prefs.setString(KEY_REQUESTED_AT, now().toString()) }
            val result = runCatching { account.requestDataExport() }
            // The user stopped watching, signed out, or started a fresh request while this one
            // was in flight. Its answer is no longer about anything on screen.
            if (generation.get() != mine) return@launch
            clearOutstanding()
            _state.value = result.fold(
                onSuccess = { ExportState.Ready(it) },
                onFailure = {
                    ExportState.Failed(it.message ?: "The job stopped partway through.")
                },
            )
        }
    }

    /**
     * Back to 81. The request is not cancellable server-side, so this stops WATCHING it — see
     * the class note on why that is a generation bump and not a `Job.cancel()`.
     */
    fun stopWaiting() {
        generation.incrementAndGet()
        _state.value = ExportState.Idle
        scope.launch { clearOutstanding() }
    }

    /**
     * Sign-out / delete-account: this device knows nothing now.
     *
     * A `@Singleton` outlives the session, and [ExportState.Ready] holds the previous account's
     * export — an inline JSON payload of everything Artistant stored about them, or a signed URL
     * to it. Handing that to whoever signs in next would be the worst leak in the app, so it is
     * cleared on the same teardown that clears the saved-artist ids.
     */
    fun reset() {
        generation.incrementAndGet()
        _state.value = ExportState.Idle
    }

    private suspend fun clearOutstanding() {
        // The generic store writes strings and has no remove; the empty string parses to no
        // timestamp, which is exactly "nothing outstanding" to [exportRestoreFor].
        runCatching { prefs.setString(KEY_REQUESTED_AT, "") }
    }

    companion object {
        /** Epoch millis of the request currently outstanding, or "" for none. */
        const val KEY_REQUESTED_AT = "export.requestedAt"
    }
}
