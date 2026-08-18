package `in`.artistant.app.platform.calendar

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.testsupport.booking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desired-state cache behind the artist-side clash + busy-day reads.
 *
 * The bug this pins (audit p03): `CalendarSyncService.ingest()` applied the
 * calendar-mirror's sync-owner gate BEFORE filling the cache. That owner is null
 * until someone turns "Sync gigs to calendar" on — the default — so the gate
 * always tripped, the cache stayed empty, and `GigRequestDetailViewModel` showed
 * an empty clash list on every gig request. An artist with a confirmed gig that
 * Saturday could accept a second one for the same night with no warning at all.
 *
 * The cache is now filled unconditionally and only the CalendarContract write is
 * owner-gated, which is what iOS does too (its `clashes`/`busyDays` query
 * EventKit directly and never touch the gated `lastSeen`). The rows are the
 * caller's own RLS-scoped bookings, so nothing leaves the process — but they
 * must still not survive an account switch, which is the other half of this.
 *
 * `CalendarSyncService` itself needs a Context + a live Supabase session, so it
 * isn't reachable from a JVM test (same boundary as DeviceTokenLifecycleTest);
 * this pins the seam its fix moved the behaviour into.
 */
class CalendarSyncCacheTest {

    private val artistA = "aaaaaaaa-0000-4000-8000-000000000001"
    private val artistB = "bbbbbbbb-0000-4000-8000-000000000002"

    /** The failure scenario, from the read side: nothing about the mirror is set up. */
    @Test
    fun aConfirmedGigClashesEvenThoughCalendarSyncWasNeverEnabled() {
        val gig = booking(id = "b1", status = BookingStatus.Confirmed)
        val cache = CalendarSyncCache()

        cache.ingest(artistA, listOf(gig))

        val clashes = cache.clashes(CalendarSyncPlanner.resolvedStartEpochMs(gig)!!)
        assertEquals(1, clashes.size)
        assertEquals("b1", clashes.single().bookingId)
    }

    @Test
    fun aGigOnAnotherNightDoesNotClash() {
        val saturday = booking(id = "b1", status = BookingStatus.Confirmed, date = "Sat, May 16, 2026")
        val sunday = booking(id = "b2", status = BookingStatus.Confirmed, date = "Sun, May 17, 2026")
        val cache = CalendarSyncCache()

        cache.ingest(artistA, listOf(sunday))

        assertTrue(cache.clashes(CalendarSyncPlanner.resolvedStartEpochMs(saturday)!!).isEmpty())
    }

    /** A later fetch of the same row replaces it, so a cancellation stops clashing. */
    @Test
    fun aCancelledGigStopsClashingOnceTheNewRowLands() {
        val gig = booking(id = "b1", status = BookingStatus.Confirmed)
        val cache = CalendarSyncCache()
        cache.ingest(artistA, listOf(gig))

        cache.ingest(artistA, listOf(gig.copy(status = BookingStatus.Cancelled)))

        assertTrue(cache.clashes(CalendarSyncPlanner.resolvedStartEpochMs(gig)!!).isEmpty())
    }

    /** Keys are lowercased — the reconcile looks entries up by the planner's lowercased id. */
    @Test
    fun bookingIdsAreLowercasedSoTheReconcileCanFindThem() {
        val cache = CalendarSyncCache()

        cache.ingest(artistA, listOf(booking(id = "B-1", status = BookingStatus.Confirmed)))

        assertEquals(setOf("b-1"), cache.snapshot().keys)
    }

    /** Client and artist list fetches both land here and merge — that's the point of the cache. */
    @Test
    fun batchesFromDifferentFetchesMergeForTheSameUser() {
        val cache = CalendarSyncCache()

        cache.ingest(artistA, listOf(booking(id = "b1", status = BookingStatus.Confirmed)))
        cache.ingest(artistA, listOf(booking(id = "b2", status = BookingStatus.Confirmed)))

        assertEquals(setOf("b1", "b2"), cache.snapshot().keys)
    }

    /**
     * The security half of dropping the owner gate: an in-process account switch
     * that never reached `clearSessionState()` (an expired session, not the
     * Profile sign-out) must not leave A's gigs answering B's clash reads — nor
     * feed them to a reconcile once B enables the mirror.
     */
    @Test
    fun anAccountSwitchReplacesTheCacheInsteadOfMergingIntoIt() {
        val cache = CalendarSyncCache()
        cache.ingest(artistA, listOf(booking(id = "a-gig", status = BookingStatus.Confirmed)))

        cache.ingest(artistB, listOf(booking(id = "b-gig", status = BookingStatus.Confirmed)))

        assertEquals(setOf("b-gig"), cache.snapshot().keys)
    }

    @Test
    fun signOutDropsEverything() {
        val cache = CalendarSyncCache()
        cache.ingest(artistA, listOf(booking(id = "b1", status = BookingStatus.Confirmed)))

        cache.clear()

        assertTrue(cache.snapshot().isEmpty())
        assertTrue(cache.busyDays().isEmpty())
    }

    /**
     * The reconcile batch runs on IO while repository fetches keep ingesting on
     * Main. It plans from one snapshot and then indexes into that same snapshot,
     * so the copy must not track later writes — an entry that appeared in the
     * plan but vanished before its `Create` ran would be skipped silently.
     */
    @Test
    fun theSnapshotIsACopyAndDoesNotTrackLaterIngests() {
        val cache = CalendarSyncCache()
        cache.ingest(artistA, listOf(booking(id = "b1", status = BookingStatus.Confirmed)))

        val snapshot = cache.snapshot()
        cache.ingest(artistA, listOf(booking(id = "b2", status = BookingStatus.Confirmed)))

        assertEquals(setOf("b1"), snapshot.keys)
        assertEquals(setOf("b1", "b2"), cache.snapshot().keys)
    }

    @Test
    fun busyDaysAreIstDayKeys() {
        val cache = CalendarSyncCache()

        cache.ingest(artistA, listOf(booking(id = "b1", status = BookingStatus.Confirmed)))

        assertEquals(setOf("2026-05-16"), cache.busyDays())
    }
}
