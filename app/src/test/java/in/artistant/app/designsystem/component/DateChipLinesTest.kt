package `in`.artistant.app.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar

/**
 * Chip content for the "Pick a date" scroller.
 *
 * Reported symptom: chips rendered as `Wed` over `Aug 5,` — a dangling comma
 * and a month/day pair cut off mid-label. The cause was arithmetic on the
 * rendered string rather than formatting: the canonical booking label
 * ("EEE, MMM d, yyyy" → "Wed, Aug 5, 2026") had its head chopped and the tail
 * truncated to a fixed character count, so the cut landed wherever it landed.
 * Widening the chip would only have moved the cut.
 *
 * The anatomy is iOS `DateScroller.swift`'s: an uppercase weekday abbreviation
 * over the bare day-of-month. Both lines are FORMATTED from the parsed date, so
 * the length of the source label can't reach the screen at all.
 *
 * Parsing goes through `BookingDateFormat.parseLabel` — the same reader
 * `monthLabelFromDateLabel` sits on — so there is exactly one place in this
 * codebase that knows what a booking date label looks like.
 */
class DateChipLinesTest {

    // --- canonical shape -----------------------------------------------------

    @Test
    fun `the canonical label yields an uppercase weekday and a bare day`() {
        // Aug 5, 2026 really is a Wednesday — this is the label off the device.
        assertEquals(DateChipLines("WED", "5"), dateChipLines("Wed, Aug 5, 2026"))
    }

    @Test
    fun `neither line carries a separator from the source label`() {
        // The actual regression: "Aug 5," reached the screen with its comma.
        val lines = dateChipLines("Wed, Aug 5, 2026")
        assertFalse(lines.weekday.contains(","))
        assertFalse(lines.day.contains(","))
    }

    @Test
    fun `nothing is truncated - a two digit day survives intact`() {
        // The old fixed-width cut ate the second digit of any day past the 9th
        // once the month token was long enough.
        assertEquals(DateChipLines("THU", "31"), dateChipLines("Thu, Dec 31, 2026"))
        assertEquals(DateChipLines("THU", "1"), dateChipLines("Thu, Jan 1, 2026"))
    }

    @Test
    fun `a label whose weekday contradicts its date is not read as a date`() {
        // `BookingDateFormat.parseLabel` parses strictly, and its strictness
        // extends to the weekday token: "Mon, Aug 5, 2026" names a Wednesday, so
        // the whole label is rejected rather than half-believed. That's the right
        // call here — a contradicting label is corrupt, and inventing "WED" for
        // it would paper over whatever wrote it. It degrades like any other
        // unreadable label.
        assertEquals(DateChipLines("", "Mon, Aug 5, 2026"), dateChipLines("Mon, Aug 5, 2026"))
    }

    @Test
    fun `an epoch produces the same two lines as its label`() {
        // The booking funnel's chips carry epochs; the calendar list carries
        // labels. Both must render identically or the same day looks like two.
        val aug5 = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 5)
        }.timeInMillis
        assertEquals(dateChipLines("Wed, Aug 5, 2026"), dateChipLines(aug5))
    }

    // --- tolerated variants --------------------------------------------------
    // `BookingDateFormat.datePatterns` also reads these, so they can reach a chip.

    @Test
    fun `a weekday-less label still resolves its weekday`() {
        assertEquals(DateChipLines("WED", "5"), dateChipLines("Aug 5, 2026"))
    }

    @Test
    fun `an ISO label is read too`() {
        assertEquals(DateChipLines("WED", "5"), dateChipLines("2026-08-05"))
    }

    // --- degraded inputs -----------------------------------------------------
    // A chip must still render something: no throw, no fabricated date.

    @Test
    fun `an unreadable label degrades to the raw label with no weekday`() {
        assertEquals(DateChipLines("", "TBD"), dateChipLines("TBD"))
        assertEquals(DateChipLines("", "not a date"), dateChipLines("not a date"))
    }

    @Test
    fun `an empty label yields empty lines rather than throwing`() {
        // `DbBooking.dateLabel` defaults to "" — reachable, not theoretical.
        assertEquals(DateChipLines("", ""), dateChipLines(""))
    }
}
