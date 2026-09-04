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
    // Stepped in IST, like every other reading of a gig day. A device-zone
    // walk labelled chip 0 with the device's date while the filter below and
    // `startEndIso` both resolve the day in India's — so a client in New York at
    // 22:00 (already the next morning in Kolkata) got a chip for a day that had
    // effectively passed there, kept its evening slots, and filed a booking whose
    // start time was hours in the past.
    val cal = Calendar.getInstance(IST).apply { timeInMillis = nowMs }
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

// ─────────────────────────────────────────────────────────────────────────────
// The month grid behind screen 05
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One cell of the funnel's month grid.
 *
 * [inMonth] is false for the leading and trailing days that fill the first and
 * last weeks out to seven. They are drawn — a week row with a hole in it reads as
 * a rendering fault — but they are never selectable, so the caller does not have
 * to carry two months' availability to draw one.
 */
data class FunnelDay(val number: Int, val inMonth: Boolean)

/**
 * The 5–6 week grid for [year]/[month] (`Calendar.MONTH`, 0-based), Monday first.
 *
 * Pure, so the grid the picker draws can be asserted without a Compose harness —
 * the off-by-one that puts the 1st under the wrong weekday is invisible in a
 * screenshot and obvious in a test.
 */
fun funnelMonthDays(year: Int, month: Int): List<FunnelDay> {
    val first = Calendar.getInstance(IST).apply {
        clear()
        timeZone = IST
        set(year, month, 1)
    }
    // Calendar.DAY_OF_WEEK is Sunday=1; +5 mod 7 rebases it to Monday=0.
    val leading = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val prevLength = Calendar.getInstance(IST).apply {
        clear()
        timeZone = IST
        set(year, month, 1)
        add(Calendar.MONTH, -1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = ArrayList<FunnelDay>(42)
    for (i in leading downTo 1) cells += FunnelDay(prevLength - i + 1, inMonth = false)
    for (d in 1..daysInMonth) cells += FunnelDay(d, inMonth = true)
    var next = 1
    while (cells.size % 7 != 0) cells += FunnelDay(next++, inMonth = false)
    return cells
}

/**
 * The days of [year]/[month] a host may actually request, by the same three
 * rules the date strip applies — and no others.
 *
 * 1. **Not in the past.** A day before today in IST is gone. The strip got this
 *    for free by starting at today; a month grid can be stepped backwards, so it
 *    has to say so.
 * 2. **A weekday the artist works** ([daysAvailable], empty meaning "all").
 * 3. **Something left on the clock**, which only ever bites today: a day whose
 *    last published slot has passed is not bookable, and offering it is how a
 *    request for a show that already ended gets filed.
 *
 * Deliberately the same predicate as [upcomingDateChips] rather than a second
 * opinion about availability — two screens disagreeing about which dates are dead
 * is the bug this shape exists to prevent.
 */
fun monthSelectableDays(
    year: Int,
    month: Int,
    daysAvailable: List<String> = emptyList(),
    timeSlots: List<String> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
): Set<Int> {
    val today = Calendar.getInstance(IST).apply { timeInMillis = nowMs }
    val todayY = today.get(Calendar.YEAR)
    val todayM = today.get(Calendar.MONTH)
    val todayD = today.get(Calendar.DAY_OF_MONTH)
    if (year < todayY || (year == todayY && month < todayM)) return emptySet()

    val cursor = Calendar.getInstance(IST).apply {
        clear()
        timeZone = IST
        set(year, month, 1)
    }
    val filterByArtist = daysAvailable.isNotEmpty()
    val days = cursor.getActualMaximum(Calendar.DAY_OF_MONTH)
    val open = LinkedHashSet<Int>()
    for (d in 1..days) {
        cursor.set(Calendar.DAY_OF_MONTH, d)
        if (year == todayY && month == todayM && d < todayD) continue
        val weekday = cursor.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US).orEmpty()
        if (filterByArtist && daysAvailable.none { it.equals(weekday, ignoreCase = true) }) continue
        // The clock only trims TODAY; `bookableTimeSlots` returns the whole grid
        // for any other day, so this is a no-op there rather than a second rule.
        if (timeSlots.isNotEmpty() &&
            bookableTimeSlots(timeSlots, cursor.timeInMillis, nowMs).isEmpty()
        ) {
            continue
        }
        open += d
    }
    return open
}

/** "October 2026" — the calendar card's own header, in the gig calendar's zone. */
fun funnelMonthLabel(year: Int, month: Int): String {
    val cal = Calendar.getInstance(IST).apply {
        clear()
        timeZone = IST
        set(year, month, 1)
    }
    val f = java.text.SimpleDateFormat("MMMM yyyy", Locale.US)
    f.timeZone = IST
    return f.format(cal.time)
}

/** Midnight IST on [year]/[month]/[day] — the epoch a picked cell stands for. */
fun funnelDayEpochMs(year: Int, month: Int, day: Int): Long =
    Calendar.getInstance(IST).apply {
        clear()
        timeZone = IST
        set(year, month, day)
    }.timeInMillis

/**
 * The day-of-month [epochMs] falls on, but ONLY if it falls inside
 * [year]/[month] — otherwise null.
 *
 * This is what lets the grid ring the picked date without the picked date living
 * in the grid: the selection is an instant, the grid is a month, and stepping to
 * November must not put a ring on the 12th because October's 12th was chosen.
 */
fun dayOfMonthIfIn(epochMs: Long, year: Int, month: Int): Int? {
    if (epochMs <= 0L) return null
    val cal = Calendar.getInstance(IST).apply { timeInMillis = epochMs }
    if (cal.get(Calendar.YEAR) != year || cal.get(Calendar.MONTH) != month) return null
    return cal.get(Calendar.DAY_OF_MONTH)
}

/** One month of the funnel calendar: which month, and which of its days are open. */
data class FunnelMonth(val year: Int, val month: Int, val selectableDays: Set<Int>)

/**
 * The first month from [nowMs] onward that has a day this artist can take, and
 * that month's open days — searched forward at most [horizonMonths].
 *
 * An artist can legitimately have nothing open for weeks (a three-day weekend
 * artist away for a month), and opening the picker on an empty grid with no hint
 * that stepping would help is the dead end this avoids. When the horizon runs out
 * the CURRENT month comes back with an empty set, which the screen renders as a
 * month with nothing in it — true, and still steppable.
 */
fun firstOpenMonth(
    daysAvailable: List<String> = emptyList(),
    timeSlots: List<String> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
    horizonMonths: Int = 12,
): FunnelMonth {
    val cal = Calendar.getInstance(IST).apply { timeInMillis = nowMs }
    var year = cal.get(Calendar.YEAR)
    var month = cal.get(Calendar.MONTH)
    repeat(horizonMonths) {
        val open = monthSelectableDays(year, month, daysAvailable, timeSlots, nowMs)
        if (open.isNotEmpty()) return FunnelMonth(year, month, open)
        val stepped = year * 12 + month + 1
        year = stepped.floorDiv(12)
        month = stepped.mod(12)
    }
    return FunnelMonth(
        year = cal.get(Calendar.YEAR),
        month = cal.get(Calendar.MONTH),
        selectableDays = emptySet(),
    )
}

/**
 * [delta] months from [year]/[month], in absolute months.
 *
 * `year * 12 + month` rather than nudging the month field, because the year
 * boundary is exactly where the naive version breaks: `month + 1` off December
 * yields an out-of-range 12. `floorDiv`/`mod` carry the sign for backward steps
 * where plain `/` and `%` would hand back a negative month.
 */
fun steppedMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
    val absolute = year * 12 + month + delta
    return absolute.floorDiv(12) to absolute.mod(12)
}

/** Is [year]/[month] strictly after the month containing [nowMs]? */
fun isAfterCurrentMonth(year: Int, month: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
    val cal = Calendar.getInstance(IST).apply { timeInMillis = nowMs }
    return year * 12 + month > cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
}
