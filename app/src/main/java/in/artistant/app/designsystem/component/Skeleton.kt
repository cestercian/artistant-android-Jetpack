package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion

/**
 * One placeholder block.
 *
 * **The pulse is opacity, not a travelling highlight.** A shimmer sweep needs a
 * moving gradient, which is a shader per block per frame, and on a screen that
 * is nothing but eight of these it is the most expensive thing being drawn at
 * the exact moment the app is already busy. A slow alpha breath says the same
 * thing — "this is not content yet" — for the cost of one layer alpha, and it
 * stops entirely under reduce-motion, where a sweeping highlight would be one of
 * the worse offenders.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    radius: Dp = AppTheme.dimens.radii.sm,
) {
    val colors = AppTheme.colors
    Box(
        modifier
            .alpha(skeletonPulse())
            .clip(RoundedCornerShape(radius))
            .background(colors.placeholder),
    )
}

/** A circular placeholder — an avatar or a header action that has not loaded. */
@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier
            .size(size)
            .alpha(skeletonPulse())
            .clip(CircleShape)
            .background(colors.placeholder),
    )
}

/**
 * The loading form of [ScreenHeader]: two text bars and an action circle, at the
 * header's real geometry.
 */
@Composable
fun SkeletonHeader(modifier: Modifier = Modifier) {
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
            SkeletonBlock(
                Modifier
                    .width(dimens.component.skeletonTitleWidth)
                    .height(dimens.component.skeletonTitleHeight),
            )
            SkeletonBlock(
                Modifier
                    .width(dimens.component.skeletonSubtitleWidth)
                    .height(dimens.component.skeletonLineHeight),
            )
        }
        SkeletonCircle(dimens.component.iconCircleSm)
    }
}

/** The loading form of a [ChipRail]: four capsules at chip height. */
@Composable
fun SkeletonChips(modifier: Modifier = Modifier, count: Int = 4) {
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        // Uneven widths on purpose: four identical capsules read as a progress
        // control, four different ones read as words that have not arrived.
        val widths = listOf(
            dimens.component.skeletonChipWide,
            dimens.component.skeletonChipNarrow,
            dimens.component.skeletonChipWide,
            dimens.component.skeletonChipNarrow,
        )
        repeat(count) { i ->
            SkeletonBlock(
                Modifier
                    .width(widths[i % widths.size])
                    .height(dimens.component.skeletonChipHeight),
                radius = dimens.radii.lg,
            )
        }
    }
}

/**
 * The loading form of one rail: a section-header bar over two tiles at the tile's
 * real size (screen 59).
 *
 * The geometry matching is the whole point, and it is the design's own note:
 * "Skeletons match rail geometry, so the fill-in doesn't reflow what the eye has
 * already parsed." A generic grey box that is then replaced by content of a
 * different height makes the page jump exactly once — right when the user has
 * started reading it.
 */
@Composable
fun SkeletonRail(modifier: Modifier = Modifier) {
    val dimens = AppTheme.dimens
    Column(modifier = modifier.fillMaxWidth()) {
        SkeletonBlock(
            Modifier
                .width(dimens.component.skeletonSectionWidth)
                .height(dimens.component.skeletonLineHeight),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = dimens.space.md),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            repeat(2) {
                SkeletonBlock(
                    Modifier
                        .weight(1f)
                        .height(dimens.component.skeletonTile),
                    radius = dimens.radii.lg,
                )
            }
        }
    }
}

/**
 * A whole loading page: header, chips, two rails (screen 59).
 *
 * It announces itself as "Loading" and hides the blocks from the accessibility
 * tree — a screen reader has nothing useful to say about eight grey rectangles,
 * and reading their bounds one by one is worse than silence.
 */
@Composable
fun SkeletonPage(modifier: Modifier = Modifier) {
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(dimens.space.xl),
    ) {
        Column(
            Modifier.clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
        ) {
            SkeletonHeader()
            SkeletonChips()
            SkeletonRail()
            SkeletonRail()
        }
    }
}

/**
 * The shared breath. One transition per block is fine — Compose runs infinite
 * transitions off a single frame clock — and it means a block dropped anywhere
 * needs no wiring.
 */
@Composable
private fun skeletonPulse(): Float {
    if (AppTheme.reduceMotion) return REST_ALPHA
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = REST_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(AppTheme.motion.medium4 * PULSE_BEATS, easing = AppTheme.motion.standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

/** How quiet a block gets at the bottom of the breath. */
private const val REST_ALPHA = 0.55f

/** The breath is deliberately slower than any interaction timing. */
private const val PULSE_BEATS = 2

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun SkeletonPreview() {
    ArtistantTheme {
        SkeletonPage(Modifier.padding(AppTheme.dimens.component.gutter))
    }
}
