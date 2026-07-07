package `in`.artistant.app.data.model.dto

import `in`.artistant.app.common.util.SupabaseISO8601
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageSender
import `in`.artistant.app.data.model.Thread
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Postgres wire rows for the chat layer (ports of the iOS `DBThread` / `DBMessage`).
 * EXACT snake_case column names. The message read/insert-returning projection is
 * COLUMN-SCOPED (never `select("*")`): migration 0061 revokes SELECT on
 * `messages.body_raw` (the un-redacted contact PII), so a `*` read 403s. `body_raw`
 * must NEVER appear here — see [DBMessage.COLUMNS].
 */

/** Wire shape for `public.threads`. Unread is per-participant; the viewer's own is picked by role. */
@Serializable
data class DBThread(
    val id: String,
    val client_id: String,
    val artist_id: String,
    val booking_id: String? = null,
    // Per-participant counters maintained by the tg_messages_bump_thread trigger.
    // Optional so a projection that omits one decodes as null → 0.
    val client_unread_count: Int? = null,
    val artist_unread_count: Int? = null,
    // Denormalized client display name (migration 0036); null before it lands.
    val client_name: String? = null,
) {
    /**
     * `messages = []` — the list endpoint doesn't fetch bodies (call listMessages).
     * `unread` is the VIEWER's own counter: artists.id == users.id and
     * threads.artist_id references artists.id, so the artist's user id equals
     * artist_id — match it to pick the artist column, else the client's.
     */
    fun toThread(currentUserId: String): Thread {
        val viewerIsArtist = currentUserId.lowercase() == artist_id.lowercase()
        val unread = if (viewerIsArtist) (artist_unread_count ?: 0) else (client_unread_count ?: 0)
        return Thread(
            id = id,
            artistId = artist_id,
            bookingId = booking_id,
            clientName = client_name,
            messages = emptyList(),
            unread = maxOf(0, unread),
        )
    }

    companion object {
        // Explicit column list — threads has no revoked column, but stay consistent
        // with the codebase's never-`select("*")` discipline.
        const val COLUMNS =
            "id,client_id,artist_id,booking_id,client_unread_count,artist_unread_count,client_name"
    }
}

/**
 * Wire shape for `public.messages`. The trigger `tg_messages_redact` populates
 * `body_raw` from `body` on insert and rewrites `body` with the redacted version.
 * We consume only `body` — `body_raw` is service-role only (ENFORCED by 0061's
 * column-level SELECT revoke), so the read/insert selects list [COLUMNS] explicitly
 * precisely because `*` would 403 on the revoked column.
 */
@Serializable
data class DBMessage(
    val id: String,
    val thread_id: String,
    val sender_id: String,
    val body: String,
    val sent_at: String,
) {
    /**
     * `sender` is computed against the signed-in viewer's id: rows the viewer
     * authored are [MessageSender.Me]; everything else is [MessageSender.Them].
     * ([MessageSender.System] is reserved for future trigger-emitted rows.)
     */
    fun toMessage(currentUserId: String): Message {
        val sender = if (currentUserId.lowercase() == sender_id.lowercase()) {
            MessageSender.Me
        } else {
            MessageSender.Them
        }
        return Message(
            id = id,
            sender = sender,
            body = body,
            sentAt = SupabaseISO8601.parse(sent_at) ?: Instant.now(),
        )
    }

    companion object {
        /**
         * The ONLY column list for reading `public.messages`. Both the list read
         * and the send RETURNING projection go through this — never `select("*")`.
         * `body_raw` must NEVER be added here.
         */
        const val COLUMNS = "id,thread_id,sender_id,body,sent_at"
    }
}
