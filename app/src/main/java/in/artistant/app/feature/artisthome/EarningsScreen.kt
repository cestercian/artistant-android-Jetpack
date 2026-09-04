package `in`.artistant.app.feature.artisthome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.SegmentedControl
import `in`.artistant.app.designsystem.component.SkeletonPage
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Earnings — design screen 133, "earnings without custody".
 *
 * Every figure on this page is a fee the two parties **agreed in the app**. No
 * money moves through Artistant in the matchmaker build, so this is not a payout
 * statement, cannot be reconciled against a bank account, and is not certifiable
 * as income. The note at the bottom says that outright rather than leaving the
 * artist to infer it from an absence — which is the whole design intent, and the
 * reason the note is not collapsible.
 *
 * The design's row states are "Agreed" and "Settled". Settlement is not
 * something this product can observe, so the second word is "Played": a fact the
 * booking row can actually answer for.
 */
@Composable
fun EarningsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onBookingClick: (bookingId: String) -> Unit = {},
    viewModel: EarningsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter
    val summary = state.summary

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page)
            .semantics { testTag = "screen.earnings" },
    ) {
        // The design draws this with a tab-root's 26sp masthead. It is reached
        // by a push here (the artist's four tabs are Studio / Gigs / Messages /
        // Profile), so it takes the app's pushed-screen chrome instead — a big
        // title with no way back is the one thing worse than a smaller one.
        BackHeader(
            title = "Earnings",
            subtitle = "Agreed fees, settled directly",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = dimens.space.sm, vertical = dimens.space.sm),
        )

        if (state.isLoading && !state.hasLoaded && state.error == null) {
            SkeletonPage(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = gutter, vertical = dimens.space.md),
            )
            return@Column
        }

        if (state.error != null && !state.hasLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Couldn't load your earnings",
                    body = state.error,
                    actionLabel = "Try again",
                    onAction = viewModel::refresh,
                )
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
                .padding(bottom = dimens.size.listTailroom),
            verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
        ) {
            SegmentedControl(
                options = EarningsWindow.entries.toList(),
                selected = state.window,
                onSelect = viewModel::setWindow,
                label = { it.label },
            )

            state.error?.let { message ->
                Banner(
                    title = "Couldn't refresh",
                    detail = message,
                    tone = BannerTone.Failure,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }

            // Headline
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        formatInr(summary.totalInr),
                        style = AppTheme.type.monoHero,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (summary.gigCount) {
                            0 -> "no gigs in this window"
                            1 -> "across 1 gig"
                            else -> "across ${summary.gigCount} gigs"
                        },
                        style = AppTheme.type.caption,
                        color = colors.ink4,
                        modifier = Modifier.padding(top = dimens.space.xs),
                    )
                }
                summary.deltaPercent?.let { percent ->
                    DeltaPill(percent = percent, up = summary.deltaUp)
                }
            }

            if (summary.hasChart) {
                EarningsChart(bars = summary.bars)
            }

            if (summary.rows.isEmpty()) {
                EmptyState(
                    title = "Nothing agreed in this window",
                    body = "Fees show up here once a host confirms a date with you.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
                    EyebrowLabel("Recent")
                    summary.rows.forEachIndexed { index, row ->
                        EarningRow(row = row, onClick = { onBookingClick(row.bookingId) })
                        if (index != summary.rows.lastIndex) HRule()
                    }
                }
            }

            Banner(
                title = "These are the fees you agreed in the app, not money Artistant " +
                    "moved. Use it for your own books — we can't certify it as income.",
                tone = BannerTone.Note,
            )
        }
    }
}

@Composable
private fun DeltaPill(percent: Int, up: Boolean) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = DELTA_FILL))
            .padding(horizontal = dimens.space.md, vertical = dimens.space.xs + dimens.space.xs / 4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Icon(
            imageVector = if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = colors.accentDeep,
            modifier = Modifier.height(dimens.size.iconMd),
        )
        Text(
            text = if (up) "+$percent%" else "−$percent%",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.accentDeep,
        )
    }
}

/**
 * Twelve trailing months of agreed fees.
 *
 * Bars are proportional to the tallest bucket, not to a fixed rupee scale: the
 * chart is shape, and the figure above it is the number. A zero-height bar would
 * vanish, so every bucket keeps a hairline floor — an empty month reads as an
 * empty month rather than as a gap in the chart.
 */
@Composable
private fun EarningsChart(bars: List<EarningsBar>) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val max = bars.maxOfOrNull { it.amountInr }?.takeIf { it > 0 } ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(dimens.component.barChart)
                .semantics {
                    contentDescription = "Agreed fees over the last ${bars.size} months"
                },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.xs / 2),
        ) {
            bars.forEach { bar ->
                val fraction = (bar.amountInr.toFloat() / max).coerceIn(BAR_FLOOR, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(
                            RoundedCornerShape(
                                topStart = dimens.component.barRadius,
                                topEnd = dimens.component.barRadius,
                            ),
                        )
                        .background(if (bar.recent) colors.accent else colors.hairline),
                )
            }
        }
        // Every third label only: twelve mono months at 10.5 on a 390dp phone
        // overlap into a smear.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            bars.filterIndexed { index, _ -> index % AXIS_EVERY == 0 || index == bars.lastIndex }
                .forEach { bar ->
                    Text(bar.label, style = AppTheme.type.monoWeekday, color = colors.ink3)
                }
        }
    }
}

@Composable
private fun EarningRow(row: EarningsRow, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.sm))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = AppTheme.type.rowTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.dateLabel,
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatInr(row.amountInr),
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
            )
            Text(
                row.state,
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
        }
    }
}

/** The delta pill's accent wash — lighter than a banner, heavier than a chip. */
private const val DELTA_FILL = 0.4f

/** Minimum visible bar height, as a fraction of the chart box. */
private const val BAR_FLOOR = 0.04f

/** Draw one axis label every N buckets. */
private const val AXIS_EVERY = 3
