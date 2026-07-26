package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.repository.FakeBookingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistHomeLogicTest {

    private fun booking(
        status: BookingStatus,
        clientFullName: String? = null,
    ) = Booking(
        id = "b-${status.name}",
        artistId = "11111111-1111-1111-1111-111111111111",
        packageIndex = 0,
        date = "Sat, May 16, 2026",
        time = "8:30 PM",
        venue = "TBD",
        guests = 80,
        fee = 20000,
        platformFee = 1000,
        gst = 3780,
        total = 24780,
        status = status,
        escrowStatus = EscrowStatus.Held,
        paymentMethod = PaymentMethod.Upi,
        protectionEnabled = true,
        createdAtEpochMs = 1L,
        clientFullName = clientFullName,
    )

    @Test
    fun pendingConfirmBookings_filtersPendingOnly() {
        val all = listOf(
            booking(BookingStatus.PendingConfirm, "Alice"),
            booking(BookingStatus.Confirmed),
            booking(BookingStatus.PendingConfirm, "Bob"),
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
        val pastIso = "2020-01-15T18:00:00Z"
        val futureIso = "2030-06-01T18:00:00Z"
        val all = listOf(
            booking(BookingStatus.Confirmed).copy(id = "past", startDatetimeIso = pastIso),
            booking(BookingStatus.Confirmed).copy(id = "future", startDatetimeIso = futureIso),
            booking(BookingStatus.PendingConfirm).copy(id = "pending", startDatetimeIso = futureIso),
        )
        val upcoming = upcomingConfirmed(all, nowEpochMs = 1_700_000_000_000L)
        assertEquals(listOf("future"), upcoming.map { it.id })
    }

    @Test
    fun earningsSparkline_bucketsByCreatedAt() {
        val ist = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val today = java.util.Calendar.getInstance(ist).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val twoDaysAgo = today.timeInMillis - 2L * 24 * 60 * 60 * 1000
        val series = earningsSparkline(
            listOf(
                booking(BookingStatus.Confirmed).copy(fee = 1000, createdAtEpochMs = twoDaysAgo),
                booking(BookingStatus.Completed).copy(fee = 500, createdAtEpochMs = today.timeInMillis),
            ),
            days = 7,
        )
        assertEquals(7, series.size)
        assertEquals(1000, series[4]) // today-2 → index 4 in a 7-day oldest→newest series
        assertEquals(500, series[6])
    }

    @Test
    fun fakeListForArtist_pendingCount() = runTest {
        val repo = FakeBookingsRepository(
            seed = listOf(
                booking(BookingStatus.PendingConfirm),
                booking(BookingStatus.PendingConfirm),
                booking(BookingStatus.Confirmed),
            ),
        )
        val pending = pendingConfirmBookings(repo.listForArtist())
        assertEquals(2, pending.size)
    }
}
