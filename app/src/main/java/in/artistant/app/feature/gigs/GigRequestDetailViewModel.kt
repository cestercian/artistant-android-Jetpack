package `in`.artistant.app.feature.gigs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.RequestsRepositoryError
import `in`.artistant.app.platform.calendar.CalendarSyncPlanner
import `in`.artistant.app.platform.calendar.CalendarSyncService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** The three mutations the artist's dock offers on an open request. */
enum class GigRequestAction { Accept, Decline, Counter }

/**
 * One-shot side effects — today just the accept buzz.
 *
 * A Channel rather than a state flag for the usual reason (ARCHITECTURE §3): the
 * haptic has to fire once, at the moment the accept lands, and a boolean on the
 * state would re-fire it on every recomposition until something consumed it.
 *
 * The reference build buzzes on the *tap* (its store call is fire-and-forget);
 * we buzz on the *result*, because here we have one. Celebrating an accept that
 * the server refused is the failure mode iOS's own Checkout comment warns about.
 */
sealed interface GigRequestDetailEvent {
    /** The accept landed on the server. */
    data object Accepted : GigRequestDetailEvent
}

data class GigRequestDetailUiState(
    val request: StoredRequest? = null,
    val clashes: List<CalendarSyncPlanner.Clash> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    /**
     * WHICH mutation is in flight, or null when none is.
     *
     * A bare `isActing` boolean was ambiguous here for the same reason it was on
     * BookingDetailUiState: the dock renders Accept and Decline together, both
     * read the flag, so tapping either made the page announce "Accepting…" and
     * "Declining…" at once — over two actions with opposite consequences for the
     * client's request. The label belongs to the button that was tapped; the
     * others are merely disabled.
     */
    val actingAction: GigRequestAction? = null,
    val actionError: String? = null,
    val showCounterSheet: Boolean = false,
    val counterAmount: String = "",
) {
    /** Any mutation in flight — every dock control is disabled while one is. */
    val isActing: Boolean get() = actingAction != null
}

@HiltViewModel
class GigRequestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val requestsRepository: RequestsRepository,
    private val calendarSync: CalendarSyncService,
    private val bookingsRepository: BookingsRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _state = MutableStateFlow(GigRequestDetailUiState())
    val state: StateFlow<GigRequestDetailUiState> = _state.asStateFlow()

    private val _events = Channel<GigRequestDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                // Seed calendar clash reads from the artist's confirmed bookings.
                runCatching { bookingsRepository.listForArtist() }
                    .onSuccess { calendarSync.ingest(it) }
                val found = requestsRepository.listForArtist()
                    .firstOrNull { it.id.equals(requestId, ignoreCase = true) }
                val clashes = found?.let { resolveClashes(it) }.orEmpty()
                _state.update {
                    it.copy(
                        request = found,
                        clashes = clashes,
                        isLoading = false,
                        loadError = if (found == null) "Request not found." else null,
                        counterAmount = found?.raw?.amount?.toString().orEmpty(),
                    )
                }
            } catch (e: RequestsRepositoryError) {
                _state.update { it.copy(isLoading = false, loadError = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.message) }
            }
        }
    }

    private fun resolveClashes(request: StoredRequest): List<CalendarSyncPlanner.Clash> {
        val epoch = parseGigDateLabel(request.raw.date) ?: return emptyList()
        return calendarSync.clashes(onDayOfEpochMs = epoch)
    }

    /** Best-effort parse of the display date label ("EEE, MMM d, yyyy"). */
    private fun parseGigDateLabel(label: String): Long? {
        if (label.isBlank()) return null
        val f = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
        f.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return runCatching { f.parse(label)?.time }.getOrNull()
    }

    fun showCounterSheet() {
        val amount = _state.value.request?.raw?.amount?.toString().orEmpty()
        _state.update { it.copy(showCounterSheet = true, counterAmount = amount, actionError = null) }
    }

    fun dismissCounterSheet() {
        _state.update { it.copy(showCounterSheet = false) }
    }

    fun setCounterAmount(value: String) {
        _state.update { it.copy(counterAmount = value.filter { ch -> ch.isDigit() }) }
    }

    fun accept() = mutate(GigRequestAction.Accept) { requestsRepository.accept(requestId) }

    fun decline() = mutate(GigRequestAction.Decline) { requestsRepository.decline(requestId) }

    fun sendCounter() {
        val amount = _state.value.counterAmount.toIntOrNull() ?: 0
        if (amount <= 0) {
            _state.update { it.copy(actionError = "Enter a counter amount above ₹0.") }
            return
        }
        mutate(GigRequestAction.Counter) {
            requestsRepository.counter(requestId, amount)
            _state.update { it.copy(showCounterSheet = false) }
        }
    }

    fun showActions(): Boolean =
        _state.value.request?.status == GigRequestStatus.Open

    private fun mutate(action: GigRequestAction, block: suspend () -> Unit) {
        if (_state.value.isActing) return
        viewModelScope.launch {
            _state.update { it.copy(actingAction = action, actionError = null) }
            try {
                block()
                refresh()
                _state.update { it.copy(actingAction = null) }
                if (action == GigRequestAction.Accept) {
                    _events.send(GigRequestDetailEvent.Accepted)
                }
            } catch (e: RequestsRepositoryError) {
                _state.update { it.copy(actingAction = null, actionError = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(actingAction = null, actionError = e.message ?: "Action failed.")
                }
            }
        }
    }
}
