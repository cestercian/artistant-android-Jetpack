package `in`.artistant.app.feature.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
            Text("Bookability Score", style = AppTheme.type.headline, color = colors.ink)
            Spacer(Modifier.height(space.sm))
            ScoreRing(
                value = ring,
                size = AppTheme.dimens.size.ringLg,
                stroke = 6.dp,
                showLabel = true,
                totalGigs = gigs,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(space.lg))
            HRule()
            Spacer(Modifier.height(space.lg))

            if (tier == ScoreTier.New) {
                Text(
                    "Still building a score — under 5 completed gigs. Gigs and reviews still count.",
                    style = AppTheme.type.body,
                    color = colors.ink2,
                )
                Spacer(Modifier.height(space.lg))
                RealRow("Gigs played", "$gigs")
                ReviewRow(reviewAvg, reviews.size, reviewsFailed)
            } else {
                RealRow("Gigs played", "$gigs")
                breakdown?.let { b ->
                    if (b.showUpRate > 0) {
                        RealRow("Shows up", "${b.showUpRate}%")
                    }
                    ReviewRow(reviewAvg, reviews.size, reviewsFailed)
                    replyLabel(b.replySpeed)?.let { RealRow("Replies", it) }
                    if (b.cancellationRate > 0) {
                        RealRow("Cancellations", "${b.cancellationRate}%")
                    }
                } ?: run {
                    ReviewRow(reviewAvg, reviews.size, reviewsFailed)
                }
            }
        }
    }
}

@Composable
private fun RealRow(label: String, value: String) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().padding(vertical = space.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppTheme.type.callout, color = colors.ink2)
        Text(value, style = AppTheme.type.monoMedium, color = colors.ink)
    }
}

@Composable
private fun ReviewRow(avg: Double?, count: Int, failed: Boolean) {
    when {
        failed -> RealRow("Reviews", "Couldn't load")
        avg == null || count == 0 -> RealRow("Reviews", "none yet")
        else -> RealRow("Reviews", String.format("%.1f across %d", avg, count))
    }
}

/**
 * Invertible linear map from metric_reply_speed (≤5min→100, ≥24h→0).
 * Approximate duration for client-facing copy.
 */
private fun replyLabel(speed: Int): String? {
    if (speed <= 0) return null
    val minutes = ((100 - speed) / 100.0 * (24 * 60 - 5) + 5).toInt().coerceAtLeast(1)
    return when {
        minutes < 60 -> "Replies ~${minutes}m"
        minutes < 24 * 60 -> "Replies ~${minutes / 60}h"
        else -> "Replies ~1d"
    }
}
