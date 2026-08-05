package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.MotionSpecs
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion

/**
 * The app's one press response: a control settles very slightly smaller while
 * held, and springs back on release.
 *
 * Why this exists as a shared modifier rather than per-component code: press
 * feedback has to feel *identical* everywhere or it reads as inconsistency
 * rather than as a system. Every pressable surface in the app — buttons, package
 * rows, date cards, tab cells, tiles — routes through here, so there is exactly
 * one scale ratio and one duration in the product.
 *
 * Two implementation choices worth knowing about:
 *
 * - **`graphicsLayer {}` with a lambda, not `Modifier.scale(value)`.** The lambda
 *   form reads `scale` during the *draw* phase, so a press re-draws the node and
 *   nothing else. Passing the animated float as a plain argument instead would
 *   invalidate composition on every animation frame, dragging the whole subtree
 *   (a tile's photo, a row's text) through recomposition ~60 times a second for
 *   what is a purely visual effect.
 * - **Scale only — no alpha.** Fading a control on press dims its label at the
 *   exact moment the user is looking at it to confirm what they hit. The state
 *   layer / ripple already carries the tonal half of the feedback.
 *
 * Under reduce-motion the scale is pinned to 1 and the animation never runs; the
 * ripple still fires, so the touch is still acknowledged. See
 * [MotionSpecs.pressScale] for why it is pinned rather than applied instantly.
 *
 * @param interactionSource the SAME source handed to the node's `clickable` /
 *   `selectable` — otherwise this animates on presses that never happened.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = AppTheme.motion.pressScale,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val scale by animateFloatAsState(
        targetValue = MotionSpecs.pressScale(pressed, reduceMotion, pressedScale),
        animationSpec = tween(
            durationMillis = MotionSpecs.durationMillis(motion.press, reduceMotion),
            easing = motion.standard,
        ),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
