package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * One schedule row below the calendar (iOS `CalendarDayEvent`). [bookingId] lets
 * a tap open that booking's detail; [tint] is the left-rule color (usually the
 * booking-status tint).
 */
data class CalendarDayEvent(
    val title: String,
    val timeLabel: String,
    val tint: Color,
    val bookingId: String? = null,
)

/**
 * Apple-Calendar-style month grid (port of iOS `MonthCalendarView`). The full
 * month renders at once — leading/trailing cells are filled with the adjacent
 * months' dates (dark grey, inert) so every row is complete. Booked/event days
 * FILL lime; other current-month days carry a status dot (orange = unavailable,
 * grey = open). The month name is a dropdown; tapping a day selects it and shows
 * that day's [eventsForDay] schedule below (when [showSchedule]).
 *
 * No internal scroll — embeds inside the host's scroll (Bookings/Gigs).
 */
@Composable
fun MonthCalendar(
    month: LocalDate,
    bookedDates: Set<LocalDate>,
    modifier: Modifier = Modifier,
    unavailableDates: Set<LocalDate> = emptySet(),
    eventsForDay: ((LocalDate) -> List<CalendarDayEvent>)? = null,
    onSelectEvent: ((CalendarDayEvent) -> Unit)? = null,
    showSchedule: Boolean = true,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val today = remember { LocalDate.now() }

    var displayed by remember { mutableStateOf(YearMonth.from(month)) }
    // Schedule selection defaults to today when it's in the shown month, else the 1st.
    var selected by remember {
        mutableStateOf(if (YearMonth.from(today) == YearMonth.from(month)) today else month.withDayOfMonth(1))
    }

    fun setMonth(m: Int) {
        val ym = displayed.withMonth(m)
        displayed = ym
        selected = if (YearMonth.from(today) == ym) today else ym.atDay(1)
    }

    fun hasEvents(d: LocalDate): Boolean =
        eventsForDay?.let { it(d).isNotEmpty() } ?: bookedDates.contains(d)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.lg)) {
        // Header — month dropdown + year.
        MonthHeader(displayed, onPick = ::setMonth)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekdayHeader()
            weeksOf(displayed).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    week.forEach { day ->
                        DayTile(
                            day = day,
                            isToday = day.date == today,
                            isSelected = day.date == selected,
                            booked = day.inMonth && hasEvents(day.date),
                            unavailable = day.inMonth && unavailableDates.contains(day.date),
                            onClick = { if (day.inMonth) selected = day.date },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (showSchedule) {
            HRule()
            ScheduleSection(
                day = selected,
                events = eventsForDay?.invoke(selected)
                    ?: if (bookedDates.contains(selected)) {
                        listOf(CalendarDayEvent("Booked", "All day", colors.brand))
                    } else {
                        emptyList()
                    },
                onSelectEvent = onSelectEvent,
            )
        }
    }
}

@Composable
private fun MonthHeader(displayed: YearMonth, onPick: (Int) -> Unit) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Bottom) {
        Box {
            Row(
                Modifier.clickable { open = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    displayed.month.getDisplayName(TextStyle.FULL, Locale.US),
                    style = AppTheme.type.displayTitle,
                    color = colors.ink,
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change month", tint = colors.ink3)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                (1..12).forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                java.time.Month.of(m).getDisplayName(TextStyle.FULL, Locale.US),
                                color = if (m == displayed.monthValue) colors.brand else colors.ink,
                            )
                        },
                        onClick = { onPick(m); open = false },
                    )
                }
            }
        }
        Text(
            "  ${displayed.year}",
            style = AppTheme.type.monoSmall,
            color = colors.ink3,
        )
    }
}

@Composable
private fun WeekdayHeader() {
    val colors = AppTheme.colors
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l ->
            Text(
                l,
                style = AppTheme.type.monoSmall,
                color = if (i >= 5) colors.ink4 else colors.ink3,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DayTile(
    day: Day,
    isToday: Boolean,
    isSelected: Boolean,
    booked: Boolean,
    unavailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.dimens.radii.sm)

    if (!day.inMonth) {
        // Adjacent-month filler — dark grey, non-interactive.
        Column(
            modifier
                .height(52.dp)
                .clip(shape)
                .background(colors.bgCard.copy(alpha = 0.35f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("${day.number}", style = AppTheme.type.monoMedium, color = colors.ink4)
        }
        return
    }

    // Ring: selected → lime (ink over a lime fill); today unselected/unbooked → ink;
    // booked unselected → none; else soft hairline.
    val ring = when {
        isSelected -> if (booked) colors.ink else colors.brand
        isToday && !booked -> colors.ink
        booked -> Color.Transparent
        else -> colors.lineSoft
    }
    val dot = when {
        booked -> Color.Transparent
        unavailable -> colors.warm
        else -> colors.ink3
    }
    Column(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (booked) colors.brand else colors.bgCard)
            .border(if (isSelected || (isToday && !booked)) 1.5.dp else 1.dp, ring, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${day.number}",
            style = AppTheme.type.monoMedium.copy(
                fontWeight = if (booked || isToday) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (booked) colors.brandInk else colors.ink,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(dot),
        )
    }
}

@Composable
private fun ScheduleSection(
    day: LocalDate,
    events: List<CalendarDayEvent>,
    onSelectEvent: ((CalendarDayEvent) -> Unit)?,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text(
            day.format(FULL_DATE),
            style = AppTheme.type.headline,
            color = colors.ink,
        )
        if (events.isEmpty()) {
            Text("Nothing scheduled", style = AppTheme.type.body, color = colors.ink3)
        } else {
            events.forEach { EventRow(it, onSelectEvent) }
        }
    }
}

@Composable
private fun EventRow(event: CalendarDayEvent, onSelectEvent: ((CalendarDayEvent) -> Unit)?) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
            .background(colors.bgCard)
            .clickable(enabled = onSelectEvent != null) { onSelectEvent?.invoke(event) }
            .padding(space.md),
        horizontalArrangement = Arrangement.spacedBy(space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(event.tint),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.title, style = AppTheme.type.headline, color = colors.ink)
            Text(event.timeLabel, style = AppTheme.type.monoSmall, color = colors.ink3)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink4,
        )
    }
}

// --- Week construction (Monday-first, adjacent-month fill) --------------------

/** A calendar cell; [inMonth] false marks an adjacent-month filler. */
private data class Day(val number: Int, val date: LocalDate, val inMonth: Boolean)

/** The month as complete Monday-first weeks with prev/next-month fillers. */
private fun weeksOf(ym: YearMonth): List<List<Day>> {
    val first = ym.atDay(1)
    // DayOfWeek: MON=1..SUN=7 → leading pad = (value-1).
    val leading = first.dayOfWeek.value - 1
    val days = mutableListOf<Day>()
    for (i in leading downTo 1) {
        val d = first.minusDays(i.toLong())
        days += Day(d.dayOfMonth, d, inMonth = false)
    }
    for (dn in 1..ym.lengthOfMonth()) {
        val d = ym.atDay(dn)
        days += Day(dn, d, inMonth = true)
    }
    while (days.size % 7 != 0) {
        val d = days.last().date.plusDays(1)
        days += Day(d.dayOfMonth, d, inMonth = false)
    }
    return days.chunked(7)
}

private val FULL_DATE = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
