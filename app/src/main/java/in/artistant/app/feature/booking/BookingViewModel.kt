package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
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
    val dateChips: List<DateChip> = emptyList(),
    val selectedDateEpochMs: Long = 0L,
    val selectedDateLabel: String = "",
    val timeSlots: List<String> = DefaultTimeSlots,
    val selectedTime: String = "",
    val venue: String = "",
    val guests: Int = 100,
    val venueNotes: String = "",
    val canContinue: Boolean = false,
)

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
            // The strip starts at today, so the clock has to be part of what
            // "available" means: a day whose last slot has already gone by is not
            // bookable, and offering it is how a request for a show that already
            // ended gets filed. See [bookableTimeSlots].
            val chips = upcomingDateChips(daysAvailable = full.daysAvailable, timeSlots = published)
            val firstAvailable = chips.firstOrNull { it.available } ?: chips.first()
            val slots = bookableTimeSlots(published, firstAvailable.epochMs)
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
                    dateChips = chips,
                    selectedDateEpochMs = firstAvailable.epochMs,
                    selectedDateLabel = firstAvailable.label,
                    timeSlots = slots,
                    selectedTime = defaultTimeFromSlots(slots),
                    canContinue = true,
                )
            }
        }
    }

    fun selectPackage(index: Int) {
        _state.update { it.copy(packageIndex = index) }
    }

    fun selectDate(chip: DateChip) {
        if (!chip.available) return
        _state.update { s ->
            // The grid is per-day, not per-screen: only today hides the slots the
            // clock has passed, so moving off today has to restore the artist's
            // whole list — and moving onto it has to trim it again. Keeping the
            // current pick when it survives the move is what stops a date tap
            // silently re-deciding the time.
            val slots = bookableTimeSlots(resolveTimeSlots(s.artist?.timeSlots.orEmpty()), chip.epochMs)
            s.copy(
                selectedDateEpochMs = chip.epochMs,
                selectedDateLabel = chip.label,
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
