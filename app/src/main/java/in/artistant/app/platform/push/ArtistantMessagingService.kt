package `in`.artistant.app.platform.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import `in`.artistant.app.MainActivity
import `in`.artistant.app.R
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.state.DeepLinkRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The FCM RECEIVER + token producer (push phase P2b, issue #39). Firebase delivers here on
 * BOTH a new registration token and every incoming message.
 *
 * WHY WE COMPOSE THE NOTIFICATION OURSELVES: the backend `send-push` sends DATA-ONLY messages
 * (no `notification` block) with `android.priority = high`. That's deliberate — a data-only
 * message routes to [onMessageReceived] in the foreground AND the background (a message with a
 * `notification` block would be auto-shown by the system when backgrounded, bypassing our code
 * and our deep-link `PendingIntent`). So the payload is always `msg.data`, and we build the
 * user-visible notification from it here, wiring the tap → [MainActivity] extras → [DeepLinkRouter]
 * chain the P2a seam already consumes.
 */
@AndroidEntryPoint
class ArtistantMessagingService : FirebaseMessagingService() {

    // The token upsert boundary (reused from P2a — do NOT re-implement the upsert here).
    @Inject lateinit var deviceTokenRepository: DeviceTokenRepository

    // Only to gate token registration on a live session (a token can't be attributed to a user
    // without one). Sign-out cleanup lives in SessionManager, not here.
    @Inject lateinit var sessionManager: SessionManager

    // A service has no lifecycleScope; a long-lived IO scope matches the other platform services
    // (SessionManager). SupervisorJob so one failed upsert can't tear the scope down.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * FCM minted (or rotated) this device's token. Register it only if signed in — an anonymous
     * device has no user to attribute it to; the post-login path (RootViewModel) registers it
     * once a session exists. Best-effort: a failed upsert is retried on the next token/sign-in.
     */
    override fun onNewToken(token: String) {
        if (sessionManager.currentUserId == null) return
        scope.launch { runCatching { deviceTokenRepository.register(token) } }
    }

    /**
     * A push arrived (foreground or background — data-only guarantees this fires either way).
     * The event key is the contract's discriminator; a payload without it isn't ours, so drop it.
     */
    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        val event = d[DeepLinkRouter.KEY_EVENT] ?: return

        // ponytail: no foreground suppression. Suppressing a `message` push while the user is
        // already reading that exact thread would need a live "currently-open thread id" signal,
        // and none exists cleanly today (no such field on MessageStore/DeepLinkRouter). Inventing
        // one is out of scope for P2b — delivery + routing is the requirement. Always post.
        // TODO(P4): suppress a `message` push when the user is viewing that thread, once a
        //           clean current-thread signal exists.

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        // The tap re-launches MainActivity carrying the SAME `artistant_*` extras the P2a producer
        // (routePush → DeepLinkRouter.routeFromExtras) already reads — so a tap parks the tab + id
        // and the tab screens deep-link exactly as a cold notification-launch intent does.
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(DeepLinkRouter.KEY_EVENT, event)
            d[DeepLinkRouter.KEY_THREAD]?.let { putExtra(DeepLinkRouter.KEY_THREAD, it) }
            d[DeepLinkRouter.KEY_REQUEST]?.let { putExtra(DeepLinkRouter.KEY_REQUEST, it) }
            d[DeepLinkRouter.KEY_BOOKING]?.let { putExtra(DeepLinkRouter.KEY_BOOKING, it) }
        }

        // Stable-ish per target so a second push about the SAME thread/booking REPLACES the prior
        // notification (and its PendingIntent extras via UPDATE_CURRENT) rather than stacking a
        // duplicate; distinct targets get distinct ids. Also the PendingIntent request code so two
        // live targets don't share (and clobber) one PendingIntent.
        val targetId = d[DeepLinkRouter.KEY_THREAD]
            ?: d[DeepLinkRouter.KEY_REQUEST]
            ?: d[DeepLinkRouter.KEY_BOOKING]
        val notificationId = (targetId ?: event).hashCode()

        val pending = PendingIntent.getActivity(
            this,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, channelFor(event))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(d["title"] ?: getString(R.string.app_name))
            .setContentText(d["body"].orEmpty())
            .setAutoCancel(true)              // dismiss on tap
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // pre-26 heads-up; 26+ uses the channel
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    companion object {
        /**
         * Pure channel router: map a push `event` family to its [NotificationChannels] id so a
         * user can mute one class of push without the others. Kept pure + tested (unknown events
         * fall back to the bookings channel — the neutral default; they still can't crash a post).
         */
        fun channelFor(event: String): String = when (event) {
            "message" -> NotificationChannels.MESSAGES
            "gig_request" -> NotificationChannels.GIGS
            else -> NotificationChannels.BOOKINGS // booking_confirmed_* / reminder / review + fallback
        }
    }
}
