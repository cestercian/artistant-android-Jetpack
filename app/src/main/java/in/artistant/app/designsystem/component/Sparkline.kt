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
) {
    val colors = AppTheme.colors
    val strokeColor = colors.brand
    val track = colors.line
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        if (values.isEmpty()) {
            drawLine(track, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 2f)
            return@Canvas
        }
        val min = (values.minOrNull() ?: 0).coerceAtMost(0)
        val max = (values.maxOrNull() ?: 100).coerceAtLeast(min + 1)
        val range = (max - min).toFloat()
        fun y(v: Int): Float = h - ((v - min) / range) * h
        if (values.size == 1) {
            val y = y(values[0])
            drawLine(strokeColor, Offset(0f, y), Offset(w, y), strokeWidth = 3f, cap = StrokeCap.Round)
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
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
