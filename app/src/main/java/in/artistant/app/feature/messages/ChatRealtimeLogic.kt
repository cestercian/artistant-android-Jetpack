package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import kotlin.math.abs

/**
 * Pure reconcile helpers for chat Realtime vs optimistic send — ported from
 * iOS `MessageStore.receiveRealtimeMessage` / `deliver` / `mergePreservingOptimistic`.
 * Kept free of Android/Hilt so JVM unit tests can pin the race cases.
 */
object ChatRealtimeLogic {

    /** Collapse a Realtime INSERT into the local list without duplicating bubbles. */
    fun receiveRealtimeMessage(existing: List<Message>, incoming: Message): List<Message> {
        if (existing.any { it.id == incoming.id }) return existing
        val confirmed = incoming.copy(delivery = MessageDelivery.Sent)

        // Realtime can beat send()'s RETURNING path: collapse into the .sending
        // placeholder with the same body. Never match .failed (would steal retry).
        if (confirmed.isMine) {
            val optIdx = existing.indexOfFirst { local ->
                local.isMine &&
                    local.delivery == MessageDelivery.Sending &&
                    local.body == confirmed.body &&
                    abs(local.sentAtEpochMs - confirmed.sentAtEpochMs) < 15_000
            }
            if (optIdx >= 0) {
                return existing.toMutableList().also { it[optIdx] = confirmed }
            }
        }
        return insertBySentAt(existing, confirmed)
    }

    /**
     * After `send()` returns: drop the optimistic id, then place the server row
     * in `sent_at` order — and only if Realtime hasn't already inserted it.
     */
    fun reconcileSendSuccess(
        existing: List<Message>,
        optimisticId: String,
        server: Message,
    ): List<Message> {
        val withoutOptimistic = existing.filterNot { it.id == optimisticId }
        val confirmed = server.copy(delivery = MessageDelivery.Sent, isMine = true)
        if (withoutOptimistic.any { it.id == confirmed.id }) return withoutOptimistic
        return insertBySentAt(withoutOptimistic, confirmed)
    }

    fun markDelivery(
        existing: List<Message>,
        messageId: String,
        delivery: MessageDelivery,
    ): List<Message> = existing.map {
        if (it.id == messageId) it.copy(delivery = delivery) else it
    }

    /**
     * Merge a server page with local scrollback / in-flight bubbles so a refresh
     * never wipes .sending/.failed or older messages outside the 50-row window.
     */
    fun mergePreservingOptimistic(server: List<Message>, existing: List<Message>): List<Message> {
        val serverIds = server.map { it.id }.toSet()
        val preserved = existing.filter { it.id !in serverIds }
        if (preserved.isEmpty()) return server
        return (server + preserved).sortedBy { it.sentAtEpochMs }
    }

    /**
     * Put a new row where its `sent_at` belongs, not at the tail.
     *
     * The transcript is ascending by `sentAtEpochMs` — [mergePreservingOptimistic]
     * sorts it, and everything downstream reads it that way ([ChatTimestamps]
     * walks it linearly to place day rules and outgoing-run captions). Appending
     * blindly breaks that in the one window where both insert paths are live at
     * once: the counterparty's Realtime INSERT lands while the viewer's own
     * `send()` RETURNING is still in flight, so the confirmed row — stamped
     * EARLIER by the server — would otherwise settle below a reply that came
     * after it, and a pair straddling local midnight would print the same day
     * separator twice.
     *
     * The common case is still an append: a message that is genuinely the newest
     * costs one comparison.
     */
    private fun insertBySentAt(existing: List<Message>, message: Message): List<Message> {
        if (existing.isEmpty() || existing.last().sentAtEpochMs <= message.sentAtEpochMs) {
            return existing + message
        }
        val at = existing.indexOfFirst { it.sentAtEpochMs > message.sentAtEpochMs }
        return existing.toMutableList().also { it.add(at, message) }
    }
}
