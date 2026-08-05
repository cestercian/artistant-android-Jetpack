package `in`.artistant.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import `in`.artistant.app.designsystem.theme.Motion
import `in`.artistant.app.designsystem.theme.MotionSpecs

/**
 * The navigation graph's motion — one spatial model, applied once at the
 * [androidx.navigation.compose.NavHost] level rather than per destination.
 *
 * Two transitions, picked by what the navigation actually means:
 *
 * - **Stack push / pop → horizontal slide + fade.** Forward moves the incoming
 *   screen in from the trailing edge and pushes the outgoing one towards the
 *   leading edge; back plays the exact inverse. The pairing is what makes the
 *   back gesture legible — a pop that animated like a push would tell the user
 *   they had gone deeper when they had come back out.
 * - **Tab switch → cross-dissolve, no translation.** The four tabs are peers.
 *   Sliding between them would imply an ordering ("Messages is to the right of
 *   Bookings") that the navigation model does not have, and which breaks the
 *   moment a deep link jumps two tabs at once.
 *
 * Both mirror the reference iOS build's feel, which is the point: same product,
 * same spatial logic. The *timings* underneath are Material 3's — see [Motion].
 *
 * These builders are plain functions, not composables, because Navigation's
 * transition slots are non-composable lambdas and so cannot read a
 * `CompositionLocal`. Reduce-motion is therefore resolved by the caller (which
 * *is* composable) and handed in.
 */

/**
 * True when a navigation is a lateral move between two top-level tabs rather
 * than a push into or out of a stack.
 *
 * Both endpoints must be tab roots. Checking only the destination would misread
 * a pop from a detail screen back to its tab as a tab switch, and cross-dissolve
 * a transition the user experienced as "going back".
 *
 * A null route (a graph node, or an entry mid-teardown) is not a tab, so it
 * falls through to the stack transition — the safer default, since a slide on a
 * genuine tab switch is merely wrong-feeling while a dissolve on a genuine push
 * loses the back-direction cue entirely.
 */
internal fun isTabSwitch(from: String?, to: String?, tabRoutes: Set<String>): Boolean =
    from != null && to != null && from in tabRoutes && to in tabRoutes && from != to

/** Enter transition for a forward navigation. */
internal fun navEnter(
    motion: Motion,
    reduceMotion: Boolean,
    tabRoutes: Set<String>,
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (reduceMotion) {
        // Not a zero-duration slide: with no motion at all there is nothing to
        // interpolate, and `None` lets Navigation skip the animation machinery
        // outright rather than run a one-frame no-op.
        EnterTransition.None
    } else if (isTabSwitch(initialState.destination.route, targetState.destination.route, tabRoutes)) {
        fadeIn(tween(motion.tabSwitch, easing = motion.standard))
    } else {
        // Incoming screen arrives from the trailing edge, decelerating as it
        // settles — the M3 "enter" half of an emphasized pair.
        slideInHorizontally(
            animationSpec = tween(motion.stackPush, easing = motion.emphasizedDecelerate),
            initialOffsetX = { width ->
                MotionSpecs.slideOffsetPx(width, motion.slideFraction, towardsStart = false)
            },
        ) + fadeIn(tween(motion.stackPush, easing = motion.standard))
    }
}

/** Exit transition for a forward navigation. */
internal fun navExit(
    motion: Motion,
    reduceMotion: Boolean,
    tabRoutes: Set<String>,
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (reduceMotion) {
        ExitTransition.None
    } else if (isTabSwitch(initialState.destination.route, targetState.destination.route, tabRoutes)) {
        fadeOut(tween(motion.tabSwitch, easing = motion.standard))
    } else {
        // Outgoing screen recedes towards the leading edge — it does not travel
        // the full width, because it is being covered rather than dismissed.
        slideOutHorizontally(
            animationSpec = tween(motion.stackPush, easing = motion.emphasizedAccelerate),
            targetOffsetX = { width ->
                MotionSpecs.slideOffsetPx(width, motion.slideFraction, towardsStart = true)
            },
        ) + fadeOut(tween(motion.stackPush, easing = motion.standard))
    }
}

/** Enter transition when popping — the screen being uncovered comes back in. */
internal fun navPopEnter(
    motion: Motion,
    reduceMotion: Boolean,
    tabRoutes: Set<String>,
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (reduceMotion) {
        EnterTransition.None
    } else if (isTabSwitch(initialState.destination.route, targetState.destination.route, tabRoutes)) {
        fadeIn(tween(motion.tabSwitch, easing = motion.standard))
    } else {
        // Mirror of [navExit]: the screen returns from where it receded to.
        slideInHorizontally(
            animationSpec = tween(motion.stackPush, easing = motion.emphasizedDecelerate),
            initialOffsetX = { width ->
                MotionSpecs.slideOffsetPx(width, motion.slideFraction, towardsStart = true)
            },
        ) + fadeIn(tween(motion.stackPush, easing = motion.standard))
    }
}

/** Exit transition when popping — the top screen leaves towards the trailing edge. */
internal fun navPopExit(
    motion: Motion,
    reduceMotion: Boolean,
    tabRoutes: Set<String>,
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (reduceMotion) {
        ExitTransition.None
    } else if (isTabSwitch(initialState.destination.route, targetState.destination.route, tabRoutes)) {
        fadeOut(tween(motion.tabSwitch, easing = motion.standard))
    } else {
        // Mirror of [navEnter]. This is the half the user drives with a back
        // gesture, so it accelerates away rather than easing out.
        slideOutHorizontally(
            animationSpec = tween(motion.stackPush, easing = motion.emphasizedAccelerate),
            targetOffsetX = { width ->
                MotionSpecs.slideOffsetPx(width, motion.slideFraction, towardsStart = false)
            },
        ) + fadeOut(tween(motion.stackPush, easing = motion.standard))
    }
}
