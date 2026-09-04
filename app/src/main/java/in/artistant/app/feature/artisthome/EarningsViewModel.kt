package `in`.artistant.app.feature.artisthome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.repository.BookingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen 133 — agreed fees, one window at a time.
 *
 * The whole screen is a projection of ONE bookings list, so switching windows is
 * a local re-derivation rather than a round trip: the artist flicking between
 * "30 days" and "This year" should not see a spinner, and the server has no
 * per-window endpoint to ask anyway.
 */
data class EarningsUiState(
    val window: EarningsWindow = EarningsWindow.ThisYear,
    val summary: EarningsSummary = EMPTY,
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    private companion object {
        val EMPTY = EarningsSummary(
            totalInr = 0,
            gigCount = 0,
            deltaPercent = null,
            deltaUp = true,
            bars = emptyList(),
            rows = emptyList(),
        )
    }
}

@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EarningsUiState())
    val state: StateFlow<EarningsUiState> = _state.asStateFlow()

    /** The list every window projects from. */
    private var bookings: List<Booking> = emptyList()

    /**
     * Newest refresh wins — the same generation stamp the dashboard next door
     * carries over the same list, and for the same reason: a pull and a Retry
     * overlap routinely, and cancelling alone does not order the WRITES (a job
     * past its last suspension point runs on to its state update regardless).
     */
    private var refreshJob: Job? = null
    private var generation = 0

    init {
        refresh()
    }

    fun setWindow(window: EarningsWindow) {
        _state.update { it.copy(window = window, summary = earningsSummary(bookings, window)) }
    }

    fun refresh() {
        refreshJob?.cancel()
        val gen = ++generation
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val loaded = bookingsRepository.listForArtist()
                if (gen != generation) return@launch
                bookings = loaded
                _state.update {
                    it.copy(
                        summary = earningsSummary(loaded, it.window),
                        isLoading = false,
                        hasLoaded = true,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                if (gen != generation) return@launch
                // A failed read leaves the previous figures alone and says so
                // above them. Zeroing an earnings screen on a dropped connection
                // tells an artist they earned nothing, which is the one wrong
                // answer this screen can give.
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't load your earnings.")
                }
            }
        }
    }
}
