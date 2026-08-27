package `in`.artistant.app.feature.saved

import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.SavedArtistsRepositoryError
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic saved-heart store — port of iOS `SavedStore`.
 *
 * Local ids flip instantly; the network add/remove is fire-and-forget per artist,
 * and the final desired state wins a rapid toggle.
 *
 * **Every mutation goes through one channel and is applied by one consumer.**
 * That is the whole design, and it is a deliberate replacement for what stood
 * here before. The previous version kept the same invariants with an epoch stamp,
 * a jobs map, a per-read touch registry, a persist mutex and a registration
 * monitor, and review still found five distinct interleavings in it: a write
 * published before it was registered; a toggle that started and finished inside
 * one server read; a save/unsave pair that netted to zero while the answer caught
 * it half-done; a check-then-act around a CAS retry loop; and a toggle that
 * repopulated the set after sign-out had emptied it. Each fix was correct and
 * each left a narrower gap, because the bookkeeping was what needed to be
 * race-free rather than the state.
 *
 * With a single consumer there is no interleaving to reason about: [toggle],
 * [reset] and an arriving server answer are messages, applied in the order they
 * were sent, one at a time. The network calls still run off the consumer so a
 * slow read never blocks a tap.
 */
@Singleton
class SavedStore @Inject constructor(
    private val repository: SavedArtistsRepository,
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    private sealed interface Command {
        /** A tap. Flip the id, write the new desired state, remember it is ours. */
        data class Toggle(val id: String) : Command

        /** A `list()` that came back. [session] is the account it was issued for. */
        data class ServerAnswer(val remote: Set<String>, val session: Int) : Command

        /** A write finished (or was cancelled); stop calling it in flight. */
        data class WriteSettled(val id: String, val job: Job) : Command

        /** Sign-out or delete-account: this device knows nothing now. */
        data object Reset : Command
    }

    /**
     * UNLIMITED because the senders cannot suspend: [toggle] is called from a tap
     * handler and [reset] from `SessionManager.signOut()`, neither of which may
     * block on a full buffer. The traffic is one message per tap.
     */
    private val commands = Channel<Command>(Channel.UNLIMITED)

    /** In-flight network writes, keyed by artist. Touched only by the consumer. */
    private val writes = mutableMapOf<String, Job>()

    /**
     * Ids whose local value the server has not yet agreed with.
     *
     * This is the whole protection mechanism, and it replaces the four registries
     * the previous version cross-referenced. An id enters on a tap and leaves
     * only when a server answer reports the value the user chose — so a read
     * issued before the tap, a read that observed the tap half-done, and a
     * save/unsave pair that nets to zero are all covered by the same rule
     * without anyone having to notice the difference. It stays small: it holds
     * only ids where local and server currently disagree.
     */
    private val unconfirmed = mutableSetOf<String>()

    /**
     * Which account the state belongs to, bumped by [Command.Reset].
     *
     * Read off the consumer by [refreshFromServer] before it issues its read, and
     * compared by the consumer when the answer lands — so an answer fetched for a
     * departed account is dropped rather than written over the new one's hearts.
     * Only the consumer writes it, so a volatile read is enough.
     */
    @Volatile
    private var session: Int = 0

    init {
        scope.launch { consume() }
        scope.launch {
            val cached = prefs.getString(PREFS_KEY).first()
                ?.split(',')
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty()
            // Seeded as an answer rather than assigned: it goes through the same
            // consumer as everything else, so a tap that beat the disk read is
            // already unconfirmed and survives it.
            if (cached.isNotEmpty()) commands.trySend(Command.ServerAnswer(cached, session))
            refreshFromServer()
        }
    }

    fun contains(artistId: String): Boolean = artistId.lowercase() in _ids.value

    fun toggle(artistId: String) {
        commands.trySend(Command.Toggle(artistId.lowercase()))
    }

    /**
     * Replace the local set with server truth, except where the user disagrees.
     *
     * The read runs HERE, off the consumer, so a slow network never delays a tap;
     * only the answer is a message. A failure leaves local untouched — the iOS
     * contract — and a `NotSignedIn` keeps the cached hearts until a session
     * lands.
     */
    suspend fun refreshFromServer() {
        val issuedFor = session
        try {
            val remote = repository.list().map { it.lowercase() }.toSet()
            commands.trySend(Command.ServerAnswer(remote, issuedFor))
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

    fun reset() {
        commands.trySend(Command.Reset)
    }

    private suspend fun consume() {
        for (command in commands) {
            when (command) {
                is Command.Toggle -> applyToggle(command.id)
                is Command.ServerAnswer -> applyAnswer(command)
                is Command.WriteSettled ->
                    if (writes[command.id] === command.job) writes.remove(command.id)
                Command.Reset -> applyReset()
            }
        }
    }

    private fun applyToggle(id: String) {
        val next = if (id in _ids.value) _ids.value - id else _ids.value + id
        _ids.value = next
        // Ours until the server says the same thing back.
        unconfirmed.add(id)
        persist(next)

        val wantSaved = id in next
        // Supersede rather than queue: a rapid double tap should cost one write
        // carrying the final state, not two that race to land in order.
        writes.remove(id)?.cancel()
        val job = scope.launch {
            runCatching {
                if (wantSaved) repository.add(id) else repository.remove(id)
            }.onFailure { if (it is CancellationException) throw it }
        }
        writes[id] = job
        // Reported back rather than pruned from the completion handler, which
        // runs on whatever thread finished the write: `writes` belongs to the
        // consumer and nothing else may touch it. Identity-checked on arrival so
        // a superseded write cannot evict the newer one that replaced it.
        job.invokeOnCompletion { commands.trySend(Command.WriteSettled(id, job)) }
    }

    private fun applyAnswer(answer: Command.ServerAnswer) {
        // Fetched for an account that has since left. Applying it would put the
        // departed user's hearts into this one's set and onto their disk.
        if (answer.session != session) return

        val local = _ids.value
        val next = reconcileSaved(answer.remote, local, unconfirmed)
        _ids.value = next
        // An id the server now agrees with is settled; one it still disagrees
        // with keeps its protection until a later answer catches up.
        unconfirmed.removeAll { id -> (id in answer.remote) == (id in next) }
        persist(next)
    }

    private fun applyReset() {
        session += 1
        writes.values.forEach { it.cancel() }
        writes.clear()
        unconfirmed.clear()
        _ids.value = emptySet()
        persist(emptySet())
    }

    /**
     * Mirror a set to disk.
     *
     * Takes the value rather than reading `_ids`, and runs on the consumer's own
     * scope in send order, so the last state the consumer decided on is the last
     * one written — a stale snapshot can no longer land after sign-out's empty
     * one.
     */
    private fun persist(value: Set<String>) {
        scope.launch { prefs.setString(PREFS_KEY, value.joinToString(",")) }
    }

    companion object {
        const val PREFS_KEY = "saved.artistIds"
    }
}

/**
 * Fold a server read into the local saved set.
 *
 * [remote] is the truth for every artist whose local state is settled.
 * [unsettled] are the ids the user has changed and the server has not yet agreed
 * with: their own tap is the more recent fact about what they want, so those keep
 * whatever [local] says until an answer reports the value they chose.
 */
internal fun reconcileSaved(
    remote: Set<String>,
    local: Set<String>,
    unsettled: Set<String>,
): Set<String> =
    if (unsettled.isEmpty()) remote else (remote - unsettled) + local.filter { it in unsettled }
