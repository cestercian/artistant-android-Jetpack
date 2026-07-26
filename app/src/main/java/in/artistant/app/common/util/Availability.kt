package `in`.artistant.app.common.util

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * "AVAILABLE TODAY" / "AVAILABLE SAT" kicker for the soonest upcoming weekday the
 * artist marked open — a faithful port of iOS `WeekdayFormatting.availabilityKicker`
 * (SharedFormatters.swift). Returns null when [daysAvailable] is empty so callers
 * render NO pill rather than a false one (Discover's no-pill-over-a-false-pill rule).
 *
 * Why `Locale.US` SHORT day names: `days_available` arrives from the shared Supabase
 * backend as English EEE abbreviations ("Mon".."Sun"), so the match key must be built
 * with a fixed English locale — a device-locale formatter would silently break the
 * `contains()` check against the stored strings on a non-English phone (the exact iOS
 * footgun the shared formatter guards against).
 *
 * Pure: [today] is injected (defaults to the system date) so the scan is unit-testable
 * without touching the system clock. A non-empty set always matches within the 7-day
 * window (it covers every weekday name), so the loop both proves a real signal exists
 * and surfaces the soonest matching day.
 */
fun availabilityKicker(
    daysAvailable: Collection<String>,
    today: LocalDate = LocalDate.now(),
): String? {
    if (daysAvailable.isEmpty()) return null
    for (offset in 0 until 7) {
        val day = today.plusDays(offset.toLong())
        val abbr = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) // "Mon".."Sun"
        if (daysAvailable.contains(abbr)) {
            return if (offset == 0) "AVAILABLE TODAY" else "AVAILABLE ${abbr.uppercase(Locale.US)}"
        }
    }
    return null
}
