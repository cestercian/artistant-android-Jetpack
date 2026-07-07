package `in`.artistant.app.feature.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.state.BookingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Drives the client `BookingsScreen` (port of iOS `BookingsView`). Refreshes the
 * shared [BookingStore] on init + pull-to-refresh, and exposes the bookings grouped
 * by day for the month calendar. Artist rows are hydrated by id so a calendar event
 * can show the artist's name rather than the venue.
 */
@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val store: BookingStore,
    private val artists: ArtistsRepository,
) : ViewModel() {

    val bookings: StateFlow<List<Booking>> = store.bookingsFlow

    // A last-refresh error surfaces a banner (kept honest — a silent failure would
    // read as "no bookings"). Set/cleared around refresh.
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        // Surface store failures on the banner. The store swallows load/cancel
        // errors into its `errors` SharedFlow (it never throws), so without this
        // collector a failed load renders an empty calendar — reads as "no
        // bookings". refresh() clears _refreshError before each attempt, so a
        // successful load leaves it null; a failure sets it here (iOS parity:
        // BookingStore.lastRefreshError → the BookingsView banner).
        viewModelScope.launch { store.errors.collect { _refreshError.value = it } }
        refresh()
    }

    /** Dismiss the error banner without retrying. */
    fun dismissError() { _refreshError.value = null }

    fun refresh() {
        viewModelScope.launch {
            _refreshError.value = null
            _refreshing.value = true
            store.refreshFromServer()
            // Hydrate the booking artists so event titles resolve a name.
            artists.fetchArtists(store.bookingsFlow.value.map { it.artistId })
            _refreshing.value = false
        }
    }

    fun artist(id: String): Artist? = artists.find(id)

    /**
     * The current bookings grouped by their day-anchored date. `Booking.dateLabel`
     * is the display string ("EEE, MMM d, yyyy"); unparseable rows are dropped.
     * Pure over the current list so it's directly unit-testable.
     */
    fun bookingsByDay(): Map<LocalDate, List<Booking>> {
        val out = linkedMapOf<LocalDate, MutableList<Booking>>()
        for (b in store.bookingsFlow.value) {
            val day = parseDay(b.dateLabel) ?: continue
            out.getOrPut(day) { mutableListOf() }.add(b)
        }
        return out
    }

    companion object {
        private val LABEL = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

        /** Parse a booking's display date label back to a day; null when it doesn't match. */
        fun parseDay(label: String): LocalDate? =
            try {
                LocalDate.parse(label, LABEL)
            } catch (_: Exception) {
                null
            }
    }
}
