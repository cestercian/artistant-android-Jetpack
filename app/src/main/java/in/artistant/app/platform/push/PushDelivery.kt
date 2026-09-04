package `in`.artistant.app.platform.push

import `in`.artistant.app.platform.preferences.NotificationSettings
import `in`.artistant.app.platform.preferences.NotificationToggle

/**
 * What an arriving push may do to the phone, once the user's own switches (design 124) are
 * applied to it.
 *
 * The pure half of [ArtistantMessagingService.onMessageReceived], for the same reason
 * [pushNotificationPlan] is pure: the interesting part is a decision table over category ×
 * preference × clock, and none of it needs Firebase, a Context or a NotificationManager to be
 * checked.
 */
enum class PushDelivery {
    /** Post it as the channel is configured — sound, vibration, heads-up. */
    Post,

    /** Post it, but on the silent twin of its channel: it appears in the shade and stays quiet. */
    Silent,

    /** Do not post at all. The user turned this category off. */
    Drop,
}

/**
 * Which switch on screen 124 owns an `artistant_event`, or null when no switch does.
 *
 * Null is the important half. Three of the eight switches govern something this backend does
 * not send — "Load-in reminder" (there is no three-hours-before trigger; mig 0016 has a 24-hour
 * one and nothing else) and the two marketing rows, which have no `send-push` event at all — and
 * a future server event will arrive here before anybody adds a row for it. A payload no switch
 * names is **never dropped**: dropping is the destructive answer, and silently swallowing an
 * event the user never chose to mute is exactly the bug this function exists to prevent the
 * other way round.
 *
 * The mapping is the screen's own words. "Quotes and replies — a request, a counter, or a reply
 * in a thread" covers `message`, `gig_request` and `booking_request`, all three of which are
 * somebody asking for something. "Booking confirmed or declined" is the confirmation pair.
 * `booking_reminder_24h` is the evening-before nudge the "Show-day reminder" row describes, and
 * `booking_review_request` is "Review reminders · once, 24h after a show".
 */
fun pushCategoryFor(event: String?): NotificationToggle? = when (event?.trim()) {
    "message", "gig_request", "booking_request" -> NotificationToggle.QuotesAndReplies
    "booking_confirmed_client", "booking_confirmed_artist" -> NotificationToggle.BookingUpdates
    "booking_reminder_24h" -> NotificationToggle.ShowDayReminder
    "booking_review_request" -> NotificationToggle.ReviewReminders
    else -> null
}

/**
 * 10pm–8am, the window screen 124 draws. Inclusive of 22:00, exclusive of 08:00.
 *
 * Local wall-clock hour, deliberately: quiet hours are about the phone's owner being asleep, and
 * the only clock that knows that is the device's.
 */
fun isQuietHour(hour: Int): Boolean = hour >= QUIET_FROM_HOUR || hour < QUIET_UNTIL_HOUR

/**
 * The whole decision: category × [settings] × wall-clock [hour] → post, post silently, or drop.
 *
 * Three rules, in order, and the order is the product:
 *
 *  1. **A switch that is off drops the notification.** Not "posts it quietly" — off means the
 *     user does not want to be told, and a silent row in the shade is still being told.
 *  2. **Quiet hours never drop anything.** They change how a notification arrives, not whether
 *     it exists: the payload still becomes a row in the shade, on the silent twin of its
 *     channel, so it is waiting in the morning rather than lost.
 *  3. **Booking confirmations are the stated exception**, because the screen states it — "Urgent
 *     booking changes still come through". Somebody answering a request at 11pm is the one push
 *     worth a sound, and it is the one the design's own subtitle promises will make one.
 *
 * Note what this function is NOT: it is not a claim about the server. The three push triggers
 * (mig 0016) fan out unconditionally and there is no preference table to tell them otherwise —
 * this decides what THIS device raises when the payload lands, which is the boundary
 * `NotificationPreferences` documents and the screen prints in its footer.
 */
fun pushDeliveryFor(
    event: String?,
    settings: NotificationSettings,
    hour: Int,
): PushDelivery {
    val category = pushCategoryFor(event)
    if (category != null && !settings[category]) return PushDelivery.Drop
    if (!settings.quietHours || !isQuietHour(hour)) return PushDelivery.Post
    // The exception, and only this one. An unmapped event does NOT get it: we do not know what
    // it is, and "unknown" is not "urgent".
    if (category == NotificationToggle.BookingUpdates) return PushDelivery.Post
    return PushDelivery.Silent
}

/** 10:00 pm, as the screen writes it. */
const val QUIET_FROM_HOUR = 22

/** 8:00 am. */
const val QUIET_UNTIL_HOUR = 8
