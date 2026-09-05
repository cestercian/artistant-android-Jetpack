package `in`.artistant.app.feature.artisthome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.ScreenHeader
import `in`.artistant.app.designsystem.component.SectionHeader
import `in`.artistant.app.designsystem.component.SkeletonPage
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.cappedFontScale

/**
 * The artist studio — design screens 09 (money), 85 (cold) and 86 (unavailable).
 *
 * Three things drive the whole rewrite:
 *
 * **Money first, score second, requests third.** That is the order an artist
 * opens the app in, and it is the opposite of what this screen used to lead with
 * (a greeting and a chart of inbound demand). The accent card at the top is the
 * only accent on the page.
 *
 * **The three states are three screens, not one screen with holes in it.**
 * [DashboardMode] picks between them, and the case that matters is
 * [DashboardMode.Unavailable]: when the bookings read fails there are no booked
 * days to shade, and an unshaded 14-day strip renders as *fourteen open nights*.
 * So a failed first load draws em-dashes and an inert grey strip, and says
 * outright that it will not guess. It is the one failure on the artist side that
 * costs real money.
 *
 * **Nothing here mutates.** The design draws Accept / Quote buttons inline on a
 * request card; this build sends the artist to the request instead. Screen 35's
 * own note is "three answers, one irreversible — with the clash warning surfaced
 * before the artist commits", and the dashboard has no clash data loaded. An
 * inline Accept would commit the one irreversible answer with the warning three
 * taps away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistHomeScreen(
    onBookingClick: (bookingId: String) -> Unit,
    onGigRequestClick: (requestId: String) -> Unit = {},
    onOpenWizard: () -> Unit = {},
    onScoreExplainer: () -> Unit = {},
    onManageAvailability: () -> Unit = {},
    onOpenAvailability: () -> Unit = onManageAvailability,
    onOpenEarnings: () -> Unit = {},
    onSubscribe: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArtistHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter
    val mode = state.mode

    PullToRefreshBox(
        // Only spin the pull indicator when there's already a dashboard under it.
        // On the very first load the skeleton below is the progress signal, and
        // running both reads as two competing loads.
        isRefreshing = state.isLoading && state.hasLoaded,
        onRefresh = viewModel::refresh,
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .semantics { testTag = "screen.artistStudio" },
    ) {
        if (state.isLoading && !state.hasLoaded && state.error == null) {
            SkeletonPage(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = gutter, vertical = dimens.space.md),
            )
            return@PullToRefreshBox
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = dimens.space.sm,
                bottom = dimens.size.listTailroom,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = "Studio",
                    subtitle = dashboardSubtitle(mode, state.avatarName.takeIf { it != "Artist" }),
                    modifier = Modifier.padding(horizontal = gutter),
                    trailing = {
                        if (mode == DashboardMode.Unavailable) {
                            IconCircle(
                                icon = Icons.Filled.Refresh,
                                contentDescription = "Retry",
                                onClick = viewModel::refresh,
                            )
                        } else {
                            TakingGigsPill(
                                daysAvailable = state.daysAvailable,
                                onClick = onOpenAvailability,
                            )
                        }
                    },
                )
            }

            // A refresh that failed OVER a working dashboard. The body below
            // keeps its data — blanking a screen because a background poll
            // dropped is how an artist loses the request they were reading.
            state.error?.takeIf { mode != DashboardMode.Unavailable }?.let { message ->
                item(key = "staleBanner") {
                    Banner(
                        title = "Couldn't refresh your dashboard",
                        detail = message,
                        tone = BannerTone.Failure,
                        actionLabel = "Retry",
                        onAction = viewModel::refresh,
                        modifier = Modifier.padding(horizontal = gutter),
                    )
                }
            }

            when (mode) {
                DashboardMode.Ready -> readyDashboard(
                    state = state,
                    gutter = gutter,
                    onOpenEarnings = onOpenEarnings,
                    onScoreExplainer = onScoreExplainer,
                    onBookingClick = onBookingClick,
                    onGigRequestClick = onGigRequestClick,
                    onOpenAvailability = onOpenAvailability,
                    onOpenWizard = onOpenWizard,
                    onSubscribe = onSubscribe,
                )
                DashboardMode.Cold -> coldDashboard(
                    state = state,
                    gutter = gutter,
                    onOpenWizard = onOpenWizard,
                    onOpenAvailability = onOpenAvailability,
                    onSubscribe = onSubscribe,
                )
                DashboardMode.Unavailable -> unavailableDashboard(
                    gutter = gutter,
                    message = state.error,
                    onRetry = viewModel::refresh,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 09 — the working dashboard
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.readyDashboard(
    state: ArtistHomeUiState,
    gutter: androidx.compose.ui.unit.Dp,
    onOpenEarnings: () -> Unit,
    onScoreExplainer: () -> Unit,
    onBookingClick: (String) -> Unit,
    onGigRequestClick: (String) -> Unit,
    onOpenAvailability: () -> Unit,
    onOpenWizard: () -> Unit,
    onSubscribe: () -> Unit,
) {
    profileBanner(state, gutter, onOpenWizard)

    item(key = "money") {
        MonthMoneyCard(
            money = state.money,
            onClick = onOpenEarnings,
            modifier = Modifier.padding(horizontal = gutter),
        )
    }

    item(key = "standing") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            BookabilityCard(
                state = state,
                onClick = onScoreExplainer,
                modifier = Modifier.weight(1f),
            )
            ReplySpeedCard(
                state = state,
                onClick = onScoreExplainer,
                modifier = Modifier.weight(1f),
            )
        }
    }

    val waiting = state.pendingRequests.size + state.openQuotes.size
    if (waiting > 0) {
        item(key = "requestsHeader") {
            SectionHeader(
                title = "Requests",
                modifier = Modifier.padding(horizontal = gutter),
            )
            Text(
                text = if (waiting == 1) "1 waiting" else "$waiting waiting",
                style = AppTheme.type.subtitle,
                color = AppTheme.colors.ink4,
                modifier = Modifier.padding(horizontal = gutter),
            )
        }
        items(state.pendingRequests, key = { "booking-${it.id}" }) { booking ->
            RequestCard(
                name = artistClientDisplayName(booking),
                title = requestTitle(booking),
                meta = requestMeta(booking),
                amountInr = booking.fee,
                onClick = { onBookingClick(booking.id) },
                modifier = Modifier.padding(horizontal = gutter),
            )
        }
        items(state.openQuotes, key = { "quote-${it.id}" }) { quote ->
            RequestCard(
                name = quote.requesterName ?: "Gig request",
                title = quoteTitle(quote),
                meta = quoteMeta(quote),
                amountInr = quote.counterAmount ?: quote.raw.amount,
                onClick = { onGigRequestClick(quote.id) },
                modifier = Modifier.padding(horizontal = gutter),
            )
        }
    }

    availabilityStrip(state, gutter, onOpenAvailability)

    if (state.upcoming.isNotEmpty()) {
        item(key = "upcomingHeader") {
            SectionHeader(
                title = "Upcoming",
                modifier = Modifier.padding(horizontal = gutter),
            )
        }
        items(state.upcoming, key = { "upcoming-${it.id}" }) { booking ->
            UpcomingRow(
                booking = booking,
                onClick = { onBookingClick(booking.id) },
                modifier = Modifier.padding(horizontal = gutter),
            )
        }
    }

    subscribeBanner(state, gutter, onSubscribe)
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 85 — cold
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.coldDashboard(
    state: ArtistHomeUiState,
    gutter: androidx.compose.ui.unit.Dp,
    onOpenWizard: () -> Unit,
    onOpenAvailability: () -> Unit,
    onSubscribe: () -> Unit,
) {
    profileBanner(state, gutter, onOpenWizard)

    item(key = "coldGrid") {
        StatGrid(
            cells = listOf(
                StatCell("Upcoming gigs", "0"),
                StatCell("Bookings / 7d", "0"),
                StatCell("Bookability", "New", emphasised = true),
                StatCell("Open requests", "0"),
            ),
            modifier = Modifier.padding(horizontal = gutter),
        )
    }

    availabilityStrip(state, gutter, onOpenAvailability)

    item(key = "coldEmpty") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter)
                .clip(RoundedCornerShape(AppTheme.dimens.radii.card))
                .background(AppTheme.colors.surface3)
                .padding(
                    horizontal = AppTheme.dimens.space.lg,
                    vertical = AppTheme.dimens.space.xl,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        ) {
            Text(
                "No upcoming gigs yet",
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
            )
            Text(
                "Requests land here the moment a host sends one.",
                style = AppTheme.type.subtitle,
                color = AppTheme.colors.ink4,
                textAlign = TextAlign.Center,
            )
        }
    }

    subscribeBanner(state, gutter, onSubscribe)
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 86 — unavailable
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.unavailableDashboard(
    gutter: androidx.compose.ui.unit.Dp,
    message: String?,
    onRetry: () -> Unit,
) {
    item(key = "failBanner") {
        Banner(
            title = "Couldn't refresh your dashboard",
            detail = message ?: "Availability and requests may be stale.",
            tone = BannerTone.Failure,
            actionLabel = "Retry",
            onAction = onRetry,
            modifier = Modifier.padding(horizontal = gutter),
        )
    }
    item(key = "failGrid") {
        // Every figure is an em-dash, INCLUDING the score. The design shows a
        // cached 86 there; this build has nothing cached to show, because
        // Unavailable is by definition the state where no read has ever landed.
        // An em-dash is that fact written down.
        StatGrid(
            cells = listOf(
                StatCell("Upcoming gigs", "—"),
                StatCell("Bookings / 7d", "—"),
                StatCell("Bookability", "—"),
                StatCell("Open requests", "—"),
            ),
            modifier = Modifier.padding(horizontal = gutter),
        )
    }
    item(key = "failStrip") {
        Column(
            Modifier.padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Next 14 days", style = AppTheme.type.sectionTitle, color = AppTheme.colors.ink)
                Text("unavailable", style = AppTheme.type.subtitle, color = AppTheme.colors.ink4)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Availability for the next 14 days is unavailable"
                    },
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xs),
            ) {
                repeat(STRIP_DAYS) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(AppTheme.dimens.component.stripCellH)
                            .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                            .background(AppTheme.colors.placeholder),
                    )
                }
            }
        }
    }
    item(key = "failNote") {
        Banner(
            title = "We won't draw these days as open. Showing an unknown day as " +
                "free is how an artist gets double-booked.",
            tone = BannerTone.Attention,
            modifier = Modifier.padding(horizontal = gutter),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sections
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.profileBanner(
    state: ArtistHomeUiState,
    gutter: androidx.compose.ui.unit.Dp,
    onOpenWizard: () -> Unit,
) {
    val gaps = state.profileGaps?.takeIf { it.needsWork } ?: return
    item(key = "profileGaps") {
        Column(
            Modifier.padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        ) {
            EyebrowLabel("Action required", color = AppTheme.colors.accentDeep)
            Banner(
                title = gaps.headline,
                detail = gaps.detail,
                // Note, not Promotion: a solid lime block at the top of the
                // dashboard would spend the screen's one accent on a nag and
                // out-shout the money card it sits above.
                tone = BannerTone.Note,
                actionLabel = "Finish",
                onAction = onOpenWizard,
            )
        }
    }
}

private fun LazyListScope.subscribeBanner(
    state: ArtistHomeUiState,
    gutter: androidx.compose.ui.unit.Dp,
    onSubscribe: () -> Unit,
) {
    if (!state.showSubscribeBanner) return
    item(key = "subscribe") {
        Banner(
            title = "Get seen first",
            detail = "Artistant Pro lifts your listing in search.",
            tone = BannerTone.Info,
            onClick = onSubscribe,
            modifier = Modifier.padding(horizontal = gutter),
        )
    }
}

private fun LazyListScope.availabilityStrip(
    state: ArtistHomeUiState,
    gutter: androidx.compose.ui.unit.Dp,
    onOpenAvailability: () -> Unit,
) {
    item(key = "strip") {
        Column(
            Modifier.padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            SectionHeader(
                title = "Next 14 days",
                actionLabel = "Manage",
                onAction = onOpenAvailability,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xs),
            ) {
                state.strip.forEach { day ->
                    StripCell(
                        day = day,
                        booked = day.key in state.bookedDayKeys,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                stripOpenDaysCopy(state.strip.size, state.bookedDayKeys.size),
                style = AppTheme.type.subtitle,
                color = AppTheme.colors.ink4,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pieces
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The header's standing pill.
 *
 * Two states, and neither of them is a guess: with days picked it reads "Taking
 * gigs" in the accent register, with none it says so plainly and routes to the
 * editor. It is only rendered once a read has landed (see [DashboardMode]), so
 * an empty [daysAvailable] here really does mean "none picked".
 */
@Composable
private fun TakingGigsPill(daysAvailable: List<String>, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val taking = daysAvailable.isNotEmpty()
    Row(
        Modifier
            .heightIn(min = dimens.size.rowMin)
            .clip(CircleShape)
            .background(if (taking) colors.accent.copy(alpha = PILL_FILL) else colors.surface2)
            .then(
                if (taking) {
                    Modifier.border(
                        dimens.size.hairline,
                        colors.accent.copy(alpha = PILL_LINE),
                        CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.component.pillPadH, vertical = dimens.component.pillPadV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        Box(
            Modifier
                .size(dimens.component.statusDot)
                .clip(CircleShape)
                .background(if (taking) colors.accentInk else colors.ink4),
        )
        Text(
            text = if (taking) "Taking gigs" else "No days set",
            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
            color = if (taking) colors.accentDeep else colors.ink2,
            maxLines = 1,
        )
    }
}

/**
 * Screen 09's accent card — the only accent on the page.
 *
 * The headline is what the month has already PLAYED, because that is the only
 * figure that is finished. The design's second line reads "₹48,000 awaiting
 * settlement"; nothing is in custody here and nothing is being settled, so the
 * honest version names the same money as agreed-and-not-yet-played, and only
 * when there is some.
 */
@Composable
private fun MonthMoneyCard(
    money: MonthMoney,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.xl))
            .background(colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(dimens.component.heroPad),
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        EyebrowLabel("Earned this month", color = colors.onAccent.copy(alpha = ON_ACCENT_SOFT))
        Text(
            formatInr(money.playedInr),
            style = AppTheme.type.monoHero,
            color = colors.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = dimens.space.xs),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.lg),
        ) {
            Text(
                text = when (money.showsPlayed) {
                    0 -> "No shows played yet"
                    1 -> "1 show played"
                    else -> "${money.showsPlayed} shows played"
                },
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onAccent,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (money.aheadInr > 0) {
                Text(
                    "${formatInr(money.aheadInr)} agreed, still to play",
                    style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onAccent.copy(alpha = ON_ACCENT_SOFT),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun BookabilityCard(
    state: ArtistHomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val score = state.breakdown.numericScore
    StatCardFrame(modifier = modifier, onClick = onClick) {
        Text("Bookability", style = AppTheme.type.caption, color = colors.ink4)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.xs + dimens.space.xs / 2),
            modifier = Modifier.padding(top = dimens.space.sm),
        ) {
            Text(
                text = score?.toString() ?: "New",
                style = AppTheme.type.displaySub,
                color = colors.ink,
            )
            state.scoreDelta?.takeIf { score != null && it != 0 }?.let { delta ->
                Text(
                    text = if (delta > 0) "+$delta" else "$delta",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (delta > 0) colors.accentInk else colors.danger,
                    modifier = Modifier.padding(bottom = dimens.space.xs),
                )
            }
        }
        // The track only draws under a real score. A full-width empty rail under
        // "New" reads as a zero rather than as an absence.
        Box(
            Modifier
                .padding(top = dimens.space.md)
                .fillMaxWidth()
                .height(dimens.dashboard.meterHeight + dimens.dashboard.meterHeight / 2)
                .clip(CircleShape)
                .background(colors.hairline),
        ) {
            if (score != null) {
                Box(
                    Modifier
                        .fillMaxWidth(score.coerceIn(0, 100) / 100f)
                        .fillMaxSize()
                        .background(colors.accent),
                )
            }
        }
    }
}

/**
 * The second standing cell.
 *
 * The design says "Reply time · 1h avg · Top 10% in Bengaluru". None of those
 * three is knowable here: the server keeps reply speed as a 0–100 metric, not a
 * duration, and a city-relative percentile would need a comparison across
 * artists that no client-side read can make. So the cell reports the metric the
 * score is actually built from, and says what it does.
 */
@Composable
private fun ReplySpeedCard(
    state: ArtistHomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    // Same gate the score explainer uses: with no completed gigs behind it every
    // metric is a meaningless zero, and a zero here reads as a penalty.
    val speed = state.breakdown.replySpeed.takeIf { state.breakdown.totalGigs > 0 }
    StatCardFrame(modifier = modifier, onClick = onClick) {
        Text("Reply speed", style = AppTheme.type.caption, color = colors.ink4)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.xs + dimens.space.xs / 2),
            modifier = Modifier.padding(top = dimens.space.sm),
        ) {
            Text(
                text = speed?.toString() ?: "—",
                style = AppTheme.type.displaySub,
                color = colors.ink,
            )
            Text(
                text = if (speed != null) "/100" else "no gigs yet",
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier.padding(bottom = dimens.space.xs),
            )
        }
        Text(
            "Answering faster lifts your score.",
            style = AppTheme.type.caption,
            color = colors.ink3,
            modifier = Modifier.padding(top = dimens.space.md),
        )
    }
}

@Composable
private fun StatCardFrame(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dimens = AppTheme.dimens
    Column(
        modifier
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(AppTheme.colors.surface3)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(dimens.space.lg),
    ) { content() }
}

/** One cell of the 2×2 grid on screens 85 and 86. */
private data class StatCell(val label: String, val value: String, val emphasised: Boolean = false)

@Composable
private fun StatGrid(cells: List<StatCell>, modifier: Modifier = Modifier) {
    val dimens = AppTheme.dimens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimens.space.md)) {
        cells.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            ) {
                pair.forEach { cell ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(dimens.radii.lg))
                            .background(AppTheme.colors.surface3)
                            .padding(dimens.component.cardPad),
                    ) {
                        Text(
                            cell.value,
                            style = AppTheme.type.displaySub,
                            // A zero and an em-dash both sit a rung down the ink
                            // ladder: neither is news. Only a real value is ink.
                            color = if (cell.emphasised) AppTheme.colors.ink else AppTheme.colors.ink3,
                        )
                        Text(
                            cell.label,
                            style = AppTheme.type.caption,
                            color = AppTheme.colors.ink4,
                            modifier = Modifier.padding(top = dimens.space.xs),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StripCell(day: StripDay, booked: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier
            .height(dimens.component.stripCellH)
            .clip(RoundedCornerShape(dimens.radii.sm))
            .background(if (booked) colors.accent else colors.surface3)
            .semantics {
                contentDescription = "${day.weekday} ${day.dayOfMonth}" +
                    if (booked) ", booked" else ", open"
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = AppTheme.type.monoStripDay,
            // The strip is fourteen equal cells across one row, so a cell can
            // never be widened to suit its contents — a date that does not fit
            // must overflow its cell, never wrap inside it. Without `softWrap`
            // a two-digit date broke as "1" over "0", which reads as two dates.
            //
            // The other two thirds of that are here too, because a numeral that
            // does not fit is not a cosmetic problem — it is the wrong date.
            // `Clip`, the default, cuts mid-glyph and shows half a digit; the
            // cap stops the numeral outgrowing its ~21dp cell in the first
            // place (see [cappedFontScale]), and `Visible` is the honest last
            // resort for whatever still overhangs.
            fontSize = AppTheme.type.monoStripDay.fontSize *
                cappedFontScale(LocalDensity.current.fontScale, STRIP_MAX_FONT_SCALE),
            color = if (booked) colors.onAccent else colors.ink4,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

/**
 * One inbound request.
 *
 * Deliberately has no Accept button on it — see the note on [ArtistHomeScreen].
 * The whole card is the target, and it opens the screen where the decision is
 * made with the clash warning in front of it.
 */
@Composable
private fun RequestCard(
    name: String,
    title: String,
    meta: String?,
    amountInr: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(dimens.component.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Avatar(name = name, size = dimens.component.rowAvatar)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!meta.isNullOrBlank()) {
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
        Text(
            formatInr(amountInr),
            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun UpcomingRow(booking: Booking, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val parts = gigDateParts(booking)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(dimens.component.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(parts.day, style = AppTheme.type.monoDay, color = colors.ink)
            Text(parts.weekday, style = AppTheme.type.monoWeekday, color = colors.ink4)
        }
        Box(
            Modifier
                .width(dimens.size.stroke)
                .height(dimens.component.rowAvatar)
                .clip(CircleShape)
                .background(colors.accent),
        )
        Column(Modifier.weight(1f)) {
            Text(
                artistClientDisplayName(booking),
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    booking.time.takeIf { it.isNotBlank() },
                    booking.venue.takeIf { it.isNotBlank() && it != "TBD" },
                ).joinToString(" · "),
                style = AppTheme.type.caption,
                color = colors.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatInr(booking.fee),
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Row copy
// ─────────────────────────────────────────────────────────────────────────────

private fun requestTitle(booking: Booking): String {
    val who = artistClientDisplayName(booking)
    val pkg = booking.packageName?.trim()?.takeIf { it.isNotEmpty() && it != "Custom" }
    return if (pkg != null) "$who · $pkg" else who
}

private fun requestMeta(booking: Booking): String = listOfNotNull(
    booking.date.takeIf { it.isNotBlank() && it != "TBD" },
    booking.guests.takeIf { it > 0 }?.let { "$it guests" },
    booking.venue.takeIf { it.isNotBlank() && it != "TBD" },
).joinToString(" · ")

private fun quoteTitle(quote: StoredRequest): String =
    quote.requesterName ?: "Gig request"

private fun quoteMeta(quote: StoredRequest): String = listOfNotNull(
    quote.raw.date.takeIf { it.isNotBlank() },
    quote.raw.crowdSize?.takeIf { it > 0 }?.let { "$it guests" },
    quote.raw.venue,
).joinToString(" · ")

/** 14, matching [ArtistHomeViewModel]'s strip window — the grey cells on 86. */
private const val STRIP_DAYS = 14

/**
 * The largest system font scale a strip cell can hold a two-digit date at.
 *
 * Measured against the geometry, not picked: fourteen cells and thirteen `xs` gaps share the
 * page's 350dp of width, so a cell is ~21dp, and JetBrains Mono's digits advance at 0.6em —
 * two of them at 10sp × 1.3 need ~15.6dp, which clears the cell with room for the rounding.
 * Everything below 1.3 scales the way the reader asked for.
 */
private const val STRIP_MAX_FONT_SCALE = 1.3f

/** The eyebrow and the secondary line on the accent card, at reading weight. */
private const val ON_ACCENT_SOFT = 0.6f

/** The header pill's accent fill and its rim (screens 09 / 22). */
private const val PILL_FILL = 0.3f
private const val PILL_LINE = 0.6f
