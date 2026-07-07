package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.state.BookingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Drives `RequestQuoteScreen` (port of iOS `RequestQuoteView`). The client
 * proposes a custom date + budget (+ optional message/venue/guests); [submit]
 * writes a `gig_requests` row via [RequestsRepository.create] with a 7-day expiry.
 * Field state lives in the screen (plain compose remember); this VM owns the
 * artist hydrate + the write lifecycle.
 */
@HiltViewModel
class RequestQuoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val requests: RequestsRepository,
    private val artists: ArtistsRepository,
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>("artistId").orEmpty()

    private val _artist = MutableStateFlow(artists.find(artistId))
    val artist: StateFlow<Artist?> = _artist.asStateFlow()

    data class UiState(val submitting: Boolean = false, val error: String? = null, val sent: Boolean = false)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { artists.ensureFull(artistId)?.let { _artist.value = it } }
    }

    fun submit(amount: Int, date: LocalDate, message: String, venue: String, guests: Int) {
        if (amount <= 0 || _state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                requests.create(
                    artistId = artistId,
                    proposedAmountInr = amount,
                    dateLabel = BookingStore.dateLabel(date),
                    message = message,
                    venue = venue,
                    crowdSize = guests,
                    // Auto-expire after a week if the artist never answers.
                    expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
                )
                _state.update { it.copy(submitting = false, sent = true) }
            } catch (t: Throwable) {
                _state.update { it.copy(submitting = false, error = t.message ?: "Couldn't send the request.") }
            }
        }
    }
}
