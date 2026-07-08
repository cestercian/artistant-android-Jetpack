package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Minimal sparkline — line + gradient area fill + lit dot at the last point (port
 * of iOS `Components/Sparkline.swift`, the B1 home earnings chart). Pass any range
 * of doubles; we normalize to the drawn height. Width/height come from the parent
 * via `Modifier` (fill the container). A flat series (all-equal, e.g. all-zero)
 * still draws a mid-height line rather than dividing by a zero range.
 */
@Composable
fun Sparkline(
    data: List<Double>,
    modifier: Modifier = Modifier,
    stroke: Color = AppTheme.colors.brand,
    dotRadius: Float = 4f,
) {
    Canvas(modifier) {
        if (data.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val ys = normalize(data, h)
        // Single point → one dot, no path (avoids a degenerate 0-width step).
        val step = if (ys.size > 1) w / (ys.size - 1) else 0f

        // Gradient area fill under the line, fading to transparent at the bottom.
        val area = Path().apply {
            moveTo(0f, ys[0])
            for (i in 1 until ys.size) lineTo(i * step, ys[i])
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                colors = listOf(stroke.copy(alpha = 0.25f), stroke.copy(alpha = 0f)),
                startY = 0f, endY = h,
            ),
        )

        // The line itself.
        if (ys.size > 1) {
            val line = Path().apply {
                moveTo(0f, ys[0])
                for (i in 1 until ys.size) lineTo(i * step, ys[i])
            }
            drawPath(
                line,
                color = stroke,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Lit dot at the last point (halo + solid).
        val lastX = if (ys.size > 1) w else w / 2f
        val lastY = ys.last()
        drawCircle(stroke.copy(alpha = 0.2f), radius = dotRadius * 2.2f, center = Offset(lastX, lastY))
        drawCircle(stroke, radius = dotRadius, center = Offset(lastX, lastY))
    }
}

/**
 * Maps values to y-pixels (top = high value), preserving the iOS arithmetic:
 * a padded band `[3, h-3]` so the stroke + dot never clip at the edges. A zero
 * range (flat series) pins to the mid band instead of NaN-dividing.
 */
private fun normalize(values: List<Double>, height: Float): List<Float> {
    val mn = values.min()
    val mx = values.max()
    val range = (mx - mn).takeIf { it > 0.0001 } ?: 0.0001
    val usable = height - 6.0
    return values.map { v ->
        val n = (v - mn) / range                 // 0..1
        (usable - (n * usable + 3.0) + 3.0).toFloat()
    }
}

/**
 * Mini bar chart — fixed-count series, last bar in brand, others translucent
 * (iOS `MiniBars`, the BOOKINGS / 7D card). A max of 0 (all-empty) draws the
 * floor-height bars so the row isn't blank.
 */
@Composable
fun MiniBars(
    data: List<Double>,
    modifier: Modifier = Modifier,
    gap: Float = 4f,
) {
    val brand = AppTheme.colors.brand
    val faint = AppTheme.colors.ink.copy(alpha = 0.18f)
    Canvas(modifier) {
        if (data.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val mx = data.max().takeIf { it > 0 } ?: 1.0
        val barW = ((w - gap * (data.size - 1)) / data.size).coerceAtLeast(2f)
        data.forEachIndexed { i, v ->
            val barH = ((v / mx) * (h - 2)).toFloat().coerceAtLeast(2f)
            val x = i * (barW + gap)
            drawRoundRect(
                color = if (i == data.lastIndex) brand else faint,
                topLeft = Offset(x, h - barH),
                size = androidx.compose.ui.geometry.Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
        }
    }
}
