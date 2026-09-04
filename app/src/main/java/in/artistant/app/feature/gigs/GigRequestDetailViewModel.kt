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

/**
 * The mutations THIS screen performs on an open request.
 *
 * Two, not three: the dock still offers a third answer, but Counter is now a
 * navigation to design screen 61 (`CounterOfferScreen`), which owns the amount
 * field and the write. Leaving a `Counter` case here would name an action this
 * ViewModel cannot take.
 */
enum class GigRequestAction { Accept, Decline }

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

/**
 * How the last attempt to load the request ended.
 *
 * Three terminal cases, not two. [NotFound] and [Failed] both leave
 * [GigRequestDetailUiState.request] null, and collapsing them is what made the
 * screen tell an artist whose network had dropped that their request had
 * "expired or been withdrawn by the client" — a false, unrecoverable-sounding
 * statement about someone else's action, with no way to retry.
 */
enum class GigRequestLoad { Loading, Loaded, NotFound, Failed }

/**
 * Which of the three a completed read was.
 *
 * An error ALWAYS wins over an absent row, because when the read threw we never
 * learned whether the row exists — [found] is null because nothing came back,
 * not because the server said there is nothing. Only a read that succeeded and
 * returned no match is genuinely [GigRequestLoad.NotFound].
 */
internal fun gigRequestLoad(found: StoredRequest?, error: Throwable?): GigRequestLoad = when {
    error != null -> GigRequestLoad.Failed
    found != null -> GigRequestLoad.Loaded
    else -> GigRequestLoad.NotFound
}

data class GigRequestDetailUiState(
    val request: StoredRequest? = null,
    val clashes: List<CalendarSyncPlanner.Clash> = emptyList(),
    val load: GigRequestLoad = GigRequestLoad.Loading,
    /** The failure's message — only ever set alongside [GigRequestLoad.Failed]. */
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
) {
    /** Any mutation in flight — every dock control is disabled while one is. */
    val isActing: Boolean get() = actingAction != null

    /** Derived so every existing call site keeps reading one flag. */
    val isLoading: Boolean get() = load == GigRequestLoad.Loading
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
            _state.update { it.copy(load = GigRequestLoad.Loading, loadError = null) }
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
                        load = gigRequestLoad(found, error = null),
                        loadError = null,
                    )
                }
            } catch (e: RequestsRepositoryError) {
                failLoad(e)
            } catch (e: Exception) {
                failLoad(e)
            }
        }
    }

    /**
     * A read that threw leaves [GigRequestDetailUiState.request] alone.
     *
     * Nulling it would blank a request the artist is reading because a
     * background refresh dropped — the same rule the studio's dashboard follows
     * next door. The failure shows as a banner over whatever is already there,
     * and as a retryable failure screen when there is nothing.
     */
    private fun failLoad(e: Throwable) {
        _state.update {
            it.copy(
                load = gigRequestLoad(found = it.request, error = e),
                loadError = e.message ?: "Couldn't load this request.",
            )
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

    fun accept() = mutate(GigRequestAction.Accept) { requestsRepository.accept(requestId) }

    fun decline() = mutate(GigRequestAction.Decline) { requestsRepository.decline(requestId) }

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
