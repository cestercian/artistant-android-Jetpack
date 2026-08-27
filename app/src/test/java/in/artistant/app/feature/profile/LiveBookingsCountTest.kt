package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.testsupport.booking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The profile header's "Bookings" column.
 *
 * These are red→green against the shipped `status != Completed` rule: under it,
 * cancelled / disputed / unknown all counted, so `excludes cancelled`,
 * `excludes disputed`, `excludes the unrecognised status` and
 * `a user who cancelled everything reads zero` all failed.
 *
 * Pure over a `List<Booking>` on purpose — the ViewModel needs Hilt, a session
 * and a repository to construct, none of which this suite can build (junit +
 * coroutines-test only). Extracting the decision is what makes it assertable.
 */
class LiveBookingsCountTest {

    @Test
    fun `a request awaiting the artist counts`() {
        assertEquals(1, liveBookingsCount(listOf(booking(status = BookingStatus.PendingConfirm))))
    }

    @Test
    fun `an accepted booking counts`() {
        assertEquals(1, liveBookingsCount(listOf(booking(status = BookingStatus.Confirmed))))
    }

    @Test
    fun `excludes completed`() {
        assertEquals(0, liveBookingsCount(listOf(booking(status = BookingStatus.Completed))))
    }

    @Test
    fun `excludes cancelled`() {
        assertEquals(0, liveBookingsCount(listOf(booking(status = BookingStatus.Cancelled))))
    }

    @Test
    fun `excludes disputed`() {
        assertEquals(0, liveBookingsCount(listOf(booking(status = BookingStatus.Disputed))))
    }

    /**
     * The decode fallback for a status this build doesn't know. It must not land
     * in a column the user reads as live work — the client cannot assert
     * anything about a booking it can't interpret.
     */
    @Test
    fun `excludes the unrecognised status`() {
        assertEquals(0, liveBookingsCount(listOf(booking(status = BookingStatus.Unknown))))
    }

    /** The reported symptom, verbatim: three bookings, all cancelled, header said 3. */
    @Test
    fun `a user who cancelled everything reads zero`() {
        val cancelled = List(3) { i ->
            booking(id = "b-$i", status = BookingStatus.Cancelled)
        }
        assertEquals(0, liveBookingsCount(cancelled))
    }

    @Test
    fun `counts only the live ones in a mixed list`() {
        val mixed = listOf(
            booking(id = "b-1", status = BookingStatus.PendingConfirm),
            booking(id = "b-2", status = BookingStatus.Confirmed),
            booking(id = "b-3", status = BookingStatus.Completed),
            booking(id = "b-4", status = BookingStatus.Cancelled),
            booking(id = "b-5", status = BookingStatus.Disputed),
            booking(id = "b-6", status = BookingStatus.Unknown),
        )
        assertEquals(2, liveBookingsCount(mixed))
    }

    /**
     * The two profile columns must never double-count a single booking. Asserted
     * across the WHOLE enum, so a status added later can't quietly land in both.
     *
     * Landing in NEITHER is allowed and expected — that is what cancelled and
     * disputed do, and it's the whole point of this change. The columns aren't a
     * partition of the user's bookings; they're two named subsets.
     */
    @Test
    fun `no status is counted in both columns`() {
        BookingStatus.entries.forEach { status ->
            val one = listOf(booking(status = status))
            val live = liveBookingsCount(one)
            // The shipped expression, not a copy of it — a change to what
            // `completedBookingsCount` counts must be able to break this test.
            val completed = completedBookingsCount(one)
            assertEquals("$status counted in both columns", 0, live * completed)
        }
    }

    @Test
    fun `no bookings reads zero`() {
        assertEquals(0, liveBookingsCount(emptyList()))
    }
}
