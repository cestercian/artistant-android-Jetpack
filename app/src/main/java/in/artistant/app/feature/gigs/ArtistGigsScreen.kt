package `in`.artistant.app.feature.gigs

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Gigs",
                        style = AppTheme.type.displaySub,
                        color = colors.ink,
                        modifier = Modifier.padding(space.lg),
                    )
                    state.error?.let { msg ->
                        Text(
                            msg,
                            style = AppTheme.type.footnote,
                            color = colors.hot,
                            modifier = Modifier.padding(horizontal = space.lg),
                        )
                        Spacer(Modifier.height(space.md))
                    }
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
                    Spacer(Modifier.height(space.xxl))
                }
            }
        }
    }
}
