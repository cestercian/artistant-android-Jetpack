package `in`.artistant.app.platform.calendar

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pure calendar-mirror planner — port of iOS `CalendarSyncService.plan` /
 * `fingerprint`. No Android CalendarContract here so unit tests stay offline.
 */
object CalendarSyncPlanner {

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

    fun resolvedStartEpochMs(b: Booking): Long? =
        parseIso(b.startDatetimeIso) ?: parseDateTime(b.date, b.time)

    fun resolvedEndEpochMs(b: Booking): Long? =
        parseIso(b.endDatetimeIso) ?: resolvedStartEpochMs(b)?.plus(2 * 60 * 60 * 1000L)

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
                    val end = resolvedEndEpochMs(b) ?: (start + 2 * 60 * 60 * 1000L)
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

    private fun parseIso(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
        )
        for (pattern in formats) {
            runCatching {
                val f = SimpleDateFormat(pattern, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                return f.parse(raw)?.time
            }
        }
        return null
    }

    private fun parseDateTime(date: String, time: String): Long? {
        if (date.isBlank() || time.isBlank()) return null
        // Booking.dateFormat = "EEE, MMM d, yyyy" + wall-clock time like "7:30 PM"
        val combined = "$date $time"
        val f = SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.US)
        f.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return runCatching { f.parse(combined)?.time }.getOrNull()
    }

    private fun isoUtc(epochMs: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(epochMs)
    }
}
