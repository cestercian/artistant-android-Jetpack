package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Wizard canonical slots — mirrors iOS `ArtistOnboardingStore.allTimeSlots`. */
val DefaultTimeSlots = listOf("6:00 PM", "7:30 PM", "8:30 PM", "9:00 PM", "10:00 PM", "11:00 PM")

data class DateChip(
    val label: String,
    val epochMs: Long,
    val weekdayAbbrev: String,
    val available: Boolean,
)

/**
 * Next [count] calendar days. When [daysAvailable] is non-empty, days whose
 * weekday abbrev isn't listed render unavailable (iOS DateScroller parity).
 *
 * [timeSlots] is the artist's published grid, and handing it over is what keeps
 * the strip honest about TODAY: the window starts at [nowMs], so chip 0 is today
 * even at 23:10, and a day with nothing left on the clock is not a day this
 * artist can still be booked for. Callers that ask for a date without a start
 * time — the quote screen — leave it empty, and every weekday-legal day stays
 * selectable.
 */
fun upcomingDateChips(
    count: Int = 14,
    daysAvailable: List<String> = emptyList(),
    timeSlots: List<String> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
): List<DateChip> {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val filterByArtist = daysAvailable.isNotEmpty()
    return buildList {
        repeat(count) {
            val epoch = cal.timeInMillis
            val weekday = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US).orEmpty()
            val weekdayOpen = !filterByArtist || daysAvailable.any { it.equals(weekday, ignoreCase = true) }
            val available = weekdayOpen &&
                (timeSlots.isEmpty() || bookableTimeSlots(timeSlots, epoch, nowMs).isNotEmpty())
            add(
                DateChip(
                    label = BookingDateFormat.weekdayString(epoch),
                    epochMs = epoch,
                    weekdayAbbrev = weekday,
                    available = available,
                ),
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

fun resolveTimeSlots(artistSlots: List<String>): List<String> =
    artistSlots.ifEmpty { DefaultTimeSlots }

fun defaultTimeFromSlots(slots: List<String>): String =
    slots.firstOrNull { it == "8:30 PM" } ?: slots.firstOrNull().orEmpty()

/**
 * The slots on the day [dayEpochMs] falls in that a client can still ask for.
 *
 * Only today is filtered; every other day keeps the artist's grid whole. The
 * funnel used to offer today's whole list regardless of the clock, so a client
 * opening it at 23:10 landed preselected on "8:30 PM" and could file a live
 * `pending_confirm` request for a show that had already ended — the artist got a
 * request for a gig in the past, and it sorted to the top of their upcoming
 * list. Nothing on the server catches it: mig 0051 forbids overlaps, not
 * backdating.
 *
 * A label this app didn't write (one [slotMinutesOfDay] can't read) survives the
 * filter. Hiding a slot we merely failed to parse would take a bookable time off
 * the artist's own profile, which is the worse of the two mistakes.
 */
fun bookableTimeSlots(
    slots: List<String>,
    dayEpochMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): List<String> {
    if (startOfDayMs(dayEpochMs) != startOfDayMs(nowMs)) return slots
    val elapsed = minutesOfDay(nowMs)
    return slots.filter { slot ->
        val minutes = slotMinutesOfDay(slot) ?: return@filter true
        minutes > elapsed
    }
}

/**
 * Minutes past midnight for a `"h:mm a"` label, or null when it isn't one.
 *
 * Hand-parsed rather than run through a `SimpleDateFormat` so it stays pure and
 * locale-free: these strings are written by the wizard from [DefaultTimeSlots]
 * and stored verbatim in `artists.time_slots`, so the US spelling is the wire
 * contract, not a formatting preference.
 */
fun slotMinutesOfDay(slot: String): Int? {
    val parts = slot.trim().split(' ')
    if (parts.size != 2) return null
    val hourMinute = parts[0].split(':')
    if (hourMinute.size != 2) return null
    val hour12 = hourMinute[0].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val minute = hourMinute[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    val hour24 = when {
        parts[1].equals("PM", ignoreCase = true) -> if (hour12 == 12) 12 else hour12 + 12
        parts[1].equals("AM", ignoreCase = true) -> if (hour12 == 12) 0 else hour12
        else -> return null
    }
    return hour24 * 60 + minute
}

/**
 * The gig clock is Asia/Kolkata, not the device's.
 *
 * `BookingsRepository.startEndIso` composes the instant it writes in IST, so a
 * label like "11:00 PM" always means 23:00 in India whoever is holding the
 * phone. Reading "now" in the device zone instead let a client west of India
 * keep slots that had already passed there — a client in Dubai at 22:00 sees
 * 23:30 IST, and the filter that exists to stop backdated requests waved the
 * 11:00 PM slot through. Both halves of the comparison are IST now.
 */
private fun minutesOfDay(epochMs: Long): Int {
    val cal = Calendar.getInstance(IST).apply { timeInMillis = epochMs }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

private fun startOfDayMs(epochMs: Long): Long = Calendar.getInstance(IST).apply {
    timeInMillis = epochMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** The wall clock every gig label is written and read in. */
private val IST: TimeZone get() = TimeZone.getTimeZone("Asia/Kolkata")
