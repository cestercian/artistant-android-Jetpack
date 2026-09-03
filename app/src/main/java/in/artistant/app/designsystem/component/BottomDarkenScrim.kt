package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import `in`.artistant.app.designsystem.theme.AppTheme

// ─────────────────────────────────────────────────────────────────────────────
// Over-media chrome.
//
// The Sep-2026 redesign turned the app light, and this file is the exception it
// did not touch (REDESIGN_2026-09 §4 says so explicitly): a cover photo is still
// a photo, so anything sitting on one still has to survive an image nobody
// controls. Text over media takes white on a scrim, not `ink` on `page`.
//
// What DID change is where these are allowed. The scrim used to be applied to
// dark non-media surfaces too, as a general-purpose bottom vignette; on a light
// page that reads as a smudge. Both helpers below are now for media only —
// `ArtistTile`, the EPK cover, Discover's hero controls — and nothing else.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The 4-stop bottom-darken scrim — gentle top vignette, clear middle, deep bottom
 * so a headline stays legible over any cover photo. Drop it directly above a
 * cover image, below the text that has to survive it.
 *
 * `matchParentSize` rather than `fillMaxSize`: a scrim must never take part in
 * measuring the surface it darkens, or a fixed-size media tile can be stretched
 * by its own overlay.
 *
 * [HeroCard] does not use this — it ramps a single gradient over the bottom 45%
 * of the card instead, because its text block is anchored to the bottom edge and
 * a four-stop vignette would darken the subject of the photograph as well.
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

/**
 * A control floating directly on a photo — Discover's status capsule and save
 * button.
 *
 * Two `background` layers, in order: darken whatever is behind, then lift the
 * result. One flat fill cannot do both — it can only move the composite in one
 * direction — and a translucent white alone would leave the control invisible
 * over a bright sky. See `AppColors.glassSoftScrim` for the calibration.
 *
 * This is deliberately NOT the light palette's `surface2` disc ([IconCircle]).
 * An opaque disc on a hero reads as a sticker pasted onto the image; these
 * belong to the photo.
 */
@Composable
fun Modifier.heroGlass(shape: Shape): Modifier {
    val colors = AppTheme.colors
    return this
        .clip(shape)
        .background(colors.glassSoftScrim)
        .background(colors.glassSoftVeil)
}
