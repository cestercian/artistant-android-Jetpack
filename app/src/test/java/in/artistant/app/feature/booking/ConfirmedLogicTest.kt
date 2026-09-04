package `in`.artistant.app.feature.booking

import `in`.artistant.app.testsupport.booking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen 07's headline and its terms card.
 *
 * "Say the outcome, not 'success'" is the design's note, and the outcome depends
 * on which state the row is actually in: the funnel files a REQUEST
 * (`pending_confirm`, mig 0098), so the confirmed headline belongs only to a row
 * the artist has accepted.
 */
class ConfirmedLogicTest {

    @Test
    fun headline_namesTheDayTheHostWillRepeatOutLoud() {
        assertEquals(
            "You've got a band on Saturday.",
            confirmedHeadline(confirmed = true, dateLabel = "Sat, Oct 12, 2026"),
        )
        assertEquals(
            "You've got a band on Friday.",
            confirmedHeadline(confirmed = true, dateLabel = "Fri, Nov 6, 2026"),
        )
    }

    @Test
    fun headline_dropsTheDayClauseForALabelWeDidNotWrite() {
        // A date this app cannot parse must not be turned into a guessed weekday
        // — naming the wrong day is worse than naming none.
        assertEquals("You've got your act.", confirmedHeadline(true, "2026-10-12"))
        assertEquals("You've got your act.", confirmedHeadline(true, ""))
    }

    @Test
    fun headline_saysWhatIsTrueBeforeTheArtistHasAnswered() {
        // The funnel lands `pending_confirm`. Drawing the confirmed page over a
        // pending row would promise a band that has not said yes.
        assertEquals("Request sent.", confirmedHeadline(false, "Sat, Oct 12, 2026"))
    }

    @Test
    fun terms_readWhenSetWhereAndTheAgreedFee() {
        val rows = confirmedTerms(
            booking(date = "Sat, Oct 12, 2026", time = "8:00 PM", venue = "Indiranagar", fee = 36_000)
                .copy(packageName = "Full band · 90 min"),
        )

        assertEquals(listOf("When", "Set", "Where", "Agreed fee"), rows.map { it.label })
        assertEquals("Sat, Oct 12, 2026 · 8:00 PM", rows[0].amount)
        // "· direct" is the one word that says nobody is holding this money.
        assertEquals("₹36,000 · direct", rows[3].amount)
    }

    @Test
    fun terms_dropTheVenuePlaceholderCreateWrites() {
        // `create()` writes "TBD" for an empty venue. A card that says the show
        // is at TBD is worse than one that does not mention where it is.
        val labels = confirmedTerms(booking(venue = "TBD")).map { it.label }

        assertFalse(labels.contains("Where"))
        assertTrue(labels.contains("Agreed fee"))
    }

    @Test
    fun terms_alwaysStateTheFee_evenWithNothingElseToShow() {
        val rows = confirmedTerms(booking(date = "", time = "", venue = "").copy(packageName = null))

        assertEquals(listOf("Agreed fee"), rows.map { it.label })
    }
}
