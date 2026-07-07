package `in`.artistant.app.state

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.platform.payments.PaymentResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide booking state (port of iOS `BookingStore`). A `@Singleton` so the
 * booking funnel, the client's Bookings tab, and the artist's Gigs tab observe one
 * source. [draft] is the in-flight compose; [bookings] the loaded list. Server is
 * the source of truth — [refreshFromServer] replaces the list (no disk cache;
 * ponytail: add DataStore only if a cold-start blink matters — matches SavedStore).
 * Failures surface on [errors] as one-shot events for a snackbar.
 */
@Singleton
class BookingStore @Inject constructor(
    private val bookings: BookingsRepository,
    private val artists: ArtistsRepository,
) {
    private val _draft = MutableStateFlow<BookingDraft?>(null)
    val draft: StateFlow<BookingDraft?> = _draft.asStateFlow()

    private val _bookings = MutableStateFlow<List<Booking>>(emptyList())
    val bookingsFlow: StateFlow<List<Booking>> = _bookings.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Clears per-user state on sign-out so the next account sees no stale bookings. */
    fun reset() {
        _draft.value = null
        _bookings.value = emptyList()
    }

    /**
     * Begins a fresh draft for [artistId]. Defaults the package to the artist's
     * popular slot, the date to the first preferred weekday within 30 days (via
     * [preferredInitialDate]), and the time to the artist's first preferred slot
     * (else 8:30 PM) — the same seed logic as iOS `startDraft`.
     */
    fun startDraft(artistId: String) {
        val artist = artists.find(artistId)
        val popularIdx = artist?.packages?.indexOfFirst { it.popular }?.takeIf { it >= 0 } ?: 0
        val date = preferredInitialDate(artist?.daysAvailable, LocalDate.now(ZoneId.systemDefault()))
        val slot = artist?.timeSlots?.firstOrNull() ?: "8:30 PM"
        _draft.value = BookingDraft(
            artistId = artistId,
            packageIndex = popularIdx,
            date = dateLabel(date),
            dateRaw = date,
            time = slot,
            paymentMethod = PaymentMethod.Upi,
        )
    }

    /** Mutate the in-flight draft (no-op when there's no draft). */
    fun updateDraft(transform: (BookingDraft) -> BookingDraft) {
        _draft.update { it?.let(transform) }
    }

    /**
     * Persists the current draft via [BookingsRepository.create] using [paymentResult]
     * (mock in v1). Prepends the new row and returns it; null on failure (an error
     * event is emitted). The caller collected the payment result from PaymentsService.
     */
    suspend fun confirmDraftAsBooking(paymentResult: PaymentResult): Booking? {
        val d = _draft.value ?: return null
        return try {
            val booking = bookings.create(d, paymentResult)
            _bookings.update { listOf(booking) + it }
            _draft.value = null
            booking
        } catch (t: Throwable) {
            _errors.tryEmit(t.message ?: "Couldn't confirm the booking.")
            null
        }
    }

    /**
     * Cancels a booking: optimistic local status flip, then the server cancel (which
     * flips escrow service-side). On failure the error surfaces; the next
     * [refreshFromServer] reconciles to server truth.
     */
    suspend fun cancel(id: String) {
        _bookings.update { list ->
            list.map { if (it.id == id) it.copy(status = BookingStatus.Cancelled) else it }
        }
        try {
            val updated = bookings.cancel(id)
            _bookings.update { list -> list.map { if (it.id == id) updated else it } }
        } catch (t: Throwable) {
            _errors.tryEmit(t.message ?: "Couldn't cancel the booking.")
        }
    }

    /** Replaces the list with the client's server bookings (server is source of truth). */
    suspend fun refreshFromServer() {
        try {
            _bookings.value = bookings.listForClient()
        } catch (t: Throwable) {
            _errors.tryEmit(t.message ?: "Couldn't load bookings.")
        }
    }

    /** Current-list lookup by id (iOS `BookingStore.booking(id:)`). Null if absent. */
    fun booking(id: String): Booking? = _bookings.value.firstOrNull { it.id == id }

    companion object {
        // Load-bearing wire format for the display label (iOS `Booking.dateFormat`).
        private val DATE_LABEL =
            java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", java.util.Locale.US)

        /** The display-label formatter for a picked date (used by the funnel VMs). */
        fun dateLabel(date: LocalDate): String = date.format(DATE_LABEL)

        /**
         * The first date within the next 30 days whose weekday abbreviation is in
         * [daysAvailable], else 14 days out (iOS's fallback). Skips today (offset 0)
         * — same-day isn't the intended initial-draft UX. Pure + [today]-injected so
         * it's directly unit-testable. Port of iOS
         * `BookingStore.preferredInitialDate(for:calendar:now:)`. Weekday abbreviations
         * are English SHORT names ("Mon".."Sun") to match how `days_available` is
         * stored on the shared backend (same key as [Availability.availabilityKicker]).
         */
        fun preferredInitialDate(daysAvailable: List<String>?, today: LocalDate): LocalDate {
            val fallback = today.plusDays(14)
            if (daysAvailable.isNullOrEmpty()) return fallback
            for (offset in 1..30) {
                val candidate = today.plusDays(offset.toLong())
                val abbr = candidate.dayOfWeek.getDisplayName(
                    java.time.format.TextStyle.SHORT, java.util.Locale.US,
                )
                if (daysAvailable.contains(abbr)) return candidate
            }
            return fallback
        }
    }
}
