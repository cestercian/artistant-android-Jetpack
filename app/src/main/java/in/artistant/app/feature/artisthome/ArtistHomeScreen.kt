package `in`.artistant.app.feature.artisthome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.component.Sparkline
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier

/**
 * Artist dashboard — port of iOS ArtistHomeView (compact): earnings sparkline,
 * 14-day busy strip, New requests, open quotes, Up next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistHomeScreen(
    onBookingClick: (bookingId: String) -> Unit,
    onGigRequestClick: (requestId: String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onOpenWizard: () -> Unit = {},
    onScoreExplainer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArtistHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val emptyDash = state.pendingRequests.isEmpty() &&
        state.openQuotes.isEmpty() &&
        state.upcoming.isEmpty()

    PullToRefreshBox(
        isRefreshing = state.isLoading && !emptyDash,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize().background(colors.bg),
    ) {
        when {
            state.isLoading && emptyDash && state.error == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
            state.error != null && emptyDash -> {
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

                    // Earnings hero — matchmaker counts bookings as ₹ fee (dormant escrow).
                    Column(Modifier.padding(horizontal = space.lg)) {
                        Text("Last 7 days", style = AppTheme.type.caption, color = colors.ink3)
                        Text(
                            formatInr(state.earningsTotal),
                            style = AppTheme.type.displaySmall,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(space.sm))
                        Sparkline(values = state.earningsSeries)
                    }
                    Spacer(Modifier.height(space.xl))

                    // 14-day busy strip
                    Text(
                        "Next 14 days",
                        style = AppTheme.type.headline,
                        color = colors.ink,
                        modifier = Modifier.padding(horizontal = space.lg),
                    )
                    Spacer(Modifier.height(space.sm))
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = space.lg),
                        horizontalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        state.dayStrip.forEach { (key, label) ->
                            val busy = key in state.busyDayKeys
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(AppTheme.dimens.size.rowMin)
                                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                                    .background(if (busy) colors.brand.copy(alpha = 0.15f) else colors.bgSoft)
                                    .border(
                                        width = AppTheme.dimens.size.hairline,
                                        color = if (busy) colors.brand else colors.lineSoft,
                                        shape = RoundedCornerShape(AppTheme.dimens.radii.sm),
                                    )
                                    .padding(vertical = space.sm),
                            ) {
                                Text(label, style = AppTheme.type.caption, color = colors.ink3, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(space.xs))
                                Text(
                                    if (busy) "Busy" else "Open",
                                    style = AppTheme.type.monoSmall,
                                    color = if (busy) colors.brand else colors.ink2,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(space.xl))

                    if (state.pendingRequests.isNotEmpty()) {
                        NewRequestsSection(
                            pending = state.pendingRequests,
                            onBookingClick = onBookingClick,
                        )
                        Spacer(Modifier.height(space.lg))
                    }
                    if (state.openQuotes.isNotEmpty()) {
                        OpenQuotesSection(
                            quotes = state.openQuotes,
                            onGigRequestClick = onGigRequestClick,
                        )
                        Spacer(Modifier.height(space.lg))
                    }
                    if (state.upcoming.isNotEmpty()) {
                        UpcomingSection(
                            upcoming = state.upcoming,
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
    pending: List<Booking>,
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
        BookingRailRow(
            title = artistClientDisplayName(booking),
            subtitle = "${booking.date} · ${booking.time}",
            trailing = formatInr(booking.fee),
            accentDot = true,
            onClick = { onBookingClick(booking.id) },
        )
        if (index < pending.lastIndex) {
            HRule(modifier = Modifier.padding(horizontal = space.lg))
        }
    }
}

@Composable
private fun OpenQuotesSection(
    quotes: List<StoredRequest>,
    onGigRequestClick: (requestId: String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Text(
        "Quote requests",
        style = AppTheme.type.headline,
        color = colors.ink,
        modifier = Modifier.padding(horizontal = space.lg, vertical = space.md),
    )
    quotes.forEachIndexed { index, req ->
        BookingRailRow(
            title = req.raw.client.ifBlank { "Client" },
            subtitle = "${req.raw.date} · ${req.status.label}",
            trailing = formatInr(req.counterAmount ?: req.raw.amount),
            accentDot = true,
            onClick = { onGigRequestClick(req.id) },
        )
        if (index < quotes.lastIndex) {
            HRule(modifier = Modifier.padding(horizontal = space.lg))
        }
    }
}

@Composable
private fun UpcomingSection(
    upcoming: List<Booking>,
    onBookingClick: (bookingId: String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Text(
        "Up next",
        style = AppTheme.type.headline,
        color = colors.ink,
        modifier = Modifier.padding(horizontal = space.lg, vertical = space.md),
    )
    upcoming.forEachIndexed { index, booking ->
        BookingRailRow(
            title = artistClientDisplayName(booking),
            subtitle = "${booking.date} · ${booking.venue}",
            trailing = booking.time,
            accentDot = false,
            onClick = { onBookingClick(booking.id) },
        )
        if (index < upcoming.lastIndex) {
            HRule(modifier = Modifier.padding(horizontal = space.lg))
        }
    }
}

@Composable
private fun BookingRailRow(
    title: String,
    subtitle: String,
    trailing: String,
    accentDot: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = space.lg, vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        if (accentDot) {
            Box(
                Modifier
                    .size(space.sm)
                    .clip(CircleShape)
                    .background(colors.warm),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.callout, color = colors.ink)
            Text(subtitle, style = AppTheme.type.footnote, color = colors.ink3)
        }
        Text(trailing, style = AppTheme.type.monoMedium, color = colors.ink)
    }
}
