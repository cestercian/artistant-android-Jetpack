package `in`.artistant.app.feature.gigs

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.platform.calendar.CalendarSyncPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Design screens 35 / 107 / 108 / 109, and screen 36's masthead.
 *
 * Every assertion here is about a sentence the artist decides money on — what
 * they were offered, what they countered, and whether the night is already sold.
 */
class GigRequestLogicTest {

    // ── requestHeaderSubtitle ────────────────────────────────────────────────

    @Test
    fun headerSubtitle_namesTheArtistsOwnActionOnACounter() {
        // "Sent 15 minutes ago" and "You countered 2 hours ago" are the same row
        // at two moments, and only the second says whose turn it is.
        val request = request(
            status = GigRequestStatus.Countered,
            timeAgo = "4 hours ago",
            updatedAgo = "2 hours ago",
        )
        assertEquals("You countered 2 hours ago", requestHeaderSubtitle(request))
    }

    @Test
    fun headerSubtitle_fallsBackToTheSentTimeOnAnOpenRequest() {
        val request = request(timeAgo = "15 minutes ago", updatedAgo = "15 minutes ago")
        assertEquals("Sent 15 minutes ago", requestHeaderSubtitle(request))
    }

    @Test
    fun headerSubtitle_isNullRatherThanATruncatedSentence() {
        // A header reading "Sent " and stopping is worse than no header line.
        assertNull(requestHeaderSubtitle(request(timeAgo = "", updatedAgo = "")))
    }

    @Test
    fun headerSubtitle_saysExpiredWithoutNeedingATimestamp() {
        // Expiry is a fact about the row, not about anyone's action, so it does
        // not depend on `updated_at` having been readable.
        assertEquals(
            "Expired without a reply",
            requestHeaderSubtitle(request(status = GigRequestStatus.Expired, updatedAgo = "")),
        )
    }

    // ── requestStatusTone ────────────────────────────────────────────────────

    @Test
    fun statusTone_leavesAnUnknownStatusUncoloured() {
        // Not Hot. An unrecognised status is an absent verdict, not a bad one,
        // and red would invent one this build cannot read.
        assertEquals(PillTone.Neutral, requestStatusTone(GigRequestStatus.Unknown))
        assertEquals(PillTone.Hot, requestStatusTone(GigRequestStatus.Declined))
    }

    // ── clashWarning ─────────────────────────────────────────────────────────

    @Test
    fun clashWarning_isNullWhenTheNightIsFree() {
        assertNull(clashWarning(emptyList()))
    }

    @Test
    fun clashWarning_namesTheFirstClashAndCountsTheRest() {
        val warning = clashWarning(listOf(clash("Rooftop brand night"), clash("Sangeet")))
        requireNotNull(warning)
        assertTrue(warning, warning.startsWith("You already have Rooftop brand night, "))
        assertTrue(warning, warning.endsWith(" +1 more."))
    }

    @Test
    fun clashWarning_readsTheClashInIst() {
        // 14:30 UTC is 8:00 pm IST. Formatting in the device zone would tell a
        // travelling artist their evening gig is in the afternoon.
        val warning = clashWarning(listOf(clash("Rooftop brand night")))
        assertTrue(warning!!, warning.contains("8:00 PM"))
    }

    // ── negotiationHistory ───────────────────────────────────────────────────

    @Test
    fun negotiationHistory_isOneEntryUntilSomeoneCounters() {
        val entries = negotiationHistory(request())
        assertEquals(1, entries.size)
        assertEquals("They requested", entries.first().who)
    }

    @Test
    fun negotiationHistory_putsTheCounterSecond() {
        // Oldest first: the block reads as a sequence, and their number is the
        // one the artist answered.
        val entries = negotiationHistory(
            request(status = GigRequestStatus.Countered, counterAmount = 42_000),
        )
        assertEquals(listOf("They requested", "You countered"), entries.map { it.who })
        assertEquals("₹42,000", entries[1].amount)
    }

    // ── requestFacts ─────────────────────────────────────────────────────────

    @Test
    fun requestFacts_dropsWhatTheHostDidNotSay() {
        // Not em-dashes: a blank venue is a question nobody asked, and a row of
        // dashes reads as data that failed to load.
        val facts = requestFacts(request(venue = null, crowdSize = null))
        assertEquals(listOf("Date"), facts.map { it.first })
    }

    @Test
    fun requestFacts_keepsTheDesignsOrder() {
        val facts = requestFacts(request(venue = "Indiranagar lawn", crowdSize = 200))
        assertEquals(listOf("Date", "Venue", "Guests"), facts.map { it.first })
    }

    @Test
    fun requestFacts_dropsAZeroHeadcount() {
        // `crowd_size` defaults to 0 rather than null on some rows; "0 guests"
        // is a claim about the party, not a missing answer.
        assertTrue(requestFacts(request(crowdSize = 0)).none { it.first == "Guests" })
    }

    // ── shortGigDate / requestIdentityLine ───────────────────────────────────

    @Test
    fun shortGigDate_reformatsTheStoredLabel() {
        assertEquals("Sat 12 Sep", shortGigDate("Sat, Sep 12, 2026"))
    }

    @Test
    fun shortGigDate_returnsAnUnparseableLabelUnchanged() {
        // A date we can't reformat is still a date the artist can read.
        assertEquals("TBD", shortGigDate("TBD"))
    }

    @Test
    fun requestIdentityLine_dropsTheCustomPackagePlaceholder() {
        // "Custom" is what the column holds when the host picked no package; it
        // is not the name of a set.
        val line = requestIdentityLine(request(packageLabel = "Custom", crowdSize = 200))
        assertTrue(line, line.startsWith("200 guests"))
    }

    // ── gigsMonthSummary (screen 36) ─────────────────────────────────────────

    @Test
    fun gigsMonthSummary_countsOnlyTheDisplayedMonth() {
        // Stepping the calendar has to move the subtitle with it; a total that
        // stayed put while the grid changed would read as an app-wide figure.
        val gigs = listOf(
            gig("a", 2026, 8, 12, fee = 36_000),
            gig("b", 2026, 8, 20, fee = 24_000),
            gig("c", 2026, 9, 3, fee = 90_000),
        )
        assertEquals("2 gigs this month · ₹60,000 booked", gigsMonthSummary(gigs, 2026, 8))
        assertEquals("1 gig this month · ₹90,000 booked", gigsMonthSummary(gigs, 2026, 9))
    }

    @Test
    fun gigsMonthSummary_saysNothingRatherThanZeroGigs() {
        assertEquals("Nothing this month", gigsMonthSummary(emptyList(), 2026, 8))
    }

    @Test
    fun gigsMonthSummary_omitsTheMoneyHalfWhenThereIsNone() {
        // A free gig is legitimate; "· ₹0 booked" is not worth the clause.
        val gigs = listOf(gig("a", 2026, 8, 12, fee = 0))
        assertEquals("1 gig this month", gigsMonthSummary(gigs, 2026, 8))
    }

    @Test
    fun gigsMonthSummary_ignoresAGigWithAnUnreadableDate() {
        // Such a row has null year/month/day and cannot be placed on the grid;
        // counting it in the month total would make the subtitle disagree with
        // every tile below it.
        val gigs = listOf(ArtistGigListItem(booking("x", 50_000), "Client"))
        assertEquals("Nothing this month", gigsMonthSummary(gigs, 2026, 8))
    }

    // ── dayHeadingCount ──────────────────────────────────────────────────────

    @Test
    fun dayHeadingCount_singularsAndSaysNothingOnAnEmptyDay() {
        assertEquals("nothing on", dayHeadingCount(0))
        assertEquals("1 gig", dayHeadingCount(1))
        assertEquals("3 gigs", dayHeadingCount(3))
    }

    // ── initialDayFor (screen 36's opening selection) ────────────────────────

    @Test
    fun initialDayFor_picksTodayWhenTodayIsOnScreen() {
        val now = ist(2026, Calendar.SEPTEMBER, 4)
        assertEquals(4, initialDayFor(2026, 8, busyDays = setOf(12, 20), nowMs = now))
    }

    @Test
    fun initialDayFor_picksTheMonthsFirstGigWhenSteppedAway() {
        // Stepping into October should land on the gig, not on an arbitrary 1st.
        val now = ist(2026, Calendar.SEPTEMBER, 4)
        assertEquals(12, initialDayFor(2026, 9, busyDays = setOf(20, 12), nowMs = now))
    }

    @Test
    fun initialDayFor_fallsBackToTheFirstOfAnEmptyMonth() {
        val now = ist(2026, Calendar.SEPTEMBER, 4)
        assertEquals(1, initialDayFor(2027, 2, busyDays = emptySet(), nowMs = now))
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun ist(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            clear()
            set(year, month, day, 12, 0)
        }.timeInMillis

    /** 14:30 UTC on 12 Sep 2026 — 8:00 pm IST, the design's own gig time. */
    private fun clash(title: String) = CalendarSyncPlanner.Clash(
        bookingId = "b-$title",
        title = title,
        startEpochMs = 1_789_309_800_000L,
        endEpochMs = 1_789_320_600_000L,
    )

    private fun request(
        status: GigRequestStatus = GigRequestStatus.Open,
        amount: Int = 38_000,
        counterAmount: Int? = null,
        timeAgo: String = "15 minutes ago",
        updatedAgo: String = "15 minutes ago",
        venue: String? = "Indiranagar lawn",
        crowdSize: Int? = 200,
        packageLabel: String = "Full band",
    ) = StoredRequest(
        raw = GigRequest(
            id = "r1",
            client = null,
            message = "Lawn set-up, power is 20 m from the stage.",
            date = "Sat, Sep 12, 2026",
            amount = amount,
            packageLabel = packageLabel,
            timeAgo = timeAgo,
            venue = venue,
            crowdSize = crowdSize,
            updatedAgo = updatedAgo,
        ),
        status = status,
        counterAmount = counterAmount,
    )

    private fun gig(id: String, year: Int, month: Int, day: Int, fee: Int) = ArtistGigListItem(
        booking = booking(id, fee),
        clientName = "Client",
        year = year,
        month = month,
        dayOfMonth = day,
    )

    private fun booking(id: String, fee: Int) = Booking(
        id = id,
        artistId = "11111111-1111-1111-1111-111111111111",
        packageIndex = 0,
        date = "Sat, Sep 12, 2026",
        time = "8:00 PM",
        venue = "Indiranagar lawn",
        guests = 200,
        fee = fee,
        platformFee = 0,
        gst = 0,
        total = fee,
        status = BookingStatus.Confirmed,
        escrowStatus = EscrowStatus.Held,
        paymentMethod = PaymentMethod.Upi,
        protectionEnabled = true,
        createdAtEpochMs = 0L,
        packageName = "Full band",
        clientFullName = "Client",
        startDatetimeIso = null,
    )
}
