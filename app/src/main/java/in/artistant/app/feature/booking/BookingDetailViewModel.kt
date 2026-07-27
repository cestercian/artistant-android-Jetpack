package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingDetailUiState(
    val booking: Booking? = null,
    val artistName: String = "",
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val isActing: Boolean = false,
    val actionError: String? = null,
)

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingsRepository: BookingsRepository,
    private val artistsRepository: ArtistsRepository,
    /** Handed to [ReviewSheet] so the detail screen doesn't need a second VM. */
    val reviewsRepository: ReviewsRepository,
) : ViewModel() {

    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"])

    private val _state = MutableStateFlow(BookingDetailUiState())
    val state: StateFlow<BookingDetailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            val booking = runCatching { bookingsRepository.fetchOne(bookingId) }.getOrNull()
            if (booking == null) {
                _state.update { it.copy(isLoading = false, loadError = "Booking not found.") }
                return@launch
            }
            val artist = artistsRepository.find(booking.artistId)
            _state.update {
                it.copy(
                    booking = booking,
                    artistName = artist?.name.orEmpty(),
                    isLoading = false,
                )
            }
        }
    }

    fun acceptRequest() = mutateRequest { bookingsRepository.accept(bookingId) }

    fun declineRequest() = mutateRequest { bookingsRepository.declineByArtist(bookingId, reason = null) }

    fun cancelBooking() = mutateRequest { bookingsRepository.cancel(bookingId, reason = null) }

    fun reportActionError(message: String) =
        _state.update { it.copy(actionError = message) }

    private fun mutateRequest(block: suspend () -> Booking) {
        if (_state.value.isActing) return
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, actionError = null) }
            try {
                val updated = block()
                _state.update { it.copy(booking = updated, isActing = false) }
            } catch (e: BookingRepositoryError) {
                _state.update { it.copy(isActing = false, actionError = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isActing = false, actionError = e.message ?: "Action failed.")
                }
            }
        }
    }

    /** Display name flips by viewer role — artist sees client, client sees artist. */
    fun counterpartyName(isArtistViewer: Boolean): String {
        val s = _state.value
        return if (isArtistViewer) {
            s.booking?.clientFullName?.takeIf { it.isNotBlank() } ?: "Client"
        } else {
            s.artistName.ifBlank { "Artist" }
        }
    }

    fun showAcceptDecline(isArtistViewer: Boolean): Boolean =
        isArtistViewer && _state.value.booking?.status == BookingStatus.PendingConfirm

    fun showClientCancel(isArtistViewer: Boolean): Boolean =
        !isArtistViewer && _state.value.booking?.status == BookingStatus.PendingConfirm
}
