package `in`.artistant.app.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion tokens — the timing half of the design system.
 *
 * Two sources feed this file and it is worth being explicit about which is which,
 * because they disagree in places and the resolution is not arbitrary:
 *
 * - **Durations and easing curves are Material 3's**, verbatim (m3.material.io
 *   "Motion → Easing and duration"). M3's curves are the best-documented, most
 *   thoroughly user-tested motion mechanics available on this platform, and they
 *   are what an Android user's muscle memory is already calibrated to. There is
 *   no house style for *timing*, so there is nothing to override.
 * - **Which motion happens, and how far things travel**, follows the reference
 *   iOS build. A stack push slides horizontally; switching tabs cross-dissolves
 *   in place. That is a product decision about spatial model — "forward is to the
 *   right, peers are in the same place" — and it is identical on both platforms
 *   by design, so the two apps feel like one product.
 *
 * Nothing here decorates. Motion in this app exists to explain where a screen
 * came from and to confirm a touch landed; if an animation does neither it does
 * not belong.
 *
 * The pure decision logic lives in [MotionSpecs] rather than in the composables,
 * so the durations, the reduce-motion resolution and the press-scale ramp are all
 * unit-testable without a device or a Compose test rule.
 */
data class Motion(
    /** M3 easing set. Named for the spec so a reader can look them up. */
    val emphasized: Easing = EMPHASIZED,
    val emphasizedDecelerate: Easing = EMPHASIZED_DECELERATE,
    val emphasizedAccelerate: Easing = EMPHASIZED_ACCELERATE,
    val standard: Easing = STANDARD,

    /**
     * M3's duration scale, in millis. Semantic aliases below pick from these —
     * no surface invents its own number.
     */
    val short2: Int = 100,
    val short4: Int = 200,
    val medium2: Int = 300,
    val medium4: Int = 400,
) {
    // ── Semantic aliases ────────────────────────────────────────────────────
    // Every animated surface names its intent, not a millisecond count, so a
    // later timing change lands in exactly one place.

    /**
     * Forward/back navigation between stacked screens. M3 `medium2` (300ms):
     * comfortably under the reference platform's ~350ms stack push, which is the
     * ceiling this brief set — past roughly a third of a second a push stops
     * reading as "the screen moved" and starts reading as "the app is thinking".
     */
    val stackPush: Int get() = medium2

    /**
     * Switching between peer tabs. Deliberately quicker than [stackPush] and a
     * cross-dissolve rather than a slide: tabs are not stacked, so implying
     * direction between them would be a lie about the navigation model.
     */
    val tabSwitch: Int get() = short4

    /** The tab bar's selection capsule travelling to a new tab. */
    val indicator: Int get() = short4

    /** Touch-down / touch-up feedback. Must feel instantaneous, so: shortest. */
    val press: Int get() = short2

    /** Skeleton → real content, and other "the data arrived" swaps. */
    val contentReveal: Int get() = medium2

    /** One full sweep of the skeleton shimmer. */
    val shimmer: Int = 1_100

    // ── Travel distances / scales ───────────────────────────────────────────

    /**
     * How far a screen slides in or out, as a fraction of its width.
     *
     * A *partial* slide on purpose. A full-width slide (1.0) makes the outgoing
     * screen race the incoming one across the whole viewport, which at 300ms
     * reads as a jolt; a third of the width is enough to establish direction
     * while the cross-fade carries the rest of the transition.
     */
    val slideFraction: Float = 0.33f

    /**
     * Scale a control settles to while held. 0.98 — the same ratio the reference
     * build uses. Small enough to read as "pressed" rather than as the button
     * shrinking.
     */
    val pressScale: Float = 0.98f
}

// M3 easing curves (m3.material.io/styles/motion/easing-and-duration/tokens-specs).
private val EMPHASIZED = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
private val EMPHASIZED_DECELERATE = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EMPHASIZED_ACCELERATE = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
private val STANDARD = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

/**
 * Whether the user has asked the system to stop animating.
 *
 * Defaults to `false` so previews, `@Composable` tests and any tree that forgot
 * to provide it still animate — the failure mode of a missing provider should be
 * "normal app", not "silently frozen".
 */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

val LocalMotion: ProvidableCompositionLocal<Motion> = staticCompositionLocalOf { Motion() }

/**
 * Pure motion decisions, extracted from the composables so they can be tested.
 *
 * Everything here is plain Kotlin over Int/Float/Boolean/String — no Compose and
 * no Android types — which is what lets the unit suite cover it with nothing but
 * JUnit on the classpath.
 */
object MotionSpecs {

    /**
     * Resolve the system's "remove animations" preference.
     *
     * Android has no single reduce-motion flag the way the reference platform
     * does. The Accessibility → "Remove animations" toggle zeroes all three
     * global animation scales at once, while Developer Options can zero them
     * individually. Treating *any* zero as reduce-motion is the inclusive read:
     * a user who has turned off transition animations has expressed the same
     * intent as one who used the accessibility switch, and honouring it costs us
     * nothing.
     *
     * @param animator `Settings.Global.ANIMATOR_DURATION_SCALE`
     * @param transition `Settings.Global.TRANSITION_ANIMATION_SCALE`
     * @param window `Settings.Global.WINDOW_ANIMATION_SCALE`
     */
    fun isReduceMotion(animator: Float, transition: Float = 1f, window: Float = 1f): Boolean =
        animator == 0f || transition == 0f || window == 0f

    /**
     * A duration, honouring reduce-motion.
     *
     * Under reduce-motion this returns 0 rather than some smaller number: the
     * setting means "do not animate", not "animate faster". A zero-duration
     * `tween` in Compose lands on the target value on the next frame, which is
     * exactly the instant swap we want — and it keeps the animation *value*
     * plumbing intact, so nothing downstream has to branch.
     */
    fun durationMillis(base: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else base

    /**
     * Target scale for a pressable control.
     *
     * Under reduce-motion the control stays at rest. A scale *animation* is the
     * motion the setting is about, and dropping it instantly to 0.98 instead
     * would be a flicker — worse than no feedback. The ripple/state layer still
     * fires, so the touch is still acknowledged, just not kinetically.
     */
    fun pressScale(pressed: Boolean, reduceMotion: Boolean, pressedScale: Float): Float =
        if (pressed && !reduceMotion) pressedScale else 1f

    /**
     * Whether the shimmer loop should run at all.
     *
     * Gated explicitly rather than left to the duration scale. An infinite
     * animation given a zero duration does not stop — it completes and restarts
     * every frame, which is a busy loop that also strobes. Loading placeholders
     * are exactly the surface where a user sensitive to motion is stuck looking
     * at the screen, so this one gets to be a hard off switch.
     */
    fun shouldShimmer(reduceMotion: Boolean): Boolean = !reduceMotion

    /**
     * Horizontal offset, in pixels, for a screen sliding in or out.
     *
     * @param width the screen's full width in px
     * @param fraction share of the width to travel ([Motion.slideFraction])
     * @param towardsStart true when the content moves left (a forward push's
     *   outgoing screen, or a back gesture's incoming one)
     */
    fun slideOffsetPx(width: Int, fraction: Float, towardsStart: Boolean): Int {
        val magnitude = (width * fraction).toInt()
        return if (towardsStart) -magnitude else magnitude
    }
}

/**
 * Read the system animation scales once per composition tree.
 *
 * `remember`ed on the Context rather than polled: these are Settings.Global
 * values that only change when the user visits system settings, at which point
 * the activity is recreated anyway. Reading them every recomposition would be a
 * `ContentResolver` round-trip on the composition hot path for a value that
 * effectively never moves.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val resolver = context.contentResolver
        fun scale(key: String) = Settings.Global.getFloat(resolver, key, 1f)
        MotionSpecs.isReduceMotion(
            animator = scale(Settings.Global.ANIMATOR_DURATION_SCALE),
            transition = scale(Settings.Global.TRANSITION_ANIMATION_SCALE),
            window = scale(Settings.Global.WINDOW_ANIMATION_SCALE),
        )
    }
}

/**
 * A `tween` that collapses to instant under reduce-motion.
 *
 * The single helper every animated surface in the app should build its spec
 * with — it is the one place the reduce-motion branch lives, so no call site can
 * forget it.
 */
@Composable
fun <T> motionTween(
    durationMillis: Int,
    easing: Easing = AppTheme.motion.standard,
): FiniteAnimationSpec<T> = tween(
    durationMillis = MotionSpecs.durationMillis(durationMillis, LocalReduceMotion.current),
    easing = easing,
)

/** Motion accessor, alongside `AppTheme.colors` / `.dimens`. */
val AppTheme.motion: Motion
    @Composable @ReadOnlyComposable get() = LocalMotion.current

/** Whether motion is currently suppressed — `AppTheme.reduceMotion`. */
val AppTheme.reduceMotion: Boolean
    @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
