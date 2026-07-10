package `in`.artistant.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import `in`.artistant.app.navigation.ArtistantNavHost
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.storage.AppPreferences
import `in`.artistant.app.state.DeepLinkRouter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Single-activity host: sets the Compose tree (theme is applied inside NavHost). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Field-injected so the OAuth deep-link callback can complete the session. Injected
    // rather than obtained via the ViewModel because the intent arrives at the Activity.
    @Inject lateinit var session: SessionManager

    // Push deep-link producer: a notification-launch intent carries the `artistant_*` extras;
    // routing them into the router parks the tab + id the tapped notification points at.
    @Inject lateinit var deepLinkRouter: DeepLinkRouter
    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A cold launch from the OAuth redirect carries the callback URL on the launch intent.
        session.handleDeepLink(intent)
        // A cold launch from a notification tap carries the push extras on the same intent.
        routePush(intent)
        setContent { ArtistantNavHost() }
    }

    // A warm return from the external browser (Apple/Google OAuth) delivers the callback here;
    // a warm notification tap (app already running) delivers its push extras here too.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        session.handleDeepLink(intent)
        routePush(intent)
    }

    /**
     * Resolve the persisted role (needed to route side-specific events like `message`), then hand
     * the intent extras to the router. Async because the role is a DataStore Flow — reading its
     * current value is a suspend `first()`. A no-op when the intent carries no `artistant_event`
     * (every non-push launch), so this stays inert until P1/P2b send real pushes.
     */
    private fun routePush(intent: Intent?) {
        val extras = intent?.extras ?: return
        if (extras.getString(DeepLinkRouter.KEY_EVENT) == null) return
        lifecycleScope.launch {
            val role = appPreferences.role.first()
            deepLinkRouter.routeFromExtras(extras, role)
        }
    }
}
