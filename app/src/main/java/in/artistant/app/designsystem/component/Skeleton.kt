package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * A shimmering placeholder block — the `Skeleton` port (iOS `Skeleton`). A
 * left-to-right highlight sweeps across a `bgCard` ground on an infinite loop.
 * Used by the Discover / Search / ArtistProfile loading states. `drawWithCache`
 * so the gradient brush is rebuilt only when the offset animation ticks, not on
 * every recomposition.
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AppTheme.dimens.radii.md,
) {
    val colors = AppTheme.colors
    // One shared infinite clock drives the sweep phase 0→1 and back.
    val transition = rememberInfiniteTransition(label = "skeleton")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "sweep",
    )
    val base = colors.bgCard
    val highlight = colors.bgSoft
    // The caller owns sizing (fixed width/height for a rail tile, fillMaxWidth +
    // height for a hero block) — Skeleton only paints the shimmer.
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(base)
            .drawWithCache {
                // Map the 0..1 phase across a band 2× the width so the highlight
                // travels fully off-screen at each end (no visible snap).
                val span = size.width * 2f
                val start = -size.width + phase * span
                val brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(start, 0f),
                    end = Offset(start + size.width, 0f),
                )
                onDrawBehind { drawRect(brush) }
            },
    )
}
