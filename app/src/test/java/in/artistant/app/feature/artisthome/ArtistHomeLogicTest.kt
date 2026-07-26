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
