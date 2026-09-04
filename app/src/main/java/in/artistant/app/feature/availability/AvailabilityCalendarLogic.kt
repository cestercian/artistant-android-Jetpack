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
 * The month grid behind design screen 22 — pure, so the awkward months are
 * assertable without a device.
 */

/** Every date in this app is an IST date. A gig at 11pm belongs to that night. */
private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

/**
 * One cell of a month grid.
 *
 * A null [date] is a LEADING BLANK, not a bug: the grid is seven columns wide
 * and a month rarely starts on a Monday, so the first row is padded. Trailing
 * blanks are omitted — the last row simply ends, because nothing renders there
 * and padding it only costs a taller card.
 */
data class CalendarCell(val date: LocalDate?)

/**
 * [month] as a seven-column grid, Monday-first.
 *
 * Monday-first because [in.artistant.app.feature.wizard.WizardWeekdays] is
 * Mon→Sun and the weekday chips underneath the calendar are drawn from it. A
 * Sunday-first grid over a Monday-first chip row would put the same two columns
 * in different places on one screen.
 */
fun monthGrid(month: YearMonth): List<CalendarCell> {
    // DayOfWeek.value is 1 for Monday, so the count of leading blanks is the
    // first day's value minus one — no lookup table, no locale.
    val leading = month.atDay(1).dayOfWeek.value - 1
    return List(leading) { CalendarCell(null) } +
        (1..month.lengthOfMonth()).map { CalendarCell(month.atDay(it)) }
}

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
 * Does the act play on [date]'s weekday?
 *
 * [days] holds the short English names the wizard writes ("Mon", "Sat"), so the
 * comparison is made in [Locale.US] rather than the device locale — a phone set
 * to Hindi would otherwise produce "सोम" and match nothing, silently greying the
 * artist's whole calendar.
 */
fun playsOn(date: LocalDate, days: Set<String>): Boolean =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) in days

/** "September 2026" — the calendar card's own heading. */
fun monthLabel(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, Locale.US)} ${month.year}"

/** Column headings, Monday-first, matching [monthGrid]. */
val WeekdayInitials: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")

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
