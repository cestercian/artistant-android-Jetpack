package `in`.artistant.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the delete flow TELLS you before and after it erases your account (design 48 / 116).
 *
 * This is the last copy anyone reads before an irreversible action, so the rule these tests
 * pin is that it never states a number nobody read. `DeleteConsequences` carries nullable
 * counts precisely so a failed stat query degrades to a sentence rather than to "0 bookings" —
 * which on this screen would tell someone they have nothing to lose moments before they lose
 * it.
 */
class DeleteConsequencesTest {

    // ── Stage 2: names every loss ──────────────────────────────────────────────────────

    @Test
    fun `a client sees three losses and an artist four`() {
        assertEquals(3, deleteConsequences(DeleteConsequences(isArtist = false)).size)
        assertEquals(4, deleteConsequences(DeleteConsequences(isArtist = true)).size)
    }

    @Test
    fun `reviews and score are named for an artist and never for a host`() {
        val artist = deleteConsequences(DeleteConsequences(isArtist = true))
        assertTrue(artist.any { it.title.contains("reviews", ignoreCase = true) })

        val client = deleteConsequences(DeleteConsequences(isArtist = false))
        // A host has no reviews about them and no Bookability Score. Listing a loss that
        // cannot happen makes the ones that can look equally uncertain.
        assertFalse(client.any { it.title.contains("reviews", ignoreCase = true) })
        assertFalse(client.any { it.title.contains("score", ignoreCase = true) })
    }

    @Test
    fun `the handle is named when we know it`() {
        val items = deleteConsequences(DeleteConsequences(handle = "tiltcollective"))
        assertEquals("@tiltcollective is released and can be taken by someone else", items[0].detail)
    }

    @Test
    fun `an unknown handle degrades to the generic sentence, never to a blank at-sign`() {
        val detail = deleteConsequences(DeleteConsequences(handle = null))[0].detail
        assertFalse("must not print a dangling @", detail.contains("@"))
        assertTrue(detail.contains("username"))
    }

    @Test
    fun `counts that were read are stated`() {
        val items = deleteConsequences(DeleteConsequences(bookings = 128, upcoming = 2))
        assertEquals("128 bookings", items[1].title)
        assertEquals("Including the 2 upcoming ones, which are cancelled", items[1].detail)
    }

    @Test
    fun `one upcoming booking reads in the singular`() {
        val items = deleteConsequences(DeleteConsequences(bookings = 3, upcoming = 1))
        assertEquals("Including the 1 upcoming one, which is cancelled", items[1].detail)
    }

    @Test
    fun `nothing upcoming says so instead of cancelling zero things`() {
        val items = deleteConsequences(DeleteConsequences(bookings = 4, upcoming = 0))
        assertEquals("Nothing is upcoming, so nothing gets cancelled", items[1].detail)
    }

    @Test
    fun `a count we could NOT read never becomes a zero`() {
        // The whole reason the fields are nullable. "0 bookings" and "we couldn't reach the
        // server" are opposite claims, and this screen is the worst place in the app to
        // confuse them.
        val items = deleteConsequences(DeleteConsequences(bookings = null, upcoming = null))
        assertEquals("Your bookings", items[1].title)
        assertFalse(items[1].detail.contains("0"))
        assertTrue(items[1].detail.contains("including anything still upcoming"))
    }

    @Test
    fun `messages are always named — both sides lose the thread`() {
        val items = deleteConsequences(DeleteConsequences())
        assertTrue(items.any { it.title == "Every message" && it.detail.contains("Both sides") })
    }

    // ── Stage 3: the receipt, including the part that has not happened yet ─────────────

    @Test
    fun `the receipt itemises four things and one of them is the backup window`() {
        val items = deleteReceipt(DeleteConsequences())
        assertEquals(4, items.size)
        assertEquals("Backups purge in 30 days", items[3].title)
        assertTrue(items[3].detail.contains("unrecoverable"))
    }

    @Test
    fun `the receipt names the handle it released`() {
        assertEquals(
            "@rheamenon is free for someone else to take",
            deleteReceipt(DeleteConsequences(handle = "rheamenon"))[1].detail,
        )
    }

    @Test
    fun `the receipt degrades the handle line rather than printing a bare at-sign`() {
        assertFalse(deleteReceipt(DeleteConsequences(handle = null))[1].detail.contains("@"))
    }

    @Test
    fun `the receipt mentions the device calendar, which is the one thing not on the server`() {
        assertTrue(
            deleteReceipt(DeleteConsequences()).any {
                it.title == "Calendar cleaned" && it.detail.contains("device calendar")
            },
        )
    }

    // ── The off-ramp on stage 1 ────────────────────────────────────────────────────────

    @Test
    fun `the support off-ramp names what is at stake when we know it`() {
        assertTrue(supportOfframpLine(DeleteConsequences(bookings = 7)).contains("7 bookings"))
    }

    @Test
    fun `the off-ramp stays truthful with no count and with a zero`() {
        val unknown = supportOfframpLine(DeleteConsequences(bookings = null))
        val zero = supportOfframpLine(DeleteConsequences(bookings = 0))
        assertEquals(unknown, zero)
        assertFalse(unknown.contains("0"))
    }

    // ── The confirmation keyword ──────────────────────────────────────────────────────

    @Test
    fun `only the exact keyword unlocks the delete`() {
        fun canDelete(typed: String) =
            DeleteAccountUiState(confirmation = typed).canDelete

        assertTrue(canDelete(DELETE_KEYWORD))
        assertTrue("surrounding whitespace is a typo, not a refusal", canDelete("  DELETE  "))
        assertFalse(canDelete("delete"))
        assertFalse(canDelete("Delete"))
        assertFalse(canDelete("DELETE MY ACCOUNT"))
        assertFalse(canDelete(""))
    }

    @Test
    fun `a delete already in flight cannot be fired twice`() {
        assertFalse(
            DeleteAccountUiState(confirmation = DELETE_KEYWORD, working = true).canDelete,
        )
    }
}
