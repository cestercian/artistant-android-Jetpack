package `in`.artistant.app.designsystem.theme

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * The one place the app talks to the window, and the one place outside the token
 * files that spells a colour as an `Int` — because that is the only currency the
 * platform window APIs take.
 *
 * Two jobs:
 *
 *  1. **Edge-to-edge with LIGHT bars.** The default `enableEdgeToEdge()` picks
 *     bar-icon polarity from the system's night mode, which is exactly wrong for
 *     this app: it is light-only regardless of what the device is set to, so a
 *     phone in dark mode drew white status-bar glyphs onto a `#fafaf6` page —
 *     invisible. `SystemBarStyle.light` fixes the polarity to dark icons.
 *
 *     Both scrims are TRANSPARENT so Discover's hero and the artist cover can
 *     still run under the status bar. The one exception is the navigation bar
 *     below API 27, where the platform cannot draw dark nav icons at all: there
 *     a translucent black is the only thing keeping the gesture pill visible
 *     over a near-white page, and `SystemBarStyle.light`'s second argument is
 *     the hook for precisely that case.
 *
 *  2. **A light window background.** The launch theme is deliberately dark —
 *     screen 01 is "the one dark room", the house lights going down — so the
 *     pre-Compose window must stay `darkest`. Once the Compose tree exists,
 *     every subsequent frame is daylight, and the window behind it has to match
 *     or a rotation/resize flashes black through the gaps.
 */
fun ComponentActivity.applyLightSystemBars() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(
            scrim = AndroidColor.TRANSPARENT,
            darkScrim = AndroidColor.TRANSPARENT,
        ),
        navigationBarStyle = SystemBarStyle.light(
            scrim = AndroidColor.TRANSPARENT,
            darkScrim = LEGACY_NAV_SCRIM,
        ),
    )
    window.setBackgroundDrawable(ColorDrawable(PAGE_ARGB))
}

/** `AppColors.page` (#fafaf6) as a platform colour int. */
private const val PAGE_ARGB = 0xFFFAFAF6.toInt()

/**
 * 45% black. Used only on API 26, the one supported level with no light-nav-bar
 * flag — without it the white nav glyphs land on a white page.
 */
private const val LEGACY_NAV_SCRIM = 0x73000000
