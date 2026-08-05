package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The role-tinted atmosphere that sits behind a screen's header and fades to
 * black — a soft brand-coloured glow rather than a flat black page.
 *
 * Two overlapping radials, both fading to transparent:
 *
 * - a strong one high and centred, so the glow reads as coming from *behind the
 *   header*;
 * - a weaker one low and to the left, which breaks the first one's perfect
 *   symmetry. Without it the wash looks like a spotlight; with it, like light in
 *   a room.
 *
 * The radii are absolute (420dp / 280dp), not fractions of the viewport. That is
 * on purpose: a fraction-sized radial scales with the screen and so produces a
 * visibly different atmosphere on a tablet than on a phone, whereas a fixed
 * radius keeps the *falloff* constant and simply covers less of a larger screen
 * — which is what "ambient light" actually does.
 *
 * Drawn with [drawBehind] rather than as a child Box so it costs no layout node
 * and can never intercept a touch.
 *
 * ## Usage
 * Paint this as the page backdrop *instead of* an opaque `colors.bg` fill — a
 * screen that paints `background(colors.bg)` over the top will hide it
 * completely.
 */
@Composable
fun Modifier.ambientRoleWash(brand: Color, background: Color): Modifier =
    this.drawBehind {
        drawRect(background)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(brand.copy(alpha = PRIMARY_ALPHA), Color.Transparent),
                center = Offset(size.width * PRIMARY_CENTER_X, size.height * PRIMARY_CENTER_Y),
                radius = PRIMARY_RADIUS.toPx(),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(brand.copy(alpha = SECONDARY_ALPHA), Color.Transparent),
                center = Offset(size.width * SECONDARY_CENTER_X, size.height * SECONDARY_CENTER_Y),
                radius = SECONDARY_RADIUS.toPx(),
            ),
        )
    }

/**
 * Composable form for callers that want the wash as a standalone backdrop layer
 * inside an existing `Box` (e.g. a scaffold container) rather than as a modifier
 * on the content itself.
 */
@Composable
fun AmbientRoleWash(brand: Color, background: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().ambientRoleWash(brand, background))
}

// Tuned against the reference build. Kept private to this file: they are the
// wash's internal shape, not app-wide design tokens — no other surface should
// reach for "0.28 brand at 18% height".
private const val PRIMARY_ALPHA = 0.28f
private const val PRIMARY_CENTER_X = 0.5f
private const val PRIMARY_CENTER_Y = 0.18f
private val PRIMARY_RADIUS = 420.dp

private const val SECONDARY_ALPHA = 0.10f
private const val SECONDARY_CENTER_X = 0.18f
private const val SECONDARY_CENTER_Y = 0.55f
private val SECONDARY_RADIUS = 280.dp
