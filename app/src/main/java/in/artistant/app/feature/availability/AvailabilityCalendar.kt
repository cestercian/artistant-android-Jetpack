package `in`.artistant.app.feature.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import `in`.artistant.app.designsystem.theme.AppTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * Design screen 22 — the month an artist's nights actually live in.
 *
 * Three readings, and the third is the point:
 *
 * - **Accent cell** — a confirmed booking. Gone.
 * - **Plain cell** — a night on a weekday the act plays. Askable.
 * - **Dimmed cell** — a weekday the act does not play, or a date in the past.
 *
 * When [bookingsUnavailable] the calendar shades NOTHING and says so above the
 * grid. It would be trivial to render the same grid with an empty booked set and
 * let it read as a clear month; that is exactly the bug. An artist who trusts an
 * unshaded calendar hands out a date they already sold, and the app has no way to
 * take it back.
 */
@Composable
fun AvailabilityCalendar(
    month: YearMonth,
    days: Set<String>,
    bookedDates: Set<LocalDate>,
    bookingsUnavailable: Boolean,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val cells = monthGrid(month)
    val bookedInMonth = bookedDates.count { YearMonth.from(it) == month }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.xl))
            .background(colors.surface3)
            .padding(dimens.component.heroPad),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                monthLabel(month),
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            MonthStep(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = "Previous month",
                onClick = { onMonthChange(month.minusMonths(1)) },
            )
            Spacer(Modifier.size(dimens.space.xs))
            MonthStep(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                label = "Next month",
                onClick = { onMonthChange(month.plusMonths(1)) },
            )
        }

        if (bookingsUnavailable) {
            Text(
                "Couldn't load your booked nights, so nothing below is marked as " +
                    "taken. Check a date in Gigs before you agree to it.",
                style = AppTheme.type.caption,
                color = colors.danger,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            WeekdayInitials.forEach { initial ->
                Text(
                    initial,
                    style = AppTheme.type.monoWeekday,
                    color = colors.ink4,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Seven at a time rather than a LazyVerticalGrid: the whole month is at
        // most six rows, it sits inside a vertically scrolling page, and nesting
        // a lazy grid in a scroller is both a crash and a lie about the cost.
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.xs / 2),
            ) {
                week.forEach { cell ->
                    DayCell(
                        date = cell.date,
                        booked = cell.date != null && !bookingsUnavailable && cell.date in bookedDates,
                        plays = cell.date != null && playsOn(cell.date, days),
                        past = cell.date != null && cell.date.isBefore(today),
                        isToday = cell.date == today,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad the last row out to seven so its cells keep the width the
                // rows above them have. Without this the final week stretches.
                repeat(DAYS_PER_WEEK - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Text(
            text = if (bookingsUnavailable) {
                "You play ${days.size} day${if (days.size == 1) "" else "s"} a week."
            } else {
                monthSummary(bookedInMonth, days.size)
            },
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
}

@Composable
private fun MonthStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .size(dimens.size.rowMin)
            .clip(CircleShape)
            .background(AppTheme.colors.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.ink2)
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    booked: Boolean,
    plays: Boolean,
    past: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    if (date == null) {
        Spacer(modifier)
        return
    }
    val fill = when {
        booked -> colors.accent
        past || !plays -> colors.page
        else -> colors.surface
    }
    val ink = when {
        booked -> colors.onAccent
        past || !plays -> colors.ink4
        else -> colors.ink
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(fill)
            .then(
                // Today gets a rim rather than a fill: a filled "today" competes
                // with a booked night for the same accent, and the booking is the
                // fact the artist came here for.
                if (isToday && !booked) {
                    Modifier.border(
                        dimens.size.stroke,
                        colors.accent,
                        RoundedCornerShape(dimens.radii.md),
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = buildString {
                    append(date.dayOfMonth)
                    append(
                        when {
                            booked -> ", booked"
                            past -> ", past"
                            !plays -> ", you don't play this day"
                            else -> ", open"
                        },
                    )
                    if (isToday) append(", today")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = AppTheme.type.monoCount.copy(
                fontWeight = if (booked || isToday) FontWeight.Bold else FontWeight.Normal,
            ),
            color = ink,
        )
    }
}

private const val DAYS_PER_WEEK = 7
