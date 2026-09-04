package `in`.artistant.app.platform.preferences

import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two controls on design screen 129 that Android lets the APP decide.
 *
 * The screen draws six rows. Four of them are not settings on this platform and the screen
 * renders them as stated facts instead of switches — text size, reduce motion, bold text and
 * higher contrast are all system-owned here, read out of the platform and already honoured
 * (`LocalReduceMotion` is driven by `Settings.Global.ANIMATOR_DURATION_SCALE`, and the type
 * ramp is in `sp` so the system font scale reaches every screen). A switch that pretends to
 * own a system setting is the "privacy control that does nothing" `PrivacyScreen` refuses to
 * ship, so those rows link to the system settings that really own them.
 *
 * That leaves the two below, both genuinely app-side.
 */
@Singleton
class AccessibilityPreferences @Inject constructor(
    private val prefs: KeyValueStore,
) {
    /**
     * "Always show labels" — draw the tab bar's glyph names under the glyphs.
     *
     * Read by both role scaffolds and passed to `LightTabBar`. Default OFF because the design
     * draws an unlabelled bar (REDESIGN_2026-09 §2: "four unlabelled glyphs"); this is the
     * accessibility opt-in out of it, not a second opinion about the default.
     */
    val alwaysShowLabels: Flow<Boolean> = prefs.bool(KEY_ALWAYS_SHOW_LABELS, default = false)

    /**
     * "Autoplay artist videos".
     *
     * Default OFF, which is also what the app does today: nothing in this build autoplays —
     * `SamplePlayer` starts only on a tap. So this preference does not change current
     * behaviour, and the screen says exactly that rather than implying it switched something
     * off. It is the seam the artist profile's gallery and sample rows read when an
     * autoplaying surface lands (`feature/artist`, another package this redesign wave) — the
     * same arrangement `PrivacyPreferences.readReceipts` has with `feature/messages`.
     */
    val autoplayVideos: Flow<Boolean> = prefs.bool(KEY_AUTOPLAY, default = false)

    val all: Flow<AccessibilitySettings> =
        combine(alwaysShowLabels, autoplayVideos) { labels, autoplay ->
            AccessibilitySettings(alwaysShowLabels = labels, autoplayVideos = autoplay)
        }

    suspend fun setAlwaysShowLabels(enabled: Boolean) =
        prefs.setString(KEY_ALWAYS_SHOW_LABELS, enabled.toString())

    suspend fun setAutoplayVideos(enabled: Boolean) =
        prefs.setString(KEY_AUTOPLAY, enabled.toString())

    companion object {
        const val KEY_ALWAYS_SHOW_LABELS = "a11y.always_show_labels"

        /** The key an autoplaying media surface reads before starting anything on its own. */
        const val KEY_AUTOPLAY = "a11y.autoplay_videos"
    }
}

/** A snapshot of both, as screen 129 renders them. */
data class AccessibilitySettings(
    val alwaysShowLabels: Boolean = false,
    val autoplayVideos: Boolean = false,
)
