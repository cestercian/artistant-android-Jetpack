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

    // --- the guest count ----------------------------------------------------

    @Test
    fun guests_mayBeLeftBlank() {
        // `crowd_size` is nullable and the design treats the head count as
        // optional — blank is a choice, not a mistake.
        assertNull(quoteGuestsError(""))
        assertNull(quoteGuestsError("   "))
    }

    @Test
    fun guests_acceptAnOrdinaryHeadCount() {
        assertNull(quoteGuestsError("1"))
        assertNull(quoteGuestsError("200"))
        assertNull(quoteGuestsError("99999"))
    }

    @Test
    fun guests_rejectAValueTheColumnCannotHold() {
        // THE regression. The field filtered to digits and nothing else, and
        // `submit` mapped the result through `toIntOrNull()` — which answers null
        // for anything past Int.MAX_VALUE. A null `crowd_size` is
        // indistinguishable from "the host left it blank", so an eleven-digit
        // paste sent a brief that did not mention how many people were coming,
        // with nothing on screen saying so.
        val overflow = "9".repeat(11)
        assertNull("precondition: this is exactly what used to slip through", overflow.toIntOrNull())

        assertEquals(
            "Enter a guest count between 1 and 99,999.",
            quoteGuestsError(overflow),
        )
    }

    @Test
    fun guests_rejectValuesOutsideTheRangeTheFieldAllows() {
        assertEquals("Enter a guest count between 1 and 99,999.", quoteGuestsError("0"))
        assertEquals("Enter a guest count between 1 and 99,999.", quoteGuestsError("100000"))
    }

    @Test
    fun guests_theDigitCapCannotProduceAnUnreadableValue() {
        // The setter's cap and the validator's range have to agree: the longest
        // string the field can hold must still pass. If someone widens
        // QUOTE_GUESTS_MAX_DIGITS without widening the range, this fails.
        val longest = "9".repeat(QUOTE_GUESTS_MAX_DIGITS)

        assertNull(quoteGuestsError(longest))
        assertEquals(QUOTE_GUESTS_MAX, longest.toInt())
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
