package `in`.artistant.app.feature.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.resolvedEndEpochMs
import `in`.artistant.app.data.model.resolvedStartEpochMs
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.feature.messages.ViewerIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One row of the list.
 *
 * The artist is carried WHOLE rather than flattened into a name, for the same
 * reason `BookingDetailUiState` carries one: screen 10's confirmed card needs
 * the cover, the pending row needs the category and the "Tech rider" control
 * needs the rider — and every one of those is optional, so a booking whose
 * artist could not be fetched still renders with a placeholder slot and a bare
 * name rather than disappearing.
 */
data class BookingsListItem(
    val booking: Booking,
    val artistName: String,
    val artist: Artist? = null,
    /** Gig start, resolved once at load so the segment and the badge agree. */
    val startMs: Long? = null,
    val endMs: Long? = null,
) {
    val category: String get() = artist?.category.orEmpty()
    val techRider: List<String> get() = artist?.tech.orEmpty()
    val coverUrl: String? get() = artist?.coverUrl
}

data class BookingsUiState(
    val items: List<BookingsListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /**
     * The failure was the NETWORK, not the server (see [isConnectivityFailure]).
     * Only this makes screen 122 say "You're offline" and mean it.
     */
    val offline: Boolean = false,
    val tab: BookingsTab = BookingsTab.Upcoming,
    /**
     * The last list we successfully read, shown when this read failed and we
     * have one. Null when the failure is the first thing that ever happened on
     * this device — which is a different screen, and says so.
     */
    val cached: BookingsSnapshot? = null,
    /** Screen 89's "Add your name" prompt — a real gap in `users.full_name`. */
    val showNameNudge: Boolean = false,
    /**
     * The clock the segments and countdowns were computed against, fixed at load.
     *
     * Not `System.currentTimeMillis()` read at render: a list that re-reads the
     * wall clock on every recomposition can move a row from Upcoming to Past
     * mid-scroll, and the badge under the user's thumb would change while they
     * are looking at it.
     */
    val asOfMs: Long = System.currentTimeMillis(),
) {
    val upcoming: List<BookingsListItem>
        get() = items.filter { isUpcoming(it.booking.status, it.startMs, it.endMs, asOfMs) }

    val past: List<BookingsListItem>
        get() = items.filterNot { isUpcoming(it.booking.status, it.startMs, it.endMs, asOfMs) }

    val visible: List<BookingsListItem>
        get() = if (tab == BookingsTab.Upcoming) upcoming else past

    /** A failed read with something cached behind it — screen 122's condition. */
    val showsCached: Boolean get() = error != null && items.isEmpty() && cached != null
}

@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
    private val artistsRepository: ArtistsRepository,
    private val localStore: BookingsLocalStore,
    private val usersRepository: UsersRepository,
    private val viewerIdentity: ViewerIdentity,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingsUiState())
    val state: StateFlow<BookingsUiState> = _state.asStateFlow()

    /**
     * The in-flight load, and the stamp that decides whether it may still commit.
     *
     * Refresh is a button (and a pull, and an `init`), so two loads can be alive
     * at once and can return in either order. Without this an older, slower read
     * finishing last overwrote the fresher one it was supposed to replace — and
     * worse, an older FAILURE could attach `error` and `offline` to a newer
     * success, putting the offline banner over a list that had just loaded fine.
     * Cancelling is most of the fix; the stamp closes the rest of the window,
     * because a coroutine cancelled after its last suspension point can still
     * reach the `update` below. Same pattern as `ArtistProfileViewModel`.
     */
    private var loadJob: Job? = null
    private var loadGeneration = 0

    init {
        refresh()
        refreshNudge()
    }

    fun selectTab(tab: BookingsTab) = _state.update { it.copy(tab = tab) }

    fun refresh() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // Captured BEFORE the read, so the snapshot is stamped with the
            // account the list was actually fetched for — not with whoever
            // happens to be signed in when the slow request finally lands.
            val owner = viewerIdentity.currentUserId()
            try {
                // Cancelled and disputed rows are NOT filtered out any more. They
                // are the Past segment's whole point: a cancelled booking keeps
                // the record legible and offers rebooking (screen 83), and a list
                // that hides it leaves that screen unreachable and the client
                // wondering where their booking went.
                val bookings = bookingsRepository.listForClient()
                hydrateArtists(bookings)
                val items = bookings.map { b ->
                    val artist = artistsRepository.find(b.artistId)
                    BookingsListItem(
                        booking = b,
                        artistName = artist?.name ?: "Artist",
                        artist = artist,
                        startMs = b.resolvedStartEpochMs(),
                        endMs = b.resolvedEndEpochMs(),
                    )
                }
                if (generation != loadGeneration) return@launch
                val now = System.currentTimeMillis()
                // Written on every successful read, not on a timer: the snapshot's
                // whole job is to be the last thing we KNEW, and the honest stamp
                // for that is the moment we learned it.
                //
                // Dropped entirely when the account that started this read is no
                // longer the one signed in. `wipeAll()` clears the store on
                // sign-out, but a read already in flight for the outgoing account
                // lands afterwards — and writing it back would hand the next
                // person to open this tab offline a stranger's venue and load-in
                // note.
                if (!owner.isNullOrBlank() && owner == viewerIdentity.currentUserId()) {
                    localStore.saveSnapshot(snapshotOf(items, now, owner))
                }
                _state.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        error = null,
                        offline = false,
                        cached = null,
                        asOfMs = now,
                    )
                }
            } catch (e: BookingRepositoryError) {
                degrade(e, generation)
            } catch (e: CancellationException) {
                // A superseded load, not a failure. Rethrowing keeps the
                // structured-concurrency contract — and stops the catch below
                // reporting "the read didn't complete" for a read WE cancelled,
                // which would have put an error banner over the newer list.
                throw e
            } catch (e: Exception) {
                degrade(e, generation)
            }
        }
    }

    /**
     * A read failed. Fall back to the snapshot and say WHICH failure it was.
     *
     * The cached list is only fetched on the failure path — reading it eagerly
     * would mean a DataStore round-trip on every successful load for a value
     * nothing renders.
     *
     * A snapshot belonging to another account is not merely ignored, it is
     * deleted: leaving it on disk means the same leak waits for the next reader.
     */
    private suspend fun degrade(error: Throwable, generation: Int) {
        // Checked FIRST, so a superseded load touches no storage at all. It is
        // belt-and-braces behind `loadJob.cancel()` — the window it closes is a
        // coroutine cancelled after its last suspension point, which still runs
        // to its next one — and the same trade `ArtistProfileViewModel` makes.
        if (generation != loadGeneration) return
        val stored = runCatching { localStore.loadSnapshot() }.getOrNull()
        val mine = stored?.takeIf { snapshotBelongsTo(it, viewerIdentity.currentUserId()) }
        if (stored != null && mine == null) {
            runCatching { localStore.clearSnapshot() }
        }
        _state.update {
            it.copy(
                isLoading = false,
                error = error.message ?: "Couldn't load bookings.",
                offline = isConnectivityFailure(error),
                cached = mine,
            )
        }
    }

    fun dismissNameNudge() {
        _state.update { it.copy(showNameNudge = false) }
        viewModelScope.launch { runCatching { localStore.setNudgeDismissed(true) } }
    }

    /**
     * Screen 89's nudge, driven by a real gap rather than by a guess.
     *
     * It appears only when `users.full_name` is genuinely blank — the artist
     * really cannot see who is asking — and only when the user has not already
     * sent it away. Every failure here (offline, RLS, no session) resolves to NOT
     * showing it: a prompt to fix a problem we could not confirm exists is worse
     * than no prompt.
     */
    private fun refreshNudge() {
        viewModelScope.launch {
            val dismissed = runCatching { localStore.nudgeDismissed() }.getOrDefault(true)
            if (dismissed) return@launch
            val profile = runCatching { usersRepository.fetchSelfProfile() }.getOrNull()
            val missingName = profile != null && profile.fullName.isNullOrBlank()
            if (missingName) _state.update { it.copy(showNameNudge = true) }
        }
    }

    /**
     * Pull the artists these bookings reference into the by-id cache.
     *
     * [ArtistsRepository.find] is a pure map read — it never fetches — and nothing
     * on this screen's path fills that map: `listForClient()` returns bookings
     * only. So on any entry that hasn't been through Discover first (a push deep
     * link straight to the Bookings tab, a process-death restore onto it) every
     * row's headline would render the "Artist" placeholder, and even after
     * Discover an artist outside its rails still would — one row saying "Artist"
     * beside neighbours with real names reads as a data bug.
     *
     * Only misses are fetched, and concurrently: a client's list is a handful of
     * distinct artists at most. `ensureFull` swallows transport errors, so a dead
     * network costs a placeholder name, not the list. Same treatment the inbox
     * already got — see `MessagesViewModel.hydrateArtists`.
     */
    private suspend fun hydrateArtists(bookings: List<Booking>) {
        val missing = bookings
            .map { it.artistId }
            .distinct()
            .filter { artistsRepository.find(it) == null }
        if (missing.isEmpty()) return
        coroutineScope {
            missing.map { id -> async { artistsRepository.ensureFull(id) } }.awaitAll()
        }
    }
}

/**
 * The list, reduced to what is worth having without a network.
 *
 * See [CachedBooking] for what is left out and why.
 */
internal fun snapshotOf(
    items: List<BookingsListItem>,
    nowMs: Long,
    ownerId: String,
): BookingsSnapshot =
    BookingsSnapshot(
        cachedAtMs = nowMs,
        ownerId = ownerId,
        items = items.map {
            CachedBooking(
                id = it.booking.id,
                artistName = it.artistName,
                status = it.booking.status.dbValue,
                date = it.booking.date,
                time = it.booking.time,
                venue = it.booking.venue,
                venueNotes = it.booking.venueNotes,
            )
        },
    )
