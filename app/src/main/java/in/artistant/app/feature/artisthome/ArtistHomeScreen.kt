package `in`.artistant.app.feature.artisthome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier

/**
 * Artist dashboard — M3 slice of iOS `ArtistHomeView`: "New requests" rail
 * for pending_confirm bookings; tap opens role-aware booking detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistHomeScreen(
    onBookingClick: (bookingId: String) -> Unit,
    onProfileClick: () -> Unit = {},
    onOpenWizard: () -> Unit = {},
    onScoreExplainer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArtistHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.pendingRequests.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize().background(colors.bg),
    ) {
        when {
            state.isLoading && state.pendingRequests.isEmpty() && state.error == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
            state.error != null && state.pendingRequests.isEmpty() -> {
                EmptyState(
                    title = "Couldn't load dashboard",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = space.lg, vertical = space.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Home", style = AppTheme.type.displaySub, color = colors.ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val ringValue = if (
                                state.gigs < ScoreBands.MIN_GIGS_FOR_RANK ||
                                    ScoreBands.tier(state.score ?: 0, state.gigs) == ScoreTier.New
                            ) {
                                null
                            } else {
                                state.score
                            }
                            ScoreRing(
                                value = ringValue,
                                size = AppTheme.dimens.size.ringMd,
                                stroke = 5.dp,
                                showLabel = false,
                                totalGigs = state.gigs,
                                modifier = Modifier
                                    .clickable(onClick = onScoreExplainer)
                                    .padding(end = space.sm),
                            )
                            IconButton(onClick = onProfileClick) {
                                Icon(Icons.Filled.Settings, contentDescription = "Profile & settings", tint = colors.ink2)
                            }
                        }
                    }
                    state.error?.let { msg ->
                        Text(
                            msg,
                            style = AppTheme.type.footnote,
                            color = colors.hot,
                            modifier = Modifier.padding(horizontal = space.lg),
                        )
                        Spacer(Modifier.height(space.md))
                    }
                    if (state.showFinishProfileCta) {
                        Column(Modifier.padding(horizontal = space.lg)) {
                            Text(
                                "Finish your profile so clients can find you.",
                                style = AppTheme.type.footnote,
                                color = colors.warm,
                            )
                            Spacer(Modifier.height(space.sm))
                            PrimaryButton(
                                text = "Finish your profile",
                                onClick = onOpenWizard,
                                fullWidth = true,
                            )
                        }
                        Spacer(Modifier.height(space.lg))
                    }
                    if (state.pendingRequests.isNotEmpty()) {
                        NewRequestsSection(
                            pending = state.pendingRequests,
                            onBookingClick = onBookingClick,
                        )
                    }
                    Spacer(Modifier.height(space.xxl))
                }
            }
        }
    }
}

@Composable
private fun NewRequestsSection(
    pending: List<`in`.artistant.app.data.model.Booking>,
    onBookingClick: (bookingId: String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.lg, vertical = space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("New requests", style = AppTheme.type.headline, color = colors.ink)
        Text("${pending.size} new", style = AppTheme.type.caption, color = colors.ink3)
    }
    pending.forEachIndexed { index, booking ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onBookingClick(booking.id) }
                .padding(horizontal = space.lg, vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Box(
                Modifier
                    .size(space.sm)
                    .clip(CircleShape)
                    .background(colors.warm),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    artistClientDisplayName(booking),
                    style = AppTheme.type.callout,
                    color = colors.ink,
                )
                Text(
                    "${booking.date} · ${booking.time}",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                )
            }
            Text(
                formatInr(booking.fee),
                style = AppTheme.type.monoMedium,
                color = colors.ink,
            )
        }
        if (index < pending.lastIndex) {
            HRule(modifier = Modifier.padding(horizontal = space.lg))
        }
    }
}
