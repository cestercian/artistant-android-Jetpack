package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Minimal score history sparkline — port of iOS Sparkline used on ScoreHistorySheet.
 * Empty / single-point lists render a flat hairline so the sheet never looks broken.
 */
@Composable
fun Sparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    stroke: Dp = 3.dp,
) {
    val colors = AppTheme.colors
    val strokeColor = colors.brand
    val track = colors.line
    val trackWidth = AppTheme.dimens.size.stroke
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        // `drawLine`/`Stroke` take PIXELS: the widths used to be raw floats, so
        // the line rendered at 1dp on a 3x phone and 3dp on a 1x one.
        val strokePx = stroke.toPx()
        if (values.isEmpty()) {
            drawLine(track, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = trackWidth.toPx())
            return@Canvas
        }
        // Normalised to the series' own range, the way the iOS original is.
        // Pinning the floor to 0 flattened every score history into the top few
        // dp — scores live in a narrow high band, so 0..max is nearly all empty.
        val min = values.minOrNull() ?: 0
        val max = (values.maxOrNull() ?: 100).coerceAtLeast(min + 1)
        val range = (max - min).toFloat()
        // Half a stroke of inset top and bottom, so the round cap on the highest
        // and lowest points is drawn inside the Canvas rather than clipped by it.
        val inset = strokePx / 2f
        val plotH = (h - strokePx).coerceAtLeast(0f)
        fun y(v: Int): Float = inset + plotH - ((v - min) / range) * plotH
        if (values.size == 1) {
            val y = y(values[0])
            drawLine(strokeColor, Offset(0f, y), Offset(w, y), strokeWidth = strokePx, cap = StrokeCap.Round)
            return@Canvas
        }
        val step = w / (values.size - 1).toFloat()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val yy = y(v)
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        drawPath(
            path,
            color = strokeColor,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
