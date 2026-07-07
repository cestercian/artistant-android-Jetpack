package `in`.artistant.app.feature.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.platform.payments.PaymentException
import `in`.artistant.app.platform.payments.PaymentsService
import `in`.artistant.app.state.BookingStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives `CheckoutScreen` (port of iOS `CheckoutView`). Confirm routes through the
 * [PaymentsService] seam (mock in v1) → [BookingStore.confirmDraftAsBooking] → a
 * one-shot [confirmed] event carrying the new booking id. A payment throw OR a
 * post-payment write failure surfaces on [state].error with a retry.
 *
 * ponytail: the v1 booking-quota paywall gate is deferred to M7 (subscriptions are
 * dormant); there's nothing to gate on yet, so confirm goes straight through.
 */
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val store: BookingStore,
    private val payments: PaymentsService,
    private val artists: ArtistsRepository,
) : ViewModel() {

    data class UiState(val confirming: Boolean = false, val error: String? = null)

    val draft = store.draft

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _confirmed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val confirmed: SharedFlow<String> = _confirmed.asSharedFlow()

    /** The artist behind the current draft (cache hit — the funnel warmed it). */
    fun artist(): Artist? = store.draft.value?.let { artists.find(it.artistId) }

    fun confirm() {
        val current = store.draft.value ?: return
        if (_state.value.confirming) return
        _state.update { it.copy(confirming = true, error = null) }
        viewModelScope.launch {
            try {
                val result = payments.collectPayment(current)
                val booking = store.confirmDraftAsBooking(result)
                if (booking != null) {
                    _confirmed.tryEmit(booking.id)
                    _state.update { it.copy(confirming = false) }
                } else {
                    // Payment cleared but the write didn't land (auth race / RLS /
                    // network) — recoverable, so a visible retry, not a dead end.
                    _state.update { it.copy(confirming = false, error = "Couldn't confirm the match. Please retry.") }
                }
            } catch (_: PaymentException) {
                _state.update { it.copy(confirming = false, error = "Payment didn't go through. Please retry.") }
            }
        }
    }
}
