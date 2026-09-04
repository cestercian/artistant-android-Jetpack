package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.testsupport.booking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen 132's derivations — "a record, not a tax invoice".
 *
 * The money lines are the ones worth pinning. `bookings` persists a 5% platform
 * fee and 18% GST on every row (BookingMath, on create), and v1 collects
 * neither: the host settles the artist fee directly. Printing the persisted
 * numbers here would put two charges on a document nobody has ever paid, which
 * is exactly the claim the design's note says this document must not make.
 */
class InvoiceLogicTest {

    @Test
    fun money_showsTheFee_thePlatformsZero_andTotalsToTheFee() {
        val lines = invoiceLines(booking(fee = 36_000))

        assertEquals(listOf("Artist fee", "Artistant fee", "Total"), lines.map { it.label })
        assertEquals("₹36,000", lines[0].amount)
        assertEquals("₹0 — no fee in this version", lines[1].amount)
        assertEquals("₹36,000", lines[2].amount)
        assertTrue("the total is the emphasised line", lines[2].emphasis)
    }

    @Test
    fun money_ignoresThePersistedPlatformFeeAndGst() {
        // The row carries them — the columns are NOT NULL and create computes
        // them — and the record must not repeat them as charges.
        val row = booking(fee = 36_000).copy(platformFee = 1_800, gst = 6_804, total = 44_604)

        val amounts = invoiceLines(row).map { it.amount }

        assertFalse(amounts.any { it.contains("1,800") })
        assertFalse(amounts.any { it.contains("6,804") })
        assertFalse(amounts.any { it.contains("44,604") })
    }

    @Test
    fun reference_isTheLeadingHexOfTheId_uppercased() {
        // Prefix-based like a short git sha, so support can resolve it back.
        assertEquals("AR-3F9A2C", bookingReference("3f9a2c18-0000-4000-8000-000000000000"))
    }

    @Test
    fun reference_isStableAcrossScreens() {
        val id = "9B1DEB4D-3B7D-4BAD-9BDD-2B0D7B3DCB6D"

        assertEquals(bookingReference(id), bookingReference(id.lowercase()))
    }

    @Test
    fun reference_isBlankRatherThanADanglingPrefix() {
        assertEquals("", bookingReference(""))
        assertEquals("", bookingReference("----"))
    }

    @Test
    fun bookingRows_dropWhatTheRowDoesNotCarry() {
        val row = booking().copy(packageName = null, venue = "TBD", time = "")

        val labels = invoiceBookingRows(row, artistName = "The Tilt Collective").map { it.label }

        assertEquals(listOf("Act", "Date"), labels)
        // "TBD" is the placeholder `create()` writes for an empty venue, not a
        // venue — a record that says the show is at TBD is worse than one that
        // does not mention where it is.
        assertFalse(labels.contains("Venue"))
    }

    @Test
    fun bookingRows_joinTheDateAndTimeIntoOneLine() {
        val rows = invoiceBookingRows(
            booking().copy(date = "Sat, Oct 12, 2026", time = "8:00 PM"),
            artistName = "The Tilt Collective",
        )

        assertEquals("Sat, Oct 12, 2026 · 8:00 PM", rows.first { it.label == "Date" }.amount)
    }

    @Test
    fun bookingRows_omitTheActWhenTheArtistCouldNotBeRead() {
        // The artist cache can be cold. A row labelled "Act" with nothing after
        // it reads as a rendering fault.
        val labels = invoiceBookingRows(booking(), artistName = "   ").map { it.label }

        assertFalse(labels.contains("Act"))
    }

    @Test
    fun shareText_carriesTheDisclaimerWithTheNumbers() {
        // The disclaimer must not be able to travel without the record: this is
        // the whole reason the share is text rather than a PDF.
        val text = invoiceShareText(
            booking(fee = 36_000),
            artistName = "The Tilt Collective",
            reference = "AR-3F9A2C",
        )

        assertTrue(text.startsWith("Artistant — booking record #AR-3F9A2C"))
        assertTrue(text.contains("Artist fee: ₹36,000"))
        assertTrue(text.endsWith(INVOICE_DISCLAIMER))
    }

    @Test
    fun subtitle_joinsWhicheverHalvesExist() {
        assertEquals("#AR-3F9A2C · Sat, Oct 12, 2026", invoiceSubtitle("AR-3F9A2C", "Sat, Oct 12, 2026"))
        assertEquals("#AR-3F9A2C", invoiceSubtitle("AR-3F9A2C", "  "))
        assertNull(invoiceSubtitle("", ""))
    }

    @Test
    fun status_isTheBookingsOwn_notAClaimThatItWasSettled() {
        // Sanity anchor for the screen's badge: there is no payment state on the
        // row, so the label is the booking's status word, whatever it is.
        assertEquals("Cancelled", booking().copy(status = BookingStatus.Cancelled).status.label)
        assertEquals("Unavailable", booking().copy(status = BookingStatus.Unknown).status.label)
    }
}
