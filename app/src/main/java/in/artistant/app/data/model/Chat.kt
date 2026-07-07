package `in`.artistant.app.data.model

import java.time.Instant

/**
 * Chat domain models — ports of iOS `Models/Messaging.swift`. The realtime chat
 * layer (M4). A [Thread] is a conversation between a client and an artist; its
 * [messages] are hydrated on demand (the thread-list read carries metadata only).
 */

/** Who authored a message, from the signed-in VIEWER's perspective. */
enum class MessageSender { Me, Them, System }

/**
 * Local-only delivery state driving the optimistic-send UX. Messages off the wire
 * / out of storage are always [Sent]; only a just-composed message cycles
 * [Sending] → [Sent] (server confirmed) or [Sending] → [Failed] (write threw,
 * retry affordance shown). Lives on the model so one source of truth drives both
 * the bubble styling and the store's reconcile.
 */
enum class MessageDelivery { Sent, Sending, Failed }

data class Message(
    val id: String,
    val sender: MessageSender,
    val body: String,          // raw author text (server-redacted once off the wire)
    val sentAt: Instant,
    val delivery: MessageDelivery = MessageDelivery.Sent,
)

data class Thread(
    val id: String,
    val artistId: String,
    val bookingId: String? = null,     // null = pre-booking inquiry
    /**
     * Denormalized client display name (migration 0036) — lets an ARTIST viewer
     * show the client's name (the model carries no client id, and RLS blocks
     * reading the client's users row). null until the migration lands / for local
     * threads; the screen falls back to a booking-derived name, then "Client".
     * The CLIENT viewer resolves the artist name via ArtistsRepository by artistId.
     */
    val clientName: String? = null,
    val messages: List<Message> = emptyList(),
    val unread: Int = 0,
) {
    val lastPreview: String get() = messages.lastOrNull()?.body ?: ""
}
