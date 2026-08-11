package `in`.artistant.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.MessagesRepository
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

    val unreadCount: Int = activeThreads.count { it.unread }
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
 *    a status, a date and a venue. Only the lists the viewer can legitimately
 *    read are fetched, chosen by which seats actually appear in their inbox — a
 *    pure client never asks for artist gigs, and vice versa.
 *
 * Filtering and search run entirely over the loaded list ([InboxProjection]);
 * changing a chip must never refetch.
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagesRepository: MessagesRepository,
    private val artistsRepository: ArtistsRepository,
    private val bookingsRepository: BookingsRepository,
    private val flagsStore: ThreadFlagsStore,
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
    private var flags: ThreadFlags = ThreadFlags()

    init {
        // Flags arrive from DataStore asynchronously and change under us as the
        // user stars/archives, so this is a subscription, not a one-shot read.
        viewModelScope.launch {
            flagsStore.flags.collect { latest ->
                flags = latest
                _state.update { it.copy(threads = project()) }
            }
        }
        refresh()
    }

    fun setFilter(filter: MessagesFilter) = _state.update { it.copy(filter = filter) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching { messagesRepository.listThreadsForUser() }
            .onSuccess { threads ->
                loadedThreads = threads
                hydrateArtists(threads)
                bookingsById = loadBookings(threads)
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

    /** Opens a real participant-pair thread; navigation receives only server UUIDs. */
    fun findOrCreateThread(artistId: String, onReady: (String) -> Unit) = viewModelScope.launch {
        runCatching { messagesRepository.findOrCreateThread(artistId) }
            .onSuccess(onReady)
            .onFailure { e -> _state.update { it.copy(error = e.message ?: FALLBACK_ERROR) } }
    }

    /** Rebuild the rows from the last payload plus the current local flags. */
    private fun project(): List<ThreadListItem> {
        val viewerId = viewer.currentUserId()
        return loadedThreads.map { thread ->
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
     * Which list to ask for is decided by the seats present in the inbox, not by
     * a stored role: an account can legitimately hold both (an artist who also
     * books other artists), and asking for the wrong list returns an empty set
     * rather than an error, which would look identical to "no gigs" and quietly
     * strip the status from every row.
     *
     * Both calls degrade to empty on failure. A booking lookup that fails costs
     * the context line; it must never take the inbox down with it, so this
     * result is deliberately not routed into `state.error`.
     */
    private suspend fun loadBookings(threads: List<Thread>): Map<String, Booking> {
        if (threads.none { it.bookingId != null }) return emptyMap()
        val viewerId = viewer.currentUserId()
        val seats = threads.map { ThreadCounterpart.viewerIsArtist(it, viewerId) }.toSet()
        val loaded = buildList {
            if (seats.contains(true)) {
                addAll(runCatching { bookingsRepository.listForArtist() }.getOrDefault(emptyList()))
            }
            if (seats.contains(false)) {
                addAll(runCatching { bookingsRepository.listForClient() }.getOrDefault(emptyList()))
            }
        }
        return loaded.associateBy { it.id.lowercase() }
    }

    private companion object {
        const val FALLBACK_ERROR = "Couldn't refresh messages."
    }
}
