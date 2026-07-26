package `in`.artistant.app.feature.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.payments.PaymentsService
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckoutUiState(
    val draft: BookingDraft? = null,
    val artistName: String = "",
    val isSubmitting: Boolean = false,
    val lastCreateErrorMessage: String? = null,
    val confirmedBookingId: String? = null,
)

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val draftStore: BookingDraftStore,
    private val artistsRepository: ArtistsRepository,
    private val bookingsRepository: BookingsRepository,
    private val paymentsService: PaymentsService,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val draft = draftStore.draft.value
            if (draft == null) {
                _state.update { it.copy(lastCreateErrorMessage = "No booking draft — go back and try again.") }
                return@launch
            }
            val artist = artistsRepository.find(draft.artistId)
            _state.update {
                it.copy(draft = draft, artistName = artist?.name.orEmpty())
            }
        }
    }

    fun sendRequest() {
        val draft = _state.value.draft ?: return
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, lastCreateErrorMessage = null) }
            try {
                val payment = paymentsService.collectPayment(draft)
                val booking = bookingsRepository.create(draft, payment)
                draftStore.clear()
                _state.update {
                    it.copy(isSubmitting = false, confirmedBookingId = booking.id)
                }
            } catch (e: BookingRepositoryError) {
                _state.update {
                    it.copy(isSubmitting = false, lastCreateErrorMessage = e.message)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        lastCreateErrorMessage = e.message ?: "Couldn't send your request.",
                    )
                }
            }
        }
    }

    fun clearNavigation() {
        _state.update { it.copy(confirmedBookingId = null) }
    }
}
