package `in`.artistant.app.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * `monthLabelFromDateLabel` is the key behind `groupedByMonth()` on both the
 * client Bookings list and the artist Gigs list — every row whose label maps to
 * the same month must collapse under ONE header. It regressed to returning the
 * whole label, which made every booking its own group (a header per row), so
 * these tests pin the canonical shape, the tolerated variants, and the degraded
 * fallbacks.
 */
class MonthLabelTest {

    // --- canonical shape -----------------------------------------------------
    // `BookingDateFormat.PATTERN` = "EEE, MMM d, yyyy" — what the app writes and
    // what `Booking.date` carries verbatim off the wire (`date_label`).

    @Test
    fun `canonical label yields month and year`() {
        assertEquals("May 2026", monthLabelFromDateLabel("Sat, May 16, 2026"))
    }

    @Test
    fun `a different month and year proves it is not May-specific`() {
        assertEquals("April 2026", monthLabelFromDateLabel("Fri, Apr 3, 2026"))
        assertEquals("December 2024", monthLabelFromDateLabel("Tue, Dec 31, 2024"))
        assertEquals("January 2027", monthLabelFromDateLabel("Mon, Jan 4, 2027"))
    }

    @Test
    fun `every month abbreviation resolves to a full month name`() {
        val expected = listOf(
            "Jan" to "January", "Feb" to "February", "Mar" to "March",
            "Apr" to "April", "May" to "May", "Jun" to "June",
            "Jul" to "July", "Aug" to "August", "Sep" to "September",
            "Oct" to "October", "Nov" to "November", "Dec" to "December",
        )
        expected.forEach { (abbrev, full) ->
            assertEquals("$full 2026", monthLabelFromDateLabel("Wed, $abbrev 1, 2026"))
        }
    }

    // --- the actual user-visible symptom -------------------------------------

    @Test
    fun `two bookings in the same month share one group key`() {
        // The bug: distinct keys here meant `groupedByMonth()` emitted one month
        // header per row instead of one header for the month.
        assertEquals(
            monthLabelFromDateLabel("Sat, May 16, 2026"),
            monthLabelFromDateLabel("Wed, May 20, 2026"),
        )
    }

    @Test
    fun `the same month in different years does not collapse`() {
        assertEquals("May 2025", monthLabelFromDateLabel("Fri, May 16, 2025"))
        assertEquals("May 2026", monthLabelFromDateLabel("Sat, May 16, 2026"))
    }

    @Test
    fun `group header matches the grid header format for the same month`() {
        // Both headers render on the same screen (BookingsScreen / ArtistGigsScreen
        // call `monthLabelFromEpoch` for the day grid and this fn for the row
        // groups), so an abbreviated "Apr 2026" under a full "April 2026" would
        // read as two different months.
        val may2026 = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.MAY, 16)
        }.timeInMillis
        assertEquals(
            monthLabelFromEpoch(may2026),
            monthLabelFromDateLabel("Sat, May 16, 2026"),
        )
    }

    // --- tolerated variants --------------------------------------------------
    // `BookingDateFormat.datePatterns` also accepts "MMM d, yyyy" on read, so a
    // weekday-less label can reach the grouping.

    @Test
    fun `weekday-less label still groups by month`() {
        assertEquals("May 2026", monthLabelFromDateLabel("May 16, 2026"))
    }

    @Test
    fun `a spelled-out weekday or month is tolerated`() {
        assertEquals("May 2026", monthLabelFromDateLabel("Saturday, May 16, 2026"))
        assertEquals("September 2026", monthLabelFromDateLabel("Tue, September 1, 2026"))
    }

    // --- degraded inputs -----------------------------------------------------
    // Nothing here may throw: a bad label must still render a row. Grouping under
    // the raw label is the accepted degraded behavior.

    @Test
    fun `a label with no year degrades to the raw label`() {
        assertEquals("May 16", monthLabelFromDateLabel("May 16"))
    }

    @Test
    fun `an unparseable label degrades to the raw label`() {
        assertEquals("not a date", monthLabelFromDateLabel("not a date"))
        assertEquals("TBD, soon", monthLabelFromDateLabel("TBD, soon"))
    }

    @Test
    fun `a word that merely starts like a month is not read as that month`() {
        // Regression: a three-letter PREFIX match fabricated a month out of any
        // lookalike word paired with a plausible year — "Maybe, 2026" grouped
        // under an invented "May 2026" header, silently collapsing unrelated
        // unreadable rows together. Only exact month tokens may match.
        assertEquals("Maybe, 2026", monthLabelFromDateLabel("Maybe, 2026"))
        assertEquals("Mars 3, 2026", monthLabelFromDateLabel("Mars 3, 2026"))
        assertEquals("Augustine, 2026", monthLabelFromDateLabel("Augustine, 2026"))
        assertEquals("Sat, Marching 16, 2026", monthLabelFromDateLabel("Sat, Marching 16, 2026"))
    }

    @Test
    fun `an ISO label degrades to the raw label`() {
        // "yyyy-MM-dd" is tolerated by the parser but carries no ", " separator,
        // so it groups under itself rather than being mis-read as a month.
        assertEquals("2026-05-16", monthLabelFromDateLabel("2026-05-16"))
    }

    @Test
    fun `an empty label is returned unchanged`() {
        // `DbBooking.dateLabel` defaults to "" — this is reachable, not theoretical.
        assertEquals("", monthLabelFromDateLabel(""))
    }
}
