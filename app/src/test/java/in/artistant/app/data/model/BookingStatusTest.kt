package `in`.artistant.app.data.model

import `in`.artistant.app.feature.artisthome.pendingConfirmBookings
import `in`.artistant.app.platform.calendar.CalendarSyncPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decode contract for [BookingStatus] — iOS PR #111 parity.
 *
 * The server owns the status vocabulary and can move ahead of a shipped client
 * (a future `refunded` / `expired` / …). The fallback for a status this build
 * doesn't know must therefore be NEUTRAL. `PendingConfirm` is the opposite of
 * neutral: it is the one state that renders the artist's Accept/Decline CTAs,
 * the client's cancel affordance, and the artist Home "New requests" bucket —
 * so decoding an unknown status to it turned every such booking into a live,
 * actionable request whose Accept would PATCH a booking in a state the client
 * cannot reason about.
 *
 * These are acceptance tests: they assert the property (never actionable), not
 * just the enum name, so they keep holding if the case is ever renamed.
 */
class BookingStatusTest {

    // --- decode ------------------------------------------------------------

    @Test
    fun `fromDb maps a status this build does not know to the neutral fallback`() {
        for (raw in listOf("some_future_status", "refunded", "expired", "")) {
            val decoded = BookingStatus.fromDb(raw)
            assertNotEquals(
                "unknown status '$raw' must not decode to the actionable PendingConfirm",
                BookingStatus.PendingConfirm,
                decoded,
            )
            assertEquals(BookingStatus.Unknown, decoded)
        }
    }

    @Test
    fun `fromDb maps null to the neutral fallback`() {
        assertNotEquals(BookingStatus.PendingConfirm, BookingStatus.fromDb(null))
        assertEquals(BookingStatus.Unknown, BookingStatus.fromDb(null))
    }

    @Test
    fun `the unknown case is decode-only and never a db value`() {
        // Its sentinel is not in the `bookings.status` check constraint, and the
        // only statuses ever written are the two literal cases below.
        assertEquals("Unknown", BookingStatus.Unknown.label)
        assertTrue(
            BookingStatus.Unknown.dbValue !in
                listOf("pending_confirm", "confirmed", "completed", "cancelled", "disputed"),
        )
        assertEquals("pending_confirm", BookingStatus.PendingConfirm.dbValue) // create insert
        assertEquals("confirmed", BookingStatus.Confirmed.dbValue) // accept PATCH
    }

    @Test
    fun `fromDb still maps every known db value to its own case`() {
        assertEquals(BookingStatus.PendingConfirm, BookingStatus.fromDb("pending_confirm"))
        assertEquals(BookingStatus.Confirmed, BookingStatus.fromDb("confirmed"))
        assertEquals(BookingStatus.Cancelled, BookingStatus.fromDb("cancelled"))
        assertEquals(BookingStatus.Completed, BookingStatus.fromDb("completed"))
        assertEquals(BookingStatus.Disputed, BookingStatus.fromDb("disputed"))
    }

    // --- consumers: the fallback must be inert -----------------------------

    @Test
    fun `an unrecognised status is never an artist new request`() {
        val rows = listOf(
            booking("b-unknown", BookingStatus.fromDb("some_future_status")),
            booking("b-confirmed", BookingStatus.Confirmed),
        )
        assertTrue(
            "a status the client can't reason about must not offer Accept/Decline",
            pendingConfirmBookings(rows).isEmpty(),
        )
    }

    @Test
    fun `an unrecognised status is never mirrored into the device calendar`() {
        val rows = listOf(booking("b-unknown", BookingStatus.fromDb("some_future_status")))
        assertTrue(CalendarSyncPlanner.plan(rows, emptyMap()).isEmpty())
    }

    // --- deliberately NOT changed ------------------------------------------

    /**
     * Payments are dormant in v1, so [EscrowStatus] / [PaymentMethod] keep their
     * existing lenient fallbacks. Pinned here so a later drive-by "consistency"
     * pass has to be a deliberate decision rather than an accident.
     */
    @Test
    fun `escrow and payment method fallbacks are unchanged`() {
        assertEquals(EscrowStatus.Held, EscrowStatus.fromDb("something_new"))
        assertEquals(EscrowStatus.Held, EscrowStatus.fromDb(null))
        assertEquals(PaymentMethod.Upi, PaymentMethod.fromDb("something_new"))
        assertEquals(PaymentMethod.Upi, PaymentMethod.fromDb(null))
    }

    private fun booking(id: String, status: BookingStatus) = Booking(
        id = id,
        artistId = "11111111-1111-1111-1111-111111111111",
        packageIndex = 0,
        date = "Sat, May 16, 2026",
        time = "8:30 PM",
        venue = "The Piano Man",
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
        clientFullName = "Asha",
    )
}
