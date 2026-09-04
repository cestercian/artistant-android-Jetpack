package `in`.artistant.app.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedStartEpochMs
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.feature.saved.SavedStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import `in`.artistant.app.designsystem.component.bookingStatusTone

data class ArtistListRow(
    val id: String,
    val artistId: String?,
    val bookingId: String?,
    val artist: Artist?,
    val fallbackTitle: String,
    val pills: List<Pair<String, PillTone>>,
)

data class ArtistListUiState(
    val kind: ArtistListKind = ArtistListKind.Saved,
    val rows: List<ArtistListRow> = emptyList(),
    /** null = "All". Filters [rows] down to one act category (screen 32). */
    val selectedCategory: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /**
     * The category chips: every act type actually present in the rows.
     *
     * Derived rather than fetched, and derived from THESE rows rather than from
     * the roster's facets: a chip for a category nobody in your saved list plays
     * is a filter that can only ever empty the screen.
     */
    val categories: List<String>
        get() = rows.mapNotNull { it.artist?.category?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()

    /** The rows the screen draws, after the category chip. */
    val visibleRows: List<ArtistListRow>
        get() = selectedCategory?.let { picked ->
            rows.filter { it.artist?.category.equals(picked, ignoreCase = true) }
        } ?: rows
}

@HiltViewModel
class ArtistListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookings: BookingsRepository,
    private val artists: ArtistsRepository,
    private val savedStore: SavedStore,
) : ViewModel() {

    private val kind = ArtistListKind.fromRaw(savedStateHandle.get<String>("kind").orEmpty())

    private val _state = MutableStateFlow(ArtistListUiState(kind = kind))
    val state: StateFlow<ArtistListUiState> = _state.asStateFlow()

    init {
        if (kind == ArtistListKind.Saved) observeSaved()
        refresh()
    }

    /**
     * Saved rows come from the store's FLOW, for the whole life of the screen.
     *
     * They used to come from one read taken immediately after
     * `savedStore.refreshFromServer()`, which is a read of the wrong moment:
     * that call issues the network request and then merely ENQUEUES the answer
     * on the store's command channel, where a single consumer applies it on
     * another dispatcher. `savedStore.ids.value` right after it returns is
     * therefore the set from BEFORE the server answered — usually the disk
     * cache, and on a cold start an empty one. The list rendered that snapshot
     * and never looked again, so a screen opened during the round trip showed
     * stale or no rows until something else happened to re-enter it.
     *
     * Collecting means every answer the consumer applies — the server's, a
     * heart tapped on another screen, a sign-out reset — re-derives these rows.
     * `collectLatest` because [hydrateArtists] suspends on the network: a set
     * superseded mid-hydration is abandoned rather than raced to completion.
     */
    private fun observeSaved() {
        viewModelScope.launch {
            savedStore.ids.collectLatest { ids -> applySavedRows(ids.toList()) }
        }
    }

    /**
     * Turn a set of saved ids into rows.
     *
     * Deliberately does NOT touch `isLoading` or `error`: those describe the
     * refresh, which [refresh] owns, and letting the flow clear them would
     * settle the screen on the pre-answer emission — the very snapshot this
     * whole mechanism exists to stop trusting.
     */
    private suspend fun applySavedRows(ids: List<String>) {
        hydrateArtists(ids)
        val rows = ids.map { id ->
            // Hydrated above, so this is the cache: the full stitch when it
            // landed, the tile projection when it didn't, null when neither.
            val artist = artists.find(id)
            ArtistListRow(
                id = id,
                artistId = id,
                bookingId = null,
                artist = artist,
                fallbackTitle = artist?.name ?: "Artist",
                pills = emptyList(),
            )
        }
        _state.update { it.copy(rows = rows, selectedCategory = surviving(it.selectedCategory, rows)) }
    }

    /** Narrow to one act type, or back to all with null. */
    fun selectCategory(category: String?) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (kind) {
            ArtistListKind.Saved -> refreshSaved()
            ArtistListKind.Bookings, ArtistListKind.Completed -> refreshBookings()
        }
    }

    /**
     * Ask the store to re-read the server, then settle loading and failure.
     *
     * The ROWS are not this function's business — [observeSaved] delivers those.
     * All that is decided here is whether the screen may claim an empty list:
     * only when the server answered. A failed read with nothing cached is
     * "couldn't load" with a retry, because a dropped connection and a list you
     * have not filled are the same empty set and the opposite meaning, and the
     * empty state's only action ("Browse Discover") is exactly the wrong advice
     * for the first one.
     */
    private suspend fun refreshSaved() {
        val readServer = savedStore.refreshFromServer()
        _state.update {
            if (!readServer && it.rows.isEmpty() && savedStore.ids.value.isEmpty()) {
                it.copy(isLoading = false, error = SAVED_UNREACHABLE)
            } else {
                it.copy(isLoading = false, error = null)
            }
        }
    }

    private suspend fun refreshBookings() {
        runCatching { loadBookings() }
            .onSuccess { rows ->
                _state.update {
                    it.copy(
                        rows = rows,
                        selectedCategory = surviving(it.selectedCategory, rows),
                        isLoading = false,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't load list")
                }
            }
    }

    /**
     * Hydrate every artist these rows will name, in ONE wait.
     *
     * `ensureFull` is a five-table stitch (artists, packages, tech_rider,
     * samples, artist_media), and these screens are drill-downs over a whole
     * list: asked one at a time, 25 saved artists is 25 sequential round trips —
     * 125 queries — with the screen holding its spinner until the last one
     * lands. Fanned out, the wait is the slowest single hydration instead of
     * their sum. Ids the repository has already hydrated (or already resolved to
     * "no row") short-circuit inside it, so re-asking for them is free.
     *
     * Same shape as `MessagesViewModel.hydrateArtists`, and for the same reason.
     */
    private suspend fun hydrateArtists(ids: List<String>) {
        val wanted = ids.distinct()
        if (wanted.isEmpty()) return
        coroutineScope {
            wanted.map { id -> async { artists.ensureFull(id) } }.awaitAll()
        }
    }

    private suspend fun loadBookings(): List<ArtistListRow> {
        val all = bookings.listForClient()
        val source = if (kind == ArtistListKind.Completed) {
            all.filter { it.status == BookingStatus.Completed }
        } else {
            all.filter { it.status != BookingStatus.Completed }
        }
        val sorted = source.sortedWith { lhs, rhs ->
            val l = lhs.resolvedStartEpochMs()
            val r = rhs.resolvedStartEpochMs()
            when {
                l != null && r != null ->
                    if (kind == ArtistListKind.Completed) r.compareTo(l) else l.compareTo(r)
                l != null -> -1
                r != null -> 1
                else -> lhs.id.compareTo(rhs.id)
            }
        }
        hydrateArtists(sorted.map { it.artistId.lowercase() })
        return sorted.map { booking ->
            val artistId = booking.artistId.lowercase()
            val artist = artists.find(artistId)
            ArtistListRow(
                id = booking.id.lowercase(),
                artistId = artistId,
                bookingId = booking.id.lowercase(),
                artist = artist,
                fallbackTitle = booking.venue,
                pills = pillsFor(booking),
            )
        }
    }

    private fun pillsFor(booking: Booking): List<Pair<String, PillTone>> {
        // Shared mapping — this used to be the only surface that varied by status;
        // the bookings list and booking detail each had their own (wrong) idea.
        val tone = bookingStatusTone(booking.status)
        val out = mutableListOf(booking.status.label.uppercase() to tone)
        booking.date.trim().takeIf { it.isNotEmpty() }?.let {
            out += it.uppercase() to PillTone.Neutral
        }
        return out
    }

    /**
     * The category chip that survives a reload, or null.
     *
     * A selection that no longer matches any row would filter the list to
     * nothing with no chip lit to explain why.
     */
    private fun surviving(selected: String?, rows: List<ArtistListRow>): String? =
        selected?.takeIf { c -> rows.any { it.artist?.category.equals(c, ignoreCase = true) } }

    companion object {
        /** The detail line under a saved-list failure. */
        const val SAVED_UNREACHABLE =
            "We couldn't reach your saved list. This is a connection problem — " +
                "it is not that you have saved nobody."
    }
}
