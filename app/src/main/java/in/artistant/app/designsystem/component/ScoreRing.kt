package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.domain.score.tierColor

/**
 * Bookability Score ring — port of iOS `ScoreRing`.
 * [value] null = New tier (<5 gigs): empty ring + "NEW" center, never a misleading "0".
 */
@Composable
fun ScoreRing(
    value: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 6.dp,
    showLabel: Boolean = true,
    totalGigs: Int? = null,
) {
    val colors = AppTheme.colors
    val isNew = value == null || (totalGigs != null && totalGigs < ScoreBands.MIN_GIGS_FOR_RANK)
    val numeric = if (isNew) 0 else value!!.coerceIn(0, 100)
    val tier = if (isNew) ScoreTier.New else ScoreBands.tier(numeric, totalGigs)
    val arcColor = if (isNew) colors.ink4 else tierColor(tier, colors)
    val pct = numeric / 100f
    val a11y = if (isNew) {
        "Bookability score New, not enough completed gigs yet"
    } else {
        "Bookability score $numeric out of 100, ${tier.label} tier"
    }

    Column(
        modifier = modifier.semantics { contentDescription = a11y },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size)) {
                val strokeWidth = stroke.toPx()
                val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                drawArc(
                    color = colors.ink.copy(alpha = 0.08f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = style,
                    size = Size(this.size.width, this.size.height),
                )
                rotate(-90f) {
                    drawArc(
                        color = arcColor,
                        startAngle = 0f,
                        sweepAngle = 360f * pct,
                        useCenter = false,
                        style = style,
                        size = Size(this.size.width, this.size.height),
                    )
                }
            }
            if (isNew) {
                Text(
                    "NEW",
                    style = AppTheme.type.monoSmall.copy(
                        fontSize = (size.value * 0.22f).sp,
                        letterSpacing = 1.2.sp,
                    ),
                    color = arcColor,
                )
            } else {
                Text(
                    "$numeric",
                    style = AppTheme.type.monoMedium.copy(fontSize = (size.value * 0.32f).sp),
                    color = colors.ink,
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                (if (isNew) "NEW" else tier.label).uppercase(),
                style = AppTheme.type.caption.copy(letterSpacing = 1.2.sp),
                color = arcColor,
            )
        }
    }
}
