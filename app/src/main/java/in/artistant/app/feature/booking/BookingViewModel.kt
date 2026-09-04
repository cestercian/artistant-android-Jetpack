package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.repository.ArtistsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Cap on "Directions for the artist", matching the server column's bound.
 *
 * Lives beside [BookingViewModel.setVenueNotes], which enforces it; the booking
 * screen reads it only to render the live counter.
 */
const val VENUE_NOTES_MAX = 500

data class BookingUiState(
    val artist: Artist? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val packageIndex: Int = 0,
    /** The month the grid is SHOWING — not the month of the picked date. */
    val visibleYear: Int = 0,
    val visibleMonth: Int = 0,
    val monthDays: List<FunnelDay> = emptyList(),
    val selectableDays: Set<Int> = emptySet(),
    val selectedDateEpochMs: Long = 0L,
    val selectedDateLabel: String = "",
    val timeSlots: List<String> = DefaultTimeSlots,
    val selectedTime: String = "",
    val venue: String = "",
    val guests: Int = 100,
    val venueNotes: String = "",
) {
    /** "October 2026" — the calendar card's header. */
    val monthLabel: String get() = funnelMonthLabel(visibleYear, visibleMonth)

    /**
     * Which cell of the VISIBLE month is ringed, or null when the picked date is
     * in another month. See [dayOfMonthIfIn] — the selection is an instant, the
     * grid is a month, and stepping away must not leave a ring behind.
     */
    val selectedDay: Int?
        get() = dayOfMonthIfIn(selectedDateEpochMs, visibleYear, visibleMonth)

    /**
     * Stepping back stops at the current month. There is nothing to book in the
     * past, and a picker that walks into 2019 is a picker with no floor.
     */
    val canStepBack: Boolean get() = isAfterCurrentMonth(visibleYear, visibleMonth)

    /**
     * A request needs a day and a start time. Both are real blocks — the day
     * because the artist may have nothing open in the month on screen, the time
     * because `bookings.start_datetime` is composed from it.
     */
    val canContinue: Boolean
        get() = selectedDateLabel.isNotBlank() && selectedTime.isNotBlank()
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistsRepository: ArtistsRepository,
    private val draftStore: BookingDraftStore,
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _state = MutableStateFlow(BookingUiState())
    val state: StateFlow<BookingUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            val full = artistsRepository.ensureFull(artistId)
            if (full == null) {
                _state.update { it.copy(isLoading = false, loadError = "Artist not found.") }
                return@launch
            }
            val published = resolveTimeSlots(full.timeSlots)
            // The grid opens on the first month that has anything in it, so an
            // artist who is away until November opens on November rather than on
            // an empty October the host has to guess their way out of. The clock
            // is part of what "open" means: a day whose last slot has already
            // gone by is not bookable, and offering it is how a request for a
            // show that already ended gets filed. See [bookableTimeSlots].
            val opening = firstOpenMonth(
                daysAvailable = full.daysAvailable,
                timeSlots = published,
            )
            val firstDay = opening.selectableDays.minOrNull()
            val firstEpoch = firstDay?.let { funnelDayEpochMs(opening.year, opening.month, it) } ?: 0L
            val slots = if (firstEpoch > 0L) bookableTimeSlots(published, firstEpoch) else published
            val popularIdx = full.packages.indexOfFirst { it.popular }.takeIf { it >= 0 } ?: 0
            // Open on the tier the client tapped on the profile, if they came
            // that way. Without this the selection there was cosmetic: the route
            // carries only the artist id, so this screen re-derived its own
            // default and silently dropped the choice. The bounds check covers
            // the artist republishing between the two reads — a stale index must
            // never reach `packages[packageIndex]`.
            val handedOver = draftStore.pendingPackageIndex(artistId)
                ?.takeIf { it in full.packages.indices }
            _state.update {
                it.copy(
                    artist = full,
                    isLoading = false,
                    packageIndex = handedOver ?: popularIdx,
                    visibleYear = opening.year,
                    visibleMonth = opening.month,
                    monthDays = funnelMonthDays(opening.year, opening.month),
                    selectableDays = opening.selectableDays,
                    selectedDateEpochMs = firstEpoch,
                    selectedDateLabel = if (firstEpoch > 0L) {
                        BookingDateFormat.weekdayString(firstEpoch)
                    } else {
                        ""
                    },
                    timeSlots = slots,
                    selectedTime = defaultTimeFromSlots(slots),
                )
            }
        }
    }

    fun selectPackage(index: Int) {
        _state.update { it.copy(packageIndex = index) }
    }

    /**
     * Step the grid a month. The PICKED date does not move — a host checking
     * whether the artist is freer in November has not thereby cancelled the 12th
     * of October, and `selectedDay` simply stops matching until they step back.
     */
    fun stepMonth(delta: Int) {
        _state.update { s ->
            if (delta < 0 && !s.canStepBack) return@update s
            val (year, month) = steppedMonth(s.visibleYear, s.visibleMonth, delta)
            s.copy(
                visibleYear = year,
                visibleMonth = month,
                monthDays = funnelMonthDays(year, month),
                selectableDays = monthSelectableDays(
                    year = year,
                    month = month,
                    daysAvailable = s.artist?.daysAvailable.orEmpty(),
                    timeSlots = resolveTimeSlots(s.artist?.timeSlots.orEmpty()),
                ),
            )
        }
    }

    /**
     * Pick a day of the VISIBLE month. A day the artist has closed is refused
     * here as well as being drawn inert — the guard belongs to the model, not to
     * the one composable that happens to grey the cell.
     */
    fun selectDay(day: Int) {
        _state.update { s ->
            if (day !in s.selectableDays) return@update s
            val epoch = funnelDayEpochMs(s.visibleYear, s.visibleMonth, day)
            // The slot list is per-day, not per-screen: only today hides the
            // slots the clock has passed, so moving off today has to restore the
            // artist's whole list — and moving onto it has to trim it again.
            // Keeping the current pick when it survives the move is what stops a
            // date tap silently re-deciding the time.
            val slots = bookableTimeSlots(resolveTimeSlots(s.artist?.timeSlots.orEmpty()), epoch)
            s.copy(
                selectedDateEpochMs = epoch,
                selectedDateLabel = BookingDateFormat.weekdayString(epoch),
                timeSlots = slots,
                selectedTime = s.selectedTime.takeIf { it in slots } ?: defaultTimeFromSlots(slots),
            )
        }
    }

    fun selectTime(slot: String) {
        _state.update { it.copy(selectedTime = slot) }
    }

    fun setVenue(value: String) {
        _state.update { it.copy(venue = value) }
    }

    fun setGuests(value: Int) {
        _state.update { it.copy(guests = value.coerceIn(10, 5000)) }
    }

    /**
     * Bounded HERE rather than at the text field, so the invariant belongs to
     * the draft instead of to one caller: the field's `take()` held only for the
     * composable that happened to apply it, and anything else writing notes — a
     * restore, a paste handler, a test — put an unbounded string into the draft
     * and on to the server column.
     */
    fun setVenueNotes(value: String) {
        _state.update { it.copy(venueNotes = value.take(VENUE_NOTES_MAX)) }
    }

    /** Snapshot draft into [BookingDraftStore] for Checkout. */
    fun onContinue(): Boolean {
        val s = _state.value
        val artist = s.artist ?: return false
        val pkg = artist.packages.getOrNull(s.packageIndex)
        val fee = pkg?.price ?: artist.price
        val draft = BookingDraft(
            artistId = artistId,
            packageIndex = s.packageIndex,
            packageName = pkg?.name ?: "Custom",
            packageDuration = pkg?.duration ?: artist.duration,
            feeInr = fee,
            date = s.selectedDateLabel,
            dateRawEpochMs = s.selectedDateEpochMs,
            time = s.selectedTime,
            venue = s.venue,
            guests = s.guests,
            venueNotes = s.venueNotes,
        )
        draftStore.setDraft(draft)
        return true
    }
}
