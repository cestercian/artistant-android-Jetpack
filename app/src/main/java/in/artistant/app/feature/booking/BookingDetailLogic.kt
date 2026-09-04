package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedEndEpochMs
import `in`.artistant.app.data.model.resolvedStartEpochMs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
 * One consequence of cancelling: the fact, and the line under it that says why
 * it matters.
 */
data class CancelConsequence(val title: String, val detail: String)

/**
 * What actually happens on cancel, read before the destructive tap.
 *
 * Every line is true in THIS codebase, which is the constraint that shaped it: a
 * cancelled booking drops out of the artist's confirmed-upcoming list (freeing
 * the date), and the thread is never torn down. No push fires on cancel — the
 * only booking push trigger is on confirm — so none is promised. Nothing about
 * refunds either: v1 moves no money, which is itself one of the four lines.
 *
 * The third entry is specific to THIS date, which is screen 52's whole point
 * ("the second one is specific to this date"). What it does NOT say is the
 * design's "outside the 7-day window, so your score is untouched": that window
 * is real but it is the `cancel-booking` Edge Function's REFUND ladder, and v1
 * holds no money for it to apply to. The score's cancellation metric is
 * `count(cancelled and cancelled_by = 'artist') / count(completed or cancelled)`
 * — blind to how close to the gig it happened, and moved only by an ARTIST's
 * cancellation. So the artist is told their rate moves whenever they cancel, the
 * client is told it doesn't, and neither is told about a deadline that would not
 * apply to them.
 *
 * [daysBefore] null (a booking with no readable date) drops the date-specific
 * row rather than guessing at it.
 */
fun cancelConsequences(
    viewer: BookingViewer,
    counterparty: String,
    daysBefore: Int?,
): List<CancelConsequence> = buildList {
    if (viewer == BookingViewer.Artist) {
        add(
            CancelConsequence(
                "$counterparty is told right away",
                "The gig drops off your upcoming schedule",
            ),
        )
        add(
            CancelConsequence(
                "Your thread stays open",
                "So you can explain, or take a later date",
            ),
        )
        daysBefore?.let { add(artistNoticeConsequence(it)) }
        add(
            CancelConsequence(
                "It counts towards your cancellation rate",
                "That rate is 15% of your Bookability Score, whenever you cancel",
            ),
        )
    } else {
        add(
            CancelConsequence(
                "$counterparty is told right away",
                "They keep the date free for someone else",
            ),
        )
        add(
            CancelConsequence(
                "Your thread stays open",
                "So you can rebook or explain",
            ),
        )
        daysBefore?.let { add(clientNoticeConsequence(it)) }
        add(
            CancelConsequence(
                "No money moves",
                "Artistant holds none — there is nothing to refund",
            ),
        )
    }
}

/** How much notice this is, in the client's words. */
private fun clientNoticeConsequence(daysBefore: Int): CancelConsequence =
    CancelConsequence(noticeTitle(daysBefore), noticeDetailForClient(daysBefore))

private fun artistNoticeConsequence(daysBefore: Int): CancelConsequence =
    CancelConsequence(
        noticeTitle(daysBefore),
        if (daysBefore <= SHORT_NOTICE_DAYS) {
            "Short notice — the host may not find anyone else"
        } else {
            "There is still time for the host to find someone else"
        },
    )

private fun noticeTitle(daysBefore: Int): String = when {
    daysBefore < 0 -> "The date has already passed"
    daysBefore == 0 -> "This is today"
    daysBefore == 1 -> "This is tomorrow"
    else -> "This is $daysBefore days before the date"
}

private fun noticeDetailForClient(daysBefore: Int): String = when {
    daysBefore < 0 -> "Cancelling now only closes the record"
    daysBefore <= SHORT_NOTICE_DAYS -> "Short notice — the artist may not refill the date"
    else -> "There is still time for them to refill the date"
}

/**
 * Where "short notice" starts.
 *
 * Seven days, matching the only window the backend actually models (the Edge
 * Function's full-refund boundary). It carries no consequence in v1 — nothing is
 * refunded because nothing is held — so it is used here only as the honest place
 * to change the wording, never as a claim about money or score.
 */
private const val SHORT_NOTICE_DAYS = 7

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
 *
 * A blank value drops its whole row for the same reason [heroWhereLine] drops
 * blank parts: a "Time" label with nothing beside it reads as a rendering fault,
 * and the two would otherwise disagree about the same booking on the same
 * screen. Nothing here can be empty on a row read whole, so this is the guard
 * for a projection that omitted a column, not a routine case.
 */
fun bookingTerms(booking: Booking, packageName: String?): List<BookingTerm> = buildList {
    packageName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(BookingTerm("Package", it)) }
    add(BookingTerm("Date", booking.date))
    add(BookingTerm("Time", booking.time))
    add(BookingTerm("Venue", booking.venue))
    add(BookingTerm("Guests", booking.guests.toString()))
    add(BookingTerm("Booking ID", truncatedBookingId(booking.id), mono = true))
}.filter { it.value.isNotBlank() }

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

// ─────────────────────────────────────────────────────────────────────────────
// Which page this is (screens 18 / 83 / 95 / 96 / 97)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The booking-detail screen is five pages, not one page with five tints.
 *
 * Each variant answers a different question, so each is laid out differently:
 *
 *  - [Awaiting] (95) — "will they say yes?" The only editable state, so it says
 *    so and offers withdraw.
 *  - [Confirmed] (18) — "what happens on the night?" A schedule both sides act
 *    on, not a receipt.
 *  - [Cancelled] (83) — "what happened, and what now?" Terminal, but the
 *    relationship may survive, so it offers rebooking.
 *  - [Disputed] (96) — "who decides?" An escalated state, not an error; it
 *    states that reviews and scoring are suspended, which is what stops
 *    retaliatory ratings.
 *  - [ReadOnly] (97) — a status this build does not know. Everything visible,
 *    nothing actionable, and the reason on the page.
 *
 * `completed` shares [Confirmed]'s shape: the night is the same schedule, read
 * afterwards. The review lives in the dock, where the action set puts it.
 */
enum class BookingDetailVariant { Awaiting, Confirmed, Cancelled, Disputed, ReadOnly }

fun variantFor(status: BookingStatus): BookingDetailVariant = when (status) {
    BookingStatus.PendingConfirm -> BookingDetailVariant.Awaiting
    BookingStatus.Confirmed, BookingStatus.Completed -> BookingDetailVariant.Confirmed
    BookingStatus.Cancelled -> BookingDetailVariant.Cancelled
    BookingStatus.Disputed -> BookingDetailVariant.Disputed
    BookingStatus.Unknown -> BookingDetailVariant.ReadOnly
}

/**
 * "Booking #AR-4F2A11" — the header's title on every variant.
 *
 * The reference itself is `InvoiceLogic.bookingReference`, which Book & confirm
 * landed while this section was being written. BN had assumed no such thing
 * could exist — `bookings` has no reference column, and minting one client-side
 * sounded like inventing an identifier support could not look up. Theirs is
 * better, and the assumption was wrong: it is the leading hex of the row's own
 * UUID, so it is deterministic, readable over the phone, and resolvable back by
 * a prefix match. This wrapper only adds the word, and drops it for a blank id
 * rather than printing "Booking #" with nothing after it.
 */
fun bookingTitle(id: String): String =
    bookingReference(id).takeIf { it.isNotBlank() }?.let { "Booking #$it" } ?: "Booking"

// ─────────────────────────────────────────────────────────────────────────────
// The night, and the wait
// ─────────────────────────────────────────────────────────────────────────────

/** One moment in a run of show or a progress list: a headline and a sub-line. */
data class BookingMoment(
    val title: String,
    val detail: String? = null,
    val done: Boolean = false,
)

/**
 * The run of show — the night, hour by hour, from what the row actually holds.
 *
 * Screen 18 draws four moments (load-in, set one, break, set two). The schema
 * has no such thing: `bookings` carries `start_datetime`, `end_datetime`,
 * `time_label` and one free-text `venue_notes`, and 107 migrations add nothing
 * else. Inventing a soundcheck at 6:30 pm would be fabricating the one thing the
 * artist plans their day around, so this prints the two moments that ARE on the
 * row and hangs the client's own access note under the first — which is exactly
 * what `venue_notes` is for ("gate, parking, load-in").
 *
 * [nowMs] marks moments already past, so the timeline reads the way it does
 * everywhere else: a filled dot is behind you.
 *
 * Empty when the row has no clock at all, so the caller can drop the section
 * rather than draw a heading over nothing.
 */
fun runOfShow(booking: Booking, nowMs: Long): List<BookingMoment> {
    val start = booking.resolvedStartEpochMs() ?: return emptyList()
    val startLabel = booking.time.trim().ifEmpty { clockLabel(start) }
    val notes = booking.venueNotes?.trim()?.takeIf { it.isNotEmpty() }
    return buildList {
        add(
            BookingMoment(
                title = "$startLabel · Set starts",
                detail = notes,
                done = start <= nowMs,
            ),
        )
        booking.resolvedEndEpochMs()?.let { end ->
            add(BookingMoment(title = "${clockLabel(end)} · Set ends", done = end <= nowMs))
        }
    }
}

/**
 * The request's three stages (screen 95).
 *
 * Only the first is a fact — we sent it, and `created_at` says when. The other
 * two are what the state machine allows next, which is why neither is ever
 * marked done: a pending booking has by definition not been answered, and once
 * it is, this variant is no longer the page being drawn.
 */
fun requestProgress(booking: Booking, nowMs: Long): List<BookingMoment> = listOf(
    BookingMoment(
        title = "Request sent",
        detail = relativeSince(booking.createdAtEpochMs, nowMs),
        done = true,
    ),
    BookingMoment("Artist responds", "Accept or decline"),
    BookingMoment("Date is held", "Once they accept"),
)

/** "8:00 PM" from an instant, in the gig calendar's zone. */
private fun clockLabel(epochMs: Long): String =
    SimpleDateFormat("h:mm a", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        .format(Date(epochMs))

/**
 * "14 minutes ago", "3 hours ago", "2 days ago".
 *
 * Null for a timestamp we do not have (`created_at` absent from a projection
 * decodes to 0) or one in the future, rather than printing "in -1 minutes" or a
 * date in 1970. The header prints this as the request's status line, so a
 * missing value has to drop the line rather than fill it with nonsense.
 */
fun relativeSince(epochMs: Long, nowMs: Long): String? {
    if (epochMs <= 0L || epochMs > nowMs) return null
    val minutes = (nowMs - epochMs) / 60_000L
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes minute${plural(minutes)} ago"
        minutes < MINUTES_PER_DAY -> (minutes / 60).let { "$it hour${plural(it)} ago" }
        else -> (minutes / MINUTES_PER_DAY).let { "$it day${plural(it)} ago" }
    }
}

private const val MINUTES_PER_DAY = 60L * 24

private fun plural(n: Long): String = if (n == 1L) "" else "s"

/**
 * "Cancelled 2 Aug" — the cancelled header's status line, from `cancelled_at`.
 *
 * Falls back to the bare word when the row carries no stamp (a projection that
 * omitted the column, or a row cancelled by something that did not set it).
 * "Cancelled" alone is still true; "Cancelled 1 Jan 1970" is not.
 */
fun cancelledOnLabel(booking: Booking): String {
    val at = booking.cancelledAtIso
        ?.let { `in`.artistant.app.common.util.SupabaseISO8601.parse(it)?.toEpochMilli() }
        ?: return "Cancelled"
    return "Cancelled " + SimpleDateFormat("d MMM", Locale.US).format(Date(at))
}

/**
 * Who pulled out, in the second person where that is who it was.
 *
 * `cancelled_by` is the server's own stamp ('client' | 'artist'), and the
 * sentence flips on the VIEWER's side rather than on the value alone: the same
 * row reads "You cancelled this booking" to whoever did it and "<name> cancelled
 * this booking" to the other party. A row with no stamp says neither — "This
 * booking was cancelled" is the honest sentence when we do not know.
 */
fun cancelledByLine(booking: Booking, viewer: BookingViewer, counterparty: String): String {
    val by = booking.cancelledBy?.lowercase()
    val viewerSide = if (viewer == BookingViewer.Artist) "artist" else "client"
    return when {
        by == null -> "This booking was cancelled"
        by == viewerSide -> "You cancelled this booking"
        else -> "$counterparty cancelled this booking"
    }
}
