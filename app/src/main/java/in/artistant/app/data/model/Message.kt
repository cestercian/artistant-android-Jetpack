package `in`.artistant.app.data.model

/** A conversation row; message bodies are loaded only after opening the thread. */
data class Thread(
    val id: String,
    val artistId: String,
    val bookingId: String? = null,
    val clientName: String? = null,
    val lastPreview: String = "",
    val lastMessageAtEpochMs: Long? = null,
    val unreadCount: Int = 0,
)

/** `kind` and `action_route` are supplied by migration 0072 system rows. */
enum class MessageKind { User, System }

/**
 * Local delivery state for optimistic chat bubbles (mirrors iOS `MessageDelivery`).
 * Server-hydrated rows are always [Sent]; [Sending]/[Failed] exist only in memory.
 */
enum class MessageDelivery { Sent, Sending, Failed }

/** A verbatim chat row. `isMine` is derived from the active session. */
data class Message(
    val id: String,
    val threadId: String,
    val senderId: String? = null,
    val body: String,
    val sentAtEpochMs: Long,
    val kind: MessageKind = MessageKind.User,
    val actionRoute: String? = null,
    val isMine: Boolean = false,
    val delivery: MessageDelivery = MessageDelivery.Sent,
)
