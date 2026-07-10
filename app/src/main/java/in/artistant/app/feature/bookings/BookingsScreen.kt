package `in`.artistant.app.feature.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.CalendarDayEvent
import `in`.artistant.app.designsystem.component.MonthCalendar
import `in`.artistant.app.designsystem.theme.AppTheme
import java.time.LocalDate

/**
 * Client Bookings tab (port of iOS `BookingsView`). A full month [MonthCalendar]
 * fed with the client's bookings-by-day (booked days fill lime); the per-day
 * schedule below lists that day's bookings, tinted by status, and tapping one
 * opens its detail via [onOpenBooking].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(
    onOpenBooking: (String) -> Unit,
    viewModel: BookingsViewModel = hiltViewModel(),
) {
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()
    val pendingBooking by viewModel.pendingBookingId.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    // Push deep-link consumer (booking_confirmed / reminder / review) — clear BEFORE navigating so
    // a re-composition can't re-push the same booking detail (mirrors MessagesScreen).
    LaunchedEffect(pendingBooking) {
        pendingBooking?.let {
            viewModel.consumePendingBooking()
            onOpenBooking(it)
        }
    }

    // Recompute grouping whenever the list changes (bookings is the collect key).
    val byDay = remember(bookings) { viewModel.bookingsByDay() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(colors.bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            refreshError?.let { msg ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = space.xl, vertical = space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    // Real message from the store (e.g. "Couldn't load bookings.")
                    // so the banner isn't a generic lie about what failed.
                    Text(
                        msg,
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = colors.hot,
                        modifier = Modifier.weight(1f),
                    )
                    // Working recovery — retry re-runs the load; dismiss clears the
                    // banner. Both are real clickables (was a dead Text before).
                    Text(
                        "Retry",
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = colors.brand,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = !refreshing) { viewModel.refresh() }
                            .padding(space.xs),
                    )
                    Text(
                        "Dismiss",
                        style = AppTheme.type.caption,
                        color = colors.ink3,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.dismissError() }
                            .padding(space.xs),
                    )
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = space.lg, vertical = space.md),
            ) {
                MonthCalendar(
                    month = LocalDate.now(),
                    bookedDates = byDay.keys,
                    eventsForDay = { day ->
                        (byDay[day] ?: emptyList()).map { b ->
                            CalendarDayEvent(
                                title = viewModel.artist(b.artistId)?.name ?: b.venue,
                                timeLabel = b.timeLabel,
                                tint = b.status.calendarTint(colors),
                                bookingId = b.id,
                            )
                        }
                    },
                    onSelectEvent = { event -> event.bookingId?.let(onOpenBooking) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
