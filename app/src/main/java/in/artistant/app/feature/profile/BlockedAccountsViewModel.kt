package `in`.artistant.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.feature.messages.BlockedUsersStore
import `in`.artistant.app.feature.messages.ThreadCounterpart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One blocked person, as much as we can honestly say about them.
 *
 * [displayName] and [role] are nullable on purpose. `blocked_users` (migration
 * 0087) stores a `blocked_id` and a timestamp — no name, no avatar, no role — so
 * everything on this row beyond the id is INFERRED from the conversation the
 * block came out of. When that inference fails the fields are null and the UI
 * says so; it must never fill the gap with a plausible-looking name, because a
 * wrong name on an unblock control is a wrong person unblocked.
 */
data class BlockedAccountRow(
    /** Lowercased user id — the only field that is always known. */
    val userId: String,
    /** Their name, or null when nothing we can read carries one. */
    val displayName: String? = null,
    /** "Artist" or "Client", or null when we can't place which seat they held. */
    val role: String? = null,
) {
    /**
     * The leading chunk of the uuid, shown only when [displayName] is null.
     *
     * Two nameless rows are otherwise indistinguishable, and "unblock one of
     * these two identical rows" is not a choice anyone can make. It is a poor
     * identifier and it is deliberately not shown next to a name that already
     * does the job.
     */
    val shortId: String = userId.take(SHORT_ID_LENGTH)

    private companion object {
        const val SHORT_ID_LENGTH = 8
    }
}

/**
 * What the screen is allowed to claim.
 *
 * The distinction this enum exists for is [Ready] with no rows versus
 * [Unavailable]: both are an empty list, and they mean opposite things — "you
 * have blocked nobody" versus "we have no idea who you have blocked". Rendering
 * the same empty state for both would tell someone who blocked a person last
 * week that they hadn't.
 */
enum class BlockedAccountsStatus {
    /** First load in flight; nothing known yet. */
    Loading,

    /** The server copy was read. The list is complete, empty or not. */
    Ready,

    /** The read failed, but we have a locally-mirrored set worth showing. */
    Stale,

    /** The read failed and we know of nobody. Must NOT be worded as "empty". */
    Unavailable,
}

/**
 * The two decisions this screen makes, as pure functions.
 *
 * Kept out of the ViewModel so they can be tested with plain JUnit — the project
 * has no Robolectric, so anything that touches a ViewModel's scope or a Compose
 * tree is untestable here by construction.
 */
object BlockedAccounts {

    /** See [BlockedAccountsStatus]. `rowCount` is what we would draw right now. */
    fun status(
        loadCompleted: Boolean,
        serverReadOk: Boolean,
        rowCount: Int,
    ): BlockedAccountsStatus = when {
        !loadCompleted -> BlockedAccountsStatus.Loading
        serverReadOk -> BlockedAccountsStatus.Ready
        rowCount > 0 -> BlockedAccountsStatus.Stale
        else -> BlockedAccountsStatus.Unavailable
    }

    /**
     * Turn a set of blocked user ids into rows people can act on.
     *
     * The seat is read off the ID, not off the viewer: you cannot block yourself
     * (0087's `blocked_users_no_self`), so whichever seat of the thread carries
     * the blocked id IS the blocked person. That is one fewer thing to get wrong
     * than [ThreadCounterpart.name]'s viewer-relative resolution needs, and it
     * keeps working when the session is momentarily unreadable.
     *
     * Name sources, in order, and there are only two because there are only two:
     *  - artist seat → the artist cache, since an artist profile is public;
     *  - client seat → `threads.client_name`, the column migration 0080 exists
     *    for (RLS nulls the `users` embed, so the artist seat can't join to it).
     *
     * [artistName] is the caller's cache lookup, passed in to keep this pure.
     *
     * Ordering is named-first and alphabetical, then by id — stable, so the list
     * doesn't reshuffle under a finger when a name arrives late from the cache.
     */
    fun rows(
        blockedIds: Set<String>,
        threads: List<Thread>,
        artistName: (String) -> String?,
    ): List<BlockedAccountRow> =
        blockedIds
            .map { it.lowercase() }
            .distinct()
            .map { id -> row(id, threads, artistName) }
            .sortedWith(
                compareBy(
                    // Unnamed rows sort last: a name is the thing people scan
                    // for, and burying it under "Blocked account" entries would
                    // make the useful rows the hard ones to find.
                    { it.displayName == null },
                    { it.displayName?.lowercase().orEmpty() },
                    { it.userId },
                ),
            )

    private fun row(
        id: String,
        threads: List<Thread>,
        artistName: (String) -> String?,
    ): BlockedAccountRow {
        val thread = threads.firstOrNull { it.artistId.equals(id, ignoreCase = true) } ?: threads
            .firstOrNull { it.clientId.isNotBlank() && it.clientId.equals(id, ignoreCase = true) }

        val theyAreTheArtist = thread?.artistId?.equals(id, ignoreCase = true) == true

        return when {
            thread == null -> {
                // No conversation to read them out of. The artist cache is still
                // worth a try — an artist's row is public, so it can answer for
                // an id the thread list couldn't (e.g. the thread list failed to
                // load, or the block was made on another device). A hit also
                // proves the seat; a miss leaves BOTH fields null rather than
                // guessing a role we have no evidence for.
                val cached = artistName(id)?.takeIf { it.isNotBlank() }
                BlockedAccountRow(
                    userId = id,
                    displayName = cached,
                    role = cached?.let { ThreadCounterpart.ARTIST_PLACEHOLDER },
                )
            }

            theyAreTheArtist -> BlockedAccountRow(
                userId = id,
                displayName = artistName(id)?.takeIf { it.isNotBlank() },
                role = ThreadCounterpart.ARTIST_PLACEHOLDER,
            )

            else -> BlockedAccountRow(
                userId = id,
                displayName = thread.clientName?.takeIf { it.isNotBlank() },
                role = ThreadCounterpart.CLIENT_PLACEHOLDER,
            )
        }
    }
}

data class BlockedAccountsUiState(
    val rows: List<BlockedAccountRow> = emptyList(),
    val status: BlockedAccountsStatus = BlockedAccountsStatus.Loading,
    /** A retry is in flight over a list that is already on screen. */
    val isRefreshing: Boolean = false,
    /** Set when an unblock write failed and the row came back. */
    val actionError: String? = null,
) {
    /** True when at least one row has no name — the screen explains why once. */
    val hasUnnamedRows: Boolean = rows.any { it.displayName == null }
}

/**
 * Blocked accounts — the way back from a block.
 *
 * This screen exists because blocking was a one-way door: the inbox filters a
 * blocked person's conversations out at the point rows are built, and the only
 * Unblock control lived INSIDE the conversation that filtering had just made
 * unreachable. Block someone by accident and the UI offered no way back.
 *
 * It deliberately shares [BlockedUsersStore] with the inbox and the chat rather
 * than reading the repository itself. That is what makes an unblock here restore
 * the conversation there in the same frame: the inbox is subscribed to the same
 * set, and a private copy would leave it hidden until a manual refresh — the
 * same mistake in the other direction.
 */
@HiltViewModel
class BlockedAccountsViewModel @Inject constructor(
    private val blockedUsers: BlockedUsersStore,
    private val messagesRepository: MessagesRepository,
    private val artistsRepository: ArtistsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BlockedAccountsUiState())
    val state: StateFlow<BlockedAccountsUiState> = _state.asStateFlow()

    /** Threads, kept so an unblock can re-derive names without a refetch. */
    private var threads: List<Thread> = emptyList()
    private var loadCompleted = false
    private var serverReadOk = false

    init {
        // A subscription, not a one-shot read: an unblock performed here — or a
        // block performed in a chat while this screen sits on the back stack —
        // has to land on the list without a reload.
        viewModelScope.launch {
            blockedUsers.blocked.collect { project() }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isRefreshing = true, actionError = null) }

        // Whether the SERVER answered is the whole point of this call here; the
        // inbox can ignore it, this screen cannot (see BlockedUsersStore.refresh).
        serverReadOk = blockedUsers.refresh()

        // Names are supplementary — a failed thread load costs names, never the
        // list — so this failure is swallowed rather than routed into `status`.
        // The screen already explains a nameless row.
        threads = runCatching { messagesRepository.listThreadsForUser() }.getOrDefault(emptyList())
        hydrateNames()

        loadCompleted = true
        _state.update { it.copy(isRefreshing = false) }
        project()
    }

    /**
     * Unblock, and put the conversation back in the inbox.
     *
     * Guarded on current membership because [BlockedUsersStore.toggle] FLIPS:
     * calling it for someone already unblocked would block them again. The row
     * disappears optimistically, so a double tap would otherwise land on a stale
     * frame and silently re-block the person the user just released.
     */
    fun unblock(userId: String) {
        val id = userId.lowercase()
        if (id !in blockedUsers.blocked.value) return
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            val wrote = blockedUsers.toggle(id)
            if (!wrote) {
                // The store has already put the id back, so the row returns on
                // its own. Say why, or the reappearing row looks like a bug.
                _state.update {
                    it.copy(actionError = "Couldn't unblock. Check your connection and try again.")
                }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }

    /**
     * Pull the blocked artists' public profiles into the by-id cache so their
     * rows can carry a name.
     *
     * Only ids the cache doesn't already hold are fetched. Ids with no thread
     * are attempted too: an artist row is public, so this is the one chance to
     * name a block whose conversation we couldn't read. `ensureFull` swallows
     * transport errors and returns null, so a blocked CLIENT id (never in the
     * artists table) costs one wasted request and yields a nameless row, which
     * is the honest outcome anyway.
     */
    private suspend fun hydrateNames() {
        val ids = blockedUsers.blocked.value.map { it.lowercase() }
        val clientSeats = threads
            .filter { it.clientId.isNotBlank() }
            .map { it.clientId.lowercase() }
            .toSet()
        ids.filter { it !in clientSeats && artistsRepository.find(it) == null }
            .forEach { artistsRepository.ensureFull(it) }
    }

    private fun project() {
        val rows = BlockedAccounts.rows(
            blockedIds = blockedUsers.blocked.value,
            threads = threads,
            artistName = { id -> artistsRepository.find(id)?.name },
        )
        _state.update {
            it.copy(
                rows = rows,
                status = BlockedAccounts.status(loadCompleted, serverReadOk, rows.size),
            )
        }
    }
}
