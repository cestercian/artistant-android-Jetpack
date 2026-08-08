package `in`.artistant.app.designsystem.component

import `in`.artistant.app.data.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the one status→tone mapping every booking surface now shares.
 *
 * The bug this covers was found on-device: the bookings list painted EVERY
 * status with the brand accent, so "Confirmed" and "Awaiting confirm" were the
 * same lime and the status label carried no information. Booking detail had the
 * opposite failure — every status Neutral. Only the artist-list drill-down
 * actually varied, so its mapping became the shared one.
 */
class BookingStatusToneTest {

    @Test
    fun `a status the reader must act on is distinct from one that is settled`() {
        // The exact pair that rendered identically on the bookings list.
        assertNotEquals(
            bookingStatusTone(BookingStatus.PendingConfirm),
            bookingStatusTone(BookingStatus.Confirmed),
        )
    }

    @Test
    fun `every status maps to its semantic tone`() {
        assertEquals(PillTone.Warm, bookingStatusTone(BookingStatus.PendingConfirm))
        assertEquals(PillTone.Brand, bookingStatusTone(BookingStatus.Confirmed))
        assertEquals(PillTone.Good, bookingStatusTone(BookingStatus.Completed))
        assertEquals(PillTone.Hot, bookingStatusTone(BookingStatus.Cancelled))
        assertEquals(PillTone.Hot, bookingStatusTone(BookingStatus.Disputed))
    }

    /**
     * `Unknown` is the decode fallback for a status this build doesn't recognise.
     * Any colour would assert a meaning we don't have, so it must stay neutral —
     * and in particular must never borrow the live-booking accent.
     */
    @Test
    fun `an unrecognised status stays neutral rather than claiming a meaning`() {
        assertEquals(PillTone.Neutral, bookingStatusTone(BookingStatus.Unknown))
        assertNotEquals(
            bookingStatusTone(BookingStatus.Confirmed),
            bookingStatusTone(BookingStatus.Unknown),
        )
    }

    /** A terminal-but-fine outcome must not read the same as a terminal-bad one. */
    @Test
    fun `completed is distinct from cancelled`() {
        assertNotEquals(
            bookingStatusTone(BookingStatus.Completed),
            bookingStatusTone(BookingStatus.Cancelled),
        )
    }
}
