package `in`.artistant.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import `in`.artistant.app.designsystem.theme.applyLightSystemBars
import `in`.artistant.app.navigation.ArtistantNavHost
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.push.PushService
import javax.inject.Inject

/** Single-activity host: sets the Compose tree (theme is applied inside NavHost). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Field-injected so the OAuth deep-link callback can complete the session. Injected
    // rather than obtained via the ViewModel because the intent arrives at the Activity.
    @Inject lateinit var session: SessionManager
    @Inject lateinit var pushService: PushService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge with DARK bar icons and a light window, regardless of the
        // device's night mode — the app is light-only. See [applyLightSystemBars];
        // the launch theme stays dark on purpose (screen 01, "the one dark room").
        applyLightSystemBars()
        // A cold launch from the OAuth redirect carries the callback URL on the launch intent.
        session.handleDeepLink(intent)
        handlePushIntent(intent)
        setContent { ArtistantNavHost() }
    }

    // A warm return from the external browser (Apple/Google OAuth) delivers the callback here.
    // Notification taps also land here under singleTop.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        session.handleDeepLink(intent)
        handlePushIntent(intent)
    }

    /** Extract FCM data extras from a notification-tap Intent into TabRouter channels. */
    private fun handlePushIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        if (!extras.containsKey("artistant_event")) return
        val data = buildMap {
            for (key in extras.keySet()) {
                extras.getString(key)?.let { put(key, it) }
            }
        }
        if (data.isNotEmpty()) pushService.handleNotificationPayload(data)
    }
}
