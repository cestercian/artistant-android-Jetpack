package `in`.artistant.app.platform.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Registers the notification channels P2b's `NotificationCompat.Builder` will post to. Called once
 * from [in.artistant.app.ArtistantApplication.onCreate]; creating an existing channel is a no-op,
 * so re-running on every launch is safe. Channels are split by category (mirroring the push
 * `artistant_event` families) so the user can mute one class of push without losing the others.
 *
 * SEAM STATUS (issue #38, phase P2a): no notification is POSTED yet — this only declares the
 * channels so the P2b receiver has somewhere to target. No Firebase dependency involved (channels
 * are a plain platform API; minSdk 26 means [NotificationChannel] is always available).
 */
object NotificationChannels {

    /** Chat `message` pushes. */
    const val MESSAGES = "messages"
    /** Booking lifecycle pushes (confirmed / 24h reminder / review request). */
    const val BOOKINGS = "bookings"
    /** Artist-side `gig_request` inbox pushes. */
    const val GIGS = "gigs"

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
    }
}
