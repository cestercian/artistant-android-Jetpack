package `in`.artistant.app.feature.gigs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.MonthCalendarHeader
import `in`.artistant.app.designsystem.component.MonthDayGrid
import `in`.artistant.app.designsystem.component.selectedDayLabel
import `in`.artistant.app.designsystem.component.currentCalendarMonth
import `in`.artistant.app.designsystem.component.dayOfMonthInMonth
import `in`.artistant.app.designsystem.component.monthLabelFromEpoch
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Artist gigs tab — calendar-style list of bookings from `listForArtist()`.
 * Port slice of iOS `ArtistGigsView` (list + month headers until full grid lands).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistGigsScreen(
    onBookingClick: (bookingId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistGigsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    // Was pinned to `Calendar.getInstance()`: a gig in any other month showed in
    // the list but its month was unreachable on the grid.
    var displayedMonth by remember { mutableStateOf(currentCalendarMonth()) }
    val year = displayedMonth.year
    val month = displayedMonth.month
    // Clear the day selection when the month changes — `selectedDay` is a bare
    // day-of-month, so keeping it would filter to that day of the NEW month.
    val stepMonth: (Int) -> Unit = { delta ->
        displayedMonth = displayedMonth.stepped(delta)
        selectedDay = null
    }
    // Same clear-the-selection rule as stepping. The year is held: the menu
    // offers months, and the steppers stay the only way across a year boundary.
    val pickMonth: (Int) -> Unit = { picked ->
        displayedMonth = displayedMonth.copy(month = picked)
        selectedDay = null
    }
    val busyDays = remember(state.items, year, month) {
        state.items.mapNotNull { dayOfMonthInMonth(it.booking.date, year, month) }.toSet()
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.items.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize().background(colors.bg),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
            state.error != null && state.items.isEmpty() -> {
                EmptyState(
                    title = "Couldn't load gigs",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.items.isEmpty() -> {
                EmptyState(
                    title = "No gigs yet",
                    body = "Confirmed bookings will show up on your calendar.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                RevealOnAppear {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // No screen heading — see the matching note on the
                        // client Bookings tab. The month header IS this
                        // screen's title, and two serif titles a line apart
                        // read as a mistake.
                        MonthCalendarHeader(
                            monthLabel = monthLabelFromEpoch(displayedMonth.firstDayEpochMs),
                            onPrevMonth = { stepMonth(-1) },
                            onNextMonth = { stepMonth(1) },
                            onSelectMonth = pickMonth,
                        )
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
                        state.error?.let { msg ->
                            Text(
                                msg,
                                style = AppTheme.type.footnote,
                                color = colors.hot,
                                modifier = Modifier.padding(horizontal = space.lg),
                            )
                            Spacer(Modifier.height(space.md))
                        }
                        val selectedRows = if (selectedDay == null) {
                            null
                        } else {
                            state.items.filter {
                                dayOfMonthInMonth(it.booking.date, year, month) == selectedDay
                            }
                        }
                        // Same as the client calendar: a day tap filters, so the
                        // day it filtered to has to be on screen. The reference
                        // heads the selected day's schedule with its full date
                        // and that heading is the only "you are filtered" signal
                        // either app gives.
                        //
                        // The way out rides on that heading, in both branches: the
                        // only other escape is re-tapping the grid tile, which is
                        // invisible unless you remember tapping it.
                        selectedDay?.let { day ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = space.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    selectedDayLabel(year, month, day),
                                    style = AppTheme.type.headline,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "Show all",
                                    style = AppTheme.type.footnote,
                                    color = colors.brand,
                                    modifier = Modifier
                                        .heightIn(min = AppTheme.dimens.size.rowMin)
                                        .clickable(role = Role.Button) { selectedDay = null }
                                        .wrapContentHeight()
                                        .padding(start = space.md),
                                )
                            }
                            Spacer(Modifier.height(space.sm))
                        }
                        if (selectedRows != null && selectedRows.isEmpty()) {
                            Text(
                                "No gigs on this day",
                                style = AppTheme.type.footnote,
                                color = colors.ink3,
                                modifier = Modifier.padding(horizontal = space.lg),
                            )
                        } else {
                            val rows = if (selectedRows == null) {
                                viewModel.groupedByMonth()
                            } else {
                                listOf("Selected" to selectedRows)
                            }
                            // Named `groupLabel`, not `month`: `month` is now the
                            // grid's 0-based Calendar.MONTH in this scope, and the
                            // two are very different things to shadow.
                            rows.forEach { (groupLabel, group) ->
                                if (selectedDay == null) MonthCalendarHeader(monthLabel = groupLabel)
                                group.forEach { item ->
                                    val b = item.booking
                                    Column(
                                        Modifier
                                            .clickable { onBookingClick(b.id) }
                                            .padding(horizontal = space.lg, vertical = space.md),
                                    ) {
                                        Text(b.date, style = AppTheme.type.caption, color = colors.ink3)
                                        Spacer(Modifier.height(space.xs))
                                        Text(item.clientName, style = AppTheme.type.headline, color = colors.ink)
                                        Text(
                                            "${b.time} · ${b.status.label}",
                                            style = AppTheme.type.footnote,
                                            color = colors.ink2,
                                        )
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
    }
}
