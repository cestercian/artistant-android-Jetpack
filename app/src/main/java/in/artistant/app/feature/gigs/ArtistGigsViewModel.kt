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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
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
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: BookingRepositoryError) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun groupedByMonth(): List<Pair<String, List<ArtistGigListItem>>> =
        _state.value.items.groupBy { it.monthKey }.toList()
}
