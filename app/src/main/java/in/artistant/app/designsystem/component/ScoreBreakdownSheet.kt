package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.averageRating
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import kotlin.math.roundToInt

/**
 * Client-facing Bookability Score detail sheet (iOS `ScoreBreakdownSheet`),
 * opened by tapping the hero score chip. Shows REAL-WORLD values, not sub-scores:
 * "42 gigs played", "shows up 96%", "4.9 across 31 reviews", "replies ~1h".
 *
 * Data honesty is preserved from iOS:
 *  - `0` is a SENTINEL for show-up / reply speed (no eligible data), so those
 *    rows are SUPPRESSED at 0 rather than stating a fabricated worst case.
 *  - a failed reviews fetch reads "couldn't load", never "none yet".
 *  - the New tier shows the truthful "not enough history" explainer, not zeros.
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Tier from the freshest source: the fetched breakdown, else the cached
    // Artist score + gigs (both honor the "<5 gigs = New" rule).
    val tier: ScoreTier = breakdown?.tier ?: ScoreBands.tier(artist.score, artist.gigs)
    val ringValue: Int? = breakdown?.numericScore ?: if (tier == ScoreTier.New) null else artist.score
    val gigsPlayed = breakdown?.totalGigs ?: artist.gigs

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElev,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.xl)
                .padding(bottom = space.xl),
        ) {
            // Header — ring + title + tier label.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(value = ringValue, size = AppTheme.dimens.size.ringMd, stroke = 5.dp, showLabel = false)
                Spacer(Modifier.width(space.lg))
                Column {
                    Text("Bookability Score", style = AppTheme.type.displaySmall, color = colors.ink)
                    Text(
                        tier.label.uppercase(),
                        style = AppTheme.type.caption,
                        color = tierColor(tier),
                    )
                }
            }
            Spacer(Modifier.height(space.lg))
            HRule()

            if (tier == ScoreTier.New) {
                NewArtistExplainer(gigsPlayed, reviews, reviewsFailed)
            } else {
                MetricRows(breakdown, gigsPlayed, reviews, reviewsFailed)
            }

            Spacer(Modifier.height(space.lg))
            Text(
                "Computed from completed bookings, verified reviews, and chat response times on Artistant.",
                style = AppTheme.type.caption,
                color = colors.ink3,
            )
        }
    }
}

@Composable
private fun MetricRows(
    breakdown: ScoreBreakdown?,
    gigsPlayed: Int,
    reviews: List<Review>,
    reviewsFailed: Boolean,
) {
    Column {
        MetricRow("Gigs played", "$gigsPlayed", "on Artistant")
        if (breakdown != null && breakdown.showUpRate > 0) {
            HRule()
            MetricRow("Shows up", "${breakdown.showUpRate}%", "of confirmed shows")
        }
        HRule()
        ReviewRow(reviews, reviewsFailed)
        if (breakdown != null) {
            if (breakdown.replySpeed > 0) {
                HRule()
                MetricRow("Replies", replyEstimate(breakdown.replySpeed), "typical response")
            }
            HRule()
            MetricRow("Cancellations", "${breakdown.cancellationRate}%", "of bookings, last 12 months")
        }
    }
}

@Composable
private fun NewArtistExplainer(gigsPlayed: Int, reviews: List<Review>, reviewsFailed: Boolean) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val preGigGate = gigsPlayed < 5
    Column(
        Modifier.padding(top = space.lg),
        verticalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Text(
            if (preGigGate) "New on Artistant" else "Score still settling",
            style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
        )
        Text(
            if (preGigGate)
                "The Bookability Score unlocks after five completed gigs — until then there isn't enough history to be fair to anyone."
            else
                "There isn't enough recent activity to pin a fair number on this artist yet — the score fills in as bookings and reviews land.",
            style = AppTheme.type.footnote,
            color = colors.ink2,
        )
        Column {
            MetricRow("Gigs played", "$gigsPlayed", if (preGigGate) "of 5 needed" else "on Artistant")
            HRule()
            ReviewRow(reviews, reviewsFailed)
        }
    }
}

@Composable
private fun ReviewRow(reviews: List<Review>, reviewsFailed: Boolean) {
    val value = if (reviews.isEmpty()) "—" else String.format("%.1f", reviews.averageRating())
    val context = when {
        reviews.isNotEmpty() -> "across ${reviews.size} review${if (reviews.size == 1) "" else "s"}"
        reviewsFailed -> "couldn't load"
        else -> "none yet"
    }
    MetricRow("Reviews", value, context)
}

@Composable
private fun MetricRow(label: String, value: String, context: String) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppTheme.type.callout, color = colors.ink2)
        Spacer(Modifier.width(AppTheme.dimens.space.sm))
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
        )
        Spacer(Modifier.width(AppTheme.dimens.space.sm))
        Text(context, style = AppTheme.type.footnote, color = colors.ink3)
    }
}

/**
 * Inverts the linear 0025 reply-speed mapping back to an approximate duration
 * (iOS `ScoreBreakdownSheet.replyEstimate`): median ≤5 min → 100, ≥24h → 0, so
 * minutes ≈ 5 + (100 − score) × 1435/100. Deliberately rendered with "~" and
 * coarse units — it's reconstructed from a score, not a stored measurement.
 * `internal` so unit tests can pin the band edges.
 */
internal fun replyEstimate(score: Int): String {
    val clamped = score.coerceIn(0, 100)
    val minutes = 5.0 + (100 - clamped) * 1435.0 / 100.0
    return when {
        minutes < 55 -> "~${minutes.roundToInt()}m"
        minutes < 60.0 * 20 -> "~${(minutes / 60.0).roundToInt()}h"
        else -> "~a day"
    }
}
