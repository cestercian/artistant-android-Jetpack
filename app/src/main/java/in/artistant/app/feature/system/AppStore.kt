package `in`.artistant.app.feature.system

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import timber.log.Timber

/**
 * Sending the user to this app's Play listing — the Android end of the design's
 * "App Store" buttons on screens 120 and 138.
 *
 * **There is no in-app review flow here, and that is a decision.** Play's
 * in-app review API lives in `com.google.android.play:review`, which the section
 * may not add (no new Gradle dependencies), and half-implementing it — showing
 * our own five stars and then pretending the tap submitted something — would be
 * a lie about where the rating went. So the stars on screen 138 are a picture of
 * what the store will ask, the button is honest about leaving the app, and the
 * PR lists the library as the follow-up.
 */
object AppStore {

    /**
     * Open the Play listing for this build's own package.
     *
     * `market://` first so an installed Play client handles it directly, with
     * the https listing as the fallback for a device that has no Play client at
     * all (a sideloaded build on an emulator without Play services, which is
     * exactly the device this gets walked on). Both are best-effort: a device
     * with neither is not worth crashing over, and the screen behind the button
     * still says what is wrong.
     */
    fun openListing(context: Context) {
        val packageName = context.packageName
        val market = Intent(Intent.ACTION_VIEW, "$MARKET_SCHEME$packageName".toUri())
            // Launched from a non-Activity context in principle (the gates are
            // composables, but the context they see is whatever the theme
            // provides), so the flag has to be here or the start throws.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(market) }.isSuccess) return

        val web = Intent(Intent.ACTION_VIEW, "$WEB_LISTING$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(web) }
            .onFailure { Timber.w(it, "No handler for the Play listing") }
    }

    /** Open an arbitrary https URL — screen 121's status-page link. */
    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "No handler for %s", url) }
    }

    private const val MARKET_SCHEME = "market://details?id="
    private const val WEB_LISTING = "https://play.google.com/store/apps/details?id="
}
