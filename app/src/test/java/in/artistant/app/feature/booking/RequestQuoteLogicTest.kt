package `in`.artistant.app.feature.booking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Screen 17's two derivations.
 *
 * [quoteBriefMessage] is the load-bearing one: `gig_requests` has no column for
 * the occasion or the start time, and the design asks for both because both
 * change what an artist charges. Composing them into the message is what stops
 * the funnel collecting two fields and dropping them on the floor — so the shape
 * of that string is a contract with the artist reading their inbox, not a
 * formatting preference.
 */
class RequestQuoteLogicTest {

    @Test
    fun brief_leadsWithTheFactsThenTheHostsOwnWords() {
        assertEquals(
            "Sangeet · 8:00 PM start\nLawn set-up, power is 20 m from the stage.",
            quoteBriefMessage(
                occasion = "Sangeet",
                startTime = "8:00 PM",
                note = "Lawn set-up, power is 20 m from the stage.",
            ),
        )
    }

    @Test
    fun brief_dropsWhicheverFactIsMissing() {
        assertEquals("Sangeet\nTwo sets.", quoteBriefMessage("Sangeet", "", "Two sets."))
        assertEquals("8:00 PM start\nTwo sets.", quoteBriefMessage(null, "8:00 PM", "Two sets."))
    }

    @Test
    fun brief_isJustTheNoteWhenNeitherFactWasAnswered() {
        assertEquals("Two 45-minute sets.", quoteBriefMessage(null, "", "Two 45-minute sets."))
    }

    @Test
    fun brief_isJustTheFactsWhenTheHostWroteNothing() {
        // No dangling newline: the artist reads one line, not a line and a blank.
        assertEquals("House show · 7:30 PM start", quoteBriefMessage("House show", "7:30 PM", "   "))
    }

    @Test
    fun brief_isEmptyWhenNothingWasSaidAtAll() {
        // Empty, not " · " or "\n" — submit() turns this into a null message
        // rather than writing whitespace into the column.
        assertEquals("", quoteBriefMessage(null, "  ", ""))
    }

    @Test
    fun replyLine_namesTheArtistAndTheirPublishedSpeed() {
        assertEquals("Saanjh usually replies in < 24h", quoteReplyLine("Saanjh", "< 24h"))
    }

    @Test
    fun replyLine_saysNothingWhenThereIsNothingToSay() {
        // `response_label` is the artist's own claim. With no artist loaded, or a
        // blank column, the subtitle is absent rather than invented.
        assertNull(quoteReplyLine("", "< 24h"))
        assertNull(quoteReplyLine("Saanjh", "   "))
    }
}
