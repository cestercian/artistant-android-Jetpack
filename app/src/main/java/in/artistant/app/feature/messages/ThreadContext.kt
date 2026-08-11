package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.model.resolvedStartEpochMs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What a conversation is *about* — the gig behind it.
 *
 * This is the single highest-value thing the inbox was missing. Without it a row
 * is a contact list entry: a name and a snippet, with no way to tell an agreed
 * booking next Saturday from a cold inquiry that went nowhere. With it, the inbox
 * reads as a pipeline — every row says which gig, what state it is in, and whether
 * the reader is the one holding it up.
 *
 * Resolved from the [Booking] the thread points at, so nothing here is invented:
 * a thread with no `booking_id` genuinely has no gig and says so, and a thread
 * whose booking hasn't loaded degrades to the same honest shape rather than
 * guessing a status. The same object feeds three surfaces (inbox row, chat
 * context strip, details sheet) so they can never disagree about a gig.
 *
 * Pure and free of Android/Compose so the derivation is unit-testable.
 */
data class ThreadContext(
    val bookingId: String? = null,
    val status: BookingStatus? = null,
    /** The stored day label, e.g. "Sat, May 16, 2026". Null for an inquiry. */
    val dateLabel: String? = null,
    val venue: String? = null,
    /** Artist fee in whole rupees — the only money figure the matchmaker model shows. */
    val fee: Int? = null,
    /** Gig start, for the "2 weeks out" caption. Null when it can't be parsed. */
    val startEpochMs: Long? = null,
    /**
     * True when the NEXT move is the viewer's.
     *
     * Deliberately seat-aware rather than status-only. `pending_confirm` means the
     * client asked and the artist decides, so it is an action for the artist seat
     * and a wait for the client seat. Painting an accelerator on the client's row
     * would offer them a button for a decision that is not theirs to make — the
     * same class of bug as showing a viewer their own name as the counterparty.
     */
    val awaitingViewer: Boolean = false,
) {
    /** The state, in the same words the bookings surfaces use. */
    val statusLabel: String get() = status?.label ?: INQUIRY_LABEL

    /** Whole-row a11y sentence: "Awaiting confirm · Sat, May 16, 2026 · Rooftop". */
    val summary: String
        get() = listOfNotNull(statusLabel, dateLabel, venue).joinToString(SEPARATOR)

    /** Date · venue, dropping whichever half is missing. Null when neither exists. */
    val detailLine: String?
        get() = listOfNotNull(dateLabel, venue).takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)

    companion object {
        const val INQUIRY_LABEL = "Inquiry"
        const val SEPARATOR = " · "

        /** A thread with no booking behind it — the honest "nothing agreed yet" shape. */
        val INQUIRY = ThreadContext()

        /**
         * `create()` writes "TBD" when the client leaves the venue blank, so the
         * string is present but carries no information. Rendering it would put a
         * placeholder in the one line that is supposed to say where the gig is.
         */
        private const val VENUE_PLACEHOLDER = "TBD"

        /**
         * Resolve one thread's gig facts from an id-keyed booking index.
         *
         * [bookings] is whatever the caller could legitimately read: the client
         * seat's own bookings, the artist seat's own gigs, or a single fetched row
         * in the chat. A miss is not an error — RLS, a stale cache, or a booking
         * outside the loaded window all land here — so a miss keeps the booking id
         * (the row can still deep-link) and leaves the status unresolved.
         */
        fun resolve(
            thread: Thread,
            bookings: Map<String, Booking>,
            viewerIsArtist: Boolean,
        ): ThreadContext {
            val bookingId = thread.bookingId ?: return INQUIRY
            val booking = bookings[bookingId] ?: bookings[bookingId.lowercase()]
                ?: return ThreadContext(bookingId = bookingId)
            return from(booking, viewerIsArtist)
        }

        /** Same derivation from a booking already in hand (the chat's single fetch). */
        fun from(booking: Booking, viewerIsArtist: Boolean) = ThreadContext(
            bookingId = booking.id,
            status = booking.status,
            dateLabel = booking.date.takeIf { it.isNotBlank() },
            venue = booking.venue
                .takeIf { it.isNotBlank() && !it.equals(VENUE_PLACEHOLDER, ignoreCase = true) },
            fee = booking.fee.takeIf { it > 0 },
            startEpochMs = booking.resolvedStartEpochMs(),
            awaitingViewer = viewerIsArtist && booking.status == BookingStatus.PendingConfirm,
        )

        /**
         * "today" / "tomorrow" / "9 days out" / "3 weeks out" — how far off the gig is.
         *
         * Null for anything already past: "3 weeks out" under a gig that happened
         * last month is worse than saying nothing. Day granularity (not elapsed
         * hours) so "tomorrow" doesn't flip to "today" at midnight-minus-one.
         */
        fun timeUntil(
            startEpochMs: Long?,
            nowMs: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): String? {
            val start = startEpochMs ?: return null
            val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val days = ChronoUnit.DAYS.between(today, startDay)
            return when {
                days < 0L -> null
                days == 0L -> "today"
                days == 1L -> "tomorrow"
                days < 14L -> "$days days out"
                else -> "${days / 7} weeks out"
            }
        }
    }
}
