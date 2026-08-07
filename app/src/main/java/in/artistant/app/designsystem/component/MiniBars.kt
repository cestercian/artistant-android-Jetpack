package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * A small run of daily bars — the "recent momentum" companion to a headline
 * count.
 *
 * Deliberately not the same shape as [Sparkline]. A line implies a continuous
 * quantity you read the *trend* of; these buckets are counts of discrete events,
 * where zero days are meaningful and adjacent days aren't interpolated between.
 * Drawing them as bars says that.
 *
 * A single [Canvas] rather than a Row of Boxes: fourteen layout nodes for
 * something this small is real cost on a low-end device, and the draw here is
 * one pass of rounded rects with no measurement of its own.
 *
 * Empty and all-zero inputs draw nothing — the caller decides whether an absent
 * chart or a flat row of stubs is the honest state, and for a count series a row
 * of zero-height bars reads as "we drew a chart of nothing" rather than "there
 * is nothing".
 */
@Composable
fun MiniBars(
    values: List<Int>,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        AppTheme.dimens.dashboard.barGap.toPx()
    }
    val fill = colors.brand
    val track = colors.bgSoft

    Canvas(modifier = modifier.size(width, height)) {
        if (values.isEmpty()) return@Canvas
        val max = values.max()
        val slot = size.width / values.size
        val barWidth = (slot - gapPx).coerceAtLeast(1f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        values.forEachIndexed { i, v ->
            val x = i * slot
            // A zero day still gets a visible stub in the track colour, so the
            // series reads as "seven days, two of which had work" rather than as
            // a two-bar chart floating in space.
            val ratio = if (max <= 0) 0f else v.toFloat() / max
            val barHeight = (size.height * ratio).coerceAtLeast(barWidth)
            drawRoundRect(
                color = if (v > 0) fill else track,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}
