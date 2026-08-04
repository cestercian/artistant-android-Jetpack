package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.designsystem.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Lightweight month header for bookings lists — not a full calendar grid.
 * Port slice of iOS `MonthCalendarView` (header + event rows only).
 */
@Composable
fun MonthCalendarHeader(
    monthLabel: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = monthLabel,
        style = AppTheme.type.displaySmall,
        color = AppTheme.colors.ink,
        modifier = modifier.padding(
            horizontal = AppTheme.dimens.space.lg,
            vertical = AppTheme.dimens.space.md,
        ),
    )
}

/**
 * Compact 7-col day grid for the visible month. [busyDays] are day-of-month
 * ints that light up (confirmed gigs). Tap a day via [onDayClick].
 */
@Composable
fun MonthDayGrid(
    year: Int,
    month: Int, // Calendar.MONTH 0-based
    busyDays: Set<Int>,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDow = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) // Mon=0
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = List(firstDow) { null } + (1..daysInMonth).toList()
    Column(modifier.padding(horizontal = space.lg)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(
                    d,
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(space.xs)
                            .then(
                                if (day != null) {
                                    val isSelected = selectedDay == day
                                    val isBusy = day in busyDays
                                    Modifier
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> colors.brand
                                                isBusy -> colors.brand.copy(alpha = 0.2f)
                                                else -> Color.Transparent
                                            },
                                        )
                                        .semantics {
                                            contentDescription = buildString {
                                                append("Day $day")
                                                if (isBusy) append(", has bookings")
                                                if (isSelected) append(", selected")
                                            }
                                            selected = isSelected
                                            if (isBusy) stateDescription = "Busy"
                                        }
                                        .clickable { onDayClick(day) }
                                } else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            Text(
                                "$day",
                                style = AppTheme.type.caption,
                                color = when {
                                    selectedDay == day -> colors.brandInk
                                    day in busyDays -> colors.ink
                                    else -> colors.ink2
                                },
                            )
                        }
                    }
                }
                repeat(7 - week.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * First three letters of a month name → the full name we render. Keyed by
 * prefix so both the "MMM" the app writes ("Sep") and a spelled-out month that
 * could arrive from another client ("September") resolve to the same entry.
 */
private val monthNamesByPrefix = mapOf(
    "jan" to "January", "feb" to "February", "mar" to "March",
    "apr" to "April", "may" to "May", "jun" to "June",
    "jul" to "July", "aug" to "August", "sep" to "September",
    "oct" to "October", "nov" to "November", "dec" to "December",
)

/**
 * Format "April 2026" from a booking date label — the group key behind
 * `groupedByMonth()` on the Bookings and Gigs lists.
 *
 * Labels arrive as `BookingDateFormat.PATTERN` ("EEE, MMM d, yyyy" →
 * "Sat, May 16, 2026"); `Booking.date` is the `date_label` column verbatim, and
 * the reader also tolerates the weekday-less "MMM d, yyyy". Splitting either on
 * ", " leaves the month-and-day part second-to-last and the year last, so we
 * read off the TAIL. The previous version indexed `parts[1]` and expected three
 * space-separated tokens there, but on the canonical shape `parts[1]` is
 * "May 16" — only ever two tokens — so the check could never pass and every
 * label fell through to the `dateLabel` return. That handed each booking its
 * own monthKey and rendered one month header per row.
 *
 * Anything we can't read confidently (ISO "2026-05-16", an empty `date_label`,
 * garbage) is returned unchanged: grouping under the raw label is wrong-ish but
 * stable, and the row still renders rather than throwing.
 */
fun monthLabelFromDateLabel(dateLabel: String): String {
    val parts = dateLabel.split(", ")
    if (parts.size >= 2) {
        val year = parts.last().trim()
        // "May 16" → "May". substringBefore is safe on a token with no space.
        val monthToken = parts[parts.size - 2].trim().substringBefore(' ')
        val month = monthNamesByPrefix[monthToken.take(3).lowercase()]
        // The year guard keeps a comma-bearing non-date ("TBD, soon") from being
        // coerced into a month just because a word starts with "may"/"mar".
        if (month != null && year.length == 4 && year.all { it.isDigit() }) {
            // Full month name, not the "MMM" abbreviation: the day grid above
            // these group headers renders `monthLabelFromEpoch` ("MMMM yyyy"),
            // so "Apr 2026" under "April 2026" would read as two months.
            return "$month $year"
        }
    }
    return dateLabel
}

fun monthLabelFromEpoch(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val f = SimpleDateFormat("MMMM yyyy", Locale.US)
    return f.format(cal.time)
}

/**
 * Day-of-month for [dateLabel] only when it falls in [year]/[month]
 * (Calendar.MONTH 0-based). Prevents cross-month collisions on the grid.
 */
fun dayOfMonthInMonth(dateLabel: String, year: Int, month: Int): Int? {
    val c = BookingDateFormat.parseLabel(dateLabel) ?: return null
    if (c.get(Calendar.YEAR) != year || c.get(Calendar.MONTH) != month) return null
    return c.get(Calendar.DAY_OF_MONTH)
}

@Composable
fun BookingDayRow(
    dateLabel: String,
    timeLabel: String,
    title: String,
    subtitle: String,
    statusLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = space.lg, vertical = space.md)
            .then(Modifier),
    ) {
        Text(dateLabel, style = AppTheme.type.caption, color = colors.ink3)
        Spacer(Modifier.height(space.xs))
        Text(title, style = AppTheme.type.headline, color = colors.ink)
        Text("$timeLabel · $subtitle", style = AppTheme.type.footnote, color = colors.ink2)
        Spacer(Modifier.height(space.xs))
        Text(statusLabel, style = AppTheme.type.caption, color = colors.brand)
    }
    HRule(modifier = Modifier.padding(horizontal = space.lg))
}
