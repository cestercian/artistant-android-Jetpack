package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.platform.payments.PaymentEscrowState
import `in`.artistant.app.platform.payments.PaymentResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * End-to-end booking money math + cancel path through the Fake repo. Proves the
 * draft → package-price → BookingMath → row fee columns wiring, and that a cancel
 * flips status/escrow the way the real Edge Function does server-side.
 */
class BookingFlowTest {

    private val artistId = "11111111-1111-1111-1111-111111111111"

    private fun artistWithFee(fee: Int): Artist = Artist(
        id = artistId, name = "DJ Neon", handle = "neon", category = "DJ", genre = "House",
        city = "Mumbai", price = fee, duration = "2 hr", score = 80,
        gradient = ArtistGradient.palette(0), bio = "", followers = "", streams = "",
        response = "", onTime = 0, gigs = 0, rating = 0.0,
        packages = listOf(ArtistPackage("Headline", "2 hr", fee, emptyList(), popular = true)),
        tech = emptyList(), samples = emptyList(), reviews = emptyList(),
    )

    private val payment = PaymentResult(
        orderId = "order_mock_1", paymentId = null, methodLabel = "UPI",
        escrowState = PaymentEscrowState.Held,
    )

    private fun draft() = BookingDraft(
        artistId = artistId, packageIndex = 0,
        date = "Sat, May 16, 2026", dateRaw = LocalDate.of(2026, 5, 16), time = "8:30 PM",
        venue = "Blue Frog", guests = 120,
    )

    @Test
    fun `create computes India fees end-to-end from the package price`() = runTest {
        val artists = FakeArtistsRepository(full = listOf(artistWithFee(10_000)))
        val repo = FakeBookingsRepository(artists)

        val booking = repo.create(draft(), payment)

        assertEquals(10_000, booking.fee)
        assertEquals(500, booking.platformFee)     // 10000 * 0.05
        assertEquals(1_890, booking.gst)           // (10000 + 500) * 0.18
        assertEquals(12_390, booking.total)        // 10000 + 500 + 1890
        assertEquals(BookingStatus.Confirmed, booking.status)
        assertEquals(EscrowStatus.Held, booking.escrowStatus)
        assertEquals(120, booking.guests)
        assertEquals("Blue Frog", booking.venue)
    }

    @Test
    fun `draft charges extension resolves the fee via ArtistsRepository`() {
        val artists = FakeArtistsRepository(full = listOf(artistWithFee(25_000)))
        assertEquals(25_000, draft().fee(artists))
        val c = draft().charges(artists)
        assertEquals(1_250, c.platform)            // 25000 * 0.05
        assertEquals(4_725, c.gst)                 // (25000 + 1250) * 0.18
        assertEquals(30_975, c.total)
    }

    @Test
    fun `unresolvable artist yields a zero-fee draft rather than crashing`() {
        val emptyArtists = FakeArtistsRepository()
        assertEquals(0, draft().fee(emptyArtists))
    }

    @Test
    fun `cancel flips status to Cancelled and escrow to Refunded`() = runTest {
        val artists = FakeArtistsRepository(full = listOf(artistWithFee(10_000)))
        val repo = FakeBookingsRepository(artists)
        val booking = repo.create(draft(), payment)

        val cancelled = repo.cancel(booking.id)

        assertEquals(BookingStatus.Cancelled, cancelled.status)
        assertEquals(EscrowStatus.Refunded, cancelled.escrowStatus)
        assertEquals(BookingStatus.Cancelled, repo.listForClient().first().status)
    }
}
