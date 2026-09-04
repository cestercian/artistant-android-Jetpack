package `in`.artistant.app.feature.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer's own rules.
 *
 * These exist because of a specific regression: typing in the chat made the send
 * button DISAPPEAR — the affordance went away exactly when it became usable. The
 * cause was in the view layer (an `alpha` that restructured the modifier chain at
 * 1f and took the disc's background with it), so no test here could have caught
 * the drawing. What these pin is the contract the view now renders against:
 * `canSend` is a two-state emphasis flag, never a should-this-exist flag, and it
 * turns TRUE — not false — the moment there is something to send.
 */
class ComposerStateTest {

    @Test
    fun `empty draft cannot send`() {
        assertFalse(ComposerState().canSend)
    }

    @Test
    fun `whitespace only draft cannot send`() {
        assertFalse(ComposerState(draft = "   \n\t ").canSend)
    }

    @Test
    fun `any real character makes the send affordance primary`() {
        // The regression in one assertion: one character in, and the control the
        // user needs is the emphasised one.
        assertTrue(ComposerState(draft = "h").canSend)
        assertTrue(ComposerState(draft = "Hello there").canSend)
        assertTrue(ComposerState(draft = "  padded  ").canSend)
    }

    @Test
    fun `a disabled composer never sends, whatever is typed`() {
        assertFalse(ComposerState(draft = "Hello", enabled = false).canSend)
    }

    @Test
    fun `payload is trimmed, because the seam trims before it inserts`() {
        // Must match SupabaseMessagesRepository.send: the optimistic bubble and
        // the row Postgres echoes back have to carry the same body or the
        // Realtime echo lands beside the placeholder instead of collapsing into
        // it (see ChatRealtimeLogic.receiveRealtimeMessage).
        assertEquals("Hello there", ComposerState(draft = "  Hello there \n").payload)
    }

    @Test
    fun `typing caps at the server limit`() {
        val long = "x".repeat(MAX_MESSAGE_CHARS + 500)
        val state = ComposerState().typed(long)
        assertEquals(MAX_MESSAGE_CHARS, state.draft.length)
        assertTrue(state.canSend)
    }

    @Test
    fun `the cap is applied on the way in, so nothing is silently dropped at send`() {
        val state = ComposerState().typed("y".repeat(MAX_MESSAGE_CHARS + 1))
        assertEquals(state.draft, state.payload)
    }

    @Test
    fun `a caller-supplied cap wins over the default`() {
        assertEquals("abcd", ComposerState().typed("abcdefgh", maxChars = 4).draft)
    }

    @Test
    fun `clearing empties the draft and keeps enablement`() {
        val sent = ComposerState(draft = "Hello", enabled = true).cleared()
        assertEquals("", sent.draft)
        assertTrue(sent.enabled)
        assertFalse(sent.canSend)
    }

    @Test
    fun `clearing a disabled composer leaves it disabled`() {
        assertFalse(ComposerState(draft = "Hello", enabled = false).cleared().enabled)
    }
}
