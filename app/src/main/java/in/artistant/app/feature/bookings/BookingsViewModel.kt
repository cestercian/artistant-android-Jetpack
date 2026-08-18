package `in`.artistant.app.feature.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.monthLabelFromDateLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingsListItem(
    val booking: Booking,
    val artistName: String,
    val monthKey: String,
)

data class BookingsUiState(
    val items: List<BookingsListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
    private val artistsRepository: ArtistsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingsUiState())
    val state: StateFlow<BookingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val bookings = bookingsRepository.listForClient()
                    .filter { it.status != BookingStatus.Cancelled }
                hydrateArtists(bookings)
                val items = bookings.map { b ->
                    val artist = artistsRepository.find(b.artistId)
                    BookingsListItem(
                        booking = b,
                        artistName = artist?.name ?: "Artist",
                        monthKey = monthLabelFromDateLabel(b.date),
                    )
                }
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: BookingRepositoryError) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
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

    /** Group consecutive rows under the same month header. */
    fun groupedByMonth(): List<Pair<String, List<BookingsListItem>>> =
        _state.value.items.groupBy { it.monthKey }.toList()
}
