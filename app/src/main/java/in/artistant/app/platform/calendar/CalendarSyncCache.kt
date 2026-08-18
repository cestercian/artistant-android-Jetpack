package `in`.artistant.app.platform.calendar

import `in`.artistant.app.data.model.Booking
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * The signed-in user's own bookings, merged across every repository fetch this
 * launch — the reconcile's source of desired state, and the only thing the
 * artist-side clash / busy-day reads answer from.
 *
 * Deliberately NOT gated on the calendar-mirror toggle or its sync owner. iOS
 * answers both reads by querying EventKit directly, so the owner gate its
 * plan-016 leak fix added only ever guarded the WRITE path; the Android port
 * answers them from this cache instead, and applying the same gate to the cache
 * left every artist who had never enabled calendar sync — the default — with a
 * permanently empty clash list, so a double booking was accepted with no
 * warning. Rows here are the caller's own RLS-scoped bookings and never leave
 * the process; only the mirror needs the owner check.
 *
 * Filled from the caller's dispatcher (repository calls resume on
 * viewModelScope = Main.immediate) and read from the reconcile's IO batch,
 * hence the ConcurrentHashMap plus [snapshot] for the batch to iterate.
 */
class CalendarSyncCache {

    private val bookings = ConcurrentHashMap<String, Booking>()

    /** Whose rows [bookings] holds — a clash read must never answer with the previous account's gigs. */
    @Volatile
    private var ownerUserId: String? = null

    /**
     * Merge one server batch. A different signed-in user REPLACES the cache
     * rather than merging into it: an in-process account switch that never ran
     * [clear] (an expired session, not the Profile sign-out) would otherwise
     * leave the previous account's gigs answering the new user's clash reads —
     * and feed them to a reconcile once the new user enables the mirror.
     */
    fun ingest(userId: String?, batch: List<Booking>) {
        if (userId != ownerUserId) {
            bookings.clear()
            ownerUserId = userId
        }
        for (b in batch) bookings[b.id.lowercase()] = b
    }

    /** Sign-out / account switch: drop the merged rows. */
    fun clear() {
        bookings.clear()
        ownerUserId = null
    }

    /** Stable copy for the reconcile batch, which runs on IO while Main keeps ingesting. */
    fun snapshot(): Map<String, Booking> = bookings.toMap()

    /** Busy day keys (yyyy-MM-dd IST) from the cached confirmed bookings. */
    fun busyDays(): Set<String> = CalendarSyncPlanner.busyDays(bookings.values)

    /** Clashes on the local calendar day containing [epochMs]. */
    fun clashes(epochMs: Long): List<CalendarSyncPlanner.Clash> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(IST)).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return CalendarSyncPlanner.clashesOnDay(bookings.values, dayStart, cal.timeInMillis)
    }

    private companion object {
        const val IST = "Asia/Kolkata"
    }
}
