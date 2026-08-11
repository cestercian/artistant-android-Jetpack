package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingDetailUiState(
    val booking: Booking? = null,
    val artistName: String = "",
    /**
     * The hydrated artist behind this booking, when we could get one.
     *
     * Carried whole rather than as three flattened fields because the screen
     * needs the cover, the city AND the package list, and every one of them is
     * optional: a booking whose artist can't be fetched still renders — with a
     * gradient hero, a bare venue address and no package row — rather than
     * showing a spinner or an error over data we already have.
     */
    val artist: Artist? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val isActing: Boolean = false,
    val actionError: String? = null,
)

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingsRepository: BookingsRepository,
    private val artistsRepository: ArtistsRepository,
    /** Handed to [ReviewSheet] so the detail screen doesn't need a second VM. */
    val reviewsRepository: ReviewsRepository,
) : ViewModel() {

    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"])

    private val _state = MutableStateFlow(BookingDetailUiState())
    val state: StateFlow<BookingDetailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            val booking = runCatching { bookingsRepository.fetchOne(bookingId) }.getOrNull()
            if (booking == null) {
                _state.update { it.copy(isLoading = false, loadError = "Booking not found.") }
                return@launch
            }
            // Publish the booking BEFORE the artist round-trip. The page's whole
            // spine — status, terms, fee, the action set — comes off the booking
            // alone, so it should not sit behind a network call that only
            // decorates it. The artist arrives a moment later and fills in the
            // cover, the city and the package name.
            _state.update { it.copy(booking = booking, isLoading = false) }

            // `find` is cache-only and misses whenever the client arrived from a
            // push or a cold launch rather than from the artist's profile — which
            // is exactly when a booking detail is opened. `ensureFull` fetches on
            // a miss and swallows transport errors as null, so a failure degrades
            // the decoration instead of failing the screen.
            val artist = artistsRepository.find(booking.artistId)
                ?.takeIf { it.packages.isNotEmpty() }
                ?: artistsRepository.ensureFull(booking.artistId)
                ?: artistsRepository.find(booking.artistId)
            _state.update { it.copy(artist = artist, artistName = artist?.name.orEmpty()) }
        }
    }

    fun acceptRequest() = mutateRequest { bookingsRepository.accept(bookingId) }

    fun declineRequest(reason: String? = null) =
        mutateRequest { bookingsRepository.declineByArtist(bookingId, reason) }

    /**
     * Withdraw the booking, stamped with the viewer's own side.
     *
     * The route is not a detail: `cancel()` reaches the Edge Function as
     * `cancelled_by = "client"` and `declineByArtist()` as `"artist"`, the stamp
     * drives whose cancellation-rate metric moves, and mig 0083 rejects a client
     * trying to claim the artist's. Defaulted so the pre-existing call sites
     * (and their tests) keep compiling as the client path they always were.
     */
    fun cancelBooking(viewer: BookingViewer = BookingViewer.Client, reason: String? = null) =
        when (cancelActor(viewer)) {
            CancelActor.Artist -> mutateRequest {
                bookingsRepository.declineByArtist(bookingId, reason)
            }
            CancelActor.Client -> mutateRequest {
                bookingsRepository.cancel(bookingId, reason)
            }
        }

    fun reportActionError(message: String) =
        _state.update { it.copy(actionError = message) }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }

    private fun mutateRequest(block: suspend () -> Booking) {
        if (_state.value.isActing) return
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, actionError = null) }
            try {
                val updated = block()
                _state.update { it.copy(booking = updated, isActing = false) }
            } catch (e: BookingRepositoryError) {
                _state.update { it.copy(isActing = false, actionError = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isActing = false, actionError = e.message ?: "Action failed.")
                }
            }
        }
    }

    /** Display name flips by viewer side — artist sees client, client sees artist. */
    fun counterpartyName(isArtistViewer: Boolean): String {
        val s = _state.value
        return if (isArtistViewer) {
            // The denormalized `client_name` column (mig 0080), never a `users`
            // embed: RLS nulls the embed for the counterparty, which is what used
            // to render every client as "TBD".
            s.booking?.clientFullName?.takeIf { it.isNotBlank() } ?: "Client"
        } else {
            s.artistName.ifBlank { "Artist" }
        }
    }

    /**
     * The booked tier's name, or null when there is nothing honest to show.
     *
     * Prefers the server's own snapshot (`bookings.package_name`, stamped at
     * insert since migration 0001) because that is the tier as it stood WHEN THE
     * GIG WAS BOOKED. Resolving the index into the artist's current package list
     * — which is all this screen used to do — meant an artist reordering their
     * tiers relabelled every past booking: a client who booked "Evening set"
     * would open a months-old gig and read "Acoustic hour".
     *
     * The index survives only as a fallback for a booking whose snapshot didn't
     * come back — a projection that omitted the column, or a blank value. It
     * cannot be a null column: `package_name` is `not null` on the server and
     * both clients write it on every create, so no stored row is missing it. The
     * fallback is bounds-checked so a dangling index drops the row rather than
     * naming the wrong tier.
     */
    fun packageName(): String? {
        val s = _state.value
        val booking = s.booking ?: return null
        booking.packageName?.takeIf { it.isNotBlank() }?.let { return it }
        return s.artist?.packages?.getOrNull(booking.packageIndex)?.name?.takeIf { it.isNotBlank() }
    }

    fun showAcceptDecline(isArtistViewer: Boolean): Boolean =
        BookingAction.Accept in actions(viewerOf(isArtistViewer))

    fun showClientCancel(isArtistViewer: Boolean): Boolean =
        !isArtistViewer &&
            _state.value.booking?.status == BookingStatus.PendingConfirm

    /** The whole action set for this viewer at the booking's current status. */
    fun actions(viewer: BookingViewer): Set<BookingAction> {
        val status = _state.value.booking?.status ?: return emptySet()
        return BookingActions.forViewer(viewer, status)
    }

    fun primaryActions(viewer: BookingViewer): List<BookingAction> {
        val status = _state.value.booking?.status ?: return emptyList()
        return BookingActions.primary(viewer, status)
    }

    fun manageActions(viewer: BookingViewer): List<BookingAction> {
        val status = _state.value.booking?.status ?: return emptyList()
        return BookingActions.manage(viewer, status)
    }
}

/** Nav passes a boolean; everything below this line thinks in sides. */
fun viewerOf(isArtistViewer: Boolean): BookingViewer =
    if (isArtistViewer) BookingViewer.Artist else BookingViewer.Client
