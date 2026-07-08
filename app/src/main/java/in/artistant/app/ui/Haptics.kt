package `in`.artistant.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Thin haptics layer mirroring iOS's three UIFeedbackGenerator intents so call
 * sites read the same on both platforms:
 *   - selection() → light tick on a discrete pick (selectable chips, toggles) = iOS .selection
 *   - success()   → confirmation buzz (match confirmed, publish done, accept) = iOS .success
 *   - error()     → rejection buzz (failed send / failed action)             = iOS .error
 *
 * Built over the platform `View.performHapticFeedback` rather than Compose's
 * `HapticFeedbackType` because this Compose BOM (2024.12) only exposes
 * LongPress / TextHandleMove — the richer CONFIRM/REJECT constants (API 30+)
 * give the success/error a distinct feel. Below API 30 we fall back to
 * LONG_PRESS so it still buzzes. The View route also respects the user's
 * system haptic setting for free.
 */
class Haptics(private val view: View) {
    fun selection() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun success() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    fun error() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS,
        )
    }
}

/** Composable accessor: `val haptics = rememberHaptics()`. Stable per host View. */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
