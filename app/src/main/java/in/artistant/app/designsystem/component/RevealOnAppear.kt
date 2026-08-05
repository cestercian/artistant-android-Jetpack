package `in`.artistant.app.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.MotionSpecs
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion

/**
 * Fades and lifts its content in the first time it composes.
 *
 * Wrapped around the loaded branch of a screen's `when (state)`, this turns the
 * spinner → content hand-off from a hard cut into a settle. The difference
 * matters more than it sounds: a full screen of text and photos appearing
 * between two frames reads as a glitch, and users routinely re-read a screen
 * that arrived that way because they cannot tell whether it finished loading.
 *
 * Deliberately one-directional — [ExitTransition.None]. The content is leaving
 * because the data went away or the screen is being replaced, and in both cases
 * something else (a nav transition, an error state) is already animating. A
 * second, competing fade-out there just muddies it.
 *
 * The lift is small and upward: content rises a few dp into place, the direction
 * that reads as "settling", not as "sliding in from somewhere".
 *
 * Under reduce-motion this is a pass-through — [EnterTransition.None] — so the
 * content simply exists, with no fade and no travel.
 */
@Composable
fun RevealOnAppear(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion

    // Seeded false then immediately driven true, which is what makes
    // AnimatedVisibility run its enter transition on FIRST composition. Passing
    // a plain `visible = true` would skip it — there would be no change to
    // animate from.
    val appeared = remember { MutableTransitionState(false) }
    appeared.targetState = true

    val duration = MotionSpecs.durationMillis(motion.contentReveal, reduceMotion)
    AnimatedVisibility(
        visibleState = appeared,
        modifier = modifier,
        enter = if (reduceMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(duration, easing = motion.standard)) +
                slideInVertically(
                    animationSpec = tween(duration, easing = motion.emphasizedDecelerate),
                    // A fraction of the content's own height, so the travel is
                    // proportionate on a short empty state and on a full list
                    // alike — and always small.
                    initialOffsetY = { height -> (height * REVEAL_LIFT_FRACTION).toInt() },
                )
        },
        exit = ExitTransition.None,
    ) {
        content()
    }
}

/**
 * How far content travels on reveal, as a share of its own height. Small on
 * purpose: this is a settle, not an entrance.
 */
private const val REVEAL_LIFT_FRACTION = 0.02f
