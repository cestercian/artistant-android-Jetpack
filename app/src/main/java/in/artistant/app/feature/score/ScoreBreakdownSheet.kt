package `in`.artistant.app.feature.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.domain.score.tierColor

/**
 * Client-facing Bookability Score sheet — port of iOS ScoreBreakdownSheet.
 * Real-world rows (gigs, show-up %, reviews, reply estimate, cancellations),
 * not the self-facing 0–100 sub-scores.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreBreakdownSheet(
    artist: Artist,
    breakdown: ScoreBreakdown?,
    reviews: List<Review>,
    reviewsFailed: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tier = breakdown?.tier ?: ScoreBands.tier(artist.score, artist.gigs)
    val ring = breakdown?.numericScore ?: if (tier == ScoreTier.New) null else artist.score
    val gigs = breakdown?.totalGigs ?: artist.gigs
    val reviewAvg = if (reviews.isEmpty()) null else reviews.map { it.rating }.average()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.xl)
                .padding(bottom = space.xxl),
        ) {
            // Identity row: the ring rides at the LEADING edge with the title and
            // tier stacked beside it, rather than centred under the title. Centring
            // it cost roughly a third of the sheet's height to one number, and
            // pushed the rows that explain that number below the fold — which is
            // the wrong way round for a sheet whose whole job is the explanation.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(
                    value = ring,
                    size = AppTheme.dimens.size.ringMd,
                    stroke = 6.dp,
                    // The tier is spelled out beside the title now, so the ring's
                    // own label would print it twice.
                    showLabel = false,
                    totalGigs = gigs,
                )
                Spacer(Modifier.width(space.lg))
                Column(Modifier.weight(1f)) {
                    Text("Bookability Score", style = AppTheme.type.headline, color = colors.ink)
                    Text(
                        tier.name.uppercase(),
                        style = AppTheme.type.monoSmall,
                        color = tierColor(tier, colors),
                    )
                }
            }
            Spacer(Modifier.height(space.lg))

            if (tier == ScoreTier.New) {
                HRule()
                Spacer(Modifier.height(space.lg))
                Text(
                    "Still building a score — under 5 completed gigs. Gigs and reviews still count.",
                    style = AppTheme.type.body,
                    color = colors.ink2,
                )
                Spacer(Modifier.height(space.lg))
                RealRow("Gigs played", "$gigs", "on Artistant")
                ReviewRow(reviewAvg, reviews.size, reviewsFailed)
            } else {
                RealRow("Gigs played", "$gigs", "on Artistant")
                breakdown?.let { b ->
                    if (b.showUpRate > 0) {
                        RealRow("Shows up", "${b.showUpRate}%", "of confirmed shows")
                    }
                    ReviewRow(reviewAvg, reviews.size, reviewsFailed)
                    replyLabel(b.replySpeed)?.let { RealRow("Replies", it, "typical response") }
                    if (b.cancellationRate > 0) {
                        RealRow(
                            "Cancellations",
                            "${b.cancellationRate}%",
                            "of bookings, last 12 months",
                        )
                    }
                } ?: run {
                    ReviewRow(reviewAvg, reviews.size, reviewsFailed)
                }
            }

            // Closing note. Without it the rows are five unsourced statistics; the
            // sheet exists because clients ask where the number comes from.
            Spacer(Modifier.height(space.lg))
            Text(
                "Computed from completed bookings, verified reviews, and chat response " +
                    "times on Artistant.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        }
    }
}

/**
 * One statistic. The trailing group is a value plus a QUALIFIER — "96%" means
 * nothing without "of confirmed shows" next to it, and the sheet was shipping
 * the bare figures. The qualifier is set dimmer so the number still leads.
 *
 * Each row closes with a hairline, which is also what pushes the row pitch onto
 * the reference's: at `space.sm` and no rule the rows sat 36.6 apart against a
 * reference 43.
 */
@Composable
private fun RealRow(label: String, value: String, qualifier: String? = null) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().padding(vertical = space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = AppTheme.type.callout, color = colors.ink2)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = AppTheme.type.monoMedium, color = colors.ink)
            if (qualifier != null) {
                Spacer(Modifier.width(space.sm))
                Text(qualifier, style = AppTheme.type.footnote, color = colors.ink3)
            }
        }
    }
    HRule()
}

@Composable
private fun ReviewRow(avg: Double?, count: Int, failed: Boolean) {
    when {
        failed -> RealRow("Reviews", "Couldn't load")
        avg == null || count == 0 -> RealRow("Reviews", "none yet")
        else -> RealRow(
            "Reviews",
            String.format("%.1f", avg),
            if (count == 1) "across 1 review" else "across $count reviews",
        )
    }
}

/**
 * Invertible linear map from metric_reply_speed (≤5min→100, ≥24h→0).
 * Approximate duration for client-facing copy.
 *
 * Returns the DURATION only. It used to prefix each string with "Replies", and
 * the row it feeds is already labelled "Replies" — so the sheet was rendering
 * "Replies · Replies ~6h".
 */
private fun replyLabel(speed: Int): String? {
    if (speed <= 0) return null
    val minutes = ((100 - speed) / 100.0 * (24 * 60 - 5) + 5).toInt().coerceAtLeast(1)
    return when {
        minutes < 60 -> "~${minutes}m"
        minutes < 24 * 60 -> "~${minutes / 60}h"
        else -> "~1d"
    }
}
