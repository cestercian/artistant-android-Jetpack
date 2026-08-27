package `in`.artistant.app.feature.signup

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

private data class Act(val name: String, val genre: String)

/** Gap between two acts, and therefore between the two sets — the wrap distance depends on it. */
private val LANE_GAP = 20.dp

private val acts = listOf(
    Act("Kaavya Rao", "INDIE"), Act("Arjun & The Echo", "ROCK"), Act("Mehfil Collective", "SUFI"),
    Act("DJ Naina", "HOUSE"), Act("The Brewhouse Trio", "JAZZ"), Act("Riya Sen", "ACOUSTIC"),
    Act("Vir Kohli", "STAND-UP"), Act("Bassline Bros", "EDM"), Act("Qawwali Nights", "DEVOTIONAL"),
    Act("Soda Pop", "POP"), Act("Tabla & Co", "FUSION"), Act("Neon Sitar", "ELECTRONIC"),
)

/**
 * Two columns of artist names + genres drifting in opposite directions, like a festival poster
 * in motion (iOS `LineupBackground`). Decorative only (auth runs pre-session, so this is a
 * curated list, not live data). Motion is gated off via [animated] under reduce-motion so it
 * stays quiescent for a11y — parity with the iOS `motionDisabled` rule.
 */
@Composable
fun LineupBackground(animated: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxSize().clipToBounds().alpha(0.375f).padding(horizontal = AppTheme.dimens.space.xl),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xl),
    ) {
        LineupLane(acts, up = true, periodMs = 34_000, animated = animated, align = Alignment.Start, modifier = Modifier.weight(1f))
        LineupLane(acts.reversed(), up = false, periodMs = 42_000, animated = animated, align = Alignment.End, modifier = Modifier.weight(1f))
    }
}

/**
 * One scrolling column. Renders the list twice and translates by exactly one set's height plus the
 * gap that separates the two sets, so the wrap lands the second set where the first began.
 *
 * The travel distance is derived from a MEASURED act instead of a guess. It used to be
 * `acts.size × 52dp`, about a quarter short of the real pitch — a 24sp serif line over a 12sp mono
 * line plus [LANE_GAP], all of which also grow with the user's font scale — so `RepeatMode.Restart`
 * snapped back mid-content every period rather than wrapping. Every act is the same two single
 * lines, so the first one answers for all of them — and it is the one child a `Column` can never
 * squeeze, since the acts below the fold are measured with whatever main-axis budget is left, which
 * is nothing (the unbounded wrap below is what gives them room). And it reads `lane`, not the
 * file-level `acts` the old spelling reached for behind the parameter's back.
 */
@Composable
private fun LineupLane(
    lane: List<Act>,
    up: Boolean,
    periodMs: Int,
    animated: Boolean,
    align: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var actHeightPx by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { LANE_GAP.toPx() }

    val fraction by if (animated) {
        rememberInfiniteTransition(label = "lineup").animateFloat(
            initialValue = if (up) 0f else 1f,
            targetValue = if (up) 1f else 0f,
            animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
            label = "lineupOffset",
        )
    } else {
        // Static mid-set frame when motion is off.
        remember { mutableFloatStateOf(0.5f) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // A set is taller than the screen on purpose; let it lay out at its own height and
            // spill past the bottom, where the Row's clipToBounds takes care of it.
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            // Both reads happen inside the layer block, so a new measurement re-draws the lane
            // without recomposing it.
            .graphicsLayer { translationY = -fraction * lane.size * (actHeightPx + gapPx) },
        verticalArrangement = Arrangement.spacedBy(LANE_GAP),
        horizontalAlignment = align,
    ) {
        repeat(2) { set ->
            lane.forEachIndexed { index, act ->
                Column(
                    modifier = if (set == 0 && index == 0) {
                        Modifier.onSizeChanged { actHeightPx = it.height }
                    } else {
                        Modifier
                    },
                    horizontalAlignment = align,
                ) {
                    Text(
                        act.name,
                        style = AppTheme.type.displaySub,
                        color = colors.ink.copy(alpha = 0.82f),
                        maxLines = 1,
                        textAlign = if (align == Alignment.Start) TextAlign.Start else TextAlign.End,
                    )
                    Text(act.genre, style = AppTheme.type.monoSmall, color = colors.brand.copy(alpha = 0.6f))
                }
            }
        }
    }
}
