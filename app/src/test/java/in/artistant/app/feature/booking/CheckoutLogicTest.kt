package `in`.artistant.app.feature.booking

import `in`.artistant.app.testsupport.bookingDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checkout's three decisions.
 *
 * The send gate is the one that matters most: an over-eager guard here disables
 * the only CTA in the funnel with no affordance anywhere to satisfy it, which
 * dead-ends the entire booking flow on a grey button.
 */
class CheckoutLogicTest {

    // --- review rows ---------------------------------------------------------

    @Test
    fun reviewRows_showTheWholeRequest_inTheOrderItWasDecided() {
        val rows = checkoutReviewRows(bookingDraft())

        // Date and time are NOT rows — the light design puts them in the act
        // header (see checkoutActMeta), and listing them here as well printed the
        // same two facts twice on one short page.
        assertEquals(
            listOf("Package", "Venue", "Guests", "Directions"),
            rows.map { it.label },
        )
        assertEquals("Evening set", rows.first { it.label == "Package" }.value)
        assertEquals("Rooftop", rows.first { it.label == "Venue" }.value)
        assertEquals("80", rows.first { it.label == "Guests" }.value)
    }

    // --- the act header's meta lines ----------------------------------------

    @Test
    fun actMeta_readsTierThenWhenAndWhere() {
        assertEquals(
            listOf("Evening set · 2h", "Sat, May 16, 2026 · 8:30 PM · Rooftop"),
            checkoutActMeta(bookingDraft()),
        )
    }

    @Test
    fun actMeta_doesNotRepeatADurationTheTierNameAlreadyCarries() {
        // The funnel snapshots `packageName` and `packageDuration` off the same
        // package, and artists routinely name the tier after its length — so
        // "Full band · 90 min" must not become "Full band · 90 min · 90 min".
        val draft = bookingDraft().copy(packageName = "Full band · 90 min", packageDuration = "90 min")

        assertEquals("Full band · 90 min", checkoutActMeta(draft).first())
    }

    @Test
    fun actMeta_dropsTheBlanks_ratherThanTrailingSeparators() {
        val draft = bookingDraft().copy(venue = "   ", packageDuration = "")

        assertEquals(
            listOf("Evening set", "Sat, May 16, 2026 · 8:30 PM"),
            checkoutActMeta(draft),
        )
    }

    @Test
    fun actMeta_dropsALineWithNothingInItAtAll() {
        // A card with an empty second line is a card with a gap in it. A draft
        // that somehow carries no date, time or venue renders one line, not two.
        val draft = bookingDraft().copy(date = "", time = "", venue = "")

        assertEquals(listOf("Evening set · 2h"), checkoutActMeta(draft))
    }

    @Test
    fun reviewRows_wordTheEmptyOptionals_ratherThanRenderingBlanks() {
        // Venue and directions are both optional in the booking form. A blank row
        // reads as a rendering fault — the client can't tell whether they skipped
        // it or the screen lost it.
        val rows = checkoutReviewRows(bookingDraft().copy(venue = "  ", venueNotes = ""))

        assertEquals("Not set", rows.first { it.label == "Venue" }.value)
        assertEquals("None", rows.first { it.label == "Directions" }.value)
    }

    @Test
    fun reviewRows_carryTheDirectionsWhenThereAreSome() {
        val rows = checkoutReviewRows(bookingDraft(venueNotes = "Gate 3, load-in at the back"))

        assertEquals("Gate 3, load-in at the back", rows.first { it.label == "Directions" }.value)
    }

    @Test
    fun reviewRows_fallBackToCustom_forAnUnnamedPackage() {
        val rows = checkoutReviewRows(bookingDraft().copy(packageName = "   "))

        assertEquals("Custom", rows.first { it.label == "Package" }.value)
    }

    // --- the send gate -------------------------------------------------------

    @Test
    fun sendIsAllowedWithAnEmptyVenue() {
        // Explicitly pinned: requiring a venue here is the guard that broke the
        // funnel, and nothing in the UI lets the client satisfy it.
        assertFalse(checkoutBlocked(isSubmitting = false, artistHasNoPackages = false))
    }

    @Test
    fun sendIsBlockedWhileTheRequestIsInFlight() {
        assertTrue(checkoutBlocked(isSubmitting = true, artistHasNoPackages = false))
    }

    @Test
    fun sendIsBlockedForAnArtistWhoPublishesNoTiers() {
        // There is no fixed price to request against — the custom-quote path is
        // the honest destination.
        assertTrue(checkoutBlocked(isSubmitting = false, artistHasNoPackages = true))
    }

    // --- narrated wait -------------------------------------------------------

    @Test
    fun waitCopy_namesTheArtistOnTheFirstHop() {
        val copy = checkoutWaitCopy(CheckoutWaitPhase.Sending, "Nova Beats")

        assertEquals("Sending your request to Nova Beats…", copy.title)
        assertTrue(copy.subtitle.isNotBlank())
    }

    @Test
    fun waitCopy_degradesToANoun_whenTheArtistNeverHydrated() {
        // A deep link or a cold cache can reach this screen before the artist
        // resolves; "Sending your request to …" with a hole in it is worse than a
        // generic noun.
        assertEquals(
            "Sending your request to the artist…",
            checkoutWaitCopy(CheckoutWaitPhase.Sending, "   ").title,
        )
    }

    @Test
    fun waitCopy_describesTheServerWriteOnTheSecondHop() {
        val copy = checkoutWaitCopy(CheckoutWaitPhase.Awaiting, "Nova Beats")

        assertEquals("Waiting for confirmation…", copy.title)
    }

    @Test
    fun expectationLine_promisesANotification_notADeadline() {
        // No response window exists on this path — `expires_at` belongs to the
        // artist-side gig-request flow — so the copy must not invent one.
        assertFalse(CHECKOUT_EXPECTATION.contains("24"))
        assertFalse(CHECKOUT_EXPECTATION.contains("hour"))
        assertTrue(CHECKOUT_EXPECTATION.contains("notify"))
    }
}
