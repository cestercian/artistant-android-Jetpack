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
import `in`.artistant.app.feature.system.ActivityEntry
import `in`.artistant.app.feature.system.ActivityLog
import `in`.artistant.app.platform.permissions.isNotificationPermissionGranted
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * FCM entry: token rotation + arriving pushes.
 * Delivery still requires operator `google-services.json` + send-push FCM path.
 */
@AndroidEntryPoint
class ArtistantMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushService: PushService

    /** Design 123 — the device's own record of what arrived. */
    @Inject lateinit var activityLog: ActivityLog

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
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return
        val plan = pushNotificationPlan(data) ?: return

        // Recorded on RECEIPT, before the permission check and before anything
        // can go wrong with posting — which is the whole point of design screen
        // 123. A notification that was never shown (permission revoked) or never
        // tapped is exactly the one the user comes to Activity looking for, so
        // the log is written from here rather than from the tap.
        logActivity(plan, data)

        // Without the runtime grant (API 33+) `notify` is a silent no-op; checking says
        // so in the log instead of leaving a dropped notification looking like a
        // delivery failure.
        if (!isNotificationPermissionGranted(this)) {
            Timber.i("Push arrived but POST_NOTIFICATIONS isn't granted — nothing shown")
            return
        }
        NotificationManagerCompat.from(this).notify(plan.notificationId, build(plan, data))
    }

    /**
     * Append this push to the device's activity log — **synchronously**.
     *
     * It used to be a `launch` on an IO scope owned by the service, which is a
     * write with no owner: `onMessageReceived` returns immediately, and for a
     * background push the process exists only for the length of this callback.
     * The system is free to kill it the moment the callback returns, so the
     * DataStore edit raced the process teardown and lost it often enough to
     * matter — and the row that goes missing is exactly the background arrival
     * screen 123 exists to keep.
     *
     * `runBlocking` is safe here and is the option the platform intends:
     * `onMessageReceived` already runs on one of FCM's own worker threads, never
     * the main thread, and the callback is allowed roughly 10–20 seconds of work
     * before the OS considers the service stuck. A DataStore edit of a single
     * capped string is milliseconds. [ACTIVITY_WRITE_TIMEOUT_MS] is a fuse, not
     * a schedule: if the write ever did block — a contended lock, a disk stall —
     * the notification still gets posted rather than the whole delivery being
     * held hostage to bookkeeping. A WorkManager one-shot would also be durable
     * but costs a scheduled wake-up and a second serialization of the payload to
     * record something already in hand.
     *
     * [ActivityLog.record] swallows its own failures — a log that cannot be
     * written must never cost the user the notification.
     *
     * The id combines the plan's collapse key with the arrival time, so a second
     * message in the same conversation REPLACES the notification (that is the
     * plan's job) while still adding its own row here (that is this one's).
     */
    private fun logActivity(plan: PushNotificationPlan, data: Map<String, String>) {
        val receivedAt = System.currentTimeMillis()
        val entry = ActivityEntry(
            id = "${plan.notificationId}:$receivedAt",
            event = data["artistant_event"]?.trim()?.takeIf { it.isNotEmpty() },
            // Titled the way the notification is titled, so the row and the
            // banner say the same thing.
            title = plan.title ?: getString(R.string.app_name),
            body = plan.body,
            receivedAtMs = receivedAt,
            bookingId = data["artistant_booking_id"]?.trim()?.takeIf { it.isNotEmpty() },
            threadId = data["artistant_thread_id"]?.trim()?.takeIf { it.isNotEmpty() },
            requestId = data["artistant_request_id"]?.trim()?.takeIf { it.isNotEmpty() },
        )
        val wrote = runBlocking {
            withTimeoutOrNull(ACTIVITY_WRITE_TIMEOUT_MS) { activityLog.record(entry) }
        }
        if (wrote == null) Timber.w("Activity log write timed out; the push is still shown")
    }

    private fun build(plan: PushNotificationPlan, data: Map<String, String>): Notification {
        val builder = NotificationCompat.Builder(this, plan.channelId)
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

        /** See [logActivity] — a fuse on the receive callback's budget, not a schedule. */
        const val ACTIVITY_WRITE_TIMEOUT_MS = 5_000L
    }
}
