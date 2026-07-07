package `in`.artistant.app.feature.bookings

import androidx.lifecycle.SavedStateHandle
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
import javax.inject.Inject

/**
 * Drives the shared `BookingDetailScreen` (port of iOS `BookingDetailView`).
 * The booking comes from the shared [BookingStore] (client deep-links); the
 * artist is hydrated by id since this screen is reached from the list, not the
 * profile. Cancel routes through the store (optimistic flip + server cancel).
 */
@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: BookingStore,
    private val artists: ArtistsRepository,
) : ViewModel() {

    val bookingId: String = savedStateHandle.get<String>("bookingId").orEmpty()

    /** Observe so a cancel/refresh re-renders the detail. */
    val bookings: StateFlow<List<Booking>> = store.bookingsFlow

    // A failed cancel used to be silent (store.cancel swallows the error into its
    // `errors` SharedFlow) — the optimistic flip would then snap back on the next
    // refresh with no explanation. Surface it on a banner instead.
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { store.errors.collect { _error.value = it } }
        // Hydrate the booking's artist by id (reached from the list, may be uncached).
        viewModelScope.launch {
            store.booking(bookingId)?.artistId?.let { artists.ensureFull(it) }
        }
    }

    fun dismissError() { _error.value = null }

    fun booking(): Booking? = store.booking(bookingId)
    fun artist(id: String): Artist? = artists.find(id)

    fun cancel() {
        _error.value = null
        viewModelScope.launch { store.cancel(bookingId) }
    }
}
