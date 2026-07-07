package `in`.artistant.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import `in`.artistant.app.navigation.ArtistantNavHost
import `in`.artistant.app.platform.auth.SessionManager
import javax.inject.Inject

/** Single-activity host: sets the Compose tree (theme is applied inside NavHost). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Field-injected so the OAuth deep-link callback can complete the session. Injected
    // rather than obtained via the ViewModel because the intent arrives at the Activity.
    @Inject lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A cold launch from the OAuth redirect carries the callback URL on the launch intent.
        session.handleDeepLink(intent)
        setContent { ArtistantNavHost() }
    }

    // A warm return from the external browser (Apple/Google OAuth) delivers the callback here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        session.handleDeepLink(intent)
    }
}
