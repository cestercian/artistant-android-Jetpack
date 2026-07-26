package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the Realtime ↔ optimistic send race cases ported from iOS MessageStore. */
class ChatRealtimeLogicTest {

    private fun msg(
        id: String,
        body: String = "hi",
        mine: Boolean = true,
        delivery: MessageDelivery = MessageDelivery.Sent,
        at: Long = 1_000L,
    ) = Message(
        id = id,
        threadId = "t1",
        body = body,
        sentAtEpochMs = at,
        isMine = mine,
        delivery = delivery,
    )

    @Test
    fun receive_dedupsByServerId() {
        val existing = listOf(msg("server-1"))
        val result = ChatRealtimeLogic.receiveRealtimeMessage(existing, msg("server-1"))
        assertEquals(1, result.size)
    }

    @Test
    fun receive_collapsesSendingOptimisticByBody() {
        val existing = listOf(
            msg("optimistic-1", body = "Meet at 8?", delivery = MessageDelivery.Sending, at = 1_000),
        )
        val echo = msg("server-9", body = "Meet at 8?", mine = true, at = 1_200)
        val result = ChatRealtimeLogic.receiveRealtimeMessage(existing, echo)
        assertEquals(1, result.size)
        assertEquals("server-9", result.single().id)
        assertEquals(MessageDelivery.Sent, result.single().delivery)
    }

    @Test
    fun receive_doesNotCollapseFailedBubble() {
        val existing = listOf(
            msg("optimistic-1", body = "Meet at 8?", delivery = MessageDelivery.Failed, at = 1_000),
        )
        val echo = msg("server-9", body = "Meet at 8?", mine = true, at = 1_200)
        val result = ChatRealtimeLogic.receiveRealtimeMessage(existing, echo)
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "optimistic-1" && it.delivery == MessageDelivery.Failed })
        assertTrue(result.any { it.id == "server-9" })
    }

    @Test
    fun reconcileSend_dropsOptimistic_andSkipsDuplicateServerRow() {
        val existing = listOf(
            msg("optimistic-1", delivery = MessageDelivery.Sending),
            msg("server-9"), // Realtime already landed
        )
        val result = ChatRealtimeLogic.reconcileSendSuccess(
            existing = existing,
            optimisticId = "optimistic-1",
            server = msg("server-9"),
        )
        assertEquals(listOf("server-9"), result.map { it.id })
    }

    @Test
    fun mergePreservingOptimistic_keepsScrollbackAndInFlight() {
        val server = listOf(msg("s2", at = 2_000), msg("s3", at = 3_000))
        val existing = listOf(
            msg("s1", at = 1_000), // older than page window
            msg("optimistic-1", delivery = MessageDelivery.Sending, at = 4_000),
            msg("s2", at = 2_000),
        )
        val merged = ChatRealtimeLogic.mergePreservingOptimistic(server, existing)
        assertEquals(listOf("s1", "s2", "s3", "optimistic-1"), merged.map { it.id })
    }
}
