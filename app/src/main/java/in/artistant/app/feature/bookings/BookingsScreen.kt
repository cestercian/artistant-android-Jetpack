package `in`.artistant.app.feature.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.MonthCalendarHeader
import `in`.artistant.app.designsystem.component.MonthDayGrid
import `in`.artistant.app.designsystem.component.dayOfMonthInMonth
import `in`.artistant.app.designsystem.component.monthLabelFromEpoch
import `in`.artistant.app.designsystem.theme.AppTheme
import java.util.Calendar

/** Client bookings tab — month day grid + upcoming/pending list. */
@Composable
fun BookingsScreen(
    onBookingClick: (bookingId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val cal = remember { Calendar.getInstance() }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val busyDays = remember(state.items, year, month) {
        state.items.mapNotNull { dayOfMonthInMonth(it.booking.date, year, month) }.toSet()
    }
    val filtered = remember(state.items, selectedDay, year, month) {
        if (selectedDay == null) state.items
        else state.items.filter { dayOfMonthInMonth(it.booking.date, year, month) == selectedDay }
    }

    when {
        state.isLoading && state.items.isEmpty() -> {
            Box(modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
        }
        state.error != null && state.items.isEmpty() -> {
            EmptyState(
                title = "Couldn't load bookings",
                body = state.error,
                actionLabel = "Retry",
                onAction = viewModel::refresh,
            )
        }
        state.items.isEmpty() -> {
            EmptyState(
                title = "No bookings yet",
                body = "When you send a request, it'll show up here.",
            )
        }
        else -> {
            Column(
                modifier
                    .fillMaxSize()
                    .background(colors.bg)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Bookings",
                    style = AppTheme.type.displaySub,
                    color = colors.ink,
                    modifier = Modifier.padding(space.lg),
                )
                MonthCalendarHeader(monthLabel = monthLabelFromEpoch(cal.timeInMillis))
                MonthDayGrid(
                    year = year,
                    month = month,
                    busyDays = busyDays,
                    selectedDay = selectedDay,
                    onDayClick = { day ->
                        selectedDay = if (selectedDay == day) null else day
                    },
                )
                Spacer(Modifier.height(space.lg))
                if (selectedDay != null && filtered.isEmpty()) {
                    Text(
                        "No bookings on this day",
                        style = AppTheme.type.footnote,
                        color = colors.ink3,
                        modifier = Modifier
                            .padding(horizontal = space.lg)
                            .clickable { selectedDay = null },
                    )
                } else {
                    val groups = if (selectedDay == null) {
                        viewModel.groupedByMonth()
                    } else {
                        listOf("Selected" to filtered)
                    }
                    groups.forEach { (monthLabel, rows) ->
                        if (selectedDay == null) MonthCalendarHeader(monthLabel = monthLabel)
                        rows.forEach { item ->
                            val b = item.booking
                            Column(
                                Modifier
                                    .clickable { onBookingClick(b.id) }
                                    .padding(horizontal = space.lg, vertical = space.md),
                            ) {
                                Text(b.date, style = AppTheme.type.caption, color = colors.ink3)
                                Spacer(Modifier.height(space.xs))
                                Text(item.artistName, style = AppTheme.type.headline, color = colors.ink)
                                Text(
                                    "${b.time} · ${b.venue}",
                                    style = AppTheme.type.footnote,
                                    color = colors.ink2,
                                )
                                Spacer(Modifier.height(space.xs))
                                Text(b.status.label, style = AppTheme.type.caption, color = colors.brand)
                            }
                            HRule(modifier = Modifier.padding(horizontal = space.lg))
                        }
                    }
                }
                Spacer(Modifier.height(space.xxl))
            }
        }
    }
}
