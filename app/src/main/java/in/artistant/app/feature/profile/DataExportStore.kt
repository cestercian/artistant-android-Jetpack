package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.platform.storage.AccountScopedStore
import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
 *    restored as [ExportState.Requested] and eventually **re-issued** rather than merely
 *    re-displayed. Re-issuing is not a shortcut: `data-export` is a synchronous Edge Function
 *    that BUILDS the export in the call and returns it (inline JSON or a one-hour signed URL).
 *    There is no job id to poll and no server-side job to poll for, so a call the process death
 *    interrupted produced nothing and left nothing behind — a "Requested" that is never
 *    re-issued is a spinner over a job that no longer exists. But the re-issue is the SCREEN's,
 *    via [resumeRestored], not the constructor's: this singleton is built before supabase-kt
 *    has restored the session, and the call it used to fire from `init` went out with no JWT.
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
) : AccountScopedStore {
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

    /**
     * The request currently in flight, so [reset] can end it rather than merely disown it.
     *
     * `@Volatile` because it is written on whichever thread tapped and read on [scope].
     */
    @Volatile
    private var inFlight: Job? = null

    /**
     * Read back what a previous process left outstanding — as STATE, never as a request.
     *
     * This used to call [request] straight out of `init`, which is a DPDP export issued in
     * somebody's name at CONSTRUCTION time. The store is a `@Singleton` built by Hilt the first
     * time anything asks for the graph, which is before supabase-kt has restored the session
     * off disk — so the re-issued call went out with no JWT, 401'd, and screen 113 was waiting
     * with "couldn't build your export" for a user who had opened the app and touched nothing.
     * Worse, it fired for whoever the process belonged to next, not necessarily the account
     * that asked.
     *
     * So the restore only puts the state back. Re-issuing is [resumeRestored]'s job and the
     * screen's decision — by which point there is a session, and somebody is looking at it.
     *
     * A [Job] rather than an `init` block so [resumeRestored] can wait for it: the screen can
     * be built before this has read a single byte, and a resume that observed [ExportState.Idle]
     * because the read had not landed yet would leave a restored request sitting forever.
     */
    private val restore: Job = scope.launch {
        val mine = generation.get()
        val requestedAt = runCatching { prefs.getString(KEY_REQUESTED_AT).first() }
            .getOrNull()
            ?.toLongOrNull()
        // The read suspends, so anything can happen across it — a tap, a "stop waiting", or a
        // sign-out. The same generation guard every other write in this class uses: a restore
        // that has been overtaken must neither publish a state nor clear a timestamp that is
        // now somebody else's.
        if (generation.get() != mine) return@launch
        when (exportRestoreFor(requestedAt, now())) {
            // Only over Idle: a request the user started in the meantime outranks a restored one.
            ExportRestore.Resume -> if (_state.value is ExportState.Idle) {
                _state.value = ExportState.Requested
            }
            ExportRestore.Expired -> clearOutstanding()
            ExportRestore.None -> Unit
        }
    }

    /**
     * Re-issue a request that [restore] put back, now that a screen is looking at it.
     *
     * `data-export` is a synchronous Edge Function: the call IS the job, it builds the export
     * and returns it. There is no job id to poll and no server-side job to poll for, so a call
     * that a process death interrupted produced nothing and left nothing behind — which is why
     * restoring "Requested" without eventually re-issuing would be a spinner over a job that no
     * longer exists.
     *
     * Idempotent and cheap: it does nothing unless the state really is a restored
     * [ExportState.Requested] with no live call under it, so the screen can call it on every
     * composition.
     */
    fun resumeRestored() {
        scope.launch {
            restore.join()
            if (_state.value !is ExportState.Requested) return@launch
            if (inFlight?.isActive == true) return@launch
            issue()
        }
    }

    /**
     * Ask the server to build the export. A no-op while one is already in flight.
     *
     * Guarded on the JOB rather than on the state, because [ExportState.Requested] is now also
     * what a restored-but-unissued request looks like, and that one has to be startable.
     *
     * Failure produces [ExportState.Failed] and **no file**: there is deliberately no path that
     * keeps a previous [ExportState.Ready] alongside a failure, because a stale file beside a
     * fresh error is how someone shares half their data believing it is all of it.
     */
    fun request() {
        if (inFlight?.isActive == true) return
        issue()
    }

    private fun issue() {
        val mine = generation.incrementAndGet()
        _state.value = ExportState.Requested
        inFlight = scope.launch {
            markOutstanding(mine)
            val result = runCatching { account.requestDataExport() }
            // The user stopped watching, signed out, or started a fresh request while this one
            // was in flight. Its answer is no longer about anything on screen — and, more to
            // the point, is no longer allowed to touch a preferences store that may now belong
            // to somebody else.
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
        inFlight = null
        _state.value = ExportState.Idle
        // Deliberately not cancelled — see the class note. The generation bump above is what
        // stops the completion writing state, and `markOutstanding`'s second check is what stops
        // a timestamp write still in flight from outliving this.
        scope.launch { clearOutstanding() }
    }

    /**
     * Sign-out / delete-account: this device knows nothing now.
     *
     * A `@Singleton` outlives the session, and [ExportState.Ready] holds the previous account's
     * export — an inline JSON payload of everything Artistant stored about them, or a signed URL
     * to it. Handing that to whoever signs in next would be the worst leak in the app, so it is
     * cleared on the same teardown that clears the saved-artist ids.
     *
     * **This one CANCELS**, unlike [stopWaiting], and it is `suspend` so the caller can rely on
     * that having finished. Disowning the job was not enough: `SessionManager.signOut()` calls
     * `prefs.wipeAll()` and then this, so a request whose coroutine had not yet reached its
     * timestamp write would write it AFTER the wipe — into a store the next account inherits —
     * and then skip its own cleanup on the stale-generation check, leaving the record behind.
     * The next person to open the export screen would have that timestamp restored as an
     * outstanding request and a DPDP export issued in their name that nobody asked for.
     *
     * The order is load-bearing: bump the generation FIRST, so anything that survives
     * cancellation still fails its own check, then join, then clear whatever the job managed to
     * write before it went. Writing the empty string into a just-wiped store is harmless — it
     * parses to "nothing outstanding", which is the truth.
     */
    override suspend fun reset() {
        generation.incrementAndGet()
        val job = inFlight
        inFlight = null
        _state.value = ExportState.Idle
        runCatching { job?.cancelAndJoin() }
        clearOutstanding()
    }

    /**
     * Record that a request is outstanding — but only while it is still ours.
     *
     * Both checks are needed and neither is sufficient alone. The first stops a job whose
     * account ended before it ever ran; the second exists because `setString` SUSPENDS, so the
     * session can end while the write is in flight, and a check that only happens before the
     * write is a check with a gap in it. On the sign-out path [reset] then clears whatever did
     * land, which is the half of this that survives the coroutine being cancelled outright.
     */
    private suspend fun markOutstanding(mine: Int) {
        if (generation.get() != mine) return
        runCatching { prefs.setString(KEY_REQUESTED_AT, now().toString()) }
        if (generation.get() != mine) clearOutstanding()
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
