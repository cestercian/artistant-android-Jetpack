package `in`.artistant.app.platform.calendar

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedEndEpochMs
import `in`.artistant.app.data.model.resolvedStartEpochMs
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pure calendar-mirror planner — port of iOS `CalendarSyncService.plan` /
 * `fingerprint`. No Android CalendarContract here so unit tests stay offline.
 */
object CalendarSyncPlanner {

    /** Placeholder gig length for a row with no `end_datetime` — same 2h `create()` writes. */
    private const val DEFAULT_GIG_MS = 2L * 60 * 60 * 1000

    data class SyncedEvent(val eventId: String, val fingerprint: String)

    sealed class Action {
        data class Create(val bookingId: String) : Action()
        data class Update(val bookingId: String, val eventId: String) : Action()
        data class Delete(val bookingId: String, val eventId: String) : Action()
    }

    fun plan(
        bookings: List<Booking>,
        map: Map<String, SyncedEvent>,
    ): List<Action> {
        val actions = mutableListOf<Action>()
        for (b in bookings) {
            val entry = map[b.id.lowercase()]
            when (b.status) {
                BookingStatus.Confirmed, BookingStatus.Completed, BookingStatus.Disputed -> {
                    if (resolvedStartEpochMs(b) == null) continue
                    if (entry != null) {
                        if (entry.fingerprint != fingerprint(b)) {
                            actions += Action.Update(b.id.lowercase(), entry.eventId)
                        }
                    } else {
                        actions += Action.Create(b.id.lowercase())
                    }
                }
                // Neither belongs in the user's calendar, and "doesn't belong" has
                // to mean RETRACT an existing mirror, not merely decline to create
                // one: a booking that was Confirmed (event written, entry in `map`)
                // and now reads Cancelled — or decodes as Unknown because the server
                // moved it to a status this build predates — would otherwise strand
                // the event on the device forever. Delete is the only action
                // `CalendarSyncService.reconcileNow` prunes the map on, so skipping
                // it leaks the mapping too. Self-healing in the Unknown case: the
                // entry is gone, so a later build that understands the status plans
                // a fresh Create.
                BookingStatus.Cancelled, BookingStatus.Unknown -> {
                    if (entry != null) {
                        actions += Action.Delete(b.id.lowercase(), entry.eventId)
                    }
                }
                // Tentative noise — never mirrored in the first place, so there is
                // normally no entry to retract. Left as a pure no-op deliberately:
                // a confirmed→pending regression would strand an event the same
                // way, but no client or server path performs that transition today
                // (create inserts pending_confirm, accept PATCHes confirmed,
                // decline/cancel route through the cancel-booking Edge Function),
                // and widening this branch is a behaviour change out of scope here.
                BookingStatus.PendingConfirm -> Unit
            }
        }
        return actions
    }

    fun fingerprint(b: Booking): String =
        listOf(
            resolvedStartEpochMs(b)?.let { isoUtc(it) }.orEmpty(),
            resolvedEndEpochMs(b)?.let { isoUtc(it) }.orEmpty(),
            b.venue,
            b.clientFullName.orEmpty(),
            b.status.dbValue,
        ).joinToString("|")

    /**
     * The gig's start clock — the model's [Booking.resolvedStartEpochMs], never a
     * second opinion about it.
     *
     * This used to be its own parser, and it was strictly weaker than the model's in
     * three ways: one date pattern (`"EEE, MMM d, yyyy"`) against
     * [BookingDateFormat]'s three, lenient parsing where the model is strict, and no
     * start-of-day fallback for a row that carries a date label but no usable time.
     * So a confirmed gig whose `date_label` reads "Jul 11, 2026" — a label every
     * list and detail screen in the app renders fine — resolved to null here and was
     * silently dropped from the calendar mirror, from [busyDays] and from every
     * [clashesOnDay] warning. Leniency cut the other way too: "Mon, Feb 30, 2026"
     * rolled over to Mar 2 and would have written a real event on the wrong day.
     */
    fun resolvedStartEpochMs(b: Booking): Long? = b.resolvedStartEpochMs()

    /** End clock — `end_datetime` when the row has one, else [DEFAULT_GIG_MS] after the start. */
    fun resolvedEndEpochMs(b: Booking): Long? =
        b.resolvedEndEpochMs() ?: resolvedStartEpochMs(b)?.plus(DEFAULT_GIG_MS)

    fun eventTitle(b: Booking): String {
        val who = b.clientFullName?.takeIf { it.isNotBlank() } ?: b.venue.ifBlank { "Gig" }
        return "Gig — $who"
    }

    fun eventNotes(bookingId: String): String =
        "Booked on Artistant.\nin.artistant.app://booking/${bookingId.lowercase()}"

    data class Clash(
        val bookingId: String,
        val title: String,
        val startEpochMs: Long,
        val endEpochMs: Long,
    )

    /** Confirmed (or completed) bookings that overlap [dayStartMs, dayEndMs). */
    fun clashesOnDay(
        bookings: Collection<Booking>,
        dayStartMs: Long,
        dayEndMs: Long,
    ): List<Clash> {
        return bookings.mapNotNull { b ->
            when (b.status) {
                BookingStatus.Confirmed, BookingStatus.Completed, BookingStatus.Disputed -> {
                    val start = resolvedStartEpochMs(b) ?: return@mapNotNull null
                    val end = resolvedEndEpochMs(b) ?: (start + DEFAULT_GIG_MS)
                    if (start < dayEndMs && end > dayStartMs) {
                        Clash(b.id.lowercase(), eventTitle(b), start, end)
                    } else null
                }
                else -> null
            }
        }
    }

    /** Local-calendar day keys (yyyy-MM-dd Asia/Kolkata) with ≥1 confirmed gig. */
    fun busyDays(bookings: Collection<Booking>): Set<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return bookings.mapNotNull { b ->
            when (b.status) {
                BookingStatus.Confirmed, BookingStatus.Completed, BookingStatus.Disputed ->
                    resolvedStartEpochMs(b)?.let { fmt.format(it) }
                else -> null
            }
        }.toSet()
    }

    private fun isoUtc(epochMs: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(epochMs)
    }
}
