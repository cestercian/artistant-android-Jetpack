package `in`.artistant.app.platform.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Registers the notification channels [ArtistantMessagingService] posts to. Called once from
 * [in.artistant.app.ArtistantApplication.onCreate]; creating an existing channel is a no-op,
 * so re-running on every launch is safe. Channels are split by category (mirroring the push
 * `artistant_event` families) so the user can mute one class of push without losing the others.
 * [pushChannelFor] is the mapping from an arriving payload to one of these ids.
 *
 * **Each channel has a silent twin**, and it exists because of quiet hours (design 124). On API
 * 26+ sound and vibration are properties of the CHANNEL, not of the notification — a builder
 * cannot quiet a notification whose channel is set to make noise, and the platform deliberately
 * ignores attempts to. So "post it, but quietly" has to mean "post it to a quiet channel", which
 * is what [quietTwinOf] returns. The twins are `IMPORTANCE_LOW`: they appear in the shade and in
 * the morning, and they do not wake anybody up.
 *
 * The user can still retune all six in system settings, which is the point of splitting them.
 *
 * No Firebase dependency involved (channels are a plain platform API; minSdk 26 means
 * [NotificationChannel] is always available).
 */
object NotificationChannels {

    /** Chat `message` pushes. */
    const val MESSAGES = "messages"
    /** Booking lifecycle pushes (confirmed / 24h reminder / review request). */
    const val BOOKINGS = "bookings"
    /** Artist-side `gig_request` inbox pushes. */
    const val GIGS = "gigs"

    /** The quiet-hours twins — same families, no sound and no vibration. */
    const val MESSAGES_QUIET = "messages_quiet"
    const val BOOKINGS_QUIET = "bookings_quiet"
    const val GIGS_QUIET = "gigs_quiet"

    /**
     * The silent channel for a loud one.
     *
     * Falls back to [BOOKINGS_QUIET] for an id it does not know, on the same reasoning
     * [pushChannelFor] uses for an unknown event: a notification on the wrong channel is still
     * delivered, and one on a channel that was never registered is not delivered at all.
     */
    fun quietTwinOf(channelId: String): String = when (channelId) {
        MESSAGES -> MESSAGES_QUIET
        GIGS -> GIGS_QUIET
        else -> BOOKINGS_QUIET
    }

    fun register(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(BOOKINGS, "Bookings", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(GIGS, "Gig requests", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(quiet(MESSAGES_QUIET, "Messages (quiet hours)"))
        manager.createNotificationChannel(quiet(BOOKINGS_QUIET, "Bookings (quiet hours)"))
        manager.createNotificationChannel(quiet(GIGS_QUIET, "Gig requests (quiet hours)"))
    }

    /**
     * `IMPORTANCE_LOW` already means no sound; the two explicit calls say so anyway, because a
     * channel's audible behaviour is the whole reason this twin exists and a reader should not
     * have to know the importance table to see it.
     */
    private fun quiet(id: String, name: String) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        }
}
