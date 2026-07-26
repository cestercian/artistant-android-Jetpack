package `in`.artistant.app.platform.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * FCM entry: token rotation + notification taps / data messages.
 * Delivery still requires operator `google-services.json` + send-push FCM path.
 */
@AndroidEntryPoint
class ArtistantMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushService: PushService

    override fun onNewToken(token: String) {
        Timber.d("FCM onNewToken")
        pushService.handleNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only payloads (and notification+data when app is foreground) land here.
        // Background notification taps are handled via the launcher intent extras in MainActivity.
        if (message.data.isNotEmpty()) {
            pushService.handleNotificationPayload(message.data)
        }
    }
}
