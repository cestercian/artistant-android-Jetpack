package `in`.artistant.app.feature.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.payments.PaymentsService
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.feature.paywall.EntitlementStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckoutUiState(
    val draft: BookingDraft? = null,
    val artistName: String = "",
    /**
     * The artist behind the draft, when we could load one.
     *
     * Held whole so the review header can show their score beside their name,
     * and so [artistHasNoPackages] can be answered from a real fact rather than
     * from the absence of one.
     */
    val artist: Artist? = null,
    val isSubmitting: Boolean = false,
    /**
     * Which half of the two-hop submit is in flight, or null when idle. Drives
     * the narrated wait; see [checkoutWaitCopy].
     */
    val waitPhase: CheckoutWaitPhase? = null,
    val lastCreateErrorMessage: String? = null,
    val confirmedBookingId: String? = null,
    /** When subscriptions are on and the client isn't entitled — navigate to paywall. */
    val needsPaywall: Boolean = false,
) {
    /**
     * True ONLY for an artist we successfully loaded who publishes no tiers.
     *
     * A null artist (fetch failed, cache cold) answers false on purpose: the
     * draft already carries a real fee, so the request is valid and must stay
     * sendable. Blocking on a failed decoration is how a CTA ends up permanently
     * grey with nothing the user can do about it.
     */
    val artistHasNoPackages: Boolean get() = artist?.packages?.isEmpty() == true

    val blocked: Boolean get() = checkoutBlocked(isSubmitting, artistHasNoPackages)
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val draftStore: BookingDraftStore,
    private val artistsRepository: ArtistsRepository,
    private val bookingsRepository: BookingsRepository,
    private val paymentsService: PaymentsService,
    private val entitlements: EntitlementStore,
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
            // Show the draft immediately — it is the whole review, and it is
            // already in hand. The artist only decorates it (name, score, and
            // whether they publish tiers at all).
            _state.update { it.copy(draft = draft) }
            val artist = artistsRepository.ensureFull(draft.artistId)
                ?: artistsRepository.find(draft.artistId)
            _state.update { it.copy(artist = artist, artistName = artist?.name.orEmpty()) }
        }
    }

    fun sendRequest() {
        val draft = _state.value.draft ?: return
        if (_state.value.blocked) return
        // Mirror the reference build's quota gate — inert until subscriptions flip on.
        if (entitlements.subscriptionsActive && !entitlements.isEntitled.value) {
            _state.update { it.copy(needsPaywall = true) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSubmitting = true,
                    lastCreateErrorMessage = null,
                    waitPhase = CheckoutWaitPhase.Sending,
                )
            }
            try {
                // The payments seam stays in the path even though v1 moves no
                // money: the mock returns a receipt whose ids are persisted onto
                // the row, so swapping in a real provider later is a config
                // change rather than a re-plumb.
                val payment = paymentsService.collectPayment(draft)
                // Hop two — the request is placed, now the server write has to land.
                _state.update { it.copy(waitPhase = CheckoutWaitPhase.Awaiting) }
                val booking = bookingsRepository.create(draft, payment)
                draftStore.clear()
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        waitPhase = null,
                        confirmedBookingId = booking.id,
                    )
                }
            } catch (e: BookingRepositoryError) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        // Drop the narrated takeover so the retryable banner is
                        // visible: a failure hidden behind a full-screen wait is
                        // a silent dead-end.
                        waitPhase = null,
                        lastCreateErrorMessage = e.message,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        waitPhase = null,
                        lastCreateErrorMessage = e.message ?: "Couldn't send your request.",
                    )
                }
            }
        }
    }

    fun clearNavigation() {
        _state.update { it.copy(confirmedBookingId = null, needsPaywall = false) }
    }

    fun consumePaywall() {
        _state.update { it.copy(needsPaywall = false) }
    }

    fun dismissError() {
        _state.update { it.copy(lastCreateErrorMessage = null) }
    }
}
