package `in`.artistant.app.feature.gigs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedStartEpochMs
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.feature.artisthome.artistClientDisplayName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

data class ArtistGigListItem(
    val booking: Booking,
    val clientName: String,
    /**
     * The gig's calendar day, resolved ONCE here rather than per composition.
     *
     * Screen 36 indexes by month and reads by day, so every frame asks "which
     * day is this on" for every row; the old `dayOfMonthInMonth` call re-parsed
     * the label each time.
     *
     * Resolved through [calendarDayOf], which reads `start_datetime` first and
     * only then falls back to the labels. Reading `date_label` alone was a bug
     * with teeth: a row whose label is unreadable but whose instant is fine —
     * an ISO date in the column, a shape the label formatter cannot parse, a
     * client that wrote it differently — got null fields, vanished from every
     * month's busy days, and still counted toward `items.isNotEmpty()`. So the
     * gig was invisible on the calendar AND suppressed the "No gigs yet" empty
     * state: the artist saw a blank month with no explanation for it.
     *
     * All three are null together only when nothing on the row resolves at all.
     * Such a gig still lists; it simply cannot be placed on the grid, and there
     * is nothing left to place it by.
     */
    val year: Int? = null,
    val month: Int? = null,
    val dayOfMonth: Int? = null,
)

/**
 * [booking]'s calendar position, or null when nothing on the row resolves.
 *
 * IST, matching every other date derivation in this section (`bookedDates`,
 * `monthMoney`, the clash reader): a gig starting at 00:30 belongs to the night
 * it is part of, and the device's zone is not the authority on that.
 */
internal fun calendarDayOf(booking: Booking): Calendar? {
    val startMs = booking.resolvedStartEpochMs() ?: return null
    return Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
        timeInMillis = startMs
    }
}

data class ArtistGigsUiState(
    val items: List<ArtistGigListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArtistGigsViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistGigsUiState())
    val state: StateFlow<ArtistGigsUiState> = _state.asStateFlow()

    /**
     * Newest refresh wins, however the network resolves — the same guard
     * ArtistHomeViewModel carries over the same bookings list.
     *
     * Refreshes overlap here whenever the pull indicator isn't showing: the
     * `init` load plus a first pull (the screen only reports refreshing once it
     * has rows), and repeated pulls or the empty state's Retry from a failed
     * load. Without the stamp the request that finishes LAST wins even when it
     * is the older one, so a gig accepted between the two pulls drops off the
     * list and the grid un-shades its day. Cancelling alone is not enough: a
     * job past its last suspension point runs on to its write regardless, and a
     * cancelled job lands in `catch` (CancellationException is an Exception),
     * which would paint the failure line over data that loaded fine.
     */
    private var refreshJob: Job? = null
    private var generation = 0

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        val gen = ++generation
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val bookings = bookingsRepository.listForArtist()
                    .filter { it.status != BookingStatus.Cancelled }
                val items = bookings.map { b ->
                    val day = calendarDayOf(b)
                    ArtistGigListItem(
                        booking = b,
                        clientName = artistClientDisplayName(b),
                        year = day?.get(Calendar.YEAR),
                        month = day?.get(Calendar.MONTH),
                        dayOfMonth = day?.get(Calendar.DAY_OF_MONTH),
                    )
                }
                if (gen != generation) return@launch
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: BookingRepositoryError) {
                if (gen != generation) return@launch
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                if (gen != generation) return@launch
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
