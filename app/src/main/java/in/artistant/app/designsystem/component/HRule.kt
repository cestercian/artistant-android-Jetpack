package `in`.artistant.app.designsystem.component

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import `in`.artistant.app.designsystem.theme.AppTheme

/** A 1dp hairline in the `line` token — the HRule port. */
@Composable
fun HRule(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = AppTheme.dimens.size.hairline,
        color = AppTheme.colors.line,
    )
}

/**
 * The same hairline as [HRule], drawn along the caller's own bottom edge instead
 * of being laid out beneath it.
 *
 * Use this when the rule belongs TO a row — a list row, an action row, anything
 * in a stack where every entry ends in a separator. [HRule] is a sibling with a
 * height, so a stack of N such rows comes out N units taller than the rows
 * themselves, and each row's pitch stops being its height. That is a small error
 * that compounds: the reference draws these separators as a zero-height overlay,
 * and its conversation-actions list measured exactly one unit per row shorter
 * than ours for that reason alone.
 *
 * Keep [HRule] for the other case — a rule *between* two blocks, where the space
 * it occupies is the point.
 *
 * Order matters in the modifier chain: put this BEFORE the row's own padding so
 * the drawn size is the padded row, not the content inside it.
 */
@Composable
fun Modifier.hairlineBottom(): Modifier {
    val color = AppTheme.colors.line
    val thickness = AppTheme.dimens.size.hairline
    return drawWithContent {
        drawContent()
        val t = thickness.toPx()
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - t),
            size = Size(size.width, t),
        )
    }
}

/**
 * [hairlineBottom]'s mirror — the rule along a container's TOP edge.
 *
 * The one thing that needs it is a bar pinned to the bottom of the window: the
 * design gives every pinned CTA a `border-top` and nothing else (no shadow, no
 * fill change), so the hairline is the only thing separating the action from the
 * content scrolling under it. Drawn rather than laid out for the same reason as
 * [hairlineBottom] — a sibling rule would add a unit of height to a bar whose
 * padding is already measured.
 */
@Composable
fun Modifier.hairlineTop(): Modifier {
    val color = AppTheme.colors.line
    val thickness = AppTheme.dimens.size.hairline
    return drawWithContent {
        drawContent()
        drawRect(color = color, size = Size(size.width, thickness.toPx()))
    }
}
