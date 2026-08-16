package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.payments.PaymentEscrowState
import `in`.artistant.app.data.payments.PaymentResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Fake twins for Bookings + Requests — create lands pending_confirm; accept→confirmed. */
class BookingsRepositoryLogicTest {

    private val defaultZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreZone() {
        TimeZone.setDefault(defaultZone)
    }

    private val pay = PaymentResult(
        orderId = "order_mock_1",
        methodLabel = "UPI",
        escrowState = PaymentEscrowState.Held,
    )

    private fun draft() = BookingDraft(
        artistId = "11111111-1111-1111-1111-111111111111",
        packageIndex = 0,
        packageName = "Evening set",
        packageDuration = "2h",
        feeInr = 20000,
        date = "Sat, May 16, 2026",
        dateRawEpochMs = 1_747_353_600_000L,
        time = "8:30 PM",
        venue = "Rooftop",
        guests = 80,
        paymentMethod = PaymentMethod.Upi,
    )

    @Test
    fun create_landsPendingConfirm_withCharges() = runTest {
        val repo = FakeBookingsRepository()
        val booking = repo.create(draft(), pay)
        assertEquals(BookingStatus.PendingConfirm, booking.status)
        assertEquals(EscrowStatus.Held, booking.escrowStatus)
        assertEquals(20000, booking.fee)
        assertEquals(1000, booking.platformFee) // 5%
        assertEquals(3780, booking.gst) // 18% of 21000
        assertEquals(24780, booking.total)
    }

    @Test
    fun create_snapshotsTheTierName_notJustItsIndex() = runTest {
        // `bookings.package_name` is written on every insert. Carrying it back on
        // the created row is what lets a booking name its tier without consulting
        // the artist's (mutable, reorderable) package list.
        val booking = FakeBookingsRepository().create(draft(), pay)
        assertEquals("Evening set", booking.packageName)
    }

    @Test
    fun accept_flipsConfirmed() = runTest {
        val repo = FakeBookingsRepository()
        val created = repo.create(draft(), pay)
        val accepted = repo.accept(created.id)
        assertEquals(BookingStatus.Confirmed, accepted.status)
    }

    @Test
    fun decline_cancelsAndRefundsEscrow() = runTest {
        val repo = FakeBookingsRepository()
        val created = repo.create(draft(), pay)
        val declined = repo.declineByArtist(created.id, reason = null)
        assertEquals(BookingStatus.Cancelled, declined.status)
        assertEquals(EscrowStatus.Refunded, declined.escrowStatus)
    }

    @Test
    fun create_notSignedIn_throws() = runTest {
        val repo = FakeBookingsRepository().apply { signedIn = false }
        val err = runCatching { repo.create(draft(), pay) }.exceptionOrNull()
        assertTrue(err is BookingRepositoryError.NotSignedIn)
    }

    @Test
    fun cancelPayloadKeys_excludeEscrow() {
        assertEquals(setOf("booking_id", "cancelled_by", "reason"), SupabaseBookingsRepository.cancelPayloadKeys)
        assertTrue("escrow_status" !in SupabaseBookingsRepository.cancelPayloadKeys)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startEndIso — the gig's wall clock is IST, wherever the phone is
    // ─────────────────────────────────────────────────────────────────────────

    /** The day the client tapped, as an instant, in whatever zone the phone is on. */
    private fun dayInDeviceZone(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0)
        }.timeInMillis

    private fun timedDraft(dateRawEpochMs: Long, time: String) =
        draft().copy(date = "Fri, Mar 6, 2026", dateRawEpochMs = dateRawEpochMs, time = time)

    /**
     * The bug: `time_label` says "8:00 PM" and everything that reads a booking
     * back (`BookingDateFormat.parseDateAndTime`, CalendarSyncPlanner, the
     * artist's calendar mirror) resolves that in IST — but the write built the
     * instant in the device's zone. A client on Asia/Dubai therefore stored a gig
     * 90 minutes after the one both sides could see, and the server's no-overlap
     * GiST (which compares instants) stopped catching the double-booking.
     */
    @Test
    fun startEndIso_pinsTheShowTimeToIst_notTheDevicesZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))
        val (start, end) = SupabaseBookingsRepository.startEndIso(
            timedDraft(dayInDeviceZone(2026, Calendar.MARCH, 6), "8:00 PM"),
        )
        // 8:00 PM IST = 14:30Z, not 16:00Z (which is what +04:00 would have written).
        assertEquals("2026-03-06T14:30:00.000Z", start)
        assertEquals("2026-03-06T16:30:00.000Z", end)
    }

    /** Same labels on an IST phone — the common case is unchanged. */
    @Test
    fun startEndIso_isTheSameInstantOnAnIstDevice() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        val (start, end) = SupabaseBookingsRepository.startEndIso(
            timedDraft(dayInDeviceZone(2026, Calendar.MARCH, 6), "8:00 PM"),
        )
        assertEquals("2026-03-06T14:30:00.000Z", start)
        assertEquals("2026-03-06T16:30:00.000Z", end)
    }

    /**
     * The written instant has to agree with the labels written alongside it —
     * `Booking.resolvedStartEpochMs` prefers `start_datetime` but falls back to
     * these labels, and the two must not name different moments.
     */
    @Test
    fun startEndIso_agreesWithTheLabelParseTheReadersFallBackTo() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val d = timedDraft(dayInDeviceZone(2026, Calendar.MARCH, 6), "8:00 PM")
        val (start, _) = SupabaseBookingsRepository.startEndIso(d)
        val fromLabels = BookingDateFormat.parseDateAndTime(d.date, d.time)
        assertEquals(isoUtcOf(fromLabels!!), start)
    }

    /** 24-hour slot labels still parse — artists' `default_time_slots` are free text. */
    @Test
    fun startEndIso_stillAcceptsTwentyFourHourSlotLabels() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))
        val (start, _) = SupabaseBookingsRepository.startEndIso(
            timedDraft(dayInDeviceZone(2026, Calendar.MARCH, 6), "20:00"),
        )
        assertEquals("2026-03-06T14:30:00.000Z", start)
    }

    @Test
    fun startEndIso_malformedTime_throws() {
        val err = runCatching {
            SupabaseBookingsRepository.startEndIso(
                timedDraft(dayInDeviceZone(2026, Calendar.MARCH, 6), "sunset"),
            )
        }.exceptionOrNull()
        assertTrue(err is BookingRepositoryError.MalformedTime)
    }

    private fun isoUtcOf(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMs))

    @Test
    fun requests_acceptDeclineCounter() = runTest {
        val repo = FakeRequestsRepository()
        val created = repo.create(
            artistId = "11111111-1111-1111-1111-111111111111",
            proposedAmountInr = 15000,
            dateLabel = "Sat, May 16, 2026",
            message = "Need a DJ",
            venue = null,
            crowdSize = 100,
            expiresAtEpochMs = System.currentTimeMillis() + 7L * 24 * 3600_000,
        )
        assertEquals(GigRequestStatus.Open, created.status)
        repo.counter(created.id, amount = 18000)
        assertEquals(GigRequestStatus.Countered, repo.listForArtist().first().status)
        assertEquals(18000, repo.listForArtist().first().counterAmount)
        repo.accept(created.id)
        assertEquals(GigRequestStatus.Accepted, repo.listForArtist().first().status)
    }
}
