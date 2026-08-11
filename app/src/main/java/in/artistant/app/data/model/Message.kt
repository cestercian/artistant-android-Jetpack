package `in`.artistant.app.data.model

/**
 * A conversation row; message bodies are loaded only after opening the thread.
 *
 * Both participant ids are carried because a thread is only ever meaningful
 * relative to a seat: which name to show, which unread counter is yours, which
 * mute column is yours, and who the "other person" is when you want to block
 * them are all answered by comparing the viewer's uid to these two.
 */
data class Thread(
    val id: String,
    val artistId: String,
    /**
     * `threads.client_id` — the client seat's user id.
     *
     * Empty only for locally-minted rows that have never been to the server (the
     * fakes' `findOrCreateThread`). It exists on the row because the artist seat
     * has no other way to name its counterparty: `artistId` is the artist's own
     * uid, so an artist viewer looking for "who am I talking to" would otherwise
     * find nothing but a display name.
     */
    val clientId: String = "",
    val bookingId: String? = null,
    val clientName: String? = null,
    val lastPreview: String = "",
    val lastMessageAtEpochMs: Long? = null,
    val unreadCount: Int = 0,
    /**
     * Whether THIS VIEWER has muted the thread — their own side of the per-side
     * pair `threads.client_muted` / `threads.artist_muted` (migration 0091),
     * resolved on decode the same way [unreadCount] is.
     *
     * Deliberately one viewer-relative boolean rather than both columns: nothing
     * in the UI may render or act on the counterparty's mute state, and the only
     * write path ([MessagesRepository.setMuted]) resolves its own column from
     * the session.
     */
    val muted: Boolean = false,
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
