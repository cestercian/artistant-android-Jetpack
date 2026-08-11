package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.payments.PaymentEscrowState
import `in`.artistant.app.data.payments.PaymentResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake twins for Bookings + Requests — create lands pending_confirm; accept→confirmed. */
class BookingsRepositoryLogicTest {

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
