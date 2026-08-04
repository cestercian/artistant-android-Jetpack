package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.MessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `messages` projection is a security boundary, not a style choice.
 *
 * Migration 0061 REVOKEd `messages.body_raw` (it leaked un-redacted contact PII
 * to the thread counterparty) — a `select("*")` on that table 403s against a
 * current server. Migration 0072 then added `kind` / `action_route` for system
 * rows. Both facts live in one const each; these tests fail loudly if someone
 * "simplifies" either projection back to a wildcard or drops the 0072 columns.
 */
class MessageColumnsTest {

    private val base = SupabaseMessagesRepository.MESSAGE_COLUMNS.value
    private val withSystem = SupabaseMessagesRepository.MESSAGE_COLUMNS_WITH_SYSTEM.value

    @Test
    fun baseProjectionNeverSelectsBodyRaw() {
        assertFalse("body_raw is REVOKEd by 0061", base.contains("body_raw"))
        assertFalse("body_raw is REVOKEd by 0061", withSystem.contains("body_raw"))
    }

    @Test
    fun neitherProjectionIsAWildcard() {
        assertFalse("* would 403 post-0061", base.contains("*"))
        assertFalse("* would 403 post-0061", withSystem.contains("*"))
    }

    @Test
    fun baseProjectionCarriesEverythingAChatBubbleNeeds() {
        assertEquals(
            listOf("id", "thread_id", "sender_id", "body", "sent_at"),
            base.split(",").map { it.trim() },
        )
    }

    @Test
    fun systemProjectionIsTheBaseProjectionPlusThe0072Columns() {
        val systemCols = withSystem.split(",").map { it.trim() }
        assertTrue(systemCols.containsAll(base.split(",").map { it.trim() }))
        assertTrue("0072 kind column", "kind" in systemCols)
        assertTrue("0072 action_route column", "action_route" in systemCols)
    }

    // --- domain defaults -----------------------------------------------------
    // The repository decoder maps kind == "system" → System and everything else
    // (including a null column on a pre-0072 server) → User. These pin the
    // defaults that decision relies on.

    @Test
    fun aMessageWithoutAnExplicitKindIsAUserMessage() {
        val m = Message(id = "m1", threadId = "t1", body = "hi", sentAtEpochMs = 0L)

        assertEquals(MessageKind.User, m.kind)
        assertEquals(MessageDelivery.Sent, m.delivery)
        assertEquals(null, m.actionRoute)
        assertFalse(m.isMine)
    }
}
