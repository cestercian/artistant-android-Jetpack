package `in`.artistant.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.RequestsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessagesUiState(
    val threads: List<ThreadListItem> = emptyList(),
    val filter: MessagesFilter = MessagesFilter.All,
    val query: String = "",
    val isLoading: Boolean = true,
    /** True once a load has completed — separates "first paint" from "refreshing". */
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    /**
     * The inbox proper. Archived conversations drop out of every inbox surface —
     * rows, chip counts, and the is-it-empty check — and live only in the archive
     * list, which is what makes archiving feel like it did something.
     */
    val activeThreads: List<ThreadListItem> = threads.filterNot { it.archived }

    val archivedThreads: List<ThreadListItem> = threads.filter { it.archived }

    val visibleThreads: List<ThreadListItem> = InboxProjection.visible(activeThreads, filter, query)

    val counts: Map<MessagesFilter, Int> = InboxProjection.counts(activeThreads, query)
}

/**
 * The inbox.
 *
 * Two things beyond "list the threads" happen here, and both are the reason the
 * rows can say anything useful:
 *
 *  - **Seat resolution.** A thread has a client side and an artist side and the
 *    row must name the OTHER one. Resolved once per load from the live session
 *    (see [ThreadCounterpart]) rather than per row, so a sign-out mid-map can't
 *    label two rows from two different seats.
 *  - **Gig resolution.** Each thread's booking is looked up so the row can carry
 *    a status, a date and a venue. Only the ids the rows actually reference are
 *    fetched, in one query — never a seat's whole booking history, which is both
 *    an unbounded payload and the calendar mirror's own trigger.
 *
 * Filtering and search run entirely over the loaded list ([InboxProjection]);
 * changing a chip must never refetch.
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagesRepository: MessagesRepository,
    private val artistsRepository: ArtistsRepository,
    private val bookingsRepository: BookingsRepository,
    private val requests: RequestsRepository,
    private val flagsStore: ThreadFlagsStore,
    private val blockedUsers: BlockedUsersStore,
    private val viewer: ViewerIdentity,
) : ViewModel() {
    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    /**
     * The last server payload, kept so a flag toggle can re-project the rows
     * without a refetch. Starring a conversation is not a reason to hit the
     * network.
     */
    private var loadedThreads: List<Thread> = emptyList()
    private var bookingsById: Map<String, Booking> = emptyMap()

    /**
     * The viewer's own gig requests, newest first, so each row can show the deal
     * it is about (design 19). ONE list call for the whole inbox — RLS already
     * restricts it to the viewer's side, and matching happens in [project].
     */
    private var quotes: List<StoredRequest> = emptyList()
    private var flags: ThreadFlags = ThreadFlags()
    private var blockedIds: Set<String> = emptySet()

    /** Whether the screen's first ON_RESUME has been seen — see [onResumed]. */
    private var resumedOnce = false

    init {
        // Flags arrive from DataStore asynchronously and change under us as the
        // user stars/archives, so this is a subscription, not a one-shot read.
        viewModelScope.launch {
            flagsStore.flags.collect { latest ->
                flags = latest
                _state.update { it.copy(threads = project()) }
            }
        }
        // Same reason, one screen further away: the block is performed inside a
        // chat, so the inbox has to hear about it without being told to reload.
        viewModelScope.launch {
            blockedUsers.blocked.collect { ids ->
                blockedIds = ids
                _state.update { it.copy(threads = project()) }
            }
        }
        refresh()
    }

    /**
     * Back on screen — from the background, or from the chat that was pushed on
     * top of the inbox.
     *
     * Reading a conversation happens one screen away: the chat zeroes the
     * server's unread count and a reply moves the row up the `last_message_at`
     * order, while this ViewModel is retained and still holds the payload from
     * before the thread was opened. Without a re-read on the way back, the row
     * you just answered keeps its unread rail, its stale preview and its old
     * place in the list until someone pulls to refresh. Nothing else can correct
     * it — the flags store only clears a device-local "mark as unread", never
     * `unreadCount`.
     *
     * The FIRST call is swallowed: that resume arrives with the screen's first
     * paint, which `init`'s [refresh] has already served, so acting on it would
     * load the inbox twice on every entry. The latch is here rather than in
     * [ResumeEffect] because navigation disposes this screen's composition
     * behind the chat while this ViewModel survives — a latch over there resets
     * on the way back and swallows the very resume that needs to re-read.
     */
    fun onResumed() {
        if (!resumedOnce) {
            resumedOnce = true
            return
        }
        load(quiet = true)
    }

    fun setFilter(filter: MessagesFilter) = _state.update { it.copy(filter = filter) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    /** The pull gesture, the retry taps and the first load. */
    fun refresh() = load(quiet = false)

    /**
     * @param quiet a resync nobody asked for ([onResumed]). It must not raise the
     * pull-to-refresh indicator over rows that are already on screen — that
     * signal belongs to the gesture — so it leaves `isLoading` alone once the
     * first load has landed. Failure is still reported: a strip over stale rows
     * is the only thing that can explain an inbox that stopped updating.
     */
    private fun load(quiet: Boolean) = viewModelScope.launch {
        _state.update { it.copy(isLoading = !quiet || !it.hasLoaded, error = null) }
        // Reconciled alongside the threads, and deliberately before them: the
        // rows are filtered against this set, so pulling it late would paint a
        // blocked conversation for one frame. It swallows its own failures and
        // never clears on one, so it can't take the inbox down or un-hide anyone.
        blockedUsers.refresh()
        runCatching { messagesRepository.listThreadsForUser() }
            .onSuccess { threads ->
                loadedThreads = threads
                hydrateArtists(threads)
                bookingsById = loadBookings(threads)
                quotes = loadQuotes()
                _state.update {
                    it.copy(threads = project(), isLoading = false, hasLoaded = true, error = null)
                }
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: FALLBACK_ERROR) }
            }
    }

    fun toggleStarred(threadId: String) = viewModelScope.launch { flagsStore.toggleStarred(threadId) }

    fun toggleArchived(threadId: String) = viewModelScope.launch { flagsStore.toggleArchived(threadId) }

    /** Rebuild the rows from the last payload plus the current local flags. */
    private fun project(): List<ThreadListItem> {
        val viewerId = viewer.currentUserId()
        // Blocked conversations are dropped HERE, before the rows exist, rather
        // than in one of the inbox's list projections. Everything the inbox
        // shows — the rows, the filter-chip counts and the archive list — is
        // derived from this one list, so filtering at the source is what makes a
        // block complete: a blocked person can't leave a count on a chip for a
        // conversation there is no way to open.
        return loadedThreads.filterNot { ThreadCounterpart.isBlocked(it, blockedIds) }.map { thread ->
            val viewerIsArtist = ThreadCounterpart.viewerIsArtist(thread, viewerId)
            val artist = artistsRepository.find(thread.artistId)
            ThreadListItem(
                thread = thread,
                counterpartName = ThreadCounterpart.name(
                    thread = thread,
                    viewerId = viewerId,
                    artistName = artist?.name,
                ),
                context = ThreadContext.resolve(thread, bookingsById, viewerIsArtist),
                quote = ThreadQuote.pick(
                    requests = quotes,
                    artistId = thread.artistId,
                    viewerIsArtist = viewerIsArtist,
                    nowMs = System.currentTimeMillis(),
                ),
                coverUrl = artist?.coverUrl,
                starred = thread.id in flags.starred,
                archived = thread.id in flags.archived,
                markedUnread = thread.id in flags.markedUnread,
            )
        }
    }

    /**
     * Pull the artists this inbox references into the by-id cache.
     *
     * The app stopped eagerly loading the whole roster, so on a cold start the
     * cache knows nothing and every client-seat row would render the "Artist"
     * placeholder. Only misses are fetched, and they go out concurrently because
     * an inbox is a handful of distinct artists at most. Failures are swallowed
     * by `ensureFull`, so a dead network costs a placeholder name, not the inbox.
     */
    private suspend fun hydrateArtists(threads: List<Thread>) {
        val missing = threads
            .map { it.artistId }
            .distinct()
            .filter { artistsRepository.find(it) == null }
        if (missing.isEmpty()) return
        coroutineScope {
            missing.map { id -> async { artistsRepository.ensureFull(id) } }.awaitAll()
        }
    }

    /**
     * The bookings behind these threads, keyed by id.
     *
     * Exactly the ids the rows reference, in one round trip. The seat's own list
     * was the wrong instrument twice over: it pulls every booking the account has
     * ever had — an inbox load's payload growing with a working artist's whole
     * history — and both list calls carry the calendar-mirror side effect, which
     * belongs to Bookings/Gigs, not here. `fetchMany` also needs no seat: RLS
     * already decides which of these ids the viewer may read, so an account that
     * holds both seats is served by the same single query.
     *
     * Degrades to empty on failure. A booking lookup that fails costs the context
     * line; it must never take the inbox down with it, so this result is
     * deliberately not routed into `state.error`.
     */
    /**
     * The viewer's gig requests, for the deal state on each row (design 19).
     *
     * ONE list call for the whole inbox — RLS already restricts it to the
     * viewer's side, and the per-thread match happens in [project]. Degrades to
     * empty, deliberately and silently: a quote that cannot be read costs a row
     * its deal line and it falls back on the last message, which is not a reason
     * to fail the inbox.
     *
     * Which seat to ask is decided per load rather than cached, because an
     * account can hold both — and asking the wrong one returns an empty list
     * rather than someone else's quotes.
     */
    private suspend fun loadQuotes(): List<StoredRequest> {
        val viewerId = viewer.currentUserId() ?: return emptyList()
        val asArtist = loadedThreads.any { it.artistId.equals(viewerId, ignoreCase = true) }
        return runCatching {
            if (asArtist) requests.listForArtist() else requests.listForClient()
        }.getOrDefault(emptyList())
    }

    private suspend fun loadBookings(threads: List<Thread>): Map<String, Booking> {
        val ids = threads.mapNotNull { it.bookingId }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return runCatching { bookingsRepository.fetchMany(ids) }
            .getOrDefault(emptyList())
            .associateBy { it.id.lowercase() }
    }

    private companion object {
        const val FALLBACK_ERROR = "Couldn't refresh messages."
    }
}
