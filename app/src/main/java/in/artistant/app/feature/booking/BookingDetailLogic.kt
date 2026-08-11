package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus

/**
 * Every decision the booking-detail screen makes, as plain Kotlin.
 *
 * It lives apart from the screen because Composables are not unit-testable on
 * the JVM, and the decisions here are exactly the ones that must not be got
 * wrong: which side of a booking a viewer is on, which buttons that side is
 * allowed to see for a given status, and which `cancelled_by` stamp their
 * cancellation carries. A wrong CTA here is not a cosmetic bug — it is either a
 * request the server rejects (mig 0083 lets only the artist confirm) or a
 * cancellation blamed on the wrong party.
 */

/**
 * WHICH SIDE of this booking the viewer is on — not what their account role is.
 *
 * The distinction is the whole point. An artist who books a collaborator is the
 * CLIENT of that booking, and keying the UI on their account role showed them
 * the artist's page: Accept/Decline buttons that 403 against the consent guard,
 * their own name as the counterparty, and no way to cancel. The nav graph
 * decides this per destination (the client stack passes Client, the artist stack
 * Artist), so the screen never has to guess.
 */
enum class BookingViewer { Client, Artist }

/** Everything either side can do from booking detail. */
enum class BookingAction {
    /** Artist-only, pending: status-only PATCH to `confirmed`. */
    Accept,

    /** Artist-only, pending: routes through the `cancel-booking` Edge Function. */
    Decline,

    /** Open (or create) the thread with the counterparty. */
    Message,

    /** Withdraw the booking. The `cancelled_by` stamp follows [cancelActor]. */
    Cancel,

    /** Hand the gig to the system calendar (zero-permission ACTION_INSERT). */
    AddToCalendar,

    /** Client-only, completed. Artists do not review themselves. */
    LeaveReview,

    /** Plain-text gig summary out through the system share sheet. */
    Share,

    /** `geo:` intent at the venue text. */
    OpenMaps,

    /** Venue address to the clipboard. */
    CopyAddress,
}

/**
 * Can this status still be acted on at all?
 *
 * [BookingStatus.Unknown] is the decode fallback for a status the SERVER knows
 * and this build does not, so it deliberately answers false: cancelling out of a
 * state we cannot reason about would PATCH a transition the server may well
 * reject, and offering an Accept would be worse — that case used to decode as
 * `PendingConfirm`, which is the one status that renders the artist's CTAs.
 * Terminal states (completed / cancelled / disputed) answer false because
 * there is nothing left to withdraw from.
 */
fun BookingStatus.isActionable(): Boolean =
    this == BookingStatus.PendingConfirm || this == BookingStatus.Confirmed

/**
 * The role × status action matrix — the single source for what a viewer is
 * offered, so the dock, the manage list and the tests can never disagree.
 *
 * Reading the table:
 *
 * | viewer | status          | actions                                                     |
 * |--------|-----------------|-------------------------------------------------------------|
 * | Client | pending_confirm | Message, Cancel                                             |
 * | Client | confirmed       | Message, Share, AddToCalendar, OpenMaps, CopyAddress, Cancel |
 * | Client | completed       | Message, LeaveReview                                        |
 * | Client | cancelled       | Message                                                     |
 * | Client | disputed        | Message                                                     |
 * | Client | UNKNOWN         | Message                                                     |
 * | Artist | pending_confirm | Accept, Decline           (Message is REPLACED — see below) |
 * | Artist | confirmed       | Message, Share, AddToCalendar, OpenMaps, CopyAddress, Cancel |
 * | Artist | completed       | Message                                                     |
 * | Artist | cancelled       | Message                                                     |
 * | Artist | disputed        | Message                                                     |
 * | Artist | UNKNOWN         | Message                                                     |
 *
 * Three rules are load-bearing rather than aesthetic:
 *
 *  - **Artist + pending has no Message.** Not a layout choice: no thread exists
 *    until the booking confirms, and `findOrCreateThread` refuses artist-side
 *    creation on a pending booking. Offering it would be a button that always
 *    errors.
 *  - **Only the client reviews.** An artist reviewing their own gig is not a
 *    thing the reviews table models.
 *  - **UNKNOWN is read-only**, per [isActionable].
 */
object BookingActions {

    fun forViewer(viewer: BookingViewer, status: BookingStatus): Set<BookingAction> =
        (primary(viewer, status) + manage(viewer, status)).toSet()

    /**
     * What the pinned dock carries. Accept + Decline REPLACE Message for an
     * artist looking at a pending request — a request's affordance is answering
     * it, and stacking three buttons buries the two that matter.
     */
    fun primary(viewer: BookingViewer, status: BookingStatus): List<BookingAction> =
        if (viewer == BookingViewer.Artist && status == BookingStatus.PendingConfirm) {
            listOf(BookingAction.Accept, BookingAction.Decline)
        } else {
            listOf(BookingAction.Message)
        }

    /**
     * The in-page action list, in render order.
     *
     * A client's pending Cancel lands here too (it is the only entry that state
     * has), which is why this is not simply "the confirmed-only list".
     */
    fun manage(viewer: BookingViewer, status: BookingStatus): List<BookingAction> = when {
        status == BookingStatus.Confirmed -> listOf(
            BookingAction.OpenMaps,
            BookingAction.CopyAddress,
            BookingAction.Share,
            BookingAction.AddToCalendar,
            BookingAction.Cancel,
        )
        // The client can still withdraw a request the artist hasn't answered.
        // The artist's equivalent at this status is Decline, in the dock above.
        status == BookingStatus.PendingConfirm && viewer == BookingViewer.Client ->
            listOf(BookingAction.Cancel)
        status == BookingStatus.Completed && viewer == BookingViewer.Client ->
            listOf(BookingAction.LeaveReview)
        else -> emptyList()
    }

    /**
     * The half of [manage] that renders as in-page rows.
     *
     * Only a confirmed booking has enough secondary actions to earn a list. The
     * lone entries the other states carry (a client's pending Cancel, a
     * completed booking's review) would otherwise be a one-item "Manage" section
     * floating above a dock — so they go to [dockSecondary] instead.
     */
    fun manageRows(viewer: BookingViewer, status: BookingStatus): List<BookingAction> =
        if (status == BookingStatus.Confirmed) manage(viewer, status) else emptyList()

    /**
     * The half of [manage] pinned in the dock under the primary.
     *
     * Defined as the complement of [manageRows] rather than as its own list, so
     * the two are a genuine PARTITION of [manage]: every secondary action is
     * rendered exactly once, and no state can grow two Cancel buttons (the
     * client's pending row did exactly that before this split existed).
     */
    fun dockSecondary(viewer: BookingViewer, status: BookingStatus): List<BookingAction> {
        val rows = manageRows(viewer, status).toSet()
        return manage(viewer, status).filterNot { it in rows }
    }

    /** Do the travel/venue rows have anything to say yet? */
    fun showsGettingThere(status: BookingStatus): Boolean = status == BookingStatus.Confirmed
}

/**
 * Who the server is told cancelled.
 *
 * `cancel-booking` takes `cancelled_by`, and the value is not cosmetic: an
 * artist cancellation moves the artist's cancellation-rate metric and refunds
 * the client, and mig 0083 rejects a client trying to stamp `artist`. So the
 * stamp follows the SIDE of the row, exactly like every other fork on this
 * screen. Repository-wise `Client` maps to `cancel()` and `Artist` to
 * `declineByArtist()` — the two entry points that carry those two stamps.
 */
enum class CancelActor(val dbValue: String) { Client("client"), Artist("artist") }

fun cancelActor(viewer: BookingViewer): CancelActor =
    if (viewer == BookingViewer.Artist) CancelActor.Artist else CancelActor.Client

/**
 * The reason offered before a cancellation, per side.
 *
 * Both lists persist into `bookings.cancel_reason` (free text server-side), so
 * these are copy rather than schema. They differ because the question does: a
 * client picking "Artist unresponsive" is meaningful, an artist picking it about
 * themselves is not.
 */
enum class CancelReason(val label: String) {
    DateChanged("Date changed"),
    FoundAnother("Found another artist"),
    EventCancelled("Event cancelled"),
    ArtistUnresponsive("Artist unresponsive"),
    BudgetChanged("Budget changed"),
    DateConflict("Double-booked that date"),
    TravelNotPossible("Can't travel to the venue"),
    NotAFit("Not the right fit for this event"),
    ClientUnresponsive("Client unresponsive"),
    Other("Other"),
}

fun cancelReasons(viewer: BookingViewer): List<CancelReason> =
    if (viewer == BookingViewer.Artist) {
        listOf(
            CancelReason.DateConflict,
            CancelReason.TravelNotPossible,
            CancelReason.NotAFit,
            CancelReason.ClientUnresponsive,
            CancelReason.Other,
        )
    } else {
        listOf(
            CancelReason.DateChanged,
            CancelReason.FoundAnother,
            CancelReason.EventCancelled,
            CancelReason.ArtistUnresponsive,
            CancelReason.BudgetChanged,
            CancelReason.Other,
        )
    }

/**
 * What actually happens on cancel, read before the destructive tap.
 *
 * Every line is true in THIS codebase, which is the constraint that shaped it: a
 * cancelled booking drops out of the artist's confirmed-upcoming list (freeing
 * the date), and the thread is never torn down. No push fires on cancel — the
 * only booking push trigger is on confirm — so none is promised. Nothing about
 * refunds either: v1 moves no money.
 */
fun cancelConsequences(viewer: BookingViewer): List<String> =
    if (viewer == BookingViewer.Artist) {
        listOf(
            "This gig drops off your upcoming schedule.",
            "The date reopens on your calendar.",
            "Your chat with the client stays open — you can still message them.",
        )
    } else {
        listOf(
            "This booking drops off the artist's upcoming gigs.",
            "Your date reopens on the artist's calendar.",
            "Your chat with the artist stays open — you can still message them.",
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// Terms + formatting
// ─────────────────────────────────────────────────────────────────────────────

/** One row of the gig's agreed terms. [mono] marks a machine value (the id). */
data class BookingTerm(val label: String, val value: String, val mono: Boolean = false)

/**
 * The full terms of the gig, in reading order.
 *
 * [packageName] is passed in rather than derived here because resolving it needs
 * the artist (see `BookingDetailViewModel.packageName`) — and it is nullable
 * because an artist with no published packages genuinely has none to name, in
 * which case the row is dropped rather than rendered as "Custom" (which would
 * assert a tier that does not exist).
 */
fun bookingTerms(booking: Booking, packageName: String?): List<BookingTerm> = buildList {
    packageName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(BookingTerm("Package", it)) }
    add(BookingTerm("Date", booking.date))
    add(BookingTerm("Time", booking.time))
    add(BookingTerm("Venue", booking.venue))
    add(BookingTerm("Guests", booking.guests.toString()))
    add(BookingTerm("Booking ID", truncatedBookingId(booking.id), mono = true))
}

/**
 * "Rooftop · May 16 · 8:30 PM" — where and when, on one line under the name.
 *
 * Blank parts are dropped rather than joined, so a booking with no time does not
 * render a dangling separator.
 */
fun heroWhereLine(booking: Booking): String =
    listOf(booking.venue, shortDate(booking.date), booking.time)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

/**
 * "Sat, May 16, 2026" → "May 16".
 *
 * Purely a display shortening for the one-line hero, and deliberately tolerant:
 * anything that isn't the stored `EEE, MMM d, yyyy` shape is returned untouched
 * rather than sliced, because a wrong slice reads as a wrong date. (The strict
 * parse lives in `BookingDateFormat` and is used where a real Date is needed;
 * this only ever produces a label.)
 */
fun shortDate(date: String): String {
    val parts = date.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return date.trim()
    val mid = parts[1].split(" ").filter { it.isNotEmpty() }
    return if (mid.size >= 2) "${mid[0]} ${mid[1]}" else parts[1]
}

/**
 * `4f2a…9c1b` — enough of the UUID to quote in a support thread, short enough to
 * sit on one row. Ids at or under the threshold are shown whole; there is
 * nothing to save by eliding them.
 */
fun truncatedBookingId(id: String): String =
    if (id.length > BOOKING_ID_ELIDE_OVER) "${id.take(4)}…${id.takeLast(4)}" else id

private const val BOOKING_ID_ELIDE_OVER = 12

/**
 * The address for Maps / the clipboard: "venue, city".
 *
 * `Booking` carries no city of its own, so the artist's city stands in — it is
 * the only geography either side has, and a venue name alone routinely resolves
 * to the wrong metro. A missing city degrades to the bare venue rather than
 * emitting a trailing comma.
 */
fun venueAddress(venue: String, city: String?): String {
    val v = venue.trim()
    val c = city?.trim().orEmpty()
    return if (c.isEmpty() || v.isEmpty()) v.ifEmpty { c } else "$v, $c"
}

/**
 * The plain-text gig summary handed to the share sheet — who, when, where, and
 * the load-in notes if the client left any.
 */
fun shareGigText(counterparty: String, booking: Booking, city: String?): String = buildList {
    add(counterparty)
    add("${booking.date} · ${booking.time}")
    venueAddress(booking.venue, city).takeIf { it.isNotEmpty() }?.let { add(it) }
    booking.venueNotes?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Getting there: $it") }
}.joinToString("\n")
