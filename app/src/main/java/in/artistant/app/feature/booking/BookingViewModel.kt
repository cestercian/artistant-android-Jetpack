package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.fee
import `in`.artistant.app.state.BookingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives `BookingScreen` (port of iOS `BookingView`). The in-flight [draft] lives
 * in the shared [BookingStore] singleton (so Checkout sees the same one); this VM
 * owns the artist hydrate + the field mutators. [artist] is fetched-on-miss so a
 * deep link straight into Book still resolves packages/availability.
 */
@HiltViewModel
class BookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val store: BookingStore,
    private val artists: ArtistsRepository,
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>("artistId").orEmpty()

    private val _artist = MutableStateFlow(artists.find(artistId))
    val artist: StateFlow<Artist?> = _artist.asStateFlow()

    val draft = store.draft

    init {
        viewModelScope.launch {
            // Hydrate before startDraft so the package/time seed reads real data.
            artists.ensureFull(artistId)?.let { _artist.value = it }
            if (store.draft.value?.artistId != artistId) store.startDraft(artistId)
        }
    }

    /** Preferred time slots for this artist, else the standard menu (iOS fallback). */
    fun timeSlots(): List<String> =
        _artist.value?.timeSlots?.takeIf { it.isNotEmpty() } ?: DEFAULT_TIME_SLOTS

    /** Artist's quoted fee for the current draft (0 until the artist resolves). */
    fun draftFee(): Int = store.draft.value?.fee(artists) ?: 0

    fun setPackage(index: Int) = store.updateDraft { it.copy(packageIndex = index) }
    fun setDate(date: LocalDate) =
        store.updateDraft { it.copy(dateRaw = date, date = BookingStore.dateLabel(date)) }
    fun setTime(time: String) = store.updateDraft { it.copy(time = time) }
    fun setVenue(venue: String) = store.updateDraft { it.copy(venue = venue) }
    fun setGuests(guests: Int) = store.updateDraft { it.copy(guests = guests.coerceIn(10, 5000)) }

    companion object {
        // The standard slot menu shown when the artist hasn't picked any (the
        // truthful "we don't know, here's the usual set" state — iOS uses the
        // wizard's full allTimeSlots list).
        private val DEFAULT_TIME_SLOTS = listOf(
            "6:00 PM", "7:30 PM", "8:30 PM", "9:00 PM", "10:00 PM", "11:00 PM",
        )
    }
}
