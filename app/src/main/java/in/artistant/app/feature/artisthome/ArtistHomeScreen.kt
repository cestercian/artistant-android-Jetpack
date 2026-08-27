package `in`.artistant.app.feature.artisthome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.InlineBanner
import `in`.artistant.app.designsystem.component.MiniBars
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.component.Sparkline
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.domain.score.tierColor
import java.util.Locale

/**
 * The artist's dashboard.
 *
 * Reads top-down the way the artist's own attention runs: **who am I → what came
 * in → how bookable am I → when am I busy → what needs answering**. Anything
 * demanding a decision (an unfinished profile, a failed refresh, an unanswered
 * request) surfaces near the top; the reference material sits below it.
 *
 * ## Structure
 *
 * One `LazyColumn` for the entire screen. That is a hard requirement rather than
 * a preference here: the artist is on a mid-range phone, and the emulator this
 * was tuned against had fallen back to software rendering, where composing every
 * off-screen section on every frame is the difference between a scroll and a
 * slideshow. The one nested scroller is the availability strip, which runs on the
 * other axis and is a `LazyRow` for the same reason.
 *
 * ## Surfaces
 *
 * No card chrome — sections are separated by hairlines and whitespace, and the
 * only filled surfaces are the two banner shapes. The page deliberately does NOT
 * paint an opaque background: the scaffold's ambient role wash sits behind it and
 * paints `bg` itself, so skipping the fill gets the artist-violet glow for free.
 * Painting over it would have cost the same and looked flatter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistHomeScreen(
    onBookingClick: (bookingId: String) -> Unit,
    onGigRequestClick: (requestId: String) -> Unit = {},
    onOpenWizard: () -> Unit = {},
    onScoreExplainer: () -> Unit = {},
    onManageAvailability: () -> Unit = {},
    onSubscribe: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArtistHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors

    PullToRefreshBox(
        // Only spin the pull indicator when there's already a dashboard under it.
        // On the very first load the centred spinner below is the progress signal,
        // and running both reads as two competing loads.
        isRefreshing = state.isLoading && state.hasLoaded,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            !state.hasLoaded && state.error == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
            !state.hasLoaded -> {
                EmptyState(
                    title = "Couldn't load your dashboard",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> RevealOnAppear {
                Dashboard(
                    state = state,
                    onRetry = viewModel::refresh,
                    onRangeChange = viewModel::setRange,
                    onBookingClick = onBookingClick,
                    onGigRequestClick = onGigRequestClick,
                    onOpenWizard = onOpenWizard,
                    onScoreExplainer = onScoreExplainer,
                    onManageAvailability = onManageAvailability,
                    onSubscribe = onSubscribe,
                )
            }
        }
    }
}

@Composable
private fun Dashboard(
    state: ArtistHomeUiState,
    onRetry: () -> Unit,
    onRangeChange: (EarningsRange) -> Unit,
    onBookingClick: (String) -> Unit,
    onGigRequestClick: (String) -> Unit,
    onOpenWizard: () -> Unit,
    onScoreExplainer: () -> Unit,
    onManageAvailability: () -> Unit,
    onSubscribe: () -> Unit,
) {
    val space = AppTheme.dimens.space

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = "screen.artistHome" },
        contentPadding = PaddingValues(bottom = space.xxl),
    ) {
        item(key = "greeting") {
            Greeting(
                name = state.greetingName,
                avatarName = state.avatarName,
                dateLabel = state.todayLabel,
            )
        }

        // ── Things asking for a decision ────────────────────────────────────
        // Grouped at the top rather than scattered beside the data they relate
        // to, so "is there anything I need to do?" is answered without scrolling.

        if (state.error != null) {
            item(key = "error") {
                InlineBanner(
                    title = "Couldn't refresh your dashboard",
                    detail = state.error,
                    tone = BannerTone.Failure,
                    actionLabel = "Retry",
                    onAction = onRetry,
                    modifier = Modifier
                        .padding(horizontal = space.xl)
                        .padding(top = space.md)
                        .semantics { testTag = "artistHome.errorBanner" },
                )
            }
        }
        state.profileGaps?.takeIf { it.needsWork }?.let { gaps ->
            item(key = "profile-gaps") {
                InlineBanner(
                    title = gaps.headline,
                    detail = gaps.detail,
                    tone = BannerTone.Attention,
                    onClick = onOpenWizard,
                    modifier = Modifier
                        .padding(horizontal = space.xl)
                        .padding(top = space.md)
                        .semantics { testTag = "artistHome.completionBanner" },
                )
            }
        }
        if (state.showSubscribeBanner) {
            item(key = "subscribe") {
                InlineBanner(
                    title = "Start your 3 months free",
                    detail = "Stay listed and keep getting client requests.",
                    tone = BannerTone.Promotion,
                    onClick = onSubscribe,
                    modifier = Modifier
                        .padding(horizontal = space.xl)
                        .padding(top = space.md)
                        .semantics { testTag = "artistHome.subscribeBanner" },
                )
            }
        }

        item(key = "hero") { EarningsHero(state = state, onRangeChange = onRangeChange) }
        item(key = "stats") { StatRow(state = state) }
        item(key = "bookability") {
            BookabilityBlock(
                state = state,
                onScoreExplainer = onScoreExplainer,
            )
        }
        item(key = "strip") { AvailabilityStrip(state = state, onManage = onManageAvailability) }

        newRequestsSection(state.pendingRequests, onBookingClick)
        quoteRequestsSection(state.openQuotes, onGigRequestClick)
        upcomingSection(state.upcoming, onBookingClick)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Greeting
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The masthead: a dateline and the artist's name, set editorially.
 *
 * Same move the client side opens on — a serif line with exactly one word in
 * italic brand accent. It is the app's signature, and the artist side had no
 * equivalent at all: it opened on the word "Home", which names a tab, not a
 * person. Setting the artist's own name as the largest thing on the screen is
 * the difference between a dashboard and a control panel.
 *
 * The avatar here is a PORTRAIT, not a control. It used to be the way into
 * account settings, which put that door on the dashboard — a screen about
 * incoming work — while the screen an artist actually opens to work on their own
 * profile had no account entry at all. The door moved to the press-kit editor's
 * title bar, where it is the only one, and this avatar went back to doing what it
 * looks like it does: showing whose dashboard this is.
 */
@Composable
private fun Greeting(
    name: String,
    avatarName: String,
    dateLabel: String,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl)
            .padding(top = space.lg, bottom = space.md)
            .semantics { testTag = "artistHome.greeting" },
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                dateLabel.uppercase(Locale.US),
                style = AppTheme.type.monoMicro,
                color = colors.ink3,
            )
            Spacer(Modifier.height(space.xs))
            Text(
                buildAnnotatedString {
                    append("Hey, ")
                    withStyle(SpanStyle(color = colors.brand, fontStyle = FontStyle.Italic)) {
                        append(name)
                    }
                    append(".")
                },
                style = AppTheme.type.masthead,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Avatar(
            name = avatarName,
            size = AppTheme.dimens.hero.avatarSize,
            ring = true,
            // No contentDescription: the greeting beside it already says whose
            // dashboard this is, so announcing the monogram again would make a
            // screen reader read the name twice for no added meaning.
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero — inbound work
// ─────────────────────────────────────────────────────────────────────────────

/**
 * How much work came in over the selected window.
 *
 * The number is a **count of bookings, not a rupee total**. No money moves
 * through the app in the matchmaker model, so a ₹ figure here would be money the
 * product never handled — and the artist would reasonably read it as earnings.
 * The fees further down the screen are real: those are what the two sides agreed
 * on, per gig.
 */
@Composable
private fun EarningsHero(
    state: ArtistHomeUiState,
    onRangeChange: (EarningsRange) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val range = state.range
    val hero = state.hero

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl)
            .padding(top = space.lg, bottom = space.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (range) {
                    EarningsRange.All -> "BOOKINGS · ALL TIME"
                    EarningsRange.SevenDays -> "BOOKINGS · LAST 7 DAYS"
                    EarningsRange.ThirtyDays -> "BOOKINGS · LAST 30 DAYS"
                },
                style = AppTheme.type.monoMicro,
                color = colors.ink3,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .semantics { testTag = "artistHome.earningsRangeLabel" },
            )
            RangePicker(selected = range, onSelect = onRangeChange)
        }
        Spacer(Modifier.height(space.sm))

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            modifier = Modifier.semantics { testTag = "artistHome.earningsHeadline" },
        ) {
            Text(
                "${hero.headline}",
                style = AppTheme.type.monoHero,
                color = colors.ink,
                maxLines = 1,
            )
            // An honest zero states its unit at full size. A new artist's "0" is
            // never hidden and never swapped for an upsell — the subscribe
            // banner is a separate element above, it does not stand in for this.
            if (hero.headline == 0) {
                Text("bookings", style = AppTheme.type.callout, color = colors.ink3)
            }
            // Meaningless in all-time mode: there is no prior period to compare
            // against, so the pill would always read +100%.
            if (range != EarningsRange.All) {
                DeltaPill(percent = hero.deltaPercent, up = hero.deltaUp)
            }
        }

        Text(
            when {
                range == EarningsRange.All && hero.headline > 0 -> "All time"
                range == EarningsRange.All -> "No bookings yet"
                range == EarningsRange.SevenDays -> "vs ${hero.prior} prior 7 days"
                else -> "vs ${hero.prior} prior 30 days"
            },
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )

        Spacer(Modifier.height(space.lg))
        if (state.hasChartData) {
            Sparkline(
                values = state.series,
                height = AppTheme.dimens.dashboard.chartHeight,
                modifier = Modifier.semantics {
                    testTag = "artistHome.earningsSparkline"
                    contentDescription = "Bookings trend over the selected window"
                },
            )
            Spacer(Modifier.height(space.sm))
            AxisLabels(range = range, dayKey = state.todayLabel)
        } else {
            Text(
                // Two different truths. An all-time artist whose only work
                // predates the chart's 90-bucket cap has a real headline number
                // and an empty chart; telling them "your chart appears here as
                // bookings come in" would contradict the count right above it.
                if (state.hasCountsOutsideChart) {
                    "All of these landed before the 90-day chart window."
                } else {
                    "Your bookings chart appears here as they come in."
                },
                style = AppTheme.type.footnote,
                color = colors.ink3,
                modifier = Modifier.semantics { testTag = "artistHome.earningsEmpty" },
            )
        }
    }
}

/**
 * Start / middle / end dates of the chart window.
 *
 * [dayKey] is the state's own dateline — a value, not a clock read, and
 * recomputed by the same refresh that rebuilds `series`. Keying the cache on
 * `range` alone froze these three dates at the day the card was first composed,
 * so a dashboard left open across IST midnight and then refreshed printed
 * yesterday's date under today's bar.
 */
@Composable
private fun AxisLabels(range: EarningsRange, dayKey: String) {
    val colors = AppTheme.colors
    // Recomputed only when the window or the day changes — three date formats
    // per recomposition would otherwise ride along on every scroll frame.
    val labels = remember(range, dayKey) {
        val last = range.bucketCount - 1
        Triple(windowLabel(last), windowLabel(last / 2), windowLabel(0))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(labels.first, labels.second, labels.third).forEach {
            Text(it, style = AppTheme.type.monoMicro, color = colors.ink3)
        }
    }
}

@Composable
private fun RangePicker(selected: EarningsRange, onSelect: (EarningsRange) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val size = AppTheme.dimens.size
    Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
        EarningsRange.entries.forEach { range ->
            val active = range == selected
            // A 9sp chip inside a 44dp tap target rather than grown to one — the
            // touch floor is a hit area, not a size. The selected state is a
            // property rather than a suffix on the description, so a screen
            // reader hears it the way it hears every other picker.
            Box(
                Modifier
                    .defaultMinSize(minWidth = size.rowMin, minHeight = size.rowMin)
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .clickable { onSelect(range) }
                    .semantics {
                        testTag = "artistHome.range.${range.label}"
                        contentDescription = "${range.label} window"
                        this.selected = active
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    range.label,
                    style = AppTheme.type.monoMicro,
                    color = if (active) colors.brandInk else colors.ink3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                        .background(if (active) colors.brand else colors.bgSoft)
                        .padding(horizontal = space.sm, vertical = space.xs),
                )
            }
        }
    }
}

@Composable
private fun DeltaPill(percent: Int, up: Boolean) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xs),
        modifier = Modifier.semantics {
            contentDescription = if (up) "Up $percent percent" else "Down $percent percent"
        },
    ) {
        Icon(
            if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = if (up) colors.good else colors.hot,
            modifier = Modifier.size(AppTheme.dimens.size.iconSm),
        )
        Text(
            "$percent%",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
            color = if (up) colors.good else colors.hot,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat row — what's committed
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Two cells, ruled above and below: the schedule on the left, recent momentum on
 * the right.
 *
 * The right cell is a FIXED 7-day window and ignores the range picker on
 * purpose. It answers "has anything come in lately", which is not a question
 * about whichever window the hero happens to be showing.
 */
@Composable
private fun StatRow(state: ArtistHomeUiState) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val dashboard = AppTheme.dimens.dashboard
    val upcoming = state.upcomingSnapshot

    Column {
        HRule()
        Row(
            Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = space.xl, vertical = space.lg),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = space.md)
                    .semantics { testTag = "artistHome.upcomingCard" },
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Text("UPCOMING", style = AppTheme.type.monoMicro, color = colors.ink3)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${upcoming.count}",
                        style = AppTheme.type.monoStat,
                        // Warm, not lime: this is a commitment the artist owes,
                        // not a positive signal. Lime stays reserved for the latter.
                        color = colors.warm,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (upcoming.count == 1) "gig" else "gigs",
                        style = AppTheme.type.footnote,
                        color = colors.ink3,
                    )
                }
                Text(upcoming.copy, style = AppTheme.type.footnote, color = colors.ink3)
            }

            Box(
                Modifier
                    .fillMaxHeight()
                    .width(AppTheme.dimens.size.hairline)
                    .background(colors.lineSoft),
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = space.md)
                    .semantics { testTag = "artistHome.bookings7dCard" },
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Text("BOOKINGS / 7D", style = AppTheme.type.monoMicro, color = colors.ink3)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${state.bookings7dCount}",
                        style = AppTheme.type.monoStat,
                        color = colors.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    // An empty week shows the honest 0 without a flat row of
                    // zero bars beside it, which reads as a broken chart.
                    if (state.bookings7dCount > 0) {
                        MiniBars(
                            values = state.bookings7d,
                            width = dashboard.barsWidth,
                            height = dashboard.barsHeight,
                            modifier = Modifier.semantics {
                                contentDescription =
                                    "Bookings over the last 7 days: ${state.bookings7dCount} total"
                            },
                        )
                    }
                }
            }
        }
        HRule()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bookability
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The artist's standing in the marketplace: the ring, the tier, and the four
 * components behind the number.
 *
 * The whole block is the tap target into the explainer — a score with no way to
 * ask "why that number" is just a judgement.
 */
@Composable
private fun BookabilityBlock(state: ArtistHomeUiState, onScoreExplainer: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val breakdown = state.breakdown
    val score = breakdown.numericScore
    val tier = breakdown.tier
    val tint = if (score == null) colors.ink3 else tierColor(tier, colors)
    val interaction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl, vertical = space.xl),
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        // An incomplete profile is precisely what suppresses being booked, so
        // the flag belongs on the standing card. Non-interactive — the banner at
        // the top carries the route to the fix.
        if (state.profileGaps?.needsWork == true) {
            ActionRequiredPill()
        }

        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction)
                .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
                .clickable(interactionSource = interaction, indication = null, onClick = onScoreExplainer)
                .semantics { testTag = "artistHome.bookabilityCard" },
            horizontalArrangement = Arrangement.spacedBy(space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreRing(
                value = score,
                size = AppTheme.dimens.dashboard.scoreRing,
                showLabel = false,
                totalGigs = breakdown.totalGigs,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs)) {
                Text("BOOKABILITY", style = AppTheme.type.monoMicro, color = colors.ink3)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    Text(score?.toString() ?: "—", style = AppTheme.type.monoLarge, color = tint)
                    Text(
                        (if (score == null) ScoreTier.New.label else tier.label).uppercase(Locale.US),
                        style = AppTheme.type.monoMicro,
                        color = tint,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier.size(AppTheme.dimens.size.iconLg),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
            breakdownRows(breakdown).forEach { MetricRow(it) }
        }
    }
}

@Composable
private fun ActionRequiredPill() {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.warm.copy(alpha = PILL_TINT))
            .padding(horizontal = space.md, vertical = space.xs)
            .semantics { testTag = "artistHome.actionRequiredPill" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.xs),
    ) {
        Box(
            Modifier
                .size(AppTheme.dimens.size.dot)
                .clip(CircleShape)
                .background(colors.warm),
        )
        Text("ACTION REQUIRED", style = AppTheme.type.monoMicro, color = colors.warm)
    }
}

/**
 * One score component as a label, meter and value.
 *
 * A null value renders an em-dash and an EMPTY track rather than a zero-width
 * bar at 0%, because those two look identical and mean opposite things ("we have
 * no data" vs "you scored nothing").
 */
@Composable
private fun MetricRow(row: BreakdownRow) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val dashboard = AppTheme.dimens.dashboard
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
        modifier = Modifier.semantics {
            contentDescription = "${row.label}: ${row.value?.let { "$it out of 100" } ?: "no data yet"}"
        },
    ) {
        Text(
            row.label,
            style = AppTheme.type.footnote,
            color = colors.ink2,
            modifier = Modifier.width(dashboard.meterLabelWidth),
        )
        Box(
            Modifier
                .weight(1f)
                .height(dashboard.meterHeight)
                .clip(CircleShape)
                .background(colors.bgSoft),
        ) {
            row.value?.let { pct ->
                Box(
                    Modifier
                        .fillMaxWidth(pct.coerceIn(0, 100) / 100f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.brand),
                )
            }
        }
        Text(
            row.value?.toString() ?: "—",
            style = AppTheme.type.monoSmall,
            color = colors.ink2,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Availability strip
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The next fortnight at a glance, and the way to change what's offered.
 *
 * Only two states, not three. The reference build also shades days that carry an
 * event on the artist's own device calendar; that read direction does not exist
 * on this platform yet (calendar sync here only WRITES confirmed gigs out to the
 * device), so inventing a third legend entry would label days that nothing can
 * ever fill.
 */
@Composable
private fun AvailabilityStrip(state: ArtistHomeUiState, onManage: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    Column(Modifier.padding(vertical = space.lg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NEXT 14 DAYS",
                style = AppTheme.type.monoMicro,
                color = colors.ink3,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    // The label stays micro; the row it is tapped through clears
                    // the 44dp floor.
                    .heightIn(min = AppTheme.dimens.size.rowMin)
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .clickable(onClick = onManage)
                    .padding(horizontal = space.sm, vertical = space.xs)
                    .semantics {
                        testTag = "artistHome.manageAvailability"
                        contentDescription = "Manage availability"
                        role = Role.Button
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MANAGE", style = AppTheme.type.monoMicro, color = colors.brand)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.brand,
                    modifier = Modifier.size(AppTheme.dimens.size.iconSm),
                )
            }
        }
        Spacer(Modifier.height(space.md))

        LazyRow(
            contentPadding = PaddingValues(horizontal = space.xl),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            items(state.strip, key = { it.key }) { day ->
                DayCell(day = day, booked = day.key in state.bookedDayKeys)
            }
        }
        Spacer(Modifier.height(space.md))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.xl),
            horizontalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            Legend(label = "Booked", filled = true)
            Legend(label = "Open", filled = false)
        }
    }
}

@Composable
private fun DayCell(day: StripDay, booked: Boolean) {
    val colors = AppTheme.colors
    val dashboard = AppTheme.dimens.dashboard
    val space = AppTheme.dimens.space
    val shape = RoundedCornerShape(AppTheme.dimens.radii.md)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.xs),
        modifier = Modifier.semantics {
            contentDescription =
                "${day.weekday} ${day.dayOfMonth}, ${if (booked) "booked" else "open"}"
        },
    ) {
        Text(day.weekday, style = AppTheme.type.monoMicro, color = colors.ink3)
        Column(
            Modifier
                .size(dashboard.dayCellW, dashboard.dayCellH)
                .clip(shape)
                .background(if (booked) colors.brand else colors.bg)
                .then(
                    if (booked) Modifier
                    else Modifier.border(AppTheme.dimens.size.hairline, colors.line, shape),
                )
                .padding(vertical = space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = AppTheme.type.monoStat,
                color = if (booked) colors.brandInk else colors.ink,
                textAlign = TextAlign.Center,
            )
            if (booked) {
                Spacer(Modifier.height(space.xs))
                Box(
                    Modifier
                        .size(dashboard.dayDot)
                        .clip(CircleShape)
                        .background(colors.brandInk),
                )
            }
        }
    }
}

@Composable
private fun Legend(label: String, filled: Boolean) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.dimens.radii.sm)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xs),
    ) {
        Box(
            Modifier
                .size(AppTheme.dimens.size.dot)
                .clip(shape)
                .background(if (filled) colors.brand else colors.bg)
                .then(
                    if (filled) Modifier
                    else Modifier.border(AppTheme.dimens.size.hairline, colors.line, shape),
                ),
        )
        Text(label, style = AppTheme.type.footnote, color = colors.ink3)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rails
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bookings the artist hasn't answered. Self-hiding — an empty rail here would be
 * a heading over nothing, and the dashboard already tells them work is quiet.
 */
private fun LazyListScope.newRequestsSection(
    pending: List<Booking>,
    onBookingClick: (String) -> Unit,
) {
    if (pending.isEmpty()) return
    item(key = "new-requests-header") {
        SectionHeader(
            title = "New requests",
            trailing = "${pending.size} new",
            testTagId = "artistHome.newRequests",
        )
    }
    items(pending, key = { "new-${it.id}" }) { booking ->
        PendingRequestRow(booking = booking, onClick = { onBookingClick(booking.id) })
        HRule(Modifier.padding(horizontal = AppTheme.dimens.space.xl))
    }
}

/**
 * The quote negotiation inbox. A DIFFERENT funnel from the one above — a client
 * proposing their own amount, versus a client requesting a published package —
 * so it gets its own title. Two rails sharing one heading read as a rendering
 * bug.
 */
private fun LazyListScope.quoteRequestsSection(
    quotes: List<StoredRequest>,
    onGigRequestClick: (String) -> Unit,
) {
    if (quotes.isEmpty()) return
    item(key = "quotes-header") {
        SectionHeader(
            title = "Quote requests",
            trailing = "${quotes.size} open",
            testTagId = "artistHome.quoteRequests",
        )
    }
    items(quotes, key = { "quote-${it.id}" }) { request ->
        QuoteRequestRow(request = request, onClick = { onGigRequestClick(request.id) })
        HRule(Modifier.padding(horizontal = AppTheme.dimens.space.xl))
    }
}

/**
 * Confirmed future gigs, soonest first — always rendered, with an empty state.
 *
 * Unlike the two rails above this one is NOT self-hiding: "what's next" is the
 * question the artist opens the app with, and a screen that silently omits the
 * answer is indistinguishable from one that failed to load it.
 */
private fun LazyListScope.upcomingSection(
    upcoming: List<Booking>,
    onBookingClick: (String) -> Unit,
) {
    item(key = "upcoming-header") {
        SectionHeader(title = "Upcoming gigs", testTagId = "artistHome.upcomingSection")
    }
    if (upcoming.isEmpty()) {
        item(key = "upcoming-empty") {
            Text(
                "No upcoming gigs yet",
                style = AppTheme.type.footnote,
                color = AppTheme.colors.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppTheme.dimens.space.xl),
            )
        }
        return
    }
    items(upcoming, key = { "gig-${it.id}" }) { booking ->
        UpcomingRow(booking = booking, onClick = { onBookingClick(booking.id) })
        HRule(Modifier.padding(horizontal = AppTheme.dimens.space.xl))
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null, testTagId: String? = null) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl)
            .padding(top = space.xl, bottom = space.sm)
            .then(
                if (testTagId != null) Modifier.semantics { testTag = testTagId } else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = AppTheme.type.footnote, color = colors.ink3)
        }
    }
}

@Composable
private fun PendingRequestRow(booking: Booking, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = space.xl, vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        // Warm, not lime — this is waiting on the artist, not a win.
        Box(
            Modifier
                .size(AppTheme.dimens.dashboard.bannerDot)
                .clip(CircleShape)
                .background(colors.warm),
        )
        Column(Modifier.weight(1f)) {
            Text(
                artistClientDisplayName(booking),
                style = AppTheme.type.callout,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${booking.date} · ${booking.time}",
                style = AppTheme.type.footnote,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatInr(booking.fee), style = AppTheme.type.monoPrice, color = colors.ink)
    }
}

@Composable
private fun QuoteRequestRow(request: StoredRequest, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    // Null when the server can't name the requester — `users` is self-only under
    // RLS, so the artist never reads the client's row. Printing "Client" here put
    // the same name and the same "C" monogram on every inbound quote; the row is
    // told apart by its date, offer and message below, which are its own.
    val client = request.requesterName

    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = space.xl, vertical = space.md)
            .semantics { testTag = "artistHome.request.${request.id}" },
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            // Avatar draws its neutral placeholder on an empty name — the right
            // look for someone we can't identify.
            Avatar(name = client.orEmpty(), size = AppTheme.dimens.size.avatarSm)
            Column(Modifier.weight(1f)) {
                Text(
                    client ?: "Gig request",
                    style = AppTheme.type.callout,
                    color = if (client != null) colors.ink else colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        // No " ago" suffix here: the repository's `relativeTimeAgo`
                        // already returns whole phrases ("just now", "3h ago"), so
                        // appending one rendered "3h ago ago" / "just now ago".
                        request.raw.timeAgo.takeIf { it.isNotBlank() },
                        request.raw.packageLabel.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                request.status.label.uppercase(Locale.US),
                style = AppTheme.type.monoMicro,
                color = statusTint(request),
            )
        }
        if (request.raw.message.isNotBlank()) {
            Text(
                request.raw.message,
                style = AppTheme.type.footnote,
                color = colors.ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                request.raw.date,
                style = AppTheme.type.monoSmall,
                color = colors.ink3,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatInr(request.counterAmount ?: request.raw.amount),
                style = AppTheme.type.monoPrice,
                color = colors.ink,
            )
        }
    }
}

@Composable
private fun statusTint(request: StoredRequest) = when (request.status) {
    GigRequestStatus.Open -> AppTheme.colors.brand
    GigRequestStatus.Countered -> AppTheme.colors.warm
    GigRequestStatus.Accepted -> AppTheme.colors.good
    else -> AppTheme.colors.ink3
}

@Composable
private fun UpcomingRow(booking: Booking, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    // Keyed on the whole immutable row, not on id/date/time: `gigDateParts`
    // resolves through `resolvedStartEpochMs()`, which PREFERS `startDatetimeIso`
    // and only falls back to those labels. The list keys its items by id, so the
    // slot survives a refresh — and a corrected `start_datetime` that left the
    // display labels alone would have left a stale day/weekday block behind it.
    val parts = remember(booking) { gigDateParts(booking) }

    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = space.xl, vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(AppTheme.dimens.size.avatarMd),
        ) {
            Text(parts.day, style = AppTheme.type.monoStat, color = colors.ink)
            Text(parts.weekday, style = AppTheme.type.monoMicro, color = colors.ink3)
        }
        Column(Modifier.weight(1f)) {
            Text(
                artistClientDisplayName(booking),
                style = AppTheme.type.callout,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Venue rather than a package name: the bookings read doesn't
                // carry the package's label, only its position, and "Package #2"
                // tells the artist nothing about the gig they're preparing for.
                listOf(booking.time, booking.venue)
                    .filter { it.isNotBlank() && it != "TBD" }
                    .joinToString(" · "),
                style = AppTheme.type.footnote,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The artist's FEE, never `total` — total carries the platform cut and
        // GST from the dormant payments model, which nobody pays and which would
        // overstate what the artist takes home.
        Text(formatInr(booking.fee), style = AppTheme.type.monoPrice, color = colors.ink)
    }
}

/** Warm fill behind the action-required pill — a tint, not a surface. */
private const val PILL_TINT = 0.12f
