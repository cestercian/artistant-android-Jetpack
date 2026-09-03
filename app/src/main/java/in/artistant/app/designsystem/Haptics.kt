package `in`.artistant.app.designsystem

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The app's haptic vocabulary, ported 1:1 from the reference build's `Haptic`
 * enum so a screen fires the same feel on both platforms.
 *
 * Seven verbs, three families: an outcome ([Success]/[Warning]/[Error]), a
 * discrete selection tick ([Select]), and three impact weights ([Tap]/[Impact]/
 * [Heavy]). Call sites read as intent — `haptics.success()` — rather than as a
 * platform constant, which is the whole reason this exists.
 *
 * Compose's own `HapticFeedbackType` is not usable here: the pinned BOM's
 * version exposes only `LongPress` and `TextHandleMove`, so an outcome and a
 * selection would collapse onto the same two buzzes. We go straight to
 * [View.performHapticFeedback], which reaches the full platform set — and which
 * already honours the system's "touch feedback" setting, so there is no
 * separate mute to respect.
 */
enum class HapticKind { Success, Warning, Error, Select, Tap, Impact, Heavy }

/**
 * The kind → platform-constant mapping, pure and injectable so the version
 * fallbacks are testable on the JVM.
 *
 * The constants this reaches for landed after our minSdk, so each has a floor:
 *
 * - `CONFIRM` / `REJECT` (API 30) are the platform's own outcome pair. Below 30
 *   both fall back to `LONG_PRESS`, the only pre-30 constant with enough body to
 *   read as "something happened" rather than as a keypress.
 * - `SEGMENT_TICK` (API 34) is the tick a picker/segment makes as it passes a
 *   detent — exactly the reference build's selection feel. `CLOCK_TICK` is the
 *   pre-34 stand-in.
 * - `KEYBOARD_TAP` / `CONTEXT_CLICK` / `LONG_PRESS` are all API 3–23, so the
 *   impact weights need no branch.
 *
 * Warning and Error share `REJECT`: the platform ships one negative-outcome
 * constant, and inventing a difference by borrowing an impact weight would make
 * "soft heads-up" and "failed" feel like different *events* rather than
 * different severities. They stay distinct at the call site, which is where the
 * distinction is worth keeping.
 *
 * @param sdkInt the running API level; defaults to this device's.
 */
// `InlinedApi`, not `NewApi`: these are `static final int`s, so kotlinc bakes the
// literal in and nothing is resolved at runtime — there is no field to be missing
// on an old device. Lint flags them anyway because the guard is a PARAMETER, which
// it can't recognise as a version check, and that parameter is exactly what makes
// the fallbacks testable off-device.
@SuppressLint("InlinedApi")
fun hapticConstantFor(kind: HapticKind, sdkInt: Int = Build.VERSION.SDK_INT): Int = when (kind) {
    HapticKind.Success ->
        if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS

    HapticKind.Warning, HapticKind.Error ->
        if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS

    HapticKind.Select ->
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK

    HapticKind.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
    HapticKind.Impact -> HapticFeedbackConstants.CONTEXT_CLICK
    HapticKind.Heavy -> HapticFeedbackConstants.LONG_PRESS
}

/** The seven verbs, as a seam so a screen never touches [View] to buzz. */
interface Haptics {
    /** Confirmation / completion — a write landed. */
    fun success()

    /** Soft "heads up": a destructive-but-recoverable action was taken. */
    fun warning()

    /** Failure / rejected action. */
    fun error()

    /** Discrete selection change — a chip, a star, a segment. */
    fun select()

    /** Light tap — row taps, card taps, send. */
    fun tap()

    /** Medium impact. No call site yet; here so future UI reaches for a weight
     *  rather than re-introducing a raw constant. */
    fun impact()

    /** Heavy impact, same rationale as [impact]. */
    fun heavy()
}

private class ViewHaptics(private val view: View) : Haptics {
    override fun success() = fire(HapticKind.Success)
    override fun warning() = fire(HapticKind.Warning)
    override fun error() = fire(HapticKind.Error)
    override fun select() = fire(HapticKind.Select)
    override fun tap() = fire(HapticKind.Tap)
    override fun impact() = fire(HapticKind.Impact)
    override fun heavy() = fire(HapticKind.Heavy)

    private fun fire(kind: HapticKind) {
        view.performHapticFeedback(hapticConstantFor(kind))
    }
}

/**
 * The composition's haptics, bound to the host [View].
 *
 * Keyed on the view so a recomposition reuses one instance and a genuinely new
 * window (a dialog, a bottom sheet with its own window) gets its own.
 */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { ViewHaptics(view) }
}
