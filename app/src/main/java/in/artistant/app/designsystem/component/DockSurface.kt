package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The raised bar pinned to the bottom of a screen — the "Send request",
 * "Accept / Decline", "Submit quote" docks.
 *
 * Only the TOP corners round. The bar is flush with the bottom of the display,
 * so its lower corners have no edge to soften: rounding them would cut two
 * notches out of the fill and show the page through, which looks like a
 * rendering fault rather than a rounded card.
 *
 * Four screens were each hand-rolling `fillMaxWidth().background(bgElev)` with
 * no shape at all, giving the app four hard-edged slabs sitting under otherwise
 * fully-rounded content. Collapsing them onto one modifier means the shape is
 * defined once — and the next dock inherits it instead of re-introducing the bug.
 *
 * Note `clip` precedes `background`: clipping the node also clips anything drawn
 * into it later, so a caller adding a ripple or an image cannot spill past the
 * rounded corners.
 */
@Composable
fun Modifier.dockSurface(): Modifier {
    val dimens = AppTheme.dimens
    return this
        .fillMaxWidth()
        .clip(RoundedCornerShape(topStart = dimens.radii.xl, topEnd = dimens.radii.xl))
        .background(AppTheme.colors.bgElev)
}
