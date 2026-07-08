package `in`.artistant.app.platform.calendar

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The tested core of the calendar mirror. The ContentResolver I/O in
 * [CalendarSyncService] is device-dependent (a real Calendar Provider) and compile-only;
 * the PURE reconcile policy lives in [CalendarPlanner] and is fully covered here — most
 * importantly the safety-critical NO-MASS-DELETE invariant.
 */
class CalendarPlannerTest {

    private val start = Instant.parse("2026-05-16T15:00:00Z")

    private fun booking(
        id: String,
        status: BookingStatus,
        venue: String = "Blue Frog, Mumbai",
        start: Instant? = this.start,
    ) = Booking(
        id = id,
        artistId = "artist-1",
        packageIndex = 0,
        dateLabel = "Sat, May 16, 2026",
        timeLabel = "8:30 PM",
        startDatetime = start,
        endDatetime = start?.plusSeconds(2 * 3600),
        venue = venue,
        guests = 100,
        fee = 10_000,
        platformFee = 500,
        gst = 1_890,
        total = 12_390,
        status = status,
        escrowStatus = EscrowStatus.Held,
        paymentMethod = PaymentMethod.Upi,
        protectionEnabled = true,
        createdAt = null,
    )

    @Test
    fun `confirmed and not in map creates`() {
        val b = booking("b1", BookingStatus.Confirmed)
        val plan = CalendarPlanner.plan(listOf(b), emptyMap())
        assertEquals(listOf(CalendarAction.Create("b1")), plan)
    }

    @Test
    fun `cancelled and present in map deletes`() {
        val b = booking("b1", BookingStatus.Cancelled)
        val persisted = mapOf("b1" to SyncedEvent(eventId = 42, fingerprint = "old"))
        val plan = CalendarPlanner.plan(listOf(b), persisted)
        assertEquals(listOf(CalendarAction.Delete("b1", 42)), plan)
    }

    @Test
    fun `pending is never mirrored`() {
        val b = booking("b1", BookingStatus.PendingConfirm)
        assertTrue(CalendarPlanner.plan(listOf(b), emptyMap()).isEmpty())
        // Even with a (somehow) stale map entry, pending produces no action.
        val persisted = mapOf("b1" to SyncedEvent(1, "x"))
        assertTrue(CalendarPlanner.plan(listOf(b), persisted).isEmpty())
    }

    @Test
    fun `unchanged fingerprint is a noop`() {
        val b = booking("b1", BookingStatus.Confirmed)
        val persisted = mapOf("b1" to SyncedEvent(7, CalendarPlanner.fingerprint(b)))
        assertTrue(CalendarPlanner.plan(listOf(b), persisted).isEmpty())
    }

    @Test
    fun `changed fingerprint updates`() {
        val b = booking("b1", BookingStatus.Confirmed, venue = "New Venue")
        // Map holds a fingerprint computed from a different venue.
        val stale = booking("b1", BookingStatus.Confirmed, venue = "Old Venue")
        val persisted = mapOf("b1" to SyncedEvent(9, CalendarPlanner.fingerprint(stale)))
        assertEquals(listOf(CalendarAction.Update("b1", 9)), CalendarPlanner.plan(listOf(b), persisted))
    }

    @Test
    fun `cancelled but absent from map produces nothing`() {
        // Nothing we created → nothing to delete (don't fabricate a delete for an unmapped id).
        val b = booking("b1", BookingStatus.Cancelled)
        assertTrue(CalendarPlanner.plan(listOf(b), emptyMap()).isEmpty())
    }

    @Test
    fun `id in map but absent from desired is NEVER deleted — no mass-delete`() {
        // The safety-critical invariant: a partial / paginated / post-sign-out list must not
        // wipe the user's other mirrored events. Only ids present in `desired` are ever acted on.
        val desired = listOf(booking("present", BookingStatus.Confirmed))
        val persisted = mapOf(
            "present" to SyncedEvent(1, CalendarPlanner.fingerprint(desired[0])),
            "absent-a" to SyncedEvent(2, "fp-a"),
            "absent-b" to SyncedEvent(3, "fp-b"),
        )
        val plan = CalendarPlanner.plan(desired, persisted)
        // "present" is unchanged → noop; the two absent ids are untouched (no Delete for them).
        assertTrue("no action should target an id missing from desired", plan.isEmpty())
    }

    @Test
    fun `booking without a resolvable start is skipped`() {
        // No datetime AND an unparseable label pair → can't become an event.
        val b = booking("b1", BookingStatus.Confirmed, start = null)
            .copy(dateLabel = "not-a-date", timeLabel = "??")
        assertTrue(CalendarPlanner.plan(listOf(b), emptyMap()).isEmpty())
    }

    @Test
    fun `fingerprint is stable and changes on a mirrored field`() {
        val b = booking("b1", BookingStatus.Confirmed)
        assertEquals(CalendarPlanner.fingerprint(b), CalendarPlanner.fingerprint(b))
        assertNotEquals(
            CalendarPlanner.fingerprint(b),
            CalendarPlanner.fingerprint(b.copy(venue = "Different Venue")),
        )
        assertNotEquals(
            CalendarPlanner.fingerprint(b),
            CalendarPlanner.fingerprint(b.copy(status = BookingStatus.Completed)),
        )
    }
}
