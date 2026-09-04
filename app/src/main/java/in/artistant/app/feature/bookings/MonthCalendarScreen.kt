package `in`.artistant.app.feature.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.designsystem.component.CalendarMonth
import `in`.artistant.app.designsystem.component.DayEventRow
import `in`.artistant.app.designsystem.component.DetailHeader
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.MonthCalendarCard
import `in`.artistant.app.designsystem.component.currentCalendarMonth
import `in`.artistant.app.designsystem.component.dayOfMonthInMonth
import `in`.artistant.app.designsystem.component.selectedDayLabel
import `in`.artistant.app.designsystem.component.splitClockLabel
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Screen 78 — the month, and one day's schedule under it.
 *
 * It is a screen of its own rather than a block on the Bookings tab because
 * that is what the design makes it, and the reason is in its note: it is the
 * DOCUMENTATION of the shared component. Bookings reaches it from the calendar
 * circle in its header; Gigs will reach it the same way. Both then get the
 * identical grid, the identical five states and the identical legend, because
 * there is only one of each.
 *
 * The client seat passes no `unavailableDays`: an availability block is an
 * artist's concept, and the legend drops that entry rather than naming a state
 * this calendar can never be in.
 */
@Composable
fun MonthCalendarScreen(
    onBack: () -> Unit,
    onBookingClick: (bookingId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    var month by remember { mutableStateOf(currentCalendarMonth()) }
    // Stepping and picking both clear the selection: `selectedDay` is a bare
    // day-of-month, so carrying "16" into the next month would silently point the
    // schedule below at a different date than the one that is highlighted.
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    val onMonth = remember(state.items, month) {
        state.items.mapNotNull { item ->
            dayOfMonthInMonth(item.booking.date, month.year, month.month)?.let { it to item }
        }
    }
    // A cancelled booking is not a gig on the calendar. It stays in the Past
    // list, where the record is the point, but a lime tile on the 12th for a
    // booking nobody is playing would be a lie the grid tells at a glance.
    val busyDays = onMonth
        .filter { it.second.booking.status != BookingStatus.Cancelled }
        .map { it.first }
        .toSet()
    val daysEvents = selectedDay?.let { day -> onMonth.filter { it.first == day }.map { it.second } }
        .orEmpty()

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
        ) {
            DetailHeader(
                title = "Month calendar",
                subtitle = "Shared by Bookings and Gigs",
                onBack = onBack,
            )
            MonthCalendarCard(
                month = month,
                busyDays = busyDays,
                selectedDay = selectedDay,
                onDayClick = { day -> selectedDay = if (selectedDay == day) null else day },
                onStepMonth = { delta ->
                    month = month.stepped(delta)
                    selectedDay = null
                },
                // Picking holds the year, which is why stepping survives beside it.
                onSelectMonth = { picked ->
                    month = CalendarMonth(month.year, picked)
                    selectedDay = null
                },
            )

            selectedDay?.let { day ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        selectedDayLabel(month.year, month.month, day),
                        style = AppTheme.type.sectionTitle,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when (daysEvents.size) {
                            0 -> "No events"
                            1 -> "1 event"
                            else -> "${daysEvents.size} events"
                        },
                        style = AppTheme.type.subtitle,
                        color = colors.ink4,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
                    daysEvents.forEach { item ->
                        val (clock, meridiem) = splitClockLabel(item.booking.time)
                        DayEventRow(
                            time = clock,
                            meridiem = meridiem,
                            title = item.artistName,
                            subtitle = listOfNotNull(
                                item.booking.packageName?.takeIf { it.isNotBlank() },
                                item.booking.venue.takeIf { it.isNotBlank() },
                            ).joinToString(" · ").takeIf { it.isNotBlank() },
                            // The spine is the accent only for a gig that is
                            // actually on: a cancelled row keeps its place in the
                            // day but not its claim on it.
                            accented = item.booking.status != BookingStatus.Cancelled,
                            onClick = { onBookingClick(item.booking.id) },
                        )
                    }
                }
            }

            if (selectedDay == null) {
                EmptyState(
                    title = if (busyDays.isEmpty()) "Nothing this month" else "Pick a day",
                    body = if (busyDays.isEmpty()) {
                        "No bookings land in this month. Step to another one, or pick a day " +
                            "to see it empty."
                    } else {
                        "Tap a highlighted day to see what is on it."
                    },
                    icon = Icons.Filled.CalendarMonth,
                )
            }
            Spacer(Modifier.height(dimens.chrome.contentTailroom))
        }
    }
}
