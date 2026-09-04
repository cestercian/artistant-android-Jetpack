package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The money-first dashboard's derivations (screens 09 / 85 / 86 / 133), pinned
 * against a fixed clock.
 *
 * [NOW] is 2027-01-15 13:30 IST — mid-month and mid-afternoon, which matters for
 * two different reasons. Mid-MONTH means the current-month window has both a
 * "played" and an "ahead" side to it, so `monthMoney`'s split is actually
 * exercised rather than degenerating. Mid-AFTERNOON means the ±24h offsets these
 * fixtures are built from can't drift across a day boundary and make a green
 * suite fail at midnight.
 */
class ArtistStudioLogicTest {

    private companion object {
        /** 2027-01-15T08:00:00Z == 2027-01-15 13:30 IST (a Friday). */
        const val NOW = 1_800_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }

    private fun booking(
        id: String,
        status: BookingStatus = BookingStatus.Confirmed,
        fee: Int = 20_000,
        startIso: String? = null,
        packageName: String? = null,
        clientFullName: String? = null,
    ) = Booking(
        id = id,
        artistId = "11111111-1111-1111-1111-111111111111",
        packageIndex = 0,
        // Deliberately unparseable-as-the-real-date: every fixture drives its
        // date through `start_datetime`, so a test that accidentally leaned on
        // the label would produce a wrong answer rather than a coincidental one.
        date = "TBD",
        time = "8:30 PM",
        venue = "TBD",
        guests = 80,
        fee = fee,
        platformFee = 1000,
        gst = 3780,
        total = 24780,
        status = status,
        escrowStatus = EscrowStatus.Held,
        paymentMethod = PaymentMethod.Upi,
        protectionEnabled = true,
        createdAtEpochMs = NOW,
        packageName = packageName,
        clientFullName = clientFullName,
        startDatetimeIso = startIso,
    )

    /** An ISO instant [daysFromNow] out, in the shape PostgREST emits. */
    private fun iso(daysFromNow: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(NOW + daysFromNow * DAY))
    }

    // ── monthMoney (screen 09's card) ────────────────────────────────────────

    @Test
    fun monthMoney_splitsPlayedFromAhead() {
        val money = monthMoney(
            listOf(
                booking("played-1", fee = 30_000, startIso = iso(-5)),
                booking("played-2", fee = 20_000, startIso = iso(-2)),
                booking("ahead-1", fee = 40_000, startIso = iso(+4)),
            ),
            NOW,
        )
        assertEquals(50_000, money.playedInr)
        assertEquals(2, money.showsPlayed)
        assertEquals(40_000, money.aheadInr)
        assertEquals(1, money.gigsAhead)
        assertFalse(money.isEmpty)
    }

    @Test
    fun monthMoney_excludesPendingConfirm() {
        // The whole point: `pending_confirm` is a number a stranger ASKED for.
        // Counting it would mean an artist's earnings drop when they decline.
        val money = monthMoney(
            listOf(booking("asked", status = BookingStatus.PendingConfirm, startIso = iso(-1))),
            NOW,
        )
        assertEquals(0, money.playedInr)
        assertTrue(money.isEmpty)
    }

    @Test
    fun monthMoney_excludesCancelledAndUnknown() {
        val money = monthMoney(
            listOf(
                booking("x", status = BookingStatus.Cancelled, startIso = iso(-1)),
                booking("y", status = BookingStatus.Unknown, startIso = iso(-1)),
            ),
            NOW,
        )
        assertTrue(money.isEmpty)
    }

    @Test
    fun monthMoney_ignoresOtherMonths() {
        // 40 days back and 40 days forward are both outside January.
        val money = monthMoney(
            listOf(
                booking("last-month", fee = 99_000, startIso = iso(-40)),
                booking("next-month", fee = 99_000, startIso = iso(+40)),
            ),
            NOW,
        )
        assertTrue(money.isEmpty)
    }

    @Test
    fun isPlayed_trustsCompletedOverAnUnreadableDate() {
        // `date` is "TBD" and there is no start ISO, so nothing can date this
        // row — but the server called it completed, and that outranks a parse.
        val row = booking("done", status = BookingStatus.Completed)
        assertTrue(isPlayed(row, NOW))
    }

    // ── earningsSummary (screen 133) ─────────────────────────────────────────

    @Test
    fun earnings_thisYearSumsAgreedFeesOnly() {
        val summary = earningsSummary(
            listOf(
                booking("a", fee = 36_000, startIso = iso(-3)),
                booking("b", fee = 68_000, startIso = iso(-30)),
                booking("pending", status = BookingStatus.PendingConfirm, fee = 100_000, startIso = iso(-3)),
                booking("cancelled", status = BookingStatus.Cancelled, fee = 100_000, startIso = iso(-3)),
            ),
            EarningsWindow.ThisYear,
            NOW,
        )
        // Jan 15: only the 3-days-back gig is inside this calendar year.
        assertEquals(36_000, summary.totalInr)
        assertEquals(1, summary.gigCount)
    }

    @Test
    fun earnings_thirtyDaysWindowExcludesOlderGigs() {
        val summary = earningsSummary(
            listOf(
                booking("in", fee = 10_000, startIso = iso(-10)),
                booking("out", fee = 90_000, startIso = iso(-45)),
            ),
            EarningsWindow.ThirtyDays,
            NOW,
        )
        assertEquals(10_000, summary.totalInr)
    }

    @Test
    fun earnings_allTimeHasNoDeltaAndNoLowerBound() {
        val summary = earningsSummary(
            listOf(
                booking("old", fee = 10_000, startIso = iso(-400)),
                booking("new", fee = 15_000, startIso = iso(-1)),
            ),
            EarningsWindow.AllTime,
            NOW,
        )
        assertEquals(25_000, summary.totalInr)
        assertNull("All time has no comparable prior window", summary.deltaPercent)
    }

    @Test
    fun earnings_deltaIsNullWhenThePriorWindowEarnedNothing() {
        val summary = earningsSummary(
            listOf(booking("only", fee = 10_000, startIso = iso(-2))),
            EarningsWindow.ThirtyDays,
            NOW,
        )
        assertNull("+100% against nothing is not a comparison", summary.deltaPercent)
    }

    @Test
    fun earnings_deltaComparesAgainstTheImmediatelyPriorWindow() {
        val summary = earningsSummary(
            listOf(
                booking("now", fee = 12_000, startIso = iso(-2)),
                booking("prior", fee = 10_000, startIso = iso(-40)),
            ),
            EarningsWindow.ThirtyDays,
            NOW,
        )
        assertEquals(20, summary.deltaPercent)
        assertTrue(summary.deltaUp)
    }

    @Test
    fun earnings_barsAreTwelveMonthsWithTheLastQuarterAccented() {
        val summary = earningsSummary(emptyList(), EarningsWindow.AllTime, NOW)
        assertEquals(12, summary.bars.size)
        assertEquals(3, summary.bars.count { it.recent })
        assertTrue("The last three months are the recent ones", summary.bars.takeLast(3).all { it.recent })
        assertFalse("A chart of zeros is not a chart", summary.hasChart)
    }

    @Test
    fun earnings_barsBucketByGigDateNotByCreation() {
        // Both rows were CREATED at NOW (the fixture's default). If the chart
        // bucketed by creation they'd land in the same column.
        val summary = earningsSummary(
            listOf(
                booking("jan", fee = 10_000, startIso = iso(-2)),
                booking("dec", fee = 20_000, startIso = iso(-40)),
            ),
            EarningsWindow.AllTime,
            NOW,
        )
        val nonEmpty = summary.bars.filter { it.amountInr > 0 }
        assertEquals(2, nonEmpty.size)
        assertTrue(summary.hasChart)
    }

    @Test
    fun earnings_rowsAreNewestFirstAndNameTheirState() {
        val summary = earningsSummary(
            listOf(
                booking("older", fee = 1_000, startIso = iso(-9)),
                booking("newest", fee = 2_000, startIso = iso(+2)),
            ),
            EarningsWindow.AllTime,
            NOW,
        )
        assertEquals(listOf("newest", "older"), summary.rows.map { it.bookingId })
        assertEquals("Agreed", summary.rows[0].state)
        assertEquals("Played", summary.rows[1].state)
    }

    @Test
    fun earningsRowTitle_neverInventsAnIdentity() {
        assertEquals("Sangeet · Rhea", earningsRowTitle(booking("a", packageName = "Sangeet", clientFullName = "Rhea")))
        assertEquals("Rhea", earningsRowTitle(booking("b", clientFullName = "Rhea")))
        assertEquals("Sangeet", earningsRowTitle(booking("c", packageName = "Sangeet")))
        // "Custom" is the default package label, not a name for anything, and
        // the venue defaults to the literal "TBD" — neither may become a title.
        assertEquals("Gig", earningsRowTitle(booking("d", packageName = "Custom")))
    }

    // ── scoreDelta (screen 09's "+4") ────────────────────────────────────────

    @Test
    fun scoreDelta_isNullWithNoHistory() {
        assertNull(scoreDelta(emptyList(), currentScore = 86, nowEpochMs = NOW))
    }

    @Test
    fun scoreDelta_isNullWhenHistoryStartsInsideTheWindow() {
        // One reading, five days old. There is no "a month ago" to compare to,
        // and a confident "+0" would be a trend invented from a single point.
        val history = listOf(ScoreHistoryPoint(score = 82, computedAtIso = isoInstant(-5)))
        assertNull(scoreDelta(history, currentScore = 86, nowEpochMs = NOW))
    }

    @Test
    fun scoreDelta_usesTheLatestReadingAtOrBeforeTheCutoff() {
        val history = listOf(
            ScoreHistoryPoint(score = 50, computedAtIso = isoInstant(-300)), // ancient
            ScoreHistoryPoint(score = 82, computedAtIso = isoInstant(-45)), // the baseline
            ScoreHistoryPoint(score = 85, computedAtIso = isoInstant(-10)), // inside window
        )
        assertEquals(4, scoreDelta(history, currentScore = 86, nowEpochMs = NOW))
    }

    @Test
    fun scoreDelta_canBeNegative() {
        val history = listOf(ScoreHistoryPoint(score = 90, computedAtIso = isoInstant(-40)))
        assertEquals(-4, scoreDelta(history, currentScore = 86, nowEpochMs = NOW))
    }

    private fun isoInstant(daysFromNow: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(NOW + daysFromNow * DAY))
    }

    // ── dashboardMode (which of 09 / 85 / 86) ────────────────────────────────

    private val noMoney = MonthMoney(0, 0, 0, 0)

    @Test
    fun dashboardMode_failedFirstLoadIsUnavailableNotCold() {
        // The load-bearing case. Everything is zero here for the same reason
        // screen 86 exists: nothing arrived. Reporting Cold would print "all 14
        // days open" over a calendar nobody has read — the failure that gets an
        // artist double-booked.
        val mode = dashboardMode(
            hasLoaded = false,
            hasError = true,
            money = noMoney,
            openRequests = 0,
            upcomingGigs = 0,
            bookings7d = 0,
        )
        assertEquals(DashboardMode.Unavailable, mode)
    }

    @Test
    fun dashboardMode_loadedAndEmptyIsCold() {
        val mode = dashboardMode(
            hasLoaded = true,
            hasError = false,
            money = noMoney,
            openRequests = 0,
            upcomingGigs = 0,
            bookings7d = 0,
        )
        assertEquals(DashboardMode.Cold, mode)
    }

    @Test
    fun dashboardMode_staleRefreshOverLiveDataStaysReady() {
        // A refresh that failed AFTER a good load must not blank a working
        // dashboard — the banner says so, the body keeps its data.
        val mode = dashboardMode(
            hasLoaded = true,
            hasError = true,
            money = MonthMoney(50_000, 2, 0, 0),
            openRequests = 1,
            upcomingGigs = 1,
            bookings7d = 2,
        )
        assertEquals(DashboardMode.Ready, mode)
    }

    @Test
    fun dashboardMode_anySingleSignalIsEnoughToBeReady() {
        listOf(
            dashboardMode(true, false, noMoney, openRequests = 1, upcomingGigs = 0, bookings7d = 0),
            dashboardMode(true, false, noMoney, openRequests = 0, upcomingGigs = 1, bookings7d = 0),
            dashboardMode(true, false, noMoney, openRequests = 0, upcomingGigs = 0, bookings7d = 1),
            dashboardMode(true, false, MonthMoney(0, 0, 5_000, 1), 0, 0, 0),
        ).forEach { assertEquals(DashboardMode.Ready, it) }
    }

    @Test
    fun dashboardSubtitle_namesTheState() {
        assertEquals("Some of this is out of date", dashboardSubtitle(DashboardMode.Unavailable, "The Tilt Collective"))
        assertEquals("Nothing needs you right now", dashboardSubtitle(DashboardMode.Cold, "The Tilt Collective"))
        assertEquals("The Tilt Collective", dashboardSubtitle(DashboardMode.Ready, "The Tilt Collective"))
        assertEquals("Your studio", dashboardSubtitle(DashboardMode.Ready, "  "))
    }

    // ── stripOpenDaysCopy (screen 85's line) ─────────────────────────────────

    @Test
    fun stripOpenDaysCopy_saysAllOpenWhenNothingIsBooked() {
        assertEquals(
            "All 14 days open — you'll show up in every search for them.",
            stripOpenDaysCopy(totalDays = 14, bookedDays = 0),
        )
    }

    @Test
    fun stripOpenDaysCopy_countsThePartialCase() {
        assertEquals(
            "11 of 14 days open — you'll show up in every search for those.",
            stripOpenDaysCopy(totalDays = 14, bookedDays = 3),
        )
    }

    @Test
    fun stripOpenDaysCopy_handlesAFullFortnight() {
        assertEquals(
            "Every day in the next 14 is spoken for.",
            stripOpenDaysCopy(totalDays = 14, bookedDays = 14),
        )
    }
}
