package `in`.artistant.app.feature.saved

import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.SavedArtistsRepositoryError
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic saved-heart store — port of iOS `SavedStore`.
 * Local ids flip instantly; network add/remove is fire-and-forget per artist
 * (serialized so the final desired state wins a rapid toggle).
 */
@Singleton
class SavedStore @Inject constructor(
    private val repository: SavedArtistsRepository,
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    // Concurrent, not plain maps. These held two different answers to the same
    // question: `mutexFor` guarded its access with `synchronized(mutexes)` while
    // `toggle` read and wrote `jobs` with no guard at all, even though the
    // entries are removed from an IO completion handler.
    //
    // [jobs] holds ONLY the writes still in flight — see the `invokeOnCompletion`
    // in [toggle] — which is what lets [refreshFromServer] use its key set as the
    // list of ids whose local state the server cannot have seen yet. It used to
    // gain an entry per artist ever toggled and never lose one.
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val jobs = ConcurrentHashMap<String, Job>()

    /**
     * Which account the in-memory state belongs to, as a counter [reset] bumps.
     *
     * This is a process-lifetime singleton and its writes are fire-and-forget, so
     * nothing tied them to the session that started them: a heart tapped by A
     * whose `add` was still queued when A signed out and B signed in ran under
     * B's JWT against an `_ids` that [reset] had emptied — deleting B's save of
     * that artist — and a `list()` issued for A could land after the switch and
     * persist A's saved ids into B's DataStore. Every write and every refresh now
     * carries the epoch it began under and abandons itself if that changed.
     */
    private val epoch = AtomicInteger(0)

    init {
        scope.launch {
            val cached = prefs.getString(PREFS_KEY).first()
                ?.split(',')
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty()
            if (cached.isNotEmpty()) _ids.value = cached
            refreshFromServer()
        }
    }

    fun contains(artistId: String): Boolean = artistId.lowercase() in _ids.value

    fun toggle(artistId: String) {
        val id = artistId.lowercase()
        // Registered as unsettled BEFORE the optimistic set is published.
        //
        // [refreshFromServer] runs on this class's own IO scope (the init
        // refresh) while taps arrive on Main, so the two genuinely interleave.
        // Publishing first left a window where `_ids` already showed the heart
        // and [jobs] did not yet name it: a refresh landing in that window read
        // local={id}, remote={} (its `list()` predates the tap) and no in-flight
        // protection, reconciled the tap away, and [persistLocal] then wrote the
        // reverted set to disk — visibly un-hearting an artist whose `add` was
        // about to succeed. Cold start plus a fast tap on Discover is exactly
        // when the init refresh is in flight.
        //
        // LAZY because the body re-reads the desired state from `_ids`, so it
        // must not run until the publish below has happened.
        val startedAt = epoch.get()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            mutexFor(id).withLock {
                // The account that armed this write must still be the one signed
                // in: `_ids` is empty after a sign-out, so an unguarded run here
                // would read wantSaved=false and issue a `remove` under the NEW
                // user's JWT, deleting their save of this artist.
                if (epoch.get() != startedAt) return@withLock
                // Re-read desired state after any superseded toggles.
                val wantSaved = id in _ids.value
                runCatching {
                    if (wantSaved) repository.add(id) else repository.remove(id)
                }.onFailure { if (it is CancellationException) throw it }
            }
        }
        val superseded = jobs.put(id, job)
        // Prune on completion so the map means "writes still in flight" rather
        // than "artists ever hearted". Conditional on identity: a superseding
        // toggle cancels this job and installs its own, and this handler must not
        // then remove the newer entry.
        job.invokeOnCompletion { jobs.remove(id, job) }
        _ids.update { if (id in it) it - id else it + id }
        persistLocal()
        superseded?.cancel()
        job.start()
    }

    /**
     * Replace the local set with server truth. Leave local alone on failure.
     *
     * Everything except the ids whose own write has not settled yet. A wholesale
     * `_ids.value = remote` lost the heart the user tapped while the read was in
     * flight: `list()` was issued before the tap, so its answer predates it, and
     * assigning it un-filled a heart whose `add` was about to succeed — then
     * [persistLocal] wrote the wrong set to DataStore, where it survived until
     * some later refresh happened to re-read. That is the opposite of the
     * "optimistic, final desired state wins" contract this class documents.
     *
     * [jobs] names exactly those ids. Snapshot it TWICE — once before the read,
     * once as the answer is applied — because a write already in flight when the
     * read was issued can settle while it is still running (the read may have
     * been served before it), and a fresh tap can arrive mid-read.
     */
    suspend fun refreshFromServer() {
        val startedAt = epoch.get()
        // What the set looked like before the read was issued. Anything that
        // differs afterwards is a change the user made while we were reading, and
        // the server's answer cannot have seen it.
        val before = _ids.value
        val inFlightAtRead = jobs.keys.toSet()
        try {
            val remote = repository.list().map { it.lowercase() }.toSet()
            // Issued for a different account: applying it would overwrite this
            // user's hearts with the previous one's, and persistLocal would put
            // them on disk.
            if (epoch.get() != startedAt) return
            _ids.update { local ->
                // `jobs` names only writes STILL in flight, so a toggle that both
                // started and finished inside this read appeared in neither
                // snapshot — its entry was added after the first and pruned before
                // the second — and the stale answer reversed a save that had just
                // succeeded. A before/after diff of the local set catches it
                // whatever became of its write, and self-cancels correctly: heart
                // then un-heart during one read nets to no change, so the server
                // rightly wins.
                val changedDuringRead = (local - before) + (before - local)
                reconcileSaved(remote, local, inFlightAtRead + jobs.keys + changedDuringRead)
            }
            persistLocal()
        } catch (e: CancellationException) {
            // Structured concurrency: the callers are viewModelScope coroutines
            // (ProfileViewModel, ArtistListViewModel), so a cleared ViewModel
            // cancels this — and a blanket `catch (_: Throwable)` absorbed the
            // cancellation and let the caller carry on running on a dead scope.
            throw e
        } catch (_: SavedArtistsRepositoryError.NotSignedIn) {
            // Keep local hearts until a session lands.
        } catch (_: Throwable) {
            // Network/RLS — leave local untouched (iOS contract).
        }
    }

    /**
     * Drop everything this device knows about the departing account.
     *
     * Called from `SessionManager.signOut()` and the delete-account path. Bumping
     * [epoch] first is what makes the abandonment checks in [toggle] and
     * [refreshFromServer] fire; cancelling the in-flight writes stops the ones
     * already past their check, and clearing the per-artist maps releases the
     * bookkeeping that would otherwise outlive the session that created it.
     */
    fun reset() {
        epoch.incrementAndGet()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        mutexes.clear()
        _ids.value = emptySet()
        persistLocal()
    }

    private fun persistLocal() {
        scope.launch {
            prefs.setString(PREFS_KEY, _ids.value.joinToString(","))
        }
    }

    // computeIfAbsent, not getOrPut: the stdlib extension is a plain
    // read-then-write and would hand two callers two different locks for the
    // same artist, which is the one thing a lock may not do.
    private fun mutexFor(id: String): Mutex = mutexes.computeIfAbsent(id) { Mutex() }

    companion object {
        const val PREFS_KEY = "saved.artistIds"
    }
}

/**
 * Fold a server read into the local saved set.
 *
 * [remote] is the truth for every artist whose local state is settled.
 * [unsettled] are the ids whose own add/remove has not come back yet: the read
 * was issued before them, so it cannot have seen them, and the user's own tap is
 * the more recent fact about what they want. Those keep whatever [local] says —
 * their pending write is what makes the server agree a moment later.
 */
internal fun reconcileSaved(
    remote: Set<String>,
    local: Set<String>,
    unsettled: Set<String>,
): Set<String> =
    if (unsettled.isEmpty()) remote else (remote - unsettled) + local.filter { it in unsettled }
