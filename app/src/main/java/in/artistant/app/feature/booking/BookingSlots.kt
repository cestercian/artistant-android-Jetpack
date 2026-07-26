package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingDateFormat
import java.util.Calendar
import java.util.Locale

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
 */
fun upcomingDateChips(
    count: Int = 14,
    daysAvailable: List<String> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
): List<DateChip> {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val filterByArtist = daysAvailable.isNotEmpty()
    return buildList {
        repeat(count) {
            val epoch = cal.timeInMillis
            val weekday = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US).orEmpty()
            val available = !filterByArtist || daysAvailable.any { it.equals(weekday, ignoreCase = true) }
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
