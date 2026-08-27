package `in`.artistant.app.feature.gigs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    // Both hoisted out of the list body below: `LazyListScope` is not a
    // composable scope, so nothing inside the LazyColumn can `remember`.
    // Null = no day picked, which is a different answer from "that day is empty".
    val selectedRows = remember(state.items, selectedDay, year, month) {
        selectedDay?.let { day ->
            state.items.filter { dayOfMonthInMonth(it.booking.date, year, month) == day }
        }
    }
    // Grouped once per list rather than once per recomposition, and keyed on the
    // COLLECTED state: `groupedByMonth()` reads `_state.value` directly, which is
    // not a snapshot read, so this scope's subscription to `items` used to be
    // inherited from the branch conditions below rather than stated here.
    val monthGroups = remember(state.items) { viewModel.groupedByMonth() }

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
                // Not "Confirmed bookings": this list is everything that isn't
                // cancelled, so an unanswered `pending_confirm` request lands here
                // too and shades its day on the grid. Promising only confirmed
                // work and then shading a request the artist hasn't accepted is
                // the screen contradicting itself.
                EmptyState(
                    title = "No gigs yet",
                    body = "Requests and confirmed gigs will show up on your calendar.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                RevealOnAppear {
                    // One LazyColumn for the whole list, for the reason the
                    // artist dashboard spells out at length: same artist, same
                    // mid-range phone. As an eager `Column(verticalScroll)` an
                    // artist with a year of gigs composed, measured and laid out
                    // every row of every month — plus a header per month — before
                    // the first frame, while the dashboard next door composes
                    // only what's on screen.
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = space.xxl),
                    ) {
                        // No screen heading — see the matching note on the
                        // client Bookings tab. The month header IS this
                        // screen's title, and two serif titles a line apart
                        // read as a mistake.
                        item(key = "monthHeader") {
                            MonthCalendarHeader(
                                monthLabel = monthLabelFromEpoch(displayedMonth.firstDayEpochMs),
                                onPrevMonth = { stepMonth(-1) },
                                onNextMonth = { stepMonth(1) },
                                onSelectMonth = pickMonth,
                            )
                        }
                        item(key = "grid") {
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
                        }
                        state.error?.let { msg ->
                            item(key = "refreshError") {
                                Text(
                                    msg,
                                    style = AppTheme.type.footnote,
                                    color = colors.hot,
                                    modifier = Modifier.padding(horizontal = space.lg),
                                )
                                Spacer(Modifier.height(space.md))
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
                            item(key = "selectedDay") {
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
                        }
                        if (selectedRows != null && selectedRows.isEmpty()) {
                            item(key = "emptyDay") {
                                Text(
                                    "No gigs on this day",
                                    style = AppTheme.type.footnote,
                                    color = colors.ink3,
                                    modifier = Modifier.padding(horizontal = space.lg),
                                )
                            }
                        } else {
                            val rows = if (selectedRows == null) {
                                monthGroups
                            } else {
                                listOf("Selected" to selectedRows)
                            }
                            // Named `groupLabel`, not `month`: `month` is now the
                            // grid's 0-based Calendar.MONTH in this scope, and the
                            // two are very different things to shadow.
                            rows.forEach { (groupLabel, group) ->
                                if (selectedDay == null) {
                                    item(key = "month-$groupLabel") {
                                        MonthCalendarHeader(monthLabel = groupLabel)
                                    }
                                }
                                items(group, key = { "gig-${it.booking.id}" }) { row ->
                                    val b = row.booking
                                    Column(
                                        Modifier
                                            .clickable { onBookingClick(b.id) }
                                            .padding(horizontal = space.lg, vertical = space.md),
                                    ) {
                                        Text(b.date, style = AppTheme.type.caption, color = colors.ink3)
                                        Spacer(Modifier.height(space.xs))
                                        Text(row.clientName, style = AppTheme.type.headline, color = colors.ink)
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
                    }
                }
            }
        }
    }
}
