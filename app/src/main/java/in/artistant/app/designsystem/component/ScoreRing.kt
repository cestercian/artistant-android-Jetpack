package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier

/**
 * Tier → accent color (iOS `Color.tier(for:)`): Elite = brand, Trusted = good,
 * Rising = warm, New = ink3. Single source so the ring, the tile score dot, and
 * the breakdown sheet never drift. `@Composable` because it reads theme tokens.
 */
@Composable
fun tierColor(tier: ScoreTier): Color {
    val c = AppTheme.colors
    return when (tier) {
        ScoreTier.Elite -> c.brand
        ScoreTier.Trusted -> c.good
        ScoreTier.Rising -> c.warm
        ScoreTier.New -> c.ink3
    }
}

/** Convenience: resolve the tier from a raw score + gig count, then its color. */
@Composable
fun tierColor(score: Int, totalGigs: Int? = null): Color =
    tierColor(ScoreBands.tier(score, totalGigs))

/**
 * The Bookability Score ring — a shared `Canvas` arc over a track with the mono
 * numeral (or a "NEW" pill) centred (iOS `ScoreRing`, Phase D #3 nil-handling).
 *
 * [value] null = the "New" tier: an empty ring in the muted `ink4` color with a
 * "NEW" pill instead of a misleading `0`. A non-null value derives its tier band
 * (and therefore color) via the shared [ScoreBands]; callers that know an artist
 * is New-by-gig-count pass null so the two paths agree. The arc animates from 0
 * on first paint (iOS `.easeOut(0.6)`).
 */
@Composable
fun ScoreRing(
    value: Int?,
    modifier: Modifier = Modifier,
    size: Dp = AppTheme.dimens.size.ringMd,
    stroke: Dp = 6.dp,
    showLabel: Boolean = true,
) {
    val colors = AppTheme.colors
    val isNew = value == null
    val numeric = (value ?: 0).coerceIn(0, 100)
    val tier = if (isNew) ScoreTier.New else ScoreBands.tier(numeric)
    // NEW rings use the muted ink4 fill (matches iOS); ranked rings tier-color.
    val ringColor = if (isNew) colors.ink4 else tierColor(tier)
    val labelColor = if (isNew) colors.ink4 else tierColor(tier)

    // Animate the fill fraction on appear so the arc sweeps in.
    val target = if (isNew) 0f else numeric / 100f
    val pct by animateFloatAsState(targetValue = target, label = "scoreRingPct")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val w = stroke.toPx()
                val inset = w / 2
                val arcSize = androidx.compose.ui.geometry.Size(
                    this.size.width - w, this.size.height - w,
                )
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                // Track — faint full circle.
                drawArc(
                    color = colors.ink.copy(alpha = 0.08f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = w),
                )
                // Progress — starts at 12 o'clock (‑90°), rounded cap.
                drawArc(
                    color = ringColor,
                    startAngle = -90f, sweepAngle = 360f * pct, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
            if (isNew) {
                Text(
                    "NEW",
                    style = AppTheme.type.monoSmall.copy(
                        fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Black,
                    ),
                    color = ringColor,
                )
            } else {
                Text(
                    "$numeric",
                    style = AppTheme.type.scoreRing.copy(fontSize = (size.value * 0.32f).sp),
                    color = colors.ink,
                )
            }
        }
        if (showLabel) {
            Text(
                if (isNew) "NEW" else tier.label.uppercase(),
                style = AppTheme.type.caption,
                color = labelColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
