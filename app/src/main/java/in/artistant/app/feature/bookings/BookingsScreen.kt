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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.MonthCalendarHeader
import `in`.artistant.app.designsystem.theme.AppTheme

/** Client bookings tab — upcoming/pending list with month headers. */
@Composable
fun BookingsScreen(
    onBookingClick: (bookingId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

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
                viewModel.groupedByMonth().forEach { (month, rows) ->
                    MonthCalendarHeader(monthLabel = month)
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
                Spacer(Modifier.height(space.xxl))
            }
        }
    }
}
