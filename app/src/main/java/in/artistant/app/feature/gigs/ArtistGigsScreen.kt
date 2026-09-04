package `in`.artistant.app.feature.gigs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.ClockColumn
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.MonthCalendarHeader
import `in`.artistant.app.designsystem.component.MonthDayGrid
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.bookingStatusTone
import `in`.artistant.app.designsystem.component.currentCalendarMonth
import `in`.artistant.app.designsystem.component.monthLabelFromEpoch
import `in`.artistant.app.designsystem.component.selectedDayLabel
import `in`.artistant.app.designsystem.component.splitClockLabel
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Artist gigs — design screen 36, "calendar, then the day".
 *
 * The rewrite is a deletion. This screen used to draw the month grid AND, below
 * it, every gig of every month grouped under its own header — so the same
 * booking appeared twice, once as a shaded tile and once as a row, and the list
 * was the thing you actually read. The design's own note is "no segments and no
 * separate list — the month is the index, the day is the detail", so the list
 * below the grid is now exactly one day's gigs and a day is always selected.
 *
 * That last part is what makes it work: with a nullable selection the screen has
 * a fourth state (grid + nothing) that says less than the grid alone already
 * did. [initialDayFor] picks today when today is in view and the month's first
 * gig otherwise, so opening the tab lands on something worth reading.
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
    val dimens = AppTheme.dimens
    val space = dimens.space

    var displayedMonth by remember { mutableStateOf(currentCalendarMonth()) }
    val year = displayedMonth.year
    val month = displayedMonth.month

    val busyDays = remember(state.items, year, month) {
        state.items
            .filter { it.year == year && it.month == month }
            .mapNotNull { it.dayOfMonth }
            .toSet()
    }

    // Selection is never null. It re-derives whenever the month or the data
    // changes, which also covers the case where the day it pointed at stops
    // existing (31 → a 30-day month).
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val effectiveDay = selectedDay?.takeIf { it in 1..31 }
        ?: remember(year, month, busyDays) { initialDayFor(year, month, busyDays) }

    val stepMonth: (Int) -> Unit = { delta ->
        displayedMonth = displayedMonth.stepped(delta)
        // Cleared, not carried: `selectedDay` is a bare day-of-month, so keeping
        // it would silently point at that day of the NEW month.
        selectedDay = null
    }
    val pickMonth: (Int) -> Unit = { picked ->
        displayedMonth = displayedMonth.copy(month = picked)
        selectedDay = null
    }

    val dayRows = remember(state.items, effectiveDay, year, month) {
        state.items.filter {
            it.year == year && it.month == month && it.dayOfMonth == effectiveDay
        }
    }
    val monthLine = remember(state.items, year, month) {
        gigsMonthSummary(state.items, year, month)
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.items.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize().background(colors.page),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentInk)
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
                // cancelled, so an unanswered `pending_confirm` request lands
                // here too and shades its day on the grid. Promising only
                // confirmed work and then shading a request the artist hasn't
                // accepted is the screen contradicting itself.
                EmptyState(
                    title = "No gigs yet",
                    body = "Requests and confirmed gigs will show up on your calendar.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> RevealOnAppear {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = dimens.size.listTailroom),
                ) {
                    item(key = "masthead") {
                        Column(Modifier.padding(horizontal = dimens.component.gutter)) {
                            Text("Gigs", style = AppTheme.type.displaySub, color = colors.ink)
                            Text(
                                monthLine,
                                style = AppTheme.type.subtitle,
                                color = colors.ink4,
                                modifier = Modifier.padding(top = space.xs),
                            )
                        }
                    }
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
                            selectedDay = effectiveDay,
                            // No toggle-off: with the list below showing exactly
                            // one day, de-selecting would leave the screen with
                            // nothing under the grid.
                            onDayClick = { selectedDay = it },
                        )
                        Spacer(Modifier.height(space.lg))
                    }
                    state.error?.let { msg ->
                        item(key = "refreshError") {
                            Text(
                                msg,
                                style = AppTheme.type.footnote,
                                color = colors.danger,
                                modifier = Modifier.padding(horizontal = dimens.component.gutter),
                            )
                            Spacer(Modifier.height(space.md))
                        }
                    }
                    item(key = "dayHeading") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimens.component.gutter),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                selectedDayLabel(year, month, effectiveDay),
                                style = AppTheme.type.sectionTitle,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                dayHeadingCount(dayRows.size),
                                style = AppTheme.type.caption,
                                color = colors.ink4,
                            )
                        }
                        Spacer(Modifier.height(space.md))
                    }
                    if (dayRows.isEmpty()) {
                        item(key = "emptyDay") {
                            Text(
                                "Nothing on this day.",
                                style = AppTheme.type.subtitle,
                                color = colors.ink4,
                                modifier = Modifier.padding(horizontal = dimens.component.gutter),
                            )
                        }
                    } else {
                        items(dayRows, key = { "gig-${it.booking.id}" }) { row ->
                            GigDayRow(
                                row = row,
                                onClick = { onBookingClick(row.booking.id) },
                                modifier = Modifier.padding(
                                    horizontal = dimens.component.gutter,
                                    vertical = space.xs,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One gig on the selected day: the time in mono down the left, the act and the
 * venue in the middle, the fee and the state on the right.
 */
@Composable
private fun GigDayRow(
    row: ArtistGigListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val booking = row.booking
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(dimens.component.cardPad),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        // "8:00" over "pm", the way design 36 draws it — see [ClockColumn]. It
        // was one 22sp line inside a fixed 62dp column, which is not wide enough
        // for any evening slot the booking flow can produce.
        val (clock, meridiem) = splitClockLabel(booking.time)
        ClockColumn(clock.takeIf { it.isNotBlank() } ?: "—", meridiem)
        Box(
            Modifier
                .width(dimens.size.stroke)
                .height(dimens.component.rowAvatar)
                .clip(CircleShape)
                .background(colors.accent),
        )
        Column(Modifier.weight(1f)) {
            Text(
                row.clientName,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                booking.packageName?.takeIf { it.isNotBlank() && it != "Custom" },
                booking.venue.takeIf { it.isNotBlank() && it != "TBD" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatInr(booking.fee),
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
            )
            Spacer(Modifier.height(dimens.space.xs))
            // The app's one status→tone map, shared with Bookings, Messages
            // and Booking detail. A second one here would drift.
            Pill(booking.status.label, tone = bookingStatusTone(booking.status))
        }
    }
}

/**
 * Which day the grid opens on.
 *
 * Today when today is in the displayed month — that is the day an artist means
 * by "my gigs" — otherwise the month's first booked day, so stepping into
 * October lands on the gig rather than on an arbitrary 1st. Falls back to the
 * 1st when the month is empty, which is the only case where any answer is as
 * good as another.
 */
internal fun initialDayFor(
    year: Int,
    month: Int,
    busyDays: Set<Int>,
    nowMs: Long = System.currentTimeMillis(),
): Int {
    val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    if (now.get(java.util.Calendar.YEAR) == year && now.get(java.util.Calendar.MONTH) == month) {
        return now.get(java.util.Calendar.DAY_OF_MONTH)
    }
    return busyDays.minOrNull() ?: 1
}
