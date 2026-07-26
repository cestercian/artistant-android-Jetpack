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
        return existing + confirmed
    }

    /**
     * After `send()` returns: drop the optimistic id, then append the server row
     * only if Realtime hasn't already inserted it.
     */
    fun reconcileSendSuccess(
        existing: List<Message>,
        optimisticId: String,
        server: Message,
    ): List<Message> {
        val withoutOptimistic = existing.filterNot { it.id == optimisticId }
        val confirmed = server.copy(delivery = MessageDelivery.Sent, isMine = true)
        if (withoutOptimistic.any { it.id == confirmed.id }) return withoutOptimistic
        return withoutOptimistic + confirmed
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
}
