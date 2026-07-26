package `in`.artistant.app.platform.calendar

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncPlannerTest {

    @Test
    fun plan_createsConfirmed_skipsPending_deletesCancelled() {
        val confirmed = sample(id = "b1", status = BookingStatus.Confirmed)
        val pending = sample(id = "b2", status = BookingStatus.PendingConfirm)
        val cancelled = sample(id = "b3", status = BookingStatus.Cancelled)
        val map = mapOf(
            "b3" to CalendarSyncPlanner.SyncedEvent("evt-3", "old"),
        )
        val actions = CalendarSyncPlanner.plan(listOf(confirmed, pending, cancelled), map)
        assertEquals(2, actions.size)
        assertTrue(actions.any { it is CalendarSyncPlanner.Action.Create && it.bookingId == "b1" })
        assertTrue(actions.any { it is CalendarSyncPlanner.Action.Delete && it.bookingId == "b3" })
    }

    @Test
    fun plan_updatesOnFingerprintChange() {
        val booking = sample(id = "b1", status = BookingStatus.Confirmed, venue = "New venue")
        val fp = CalendarSyncPlanner.fingerprint(booking.copy(venue = "Old"))
        val map = mapOf("b1" to CalendarSyncPlanner.SyncedEvent("evt-1", fp))
        val actions = CalendarSyncPlanner.plan(listOf(booking), map)
        assertEquals(1, actions.size)
        assertTrue(actions.first() is CalendarSyncPlanner.Action.Update)
    }

    @Test
    fun eventTitle_prefersClientName() {
        val b = sample(id = "b1", status = BookingStatus.Confirmed, client = "Asha")
        assertEquals("Gig — Asha", CalendarSyncPlanner.eventTitle(b))
    }

    @Test
    fun clashesOnDay_findsOverlap() {
        val a = sample(id = "b1", status = BookingStatus.Confirmed)
        val pending = sample(id = "b2", status = BookingStatus.PendingConfirm)
        // 2026-07-10 UTC day window covering the sample start
        val dayStart = 1_752_134_400_000L // approx — use start of sample day via planner start
        val start = CalendarSyncPlanner.resolvedStartEpochMs(a)!!
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
            timeInMillis = start
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val from = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val clashes = CalendarSyncPlanner.clashesOnDay(listOf(a, pending), from, cal.timeInMillis)
        assertEquals(1, clashes.size)
        assertEquals("b1", clashes.first().bookingId)
    }

    @Test
    fun busyDays_returnsIstKeys() {
        val a = sample(id = "b1", status = BookingStatus.Confirmed)
        val days = CalendarSyncPlanner.busyDays(listOf(a))
        assertTrue(days.isNotEmpty())
        assertTrue(days.first().matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    private fun sample(
        id: String,
        status: BookingStatus,
        venue: String = "Rooftop",
        client: String? = "Client",
    ) = Booking(
        id = id,
        artistId = "a1",
        packageIndex = 0,
        date = "Fri, Jul 10, 2026",
        time = "7:30 PM",
        venue = venue,
        guests = 50,
        fee = 10000,
        platformFee = 500,
        gst = 1890,
        total = 12390,
        status = status,
        escrowStatus = EscrowStatus.Held,
        paymentMethod = PaymentMethod.Upi,
        protectionEnabled = true,
        createdAtEpochMs = 0L,
        clientFullName = client,
        startDatetimeIso = "2026-07-10T14:00:00Z",
        endDatetimeIso = "2026-07-10T16:00:00Z",
    )
}
