package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.ScoreHistoryPoint
import `in`.artistant.app.data.model.SelfArtistRow
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.FakeRequestsRepository
import `in`.artistant.app.data.repository.FakeScoreRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.platform.upload.UploadBannerSource
import `in`.artistant.app.platform.upload.UploadBannerState
import `in`.artistant.app.state.DeepLinkRouter
import `in`.artistant.app.state.RequestStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Covers the dashboard's parallel load (greeting + bookings + score + requests via
 * fakes), the honest error surface when one load throws, and the pure earnings /
 * availability derivations that drive the hero + strip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistHomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val zone: ZoneId = ZoneId.systemDefault()
    private val labelFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

    private fun booking(
        id: String,
        createdDaysAgo: Int = 0,
        dateLabel: String = "Sat, May 16, 2026",
        status: BookingStatus = BookingStatus.Confirmed,
        today: LocalDate = LocalDate.now(),
        total: Int = 12_390,
    ) = Booking(
        id = id, artistId = "a1", packageIndex = 0, dateLabel = dateLabel, timeLabel = "8:30 PM",
        startDatetime = null, endDatetime = null, venue = "V", guests = 100, fee = 10_000,
        platformFee = 500, gst = 1_890, total = total, status = status, escrowStatus = EscrowStatus.Held,
        paymentMethod = PaymentMethod.Upi, protectionEnabled = true,
        createdAt = today.minusDays(createdDaysAgo.toLong()).atStartOfDay(zone).toInstant(),
    )

    private fun selfRow(stage: String) = SelfArtistRow(
        stageName = stage, handle = "h", category = "DJ", baseCity = "Goa", genre = null, bio = null,
        coverGradientIndex = 0, published = true, setupComplete = true,
        instagramHandle = null, spotifyArtistUrl = null, youtubeChannelUrl = null,
    )

    private val idleBanner = object : UploadBannerSource {
        override fun bannerStateFlow(): Flow<UploadBannerState> = flowOf(UploadBannerState())
        override fun clearFinished() {}
    }

    private fun request(id: String) = StoredRequest(
        raw = GigRequest(id = id, client = "Priya", message = "Diwali gig", date = "Sat, May 16",
            amount = 40_000, `package` = "Custom", timeAgo = "2h"),
        status = GigRequestStatus.Open,
    )

    @Test
    fun `parallel load hydrates greeting, bookings, score and requests`() = runTest(dispatcher) {
        val artists = FakeArtistsRepository().apply { selfRow = selfRow("Kaavya Menon") }
        val bookings = FakeBookingsRepository(artists, seed = listOf(booking("b1")))
        val score = FakeScoreRepository(
            self = ScoreBreakdown.from(92, 96, 98, 90, 2, 80, 40),
        )
        val store = RequestStore(FakeRequestsRepository(seed = listOf(request("r1"))))
        val vm = ArtistHomeViewModel(artists, bookings, score, store, idleBanner, DeepLinkRouter())
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Kaavya", s.greetingName)          // first word of the stage name
        assertEquals(1, s.bookings.size)
        assertEquals(92, s.breakdown.numericScore)
        assertEquals(ScoreTier.Elite, s.breakdown.tier) // score-tier display
        assertNull(s.error)
        assertEquals(1, vm.requests.value.size)          // requests pulled into the store
    }

    @Test
    fun `a failing load surfaces an error instead of a silent empty dashboard`() = runTest(dispatcher) {
        val artists = FakeArtistsRepository()
        val bookings = FakeBookingsRepository(artists)
        val throwingScore = object : ScoreRepository {
            override suspend fun breakdownForSelf(): ScoreBreakdown = throw RuntimeException("RLS denied")
            override suspend fun breakdown(forArtist: String): ScoreBreakdown = ScoreBreakdown.newArtist
            override suspend fun historyForSelf(): List<ScoreHistoryPoint> = emptyList()
        }
        val store = RequestStore(FakeRequestsRepository())
        val vm = ArtistHomeViewModel(artists, bookings, throwingScore, store, idleBanner, DeepLinkRouter())
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("RLS"))
    }

    // --- Pure earnings / availability derivations ---------------------------

    @Test
    fun `earnings window counts split current vs prior period`() {
        val today = LocalDate.of(2026, 7, 8)
        val bookings = listOf(
            booking("b0", createdDaysAgo = 0, today = today),
            booking("b3", createdDaysAgo = 3, today = today),
            booking("b8", createdDaysAgo = 8, today = today),
            booking("b10", createdDaysAgo = 10, today = today),
        )
        val (headline, prior) = ArtistHomeViewModel.windowCounts(bookings, EarningsRange.SevenDays, today)
        assertEquals(2, headline) // 0 + 3 days ago fall in the last 7
        assertEquals(2, prior)    // 8 + 10 days ago fall in the prior 7
        assertEquals(4 to 0, ArtistHomeViewModel.windowCounts(bookings, EarningsRange.All, today))
    }

    @Test
    fun `daily buckets place bookings by created date, oldest-first`() {
        val today = LocalDate.of(2026, 7, 8)
        val bookings = listOf(
            booking("b0", createdDaysAgo = 0, today = today),
            booking("b3", createdDaysAgo = 3, today = today),
            booking("b8", createdDaysAgo = 8, today = today), // outside the 7-day window
        )
        val series = ArtistHomeViewModel.dailyBuckets(bookings, 7, today)
        assertEquals(7, series.size)
        assertEquals(1.0, series[6], 0.0)  // today = last bucket
        assertEquals(1.0, series[3], 0.0)  // 3 days ago
        assertEquals(2.0, series.sum(), 0.0)
    }

    @Test
    fun `delta is up-100 on a fresh window, down-percent when the prior beat it`() {
        assertEquals(EarningsDelta(100, true), ArtistHomeViewModel.delta(10, 5))
        assertEquals(EarningsDelta(50, false), ArtistHomeViewModel.delta(5, 10))
        assertEquals(EarningsDelta(100, true), ArtistHomeViewModel.delta(3, 0))
        assertEquals(EarningsDelta(0, true), ArtistHomeViewModel.delta(0, 0))
    }

    @Test
    fun `only confirmed bookings within 14 days count as booked days`() {
        val today = LocalDate.now()
        val soon = today.plusDays(3)
        val far = today.plusDays(20)
        val bookings = listOf(
            booking("b1", dateLabel = soon.format(labelFmt), status = BookingStatus.Confirmed),
            booking("b2", dateLabel = far.format(labelFmt), status = BookingStatus.Confirmed),   // out of range
            booking("b3", dateLabel = soon.format(labelFmt), status = BookingStatus.PendingConfirm), // not confirmed
        )
        val booked = ArtistHomeViewModel.bookedDatesNext14Days(bookings, today)
        assertEquals(setOf(soon), booked)
    }

    @Test
    fun `upcoming gigs are confirmed-only, soonest gig-date first`() {
        val today = LocalDate.now()
        val bookings = listOf(
            booking("late", dateLabel = today.plusDays(5).format(labelFmt), status = BookingStatus.Confirmed),
            booking("soon", dateLabel = today.plusDays(2).format(labelFmt), status = BookingStatus.Confirmed),
            booking("pending", dateLabel = today.plusDays(1).format(labelFmt), status = BookingStatus.PendingConfirm),
        )
        val upcoming = ArtistHomeViewModel.upcomingConfirmed(bookings)
        assertEquals(listOf("soon", "late"), upcoming.map { it.id })
    }

    @Test
    fun `upcoming copy reads today, N-days, and empty from the soonest gig`() {
        val today = LocalDate.of(2026, 7, 8)
        assertEquals("No upcoming gigs", ArtistHomeViewModel.upcomingCopy(emptyList(), today))
        // Single gig today → "Next gig today".
        assertEquals(
            "Next gig today",
            ArtistHomeViewModel.upcomingCopy(
                listOf(booking("t", dateLabel = today.format(labelFmt))), today,
            ),
        )
        // Single gig in 3 days → "Next gig in 3 days".
        assertEquals(
            "Next gig in 3 days",
            ArtistHomeViewModel.upcomingCopy(
                listOf(booking("s", dateLabel = today.plusDays(3).format(labelFmt))), today,
            ),
        )
        // Two gigs on different days → "Spread over N days" (latest).
        assertEquals(
            "Spread over 5 days",
            ArtistHomeViewModel.upcomingCopy(
                listOf(
                    booking("a", dateLabel = today.plusDays(2).format(labelFmt)),
                    booking("b", dateLabel = today.plusDays(5).format(labelFmt)),
                ),
                today,
            ),
        )
    }
}
