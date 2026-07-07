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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

private data class Act(val name: String, val genre: String)

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
 * One scrolling column. Renders the list twice and translates by exactly one set's height so the
 * loop is seamless. We drive the offset off the animated fraction × a fixed travel distance
 * (screen-height-ish) rather than measuring, since a decorative background doesn't need pixel-
 * exact seam alignment — the scrim hides the wrap. ponytail: fixed travel over GeometryReader
 * measurement; the seam is under a dark scrim so sub-pixel drift is invisible.
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
    // Approximate one set's pixel height (12 acts × ~52dp each). Good enough for a seamless-
    // looking decorative loop; exact measurement isn't worth a GeometryReader here.
    val travel = with(androidx.compose.ui.platform.LocalDensity.current) { (acts.size * 52).dp.toPx() }

    val fraction by if (animated) {
        rememberInfiniteTransition(label = "lineup").animateFloat(
            initialValue = if (up) 0f else 1f,
            targetValue = if (up) 1f else 0f,
            animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
            label = "lineupOffset",
        )
    } else {
        // Static mid-set frame when motion is off.
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -fraction * travel },
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = align,
    ) {
        repeat(2) {
            lane.forEach { act ->
                Column(horizontalAlignment = align) {
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
