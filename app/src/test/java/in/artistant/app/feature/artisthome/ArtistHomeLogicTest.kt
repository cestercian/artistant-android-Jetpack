package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.ScoreBreakdown
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard's derivations, pinned against a FIXED clock.
 *
 * [NOW] is 2027-01-15 13:30 IST — deliberately mid-afternoon, so that the
 * exact-24h offsets these tests build from can never drift across a day
 * boundary and make a passing suite fail at midnight. Fixtures express "k days
 * ago" as `NOW - k * DAY`, which in a DST-free zone lands on the same wall clock
 * every time.
 */
class ArtistHomeLogicTest {

    private companion object {
        /** 2027-01-15T08:00:00Z == 2027-01-15 13:30 IST (a Friday). */
        const val NOW = 1_800_000_000_000L
        const val DAY = 24L * 60 * 60 * 1000
    }

    private fun booking(
        status: BookingStatus,
        clientFullName: String? = null,
        id: String = "b-${status.name}",
        fee: Int = 20_000,
        createdAt: Long = NOW,
        startIso: String? = null,
    ) = Booking(
        id = id,
        artistId = "11111111-1111-1111-1111-111111111111",
        packageIndex = 0,
        date = "Sat, May 16, 2026",
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
        createdAtEpochMs = createdAt,
        clientFullName = clientFullName,
        startDatetimeIso = startIso,
    )

    private fun quote(status: GigRequestStatus, id: String = "q-${status.name}") = StoredRequest(
        raw = GigRequest(
            id = id,
            client = "Meera",
            message = "Sangeet night, 2 hours",
            date = "Sat, May 16, 2026",
            amount = 45_000,
        ),
        status = status,
    )

    // ── Row filters ─────────────────────────────────────────────────────────

    @Test
    fun pendingConfirmBookings_filtersPendingOnly() {
        val all = listOf(
            booking(BookingStatus.PendingConfirm, "Alice", id = "a"),
            booking(BookingStatus.Confirmed, id = "b"),
            booking(BookingStatus.PendingConfirm, "Bob", id = "c"),
        )
        val pending = pendingConfirmBookings(all)
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.status == BookingStatus.PendingConfirm })
    }

    @Test
    fun artistClientDisplayName_neverFallsBackToVenue() {
        val unnamed = booking(BookingStatus.PendingConfirm, clientFullName = null)
        assertEquals("Client", artistClientDisplayName(unnamed))
        assertEquals("Priya S.", artistClientDisplayName(unnamed.copy(clientFullName = "Priya S.")))
    }

    @Test
    fun upcomingConfirmed_excludesPastShows() {
        val all = listOf(
            booking(BookingStatus.Confirmed, id = "past", startIso = "2020-01-15T18:00:00Z"),
            booking(BookingStatus.Confirmed, id = "future", startIso = "2030-06-01T18:00:00Z"),
            booking(BookingStatus.PendingConfirm, id = "pending", startIso = "2030-06-01T18:00:00Z"),
        )
        assertEquals(listOf("future"), upcomingConfirmed(all, nowEpochMs = NOW).map { it.id })
    }

    @Test
    fun upcomingConfirmed_sortsSoonestFirst() {
        val all = listOf(
            booking(BookingStatus.Confirmed, id = "later", startIso = "2027-03-01T10:00:00Z"),
            booking(BookingStatus.Confirmed, id = "sooner", startIso = "2027-01-20T10:00:00Z"),
        )
        assertEquals(listOf("sooner", "later"), upcomingConfirmed(all, nowEpochMs = NOW).map { it.id })
    }

    @Test
    fun openGigRequests_keepsOpenAndCounteredOnly() {
        val all = GigRequestStatus.entries.map { quote(it) }
        assertEquals(
            listOf(GigRequestStatus.Open, GigRequestStatus.Countered),
            openGigRequests(all).map { it.status },
        )
    }

    @Test
    fun fakeListForArtist_pendingCount() = runTest {
        val repo = FakeBookingsRepository(
            seed = listOf(
                booking(BookingStatus.PendingConfirm, id = "p1"),
                booking(BookingStatus.PendingConfirm, id = "p2"),
                booking(BookingStatus.Confirmed, id = "c1"),
            ),
        )
        assertEquals(2, pendingConfirmBookings(repo.listForArtist()).size)
    }

    // ── Hero series ─────────────────────────────────────────────────────────

    @Test
    fun bookingCountSeries_countsBookingsNotFees() {
        // Two bookings on the same day is "2", regardless of what they're worth.
        // A fee sum here would be money the app never handled.
        val series = bookingCountSeries(
            listOf(
                booking(BookingStatus.Confirmed, id = "a", fee = 90_000, createdAt = NOW),
                booking(BookingStatus.Confirmed, id = "b", fee = 1_000, createdAt = NOW),
            ),
            buckets = 7,
            nowEpochMs = NOW,
        )
        assertEquals(2, series.last())
    }

    @Test
    fun bookingCountSeries_bucketsOldestFirst() {
        val series = bookingCountSeries(
            listOf(
                booking(BookingStatus.Confirmed, id = "old", createdAt = NOW - 2 * DAY),
                booking(BookingStatus.Confirmed, id = "new", createdAt = NOW),
            ),
            buckets = 7,
            nowEpochMs = NOW,
        )
        assertEquals(listOf(0, 0, 0, 0, 1, 0, 1), series)
    }

    @Test
    fun bookingCountSeries_countsEveryStatus() {
        // A pending request is still inbound demand — the hero measures what came
        // in, not what was agreed to.
        val series = bookingCountSeries(
            listOf(
                booking(BookingStatus.PendingConfirm, id = "p"),
                booking(BookingStatus.Cancelled, id = "x"),
            ),
            buckets = 7,
            nowEpochMs = NOW,
        )
        assertEquals(2, series.last())
    }

    @Test
    fun bookingCountSeries_dropsAnythingOutsideTheWindow() {
        val series = bookingCountSeries(
            listOf(
                booking(BookingStatus.Confirmed, id = "stale", createdAt = NOW - 30 * DAY),
                booking(BookingStatus.Confirmed, id = "future", createdAt = NOW + DAY),
            ),
            buckets = 7,
            nowEpochMs = NOW,
        )
        assertEquals(7, series.size)
        assertEquals(0, series.sum())
    }

    @Test
    fun bookingCountSeries_alwaysReturnsRequestedLength() {
        EarningsRange.entries.forEach { range ->
            assertEquals(
                range.bucketCount,
                bookingCountSeries(emptyList(), range.bucketCount, NOW).size,
            )
        }
    }

    // ── Hero counts + delta ─────────────────────────────────────────────────

    @Test
    fun heroCounts_currentWindowIsSevenDaysEndingToday() {
        // Six days back is the oldest day the 7D window covers — matching the
        // chart's oldest bucket exactly, so no booking can count toward the
        // headline without a bar underneath it.
        val hero = heroCounts(
            listOf(
                booking(BookingStatus.Confirmed, id = "edge", createdAt = NOW - 6 * DAY),
                booking(BookingStatus.Confirmed, id = "outside", createdAt = NOW - 7 * DAY),
            ),
            range = EarningsRange.SevenDays,
            nowEpochMs = NOW,
        )
        assertEquals(1, hero.headline)
        assertEquals(1, hero.prior)
    }

    @Test
    fun heroCounts_priorWindowButtsAgainstCurrent() {
        val hero = heroCounts(
            listOf(
                booking(BookingStatus.Confirmed, id = "prior-edge", createdAt = NOW - 13 * DAY),
                booking(BookingStatus.Confirmed, id = "too-old", createdAt = NOW - 14 * DAY),
            ),
            range = EarningsRange.SevenDays,
            nowEpochMs = NOW,
        )
        assertEquals(0, hero.headline)
        assertEquals(1, hero.prior)
    }

    @Test
    fun heroCounts_deltaIsPercentAgainstPriorWindow() {
        val bookings = buildList {
            repeat(3) { add(booking(BookingStatus.Confirmed, id = "now$it", createdAt = NOW)) }
            repeat(4) { add(booking(BookingStatus.Confirmed, id = "then$it", createdAt = NOW - 10 * DAY)) }
        }
        val hero = heroCounts(bookings, EarningsRange.SevenDays, NOW)
        assertEquals(3, hero.headline)
        assertEquals(4, hero.prior)
        assertEquals(25, hero.deltaPercent)
        assertFalse(hero.deltaUp)
    }

    @Test
    fun heroCounts_firstEverWindowReadsAsFullyUp() {
        val hero = heroCounts(
            listOf(booking(BookingStatus.Confirmed, id = "first", createdAt = NOW)),
            EarningsRange.SevenDays,
            NOW,
        )
        assertEquals(100, hero.deltaPercent)
        assertTrue(hero.deltaUp)
    }

    @Test
    fun heroCounts_emptyDashboardHasNoDelta() {
        val hero = heroCounts(emptyList(), EarningsRange.ThirtyDays, NOW)
        assertEquals(0, hero.headline)
        assertEquals(0, hero.deltaPercent)
    }

    @Test
    fun heroCounts_allTimeCountsEverythingAndHasNoPrior() {
        val hero = heroCounts(
            listOf(
                booking(BookingStatus.Confirmed, id = "ancient", createdAt = NOW - 900 * DAY),
                booking(BookingStatus.Confirmed, id = "today", createdAt = NOW),
            ),
            EarningsRange.All,
            NOW,
        )
        assertEquals(2, hero.headline)
        assertEquals(0, hero.prior)
    }

    @Test
    fun windowLabel_countsBackFromToday() {
        assertEquals("JAN 15", windowLabel(0, NOW))
        assertEquals("JAN 13", windowLabel(2, NOW))
    }

    // ── Upcoming snapshot ───────────────────────────────────────────────────

    @Test
    fun upcomingSnapshot_saysNothingIsBookedWhenNothingIs() {
        val snapshot = upcomingSnapshot(
            listOf(booking(BookingStatus.PendingConfirm, startIso = "2030-01-01T10:00:00Z")),
            NOW,
        )
        assertEquals(0, snapshot.count)
        assertEquals("No upcoming gigs", snapshot.copy)
    }

    @Test
    fun upcomingSnapshot_todayReadsAsToday() {
        val snapshot = upcomingSnapshot(
            listOf(booking(BookingStatus.Confirmed, id = "tonight", startIso = "2027-01-15T15:00:00Z")),
            NOW,
        )
        assertEquals(1, snapshot.count)
        assertEquals("Next gig today", snapshot.copy)
    }

    @Test
    fun upcomingSnapshot_singleDayIsSingular() {
        val snapshot = upcomingSnapshot(
            listOf(booking(BookingStatus.Confirmed, id = "tomorrow", startIso = "2027-01-16T10:00:00Z")),
            NOW,
        )
        assertEquals("Next gig in 1 day", snapshot.copy)
    }

    @Test
    fun upcomingSnapshot_spreadWhenGigsStraddleDays() {
        val snapshot = upcomingSnapshot(
            listOf(
                booking(BookingStatus.Confirmed, id = "soon", startIso = "2027-01-17T10:00:00Z"),
                booking(BookingStatus.Confirmed, id = "later", startIso = "2027-01-25T10:00:00Z"),
            ),
            NOW,
        )
        assertEquals(2, snapshot.count)
        assertEquals("Spread over 10 days", snapshot.copy)
    }

    // ── 14-day strip ────────────────────────────────────────────────────────

    @Test
    fun calendarStripDays_startsTodayAndRunsForward() {
        val strip = calendarStripDays(3, NOW)
        assertEquals(listOf("2027-01-15", "2027-01-16", "2027-01-17"), strip.map { it.key })
        assertEquals(listOf("FRI", "SAT", "SUN"), strip.map { it.weekday })
        assertEquals(listOf(15, 16, 17), strip.map { it.dayOfMonth })
    }

    @Test
    fun bookedDayKeys_onlyConfirmedGigsInsideTheWindow() {
        val keys = bookedDayKeys(
            listOf(
                booking(BookingStatus.Confirmed, id = "in", startIso = "2027-01-17T10:00:00Z"),
                // Pending is not a commitment — shading it would claim a day the
                // artist hasn't agreed to yet.
                booking(BookingStatus.PendingConfirm, id = "pending", startIso = "2027-01-18T10:00:00Z"),
                booking(BookingStatus.Confirmed, id = "beyond", startIso = "2027-03-01T10:00:00Z"),
                booking(BookingStatus.Confirmed, id = "past", startIso = "2026-12-01T10:00:00Z"),
            ),
            withinDays = 14,
            nowEpochMs = NOW,
        )
        assertEquals(setOf("2027-01-17"), keys)
    }

    @Test
    fun gigDateParts_readsTheResolvedStartNotTheLabel() {
        val parts = gigDateParts(
            booking(BookingStatus.Confirmed, id = "g", startIso = "2027-01-17T10:00:00Z"),
        )
        assertEquals("17", parts.day)
        assertEquals("SUN", parts.weekday)
    }

    // ── Bookability breakdown ───────────────────────────────────────────────

    @Test
    fun breakdownRows_showNoDataUntilTheFirstGig() {
        // Every metric is 0 for an artist with no history — and Cancellations,
        // shown flipped, would read as a flawless 100%.
        val rows = breakdownRows(ScoreBreakdown.NewArtist)
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.value == null })
        assertNull(rows.first { it.label == "Cancellations" }.value)
    }

    @Test
    fun breakdownRows_flipCancellationsSoHigherIsBetter() {
        val rows = breakdownRows(
            ScoreBreakdown(
                score = 78,
                showUpRate = 96,
                reviewScore = 88,
                replySpeed = 71,
                cancellationRate = 4,
                socialProof = 50,
                totalGigs = 12,
            ),
        )
        assertEquals(96, rows.first { it.label == "Show-up rate" }.value)
        assertEquals(71, rows.first { it.label == "Reply speed" }.value)
        assertEquals(88, rows.first { it.label == "Reviews" }.value)
        assertEquals(96, rows.first { it.label == "Cancellations" }.value)
    }

    // ── Profile completeness ────────────────────────────────────────────────

    @Test
    fun profileGaps_unfinishedSetupIsTheOnlyDiscoveryBlocker() {
        val gaps = profileGaps(setupComplete = false, genre = "Indie", bio = "Bangalore four-piece")
        assertTrue(gaps.blocksDiscovery)
        assertTrue(gaps.needsWork)
        assertTrue(gaps.headline.contains("required to appear in search"))
    }

    @Test
    fun profileGaps_thinButLiveProfileStaysNeutral() {
        val gaps = profileGaps(setupComplete = true, genre = "  ", bio = null)
        assertFalse(gaps.blocksDiscovery)
        assertTrue(gaps.needsWork)
        assertFalse(gaps.headline.contains("search"))
        assertEquals("Add your genre and a short bio so clients pick you.", gaps.detail)
    }

    @Test
    fun profileGaps_completeProfileNeedsNothing() {
        val gaps = profileGaps(setupComplete = true, genre = "Indie", bio = "Bangalore four-piece")
        assertFalse(gaps.needsWork)
    }

    // ── Greeting ────────────────────────────────────────────────────────────

    @Test
    fun greetingName_usesFirstWordOfTheStageName() {
        assertEquals("Kaavya", greetingName("Kaavya Rao Collective"))
    }

    @Test
    fun greetingName_fallsBackBeforeHydration() {
        assertEquals("there", greetingName(null))
        assertEquals("there", greetingName("   "))
    }
}
