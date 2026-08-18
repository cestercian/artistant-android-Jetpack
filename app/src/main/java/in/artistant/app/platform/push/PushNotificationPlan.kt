package `in`.artistant.app.platform.push

/**
 * What an arriving push should SHOW — the pure half of posting a notification, so the
 * mapping is testable without Firebase or a Context.
 *
 * `send-push` sends Android a DATA-ONLY message (`{artistant_event, title, body,
 * artistant_thread_id|booking_id|request_id}` — see the shared backend's
 * `sendOneFCMPush`), which has two consequences the app has to live with. FCM posts
 * nothing on our behalf, so if we don't build a notification the user never learns
 * anything arrived; and `onMessageReceived` fires on RECEIPT, foreground and
 * background alike, which is why receipt may only ever *show* — routing belongs to the
 * tap ([PushService.handleNotificationPayload] from `MainActivity`).
 */
data class PushNotificationPlan(
    val channelId: String,
    val notificationId: Int,
    /** Server copy (`formatPush`), or null when it sent none — the caller titles it. */
    val title: String?,
    val body: String,
)

/**
 * Plan a notification for a `send-push` data payload, or null when there is nothing
 * honest to show.
 *
 * Null on a payload carrying no `artistant_event`: that is exactly what
 * `PushPayloadRouter` maps to `Ignore`, so a notification for it would promise
 * somewhere to go and then open the app on whatever tab it was already showing.
 *
 * The id is derived from the notification's TARGET, so a second message in the same
 * conversation REPLACES the first instead of stacking a column of near-identical rows
 * on the lock screen, while a booking push about that same evening keeps its own row —
 * the channel is part of the key, and the two are different families. Payloads with no
 * id at all (`booking_confirmed_artist`, which routes to a tab) collapse per event,
 * which is the same rule: one row per thing to look at.
 */
fun pushNotificationPlan(data: Map<String, String>): PushNotificationPlan? {
    val event = data.pushValue("artistant_event") ?: return null
    val channelId = pushChannelFor(event)
    val target = data.pushValue("artistant_thread_id")
        ?: data.pushValue("artistant_booking_id")
        ?: data.pushValue("artistant_request_id")
        ?: event
    return PushNotificationPlan(
        channelId = channelId,
        notificationId = "$channelId:$target".hashCode(),
        title = data.pushValue("title"),
        body = data.pushValue("body").orEmpty(),
    )
}

/**
 * Which channel a push belongs to, by `artistant_event` family — the split
 * [NotificationChannels] declares so muting chat doesn't also mute a gig reminder.
 * Unknown events fall to [NotificationChannels.BOOKINGS]: a future server event is far
 * likelier to be booking lifecycle than chat, and a wrong-channel notification is
 * still delivered where a missing channel id is not delivered at all.
 */
fun pushChannelFor(event: String?): String = when (event) {
    "message" -> NotificationChannels.MESSAGES
    "gig_request" -> NotificationChannels.GIGS
    else -> NotificationChannels.BOOKINGS
}

/** FCM data values are strings, and a blank one carries no more meaning than a missing one. */
private fun Map<String, String>.pushValue(key: String): String? =
    this[key]?.trim()?.takeIf { it.isNotEmpty() }
