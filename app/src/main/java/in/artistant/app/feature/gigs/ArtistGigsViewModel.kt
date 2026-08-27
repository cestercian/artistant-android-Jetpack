package `in`.artistant.app.feature.gigs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.monthLabelFromDateLabel
import `in`.artistant.app.feature.artisthome.artistClientDisplayName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistGigListItem(
    val booking: Booking,
    val clientName: String,
    val monthKey: String,
)

data class ArtistGigsUiState(
    val items: List<ArtistGigListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArtistGigsViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistGigsUiState())
    val state: StateFlow<ArtistGigsUiState> = _state.asStateFlow()

    /**
     * Newest refresh wins, however the network resolves — the same guard
     * ArtistHomeViewModel carries over the same bookings list.
     *
     * Refreshes overlap here whenever the pull indicator isn't showing: the
     * `init` load plus a first pull (the screen only reports refreshing once it
     * has rows), and repeated pulls or the empty state's Retry from a failed
     * load. Without the stamp the request that finishes LAST wins even when it
     * is the older one, so a gig accepted between the two pulls drops off the
     * list and the grid un-shades its day. Cancelling alone is not enough: a
     * job past its last suspension point runs on to its write regardless, and a
     * cancelled job lands in `catch` (CancellationException is an Exception),
     * which would paint the failure line over data that loaded fine.
     */
    private var refreshJob: Job? = null
    private var generation = 0

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        val gen = ++generation
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val bookings = bookingsRepository.listForArtist()
                    .filter { it.status != BookingStatus.Cancelled }
                val items = bookings.map { b ->
                    ArtistGigListItem(
                        booking = b,
                        clientName = artistClientDisplayName(b),
                        monthKey = monthLabelFromDateLabel(b.date),
                    )
                }
                if (gen != generation) return@launch
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: BookingRepositoryError) {
                if (gen != generation) return@launch
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                if (gen != generation) return@launch
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun groupedByMonth(): List<Pair<String, List<ArtistGigListItem>>> =
        _state.value.items.groupBy { it.monthKey }.toList()
}
