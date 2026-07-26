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

    /** Group consecutive rows under the same month header. */
    fun groupedByMonth(): List<Pair<String, List<BookingsListItem>>> =
        _state.value.items.groupBy { it.monthKey }.toList()
}
