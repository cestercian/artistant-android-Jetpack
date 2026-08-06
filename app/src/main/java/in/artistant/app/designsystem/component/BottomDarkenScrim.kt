package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The 4-stop bottom-darken scrim — gentle top vignette, clear middle, deep bottom
 * so a headline stays legible over any cover photo. Drop it directly above a
 * cover image, below the text that has to survive it.
 *
 * `matchParentSize` rather than `fillMaxSize`: a scrim must never take part in
 * measuring the surface it darkens, or a fixed-size media tile can be stretched
 * by its own overlay.
 *
 * This file used to also hold `MediaContainer`, a fixed-aspect media surface that
 * never acquired a caller. It was removed rather than adopted — see the commit
 * that deleted it. The scrim survived because it is the piece that was actually
 * being copied: the ArtistTile carried these exact four stops inline.
 */
@Composable
fun BoxScope.BottomDarkenScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    // location → alpha, mirroring the reference build's stops.
                    0.0f to Color.Black.copy(alpha = 0.20f),
                    0.30f to Color.Transparent,
                    0.65f to Color.Black.copy(alpha = 0.45f),
                    1.0f to Color.Black.copy(alpha = 0.85f),
                ),
            ),
    )
}
