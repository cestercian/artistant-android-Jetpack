package `in`.artistant.app.feature.bookings

import `in`.artistant.app.data.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.IOException
import java.net.UnknownHostException
import java.util.Calendar
import java.util.TimeZone
import org.junit.Test

/** The Bookings list's decisions: segment, affordance, countdown, failure kind. */
class BookingsLogicTest {

    private fun ist(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    // --- affordance ----------------------------------------------------------

    @Test
    fun eachStatusGetsItsOwnAffordance() {
        assertEquals(BookingAffordance.Confirmed, affordanceFor(BookingStatus.Confirmed))
        assertEquals(BookingAffordance.Awaiting, affordanceFor(BookingStatus.PendingConfirm))
        assertEquals(BookingAffordance.Review, affordanceFor(BookingStatus.Completed))
        assertEquals(BookingAffordance.Ended, affordanceFor(BookingStatus.Cancelled))
        assertEquals(BookingAffordance.Ended, affordanceFor(BookingStatus.Disputed))
    }

    @Test
    fun aStatusThisBuildCannotReadOffersNothing() {
        // Same rule as `BookingStatus.isActionable`: a booking we cannot reason
        // about must never be handed a control.
        assertEquals(BookingAffordance.Ended, affordanceFor(BookingStatus.Unknown))
    }

    // --- the segment ---------------------------------------------------------

    @Test
    fun aGigIsUpcomingUntilItENDS_notUntilItStarts() {
        // 8pm show, 10pm end. At 8:30pm the client is most likely to be opening
        // this screen, and the gig must not have moved to Past under them.
        val start = ist(2026, Calendar.OCTOBER, 12, 20)
        val end = ist(2026, Calendar.OCTOBER, 12, 22)
        val during = ist(2026, Calendar.OCTOBER, 12, 20, 30)
        val after = ist(2026, Calendar.OCTOBER, 12, 23)

        assertTrue(isUpcoming(BookingStatus.Confirmed, start, end, during))
        assertFalse(isUpcoming(BookingStatus.Confirmed, start, end, after))
    }

    @Test
    fun aGigWithNoEndTimeGetsTheSameTwoHoursTheCreatePathWrites() {
        val start = ist(2026, Calendar.OCTOBER, 12, 20)
        assertTrue(
            isUpcoming(BookingStatus.Confirmed, start, null, ist(2026, Calendar.OCTOBER, 12, 21)),
        )
        assertFalse(
            isUpcoming(BookingStatus.Confirmed, start, null, ist(2026, Calendar.OCTOBER, 12, 23)),
        )
    }

    @Test
    fun terminalStatusesArePastWhateverTheirDateSays() {
        val future = ist(2027, Calendar.JANUARY, 1)
        val now = ist(2026, Calendar.OCTOBER, 12)
        assertFalse(isUpcoming(BookingStatus.Cancelled, future, null, now))
        assertFalse(isUpcoming(BookingStatus.Completed, future, null, now))
        assertFalse(isUpcoming(BookingStatus.Disputed, future, null, now))
    }

    @Test
    fun aBookingWithNoReadableClockStaysVisible() {
        // Hiding a booking is worse than listing it in the wrong half.
        assertTrue(isUpcoming(BookingStatus.Confirmed, null, null, ist(2026, Calendar.OCTOBER, 12)))
    }

    // --- the countdown badge -------------------------------------------------

    @Test
    fun theCountdownCountsCalendarDaysInIndia_notElapsedHours() {
        // 23:00 tonight to 01:00 tomorrow is two hours and one sleep. "Tomorrow"
        // is the only honest answer, and an hours-based count would say "Today".
        val now = ist(2026, Calendar.OCTOBER, 12, 23)
        assertEquals("Tomorrow", countdownBadge(ist(2026, Calendar.OCTOBER, 13, 1), now))
        assertEquals("Today", countdownBadge(ist(2026, Calendar.OCTOBER, 12, 23, 30), now))
        assertEquals("In 3 days", countdownBadge(ist(2026, Calendar.OCTOBER, 15, 1), now))
    }

    @Test
    fun theBadgeIsSpentOnlyOnGigsThatAreClose() {
        val now = ist(2026, Calendar.OCTOBER, 12)
        assertNull(countdownBadge(ist(2027, Calendar.JANUARY, 12), now))
        assertNull(countdownBadge(ist(2026, Calendar.OCTOBER, 11), now))
        assertNull(countdownBadge(null, now))
    }

    @Test
    fun daysUntilIsNegativeForAGigAlreadyPlayed() {
        val now = ist(2026, Calendar.OCTOBER, 12)
        assertEquals(-3, daysUntilGig(ist(2026, Calendar.OCTOBER, 9), now))
        assertEquals(24, daysUntilGig(ist(2026, Calendar.NOVEMBER, 5), now))
    }

    // --- the lines -----------------------------------------------------------

    @Test
    fun datesRenderTheWayTheDesignPrintsThem() {
        assertEquals("Sat 16 May", compactDate("Sat, May 16, 2026"))
        assertEquals("16 May", bareDate("Sat, May 16, 2026"))
    }

    @Test
    fun aDateWeCannotParseIsPrintedWhole() {
        // A wrong date reads as a data bug; an unfamiliar format reads as
        // somebody else's data. Only one of those is our fault.
        assertEquals("TBD", compactDate("TBD"))
        assertEquals("next Tuesday", bareDate(" next Tuesday "))
        // The ISO form IS one of `BookingDateFormat`'s patterns, so it reads.
        assertEquals("Sat 16 May", compactDate("2026-05-16"))
    }

    @Test
    fun blankPartsAreDroppedRatherThanJoined() {
        assertEquals(
            "Sat 16 May · 8:30 pm · Rooftop",
            whenAndWhereLine("Sat, May 16, 2026", "8:30 PM", "Rooftop"),
        )
        assertEquals("Sat 16 May · 8:30 pm", whenAndWhereLine("Sat, May 16, 2026", "8:30 PM", "  "))
        assertEquals("Sat 16 May", whenAndWhereLine("Sat, May 16, 2026", "", ""))
    }

    @Test
    fun theSecondLineNamesTheActAndTheDate() {
        assertEquals("Techno DJ · Sat 16 May", categoryAndDateLine("Techno DJ", "Sat, May 16, 2026"))
        assertEquals("Techno DJ · Played 16 May", playedLine("Techno DJ", "Sat, May 16, 2026"))
        // An artist whose category never loaded leaves the line to the date.
        assertEquals("Sat 16 May", categoryAndDateLine("", "Sat, May 16, 2026"))
    }

    // --- which failure it was ------------------------------------------------

    @Test
    fun onlyANetworkFailureMakesTheScreenSayOffline() {
        assertTrue(isConnectivityFailure(UnknownHostException("no dns")))
        assertTrue(isConnectivityFailure(RuntimeException("wrapped", IOException("socket"))))
        assertFalse(isConnectivityFailure(IllegalStateException("row-level security")))
        assertFalse(isConnectivityFailure(null))
    }

    @Test
    fun aCyclicCauseChainCannotHangTheCheck() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertFalse(isConnectivityFailure(a))
    }

    // --- the snapshot codec --------------------------------------------------

    @Test
    fun theSnapshotSurvivesARoundTrip() {
        val snapshot = BookingsSnapshot(
            cachedAtMs = 1_700_000_000_000L,
            items = listOf(
                CachedBooking(
                    id = "b-1",
                    artistName = "The Tilt Collective",
                    status = "confirmed",
                    date = "Sat, Oct 12, 2026",
                    time = "8:00 PM",
                    venue = "12th Main",
                    venueNotes = "Gate 3, load-in from the lane",
                ),
            ),
        )

        assertEquals(snapshot, decodeSnapshot(encodeSnapshot(snapshot)))
    }

    @Test
    fun anUnreadableSnapshotIsNoSnapshot() {
        // A corrupt cache must degrade to "we have nothing", which is a state the
        // screen already draws, and never to a crash on the Bookings tab.
        assertNull(decodeSnapshot(null))
        assertNull(decodeSnapshot(""))
        assertNull(decodeSnapshot("{ not json"))
        assertNull(decodeSnapshot("""{"items":[]}"""))
    }

    @Test
    fun aCachedStatusThisBuildCannotReadDecodesAsUnknown() {
        // A snapshot written by a newer build must still open. `Unknown` is
        // exactly how the offline screen renders anything it cannot vouch for.
        assertEquals(BookingStatus.Unknown, BookingStatus.fromDb("refunded"))
    }

    @Test
    fun theCacheStampNamesTheClockTimeItWasWritten() {
        val at = ist(2026, Calendar.OCTOBER, 12, 9, 4)
        // Formatted in the device zone, which is where the person reading it is.
        assertTrue(cachedAtLabel(at).startsWith("Cached "))
        assertTrue(cachedAtLabel(at).endsWith("am") || cachedAtLabel(at).endsWith("pm"))
    }
}
