package `in`.artistant.app.platform.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import `in`.artistant.app.MainActivity
import `in`.artistant.app.R
import `in`.artistant.app.platform.permissions.isNotificationPermissionGranted
import `in`.artistant.app.platform.preferences.NotificationPreferences
import `in`.artistant.app.platform.preferences.NotificationSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.time.LocalTime
import javax.inject.Inject

/**
 * FCM entry: token rotation + arriving pushes.
 * Delivery still requires operator `google-services.json` + send-push FCM path.
 */
@AndroidEntryPoint
class ArtistantMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushService: PushService

    /**
     * Screen 124's eight switches, read here because here is the only place they can do
     * anything. The screen persists them to DataStore and says out loud that they decide what
     * THIS device raises — until this service read them that sentence was false, and three of
     * the rows (booking updates, review reminders, quiet hours) governed nothing at all.
     */
    @Inject lateinit var notificationPrefs: NotificationPreferences

    override fun onNewToken(token: String) {
        Timber.d("FCM onNewToken")
        pushService.handleNewToken(token)
    }

    /**
     * A push ARRIVED. That is not a tap, so nothing here may navigate.
     *
     * `send-push` sends Android a data-only message, which means this fires for every
     * push — foreground and background — and FCM posts nothing itself. Both halves
     * matter. It used to hand the payload straight to
     * [PushService.handleNotificationPayload], which ends in `TabRouter.apply`: a
     * message arriving while a client was mid-way through a booking form flipped them
     * to Messages and opened the thread, and a background arrival latched the same
     * pending id so the next launch landed on a conversation nobody had tapped. And
     * because nothing was ever POSTED, the one thing the user should have got — a
     * notification — never appeared.
     *
     * So receipt only shows. The payload rides along as intent extras, and
     * `MainActivity.handlePushIntent` routes it when (and only when) the notification
     * is tapped — the same seam iOS uses, where `willPresent` presents and
     * `didReceive` routes.
     *
     * **And what it shows is the user's call.** [pushDeliveryFor] applies screen 124's
     * switches to the arriving event: a category the user turned off never becomes a
     * notification, and during quiet hours everything but an urgent booking change goes to
     * the silent twin of its channel.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return
        val plan = pushNotificationPlan(data) ?: return
        // Without the runtime grant (API 33+) `notify` is a silent no-op; checking says
        // so in the log instead of leaving a dropped notification looking like a
        // delivery failure.
        if (!isNotificationPermissionGranted(this)) {
            Timber.i("Push arrived but POST_NOTIFICATIONS isn't granted — nothing shown")
            return
        }
        val event = data["artistant_event"]
        val delivery = pushDeliveryFor(event, currentSettings(), LocalTime.now().hour)
        if (delivery == PushDelivery.Drop) {
            Timber.i("Push '%s' dropped — its category is switched off on this device", event)
            return
        }
        val channelId = when (delivery) {
            PushDelivery.Silent -> NotificationChannels.quietTwinOf(plan.channelId)
            else -> plan.channelId
        }
        NotificationManagerCompat.from(this).notify(plan.notificationId, build(plan, channelId, data))
    }

    /**
     * The switches, read synchronously — `onMessageReceived` is not a suspend function and the
     * notification has to be posted inside the window FCM gives this callback, so there is
     * nowhere to hand the decision off to.
     *
     * A DataStore read that throws (IOException on an unreadable preferences file) falls back to
     * the DEFAULTS rather than to a drop: the defaults are what the screen shows for a user who
     * has never touched it, and losing somebody's booking confirmation because a preferences
     * file was corrupt is a far worse failure than making a sound they had asked us not to.
     */
    private fun currentSettings(): NotificationSettings =
        runCatching { runBlocking { notificationPrefs.all.first() } }
            .getOrElse {
                Timber.w(it, "Couldn't read notification preferences — using defaults")
                NotificationSettings()
            }

    private fun build(
        plan: PushNotificationPlan,
        channelId: String,
        data: Map<String, String>,
    ): Notification {
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            // The launcher mark's lime, from the same resource the adaptive icon uses —
            // this tints the small icon in the expanded row, so it is the one place the
            // app's accent shows outside Compose.
            .setColor(ContextCompat.getColor(this, R.color.brand_lime))
            .setContentTitle(plan.title ?: getString(R.string.app_name))
            .setAutoCancel(true)
            .setContentIntent(tapIntent(plan, data))
        if (plan.body.isNotEmpty()) {
            builder.setContentText(plan.body)
                // A message preview is up to 140 characters and the collapsed row holds
                // one line, so the rest is only readable if the row can expand.
                .setStyle(NotificationCompat.BigTextStyle().bigText(plan.body))
        }
        return builder.build()
    }

    /**
     * The tap: MainActivity (`singleTop`) with the routing keys as extras.
     *
     * Only the `artistant_*` keys travel — the title and body are already on the
     * notification, and `MainActivity.handlePushIntent` treats every string extra as
     * payload. The request code is the notification id, so the extras of a second push
     * about the SAME thread update that notification's PendingIntent (the classic
     * stale-extras trap) while a different thread gets its own.
     */
    private fun tapIntent(plan: PushNotificationPlan, data: Map<String, String>): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            for ((key, value) in data) {
                if (key.startsWith(PAYLOAD_KEY_PREFIX)) putExtra(key, value)
            }
        }
        return PendingIntent.getActivity(
            this,
            plan.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val PAYLOAD_KEY_PREFIX = "artistant_"
    }
}
