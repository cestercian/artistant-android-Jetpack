package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.RequestsRepositoryError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class RequestQuoteUiState(
    val artistName: String = "",
    val amountInr: String = "",
    val dateLabel: String = "",
    val message: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class RequestQuoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistsRepository: ArtistsRepository,
    private val requestsRepository: RequestsRepository,
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _state = MutableStateFlow(RequestQuoteUiState())
    val state: StateFlow<RequestQuoteUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val artist = artistsRepository.find(artistId) ?: artistsRepository.ensureFull(artistId)
            val chips = upcomingDateChips(count = 1)
            _state.update {
                it.copy(
                    artistName = artist?.name.orEmpty(),
                    dateLabel = chips.firstOrNull()?.label.orEmpty(),
                )
            }
        }
    }

    fun setAmount(value: String) {
        _state.update { it.copy(amountInr = value.filter { c -> c.isDigit() }) }
    }

    fun setMessage(value: String) {
        _state.update { it.copy(message = value) }
    }

    fun submit() {
        val s = _state.value
        val amount = s.amountInr.toIntOrNull() ?: 0
        if (amount <= 0) {
            _state.update { it.copy(errorMessage = "Enter a valid amount.") }
            return
        }
        if (s.dateLabel.isBlank()) {
            _state.update { it.copy(errorMessage = "Pick a date.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                requestsRepository.create(
                    artistId = artistId,
                    proposedAmountInr = amount,
                    dateLabel = s.dateLabel,
                    message = s.message.ifBlank { null },
                    venue = null,
                    crowdSize = null,
                    expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7),
                )
                _state.update { it.copy(isSubmitting = false, success = true) }
            } catch (e: RequestsRepositoryError) {
                _state.update { it.copy(isSubmitting = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSubmitting = false, errorMessage = e.message ?: "Request failed.")
                }
            }
        }
    }

    /** Quick-pick chips for date — same formatter as booking funnel. */
    fun pickChip(chip: DateChip) {
        _state.update { it.copy(dateLabel = chip.label) }
    }

    fun dateChips(): List<DateChip> = upcomingDateChips()
}
