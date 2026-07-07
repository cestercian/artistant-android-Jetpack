package `in`.artistant.app.data.model

import `in`.artistant.app.data.model.dto.DBBooking
import `in`.artistant.app.data.model.dto.DBBookingWithClient
import `in`.artistant.app.data.model.dto.DBGigRequestWithClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Pins the wire → domain mapping for the booking funnel + gig-request read shapes. */
class BookingDtoMappingTest {

    private fun row(status: String = "confirmed", escrow: String = "held") = DBBooking(
        id = "b1", client_id = "c1", artist_id = "a1", package_index = 1,
        date_label = "Sat, May 16, 2026", time_label = "8:30 PM", venue = "Blue Frog",
        guests = 100, fee_inr = 10_000, platform_fee_inr = 500, gst_inr = 1_890,
        total_inr = 12_390, status = status, escrow_status = escrow, payment_method = "upi",
        protection_enabled = true, created_at = "2026-05-01T10:00:00Z",
        start_datetime = "2026-05-16T15:00:00Z", end_datetime = "2026-05-16T17:00:00Z",
    )

    @Test
    fun `DBBooking maps enums, money, and timestamps`() {
        val b = row().toBooking()
        assertEquals("b1", b.id)
        assertEquals("a1", b.artistId)
        assertEquals(BookingStatus.Confirmed, b.status)
        assertEquals(EscrowStatus.Held, b.escrowStatus)
        assertEquals(PaymentMethod.Upi, b.paymentMethod)
        assertEquals(12_390, b.total)
        assertEquals(Instant.parse("2026-05-16T15:00:00Z"), b.startDatetime)
        assertNull(b.clientFullName)   // client list carries no embed
    }

    @Test
    fun `unknown enum strings fall back to safe defaults`() {
        val b = row(status = "weird", escrow = "??").toBooking()
        assertEquals(BookingStatus.PendingConfirm, b.status)
        assertEquals(EscrowStatus.Held, b.escrowStatus)
    }

    @Test
    fun `DBBookingWithClient threads the trimmed embed name`() {
        val withClient = DBBookingWithClient(
            id = "b1", client_id = "c1", artist_id = "a1", package_index = 0,
            date_label = "d", time_label = "t", venue = "v", guests = 50,
            fee_inr = 1000, platform_fee_inr = 50, gst_inr = 189, total_inr = 1239,
            status = "completed", escrow_status = "released", payment_method = "card",
            protection_enabled = true,
            client = DBBookingWithClient.ClientEmbed(full_name = "  Yash A.  "),
        )
        val b = withClient.toBooking()
        assertEquals("Yash A.", b.clientFullName)
        assertEquals(BookingStatus.Completed, b.status)
        assertEquals(PaymentMethod.Card, b.paymentMethod)
    }

    @Test
    fun `blank embed name maps to null`() {
        val withClient = DBBookingWithClient(
            id = "b1", client_id = "c1", artist_id = "a1", package_index = 0,
            date_label = "d", time_label = "t", venue = "v", guests = 1,
            fee_inr = 0, platform_fee_inr = 0, gst_inr = 0, total_inr = 0,
            status = "confirmed", escrow_status = "held", payment_method = "upi",
            protection_enabled = false, client = DBBookingWithClient.ClientEmbed("   "),
        )
        assertNull(withClient.toBooking().clientFullName)
    }

    @Test
    fun `DBGigRequestWithClient maps to a StoredRequest with client name and status`() {
        val req = DBGigRequestWithClient(
            id = "r1", artist_id = "a1", client_id = "c1", message = "Play my wedding",
            proposed_amount_inr = 40_000, counter_amount_inr = 45_000, date_label = "Jun 20",
            venue = "Taj", crowd_size = 200, status = "countered",
            created_at = "2026-05-01T10:00:00Z",
            client = DBGigRequestWithClient.ClientEmbed("Priya"),
        )
        val stored = req.toStoredRequest()
        assertEquals("r1", stored.id)
        assertEquals("Priya", stored.raw.client)
        assertEquals(40_000, stored.raw.amount)
        assertEquals("Custom", stored.raw.`package`)
        assertEquals(GigRequestStatus.Countered, stored.status)
        assertEquals(45_000, stored.counterAmount)
    }

    @Test
    fun `missing embed name defaults to Client`() {
        val req = DBGigRequestWithClient(
            id = "r2", artist_id = "a1", client_id = "c1", proposed_amount_inr = 100,
            date_label = "d", status = "open",
        )
        assertEquals("Client", req.toStoredRequest().raw.client)
        assertEquals(GigRequestStatus.Open, req.toStoredRequest().status)
    }

    @Test
    fun `relativeTimeAgo buckets by elapsed seconds`() {
        val now = Instant.parse("2026-05-01T12:00:00Z")
        fun ago(iso: String?) = DBGigRequestWithClient.relativeTimeAgo(iso, now)
        assertEquals("now", ago("2026-05-01T11:59:30Z"))     // 30s
        assertEquals("5m ago", ago("2026-05-01T11:55:00Z"))  // 5m
        assertEquals("3h ago", ago("2026-05-01T09:00:00Z"))  // 3h
        assertEquals("2d ago", ago("2026-04-29T12:00:00Z"))  // 2d
        assertEquals("now", ago(null))
    }
}
