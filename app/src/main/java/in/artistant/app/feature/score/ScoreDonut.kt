package `in`.artistant.app.feature.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import `in`.artistant.app.designsystem.component.EM_DASH
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The self-facing score, as the light design draws it: a thick ring with the
 * headline word or number in the hole, and one line of caption under it.
 *
 * Three states, and they are three different claims:
 *
 *  - **a number** — the ring fills to it in the accent;
 *  - **New** ([value] null, [unavailable] false) — a complete ring in the quiet
 *    line colour, because a New artist has no arc to draw and an empty ring
 *    would read as zero. The caption says "no score yet", which is screen 79's
 *    whole argument: new is not a low score, it is no score.
 *  - **unavailable** ([unavailable] true) — a grey ring and an em dash. Screen
 *    80 puts "This isn't your real score" *above* this for the same reason: a
 *    dash where a number belongs is otherwise read as a penalty.
 *
 * The centre glyph is sized off the ring through the density rather than tagged
 * `.sp`, so it cannot outgrow the hole at a large font scale while the ring
 * around it stays put.
 */
@Composable
internal fun ScoreDonut(
    value: Int?,
    caption: String,
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val ringSize = dimens.size.ringXl
    val stroke = dimens.size.ringXlStroke * RING_STROKE_MULTIPLIER
    val density = LocalDensity.current
    val glyphSize = with(density) { (ringSize * GLYPH_FRACTION).toSp() }
    val numeric = value?.coerceIn(0, 100)

    val trackColor = if (unavailable) colors.placeholder else colors.hairline
    val glyph = when {
        unavailable -> EM_DASH
        numeric == null -> "New"
        else -> numeric.toString()
    }
    val a11y = when {
        unavailable -> "Bookability score unavailable"
        numeric == null -> "Bookability score: New, no score yet"
        else -> "Bookability score $numeric out of 100"
    }

    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(ringSize)) {
                val width = stroke.toPx()
                val style = Stroke(width = width, cap = StrokeCap.Butt)
                // Inset by half the stroke: drawArc centres the stroke on the
                // ellipse it is handed, so the full canvas size would clip the
                // outer half of the ring away.
                val topLeft = Offset(width / 2f, width / 2f)
                val arcSize = Size(size.width - width, size.height - width)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = FULL_SWEEP,
                    useCenter = false,
                    style = style,
                    topLeft = topLeft,
                    size = arcSize,
                )
                if (numeric != null && !unavailable) {
                    rotate(START_AT_TWELVE) {
                        drawArc(
                            color = colors.accent,
                            startAngle = 0f,
                            sweepAngle = FULL_SWEEP * numeric / 100f,
                            useCenter = false,
                            style = style,
                            topLeft = topLeft,
                            size = arcSize,
                        )
                    }
                }
            }
            Text(
                text = glyph,
                style = AppTheme.type.displayHero.copy(
                    fontSize = glyphSize,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = if (unavailable) colors.ink3 else colors.ink,
            )
        }
        Text(caption, style = AppTheme.type.caption, color = colors.ink4)
    }
}

/** 8dp reads as a progress ring; the design's is a donut, so twice that. */
private const val RING_STROKE_MULTIPLIER = 2
private const val GLYPH_FRACTION = 0.26f
private const val FULL_SWEEP = 360f
private const val START_AT_TWELVE = -90f
