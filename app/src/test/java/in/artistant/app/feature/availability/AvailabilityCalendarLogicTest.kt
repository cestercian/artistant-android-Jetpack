package `in`.artistant.app.feature.availability

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * The month grid behind design screen 22.
 *
 * Calendars are where off-by-one bugs live, and this one carries a booked-night
 * shading that an artist plans around — so the awkward months (a Monday start, a
 * Sunday start, a leap February) get asserted rather than eyeballed.
 */
class AvailabilityCalendarLogicTest {

    // ── busyDaysIn ───────────────────────────────────────────────────────────

    @Test
    fun busyDaysIn_rebasesToTheGridsZeroBasedMonth() {
        // MonthDayGrid speaks Calendar.MONTH; LocalDate speaks 1-12. Getting
        // this backwards silently shades the wrong month, which looks fine.
        val dates = setOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 30))
        assertEquals(setOf(12, 30), busyDaysIn(dates, year = 2026, month = 8))
        assertEquals(emptySet<Int>(), busyDaysIn(dates, year = 2026, month = 9))
    }

    @Test
    fun busyDaysIn_keepsTheYearsApart() {
        // Two Septembers a year apart both answer "day 12" — the year has to be
        // part of the filter or a 2027 gig shades a 2026 tile.
        val dates = setOf(LocalDate.of(2027, 9, 12))
        assertEquals(emptySet<Int>(), busyDaysIn(dates, year = 2026, month = 8))
    }

    // ── closedDaysIn ─────────────────────────────────────────────────────────

    @Test
    fun closedDaysIn_greysTheWeekdaysTheActDoesNotPlay() {
        // Sep 2026: Fridays are 4, 11, 18, 25; Saturdays 5, 12, 19, 26.
        val closed = closedDaysIn(year = 2026, month = 8, days = setOf("Fri", "Sat"))
        assertFalse(closed.contains(4))
        assertFalse(closed.contains(26))
        assertTrue(closed.contains(1))
        assertEquals(30 - 8, closed.size)
    }

    @Test
    fun closedDaysIn_greysNothingWhenNoDaysArePicked() {
        // The opposite reading — an all-grey month — would say the artist is
        // unavailable forever, when in fact they just haven't answered yet. The
        // summary line says that in words instead.
        assertEquals(emptySet<Int>(), closedDaysIn(year = 2026, month = 8, days = emptySet()))
    }

    @Test
    fun closedDaysIn_coversAllOfALeapFebruary() {
        val closed = closedDaysIn(year = 2028, month = 1, days = setOf("Mon"))
        assertTrue(closed.contains(29))
        assertEquals(29 - 4, closed.size)
    }

    // ── playsOn ──────────────────────────────────────────────────────────────

    @Test
    fun playsOn_matchesTheWizardsShortNames() {
        // 5 Sep 2026 is a Saturday.
        assertTrue(playsOn(LocalDate.of(2026, 9, 5), setOf("Fri", "Sat")))
        assertFalse(playsOn(LocalDate.of(2026, 9, 5), setOf("Mon", "Tue")))
    }

    @Test
    fun playsOn_isLocaleIndependent() {
        // A phone in a non-English locale must not grey out the artist's whole
        // calendar: `days` holds the English names the wizard wrote.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("hi-IN"))
            assertTrue(playsOn(LocalDate.of(2026, 9, 5), setOf("Sat")))
        } finally {
            Locale.setDefault(original)
        }
    }

    // ── bookedDates ──────────────────────────────────────────────────────────

    @Test
    fun bookedDates_takesConfirmedOnly() {
        val dates = bookedDates(
            listOf(
                booking("a", BookingStatus.Confirmed, "2026-09-12T14:30:00Z"),
                booking("b", BookingStatus.PendingConfirm, "2026-09-13T14:30:00Z"),
                booking("c", BookingStatus.Cancelled, "2026-09-14T14:30:00Z"),
            ),
        )
        assertEquals(setOf(LocalDate.of(2026, 9, 12)), dates)
    }

    @Test
    fun bookedDates_putsALateNightGigOnTheNightItStarts() {
        // 20:00 UTC is 01:30 IST the NEXT day. A gig that starts at half past
        // one in the morning belongs to the night the artist thinks of it as,
        // and IST is where that decision gets made.
        val dates = bookedDates(listOf(booking("a", BookingStatus.Confirmed, "2026-09-12T20:00:00Z")))
        assertEquals(setOf(LocalDate.of(2026, 9, 13)), dates)
    }

    @Test
    fun bookedDates_ignoresABookingWithNoResolvableStart() {
        assertEquals(emptySet<LocalDate>(), bookedDates(listOf(booking("a", BookingStatus.Confirmed, null))))
    }

    // ── monthSummary ─────────────────────────────────────────────────────────

    @Test
    fun monthSummary_leadsWithTheMissingWeekdaysWhenThereAreNone() {
        // With no weekdays picked the booked count is beside the point: the
        // artist is invisible, and that is the sentence worth spending.
        assertTrue(monthSummary(bookedInMonth = 3, openWeekdays = 0).startsWith("No weekdays picked"))
    }

    @Test
    fun monthSummary_singularsBothHalves() {
        assertEquals("1 night booked · you play 1 day a week", monthSummary(1, 1))
    }

    @Test
    fun monthSummary_saysNothingBookedRatherThanZero() {
        assertEquals("Nothing booked this month · you play 2 days a week", monthSummary(0, 2))
    }

    private fun booking(id: String, status: BookingStatus, startIso: String?) = Booking(
        id = id,
        artistId = "11111111-1111-1111-1111-111111111111",
        packageIndex = 0,
        date = "TBD",
        time = "8:30 PM",
        venue = "TBD",
        guests = 80,
        fee = 20_000,
        platformFee = 1000,
        gst = 3780,
        total = 24_780,
        status = status,
        escrowStatus = EscrowStatus.Held,
        paymentMethod = PaymentMethod.Upi,
        protectionEnabled = true,
        createdAtEpochMs = 0L,
        packageName = null,
        clientFullName = null,
        startDatetimeIso = startIso,
    )
}
