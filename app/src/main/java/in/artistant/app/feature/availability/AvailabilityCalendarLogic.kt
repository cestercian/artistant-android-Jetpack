package `in`.artistant.app.feature.availability

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedStartEpochMs
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * What design screen 22 needs on top of the shared month grid.
 *
 * The grid itself is `designsystem/component/MonthCalendar.kt`, which the
 * bookings section owns — this file only answers the two questions that
 * component takes as `Set<Int>` parameters, plus the sentence underneath. Pure,
 * so the awkward months are assertable without a device.
 */

/** Every date in this app is an IST date. A gig at 11pm belongs to that night. */
private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

/**
 * The nights already spoken for.
 *
 * Confirmed only — the same rule the dashboard's 14-day strip uses. A
 * `pending_confirm` booking is a night a stranger ASKED for; shading it would
 * tell the artist a date is gone while they are still deciding whether to take
 * it, and un-shade it the moment they decline.
 */
fun bookedDates(bookings: List<Booking>): Set<LocalDate> = bookings
    .asSequence()
    .filter { it.status == BookingStatus.Confirmed }
    .mapNotNull { it.resolvedStartEpochMs() }
    .map { Instant.ofEpochMilli(it).atZone(IST).toLocalDate() }
    .toSet()

/**
 * [bookedDates] narrowed to one month, as the day-of-month ints `MonthDayGrid`
 * paints lime.
 *
 * [month] is `Calendar.MONTH` — 0-based, matching the shared grid and every
 * other helper in that file. That `+ 1` against `LocalDate.monthValue` is the
 * whole reason this is a named function rather than a filter at the call site.
 */
fun busyDaysIn(dates: Set<LocalDate>, year: Int, month: Int): Set<Int> = dates
    .asSequence()
    .filter { it.year == year && it.monthValue == month + 1 }
    .map { it.dayOfMonth }
    .toSet()

/**
 * The days in [year]/[month] the act does NOT play, because their weekday is not
 * in [days].
 *
 * The third reading on the calendar, and the one that makes this an availability
 * screen rather than a bookings list: a free Tuesday and a Tuesday the act never
 * plays are both un-booked, and only one of them is worth a host's message.
 *
 * An empty [days] returns an empty set rather than the whole month. Nothing is
 * picked yet, which is a state the summary line names in words — greying out all
 * thirty-one squares would instead say the artist is unavailable forever.
 */
fun closedDaysIn(year: Int, month: Int, days: Set<String>): Set<Int> {
    if (days.isEmpty()) return emptySet()
    val ym = YearMonth.of(year, month + 1)
    return (1..ym.lengthOfMonth())
        .filterNot { playsOn(ym.atDay(it), days) }
        .toSet()
}

/**
 * Does the act play on [date]'s weekday?
 *
 * [days] holds the short English names the wizard writes ("Mon", "Sat"), so the
 * comparison is made in [Locale.US] rather than the device locale — a phone set
 * to Hindi would otherwise produce "सोम" and match nothing, silently greying the
 * artist's whole calendar.
 */
fun playsOn(date: LocalDate, days: Set<String>): Boolean =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) in days

/**
 * How the artist's month reads in one line, under the grid.
 *
 * [bookedInMonth] is a count rather than a list because the sentence is about
 * volume; the grid above it says which nights.
 *
 * Only ever called on a month whose bookings actually loaded. A failed read
 * takes the caller's other branch, because "nothing booked this month" and "we
 * could not find out" are the same empty grid and the opposite meaning.
 */
fun monthSummary(bookedInMonth: Int, openWeekdays: Int): String {
    if (openWeekdays == 0) return "No weekdays picked yet — clients can't see a date to ask for."
    val nights = when (bookedInMonth) {
        0 -> "Nothing booked this month"
        1 -> "1 night booked"
        else -> "$bookedInMonth nights booked"
    }
    return "$nights · you play $openWeekdays day${if (openWeekdays == 1) "" else "s"} a week"
}
