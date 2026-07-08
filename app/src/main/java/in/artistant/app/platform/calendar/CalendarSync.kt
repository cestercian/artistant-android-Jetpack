package `in`.artistant.app.platform.calendar

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedEnd
import `in`.artistant.app.data.model.resolvedStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Calendar-sync seam (port of iOS `CalendarSyncService`). The real Android impl is
 * [CalendarSyncService], backed by the system Calendar Provider (ContentResolver on
 * `CalendarContract`). We write to the OS calendar store and let Android relay to
 * whatever Google / Exchange account the phone already syncs — no Google Calendar
 * API, no OAuth, no server tokens.
 *
 * Two independent calendar paths (both kept): [AddToCalendarButton] is the
 * zero-permission one-shot `ACTION_INSERT`; this is the permissioned auto-mirror the
 * user flips on in Profile.
 */
interface CalendarSync {
    /** Toggle state as the UI shows it (enabled AND still permitted). Drives the switch. */
    val isEnabled: StateFlow<Boolean>

    /** The user's chosen target calendar's id (null → not chosen / falls back to primary). */
    val targetCalendarId: StateFlow<Long?>

    /**
     * Flip sync on/off. Turning ON requires READ_CALENDAR + WRITE_CALENDAR already
     * granted — the caller (the settings row) requests them just-in-time before this,
     * and this returns false when they're still denied so the toggle snaps back.
     * Turning OFF removes every event this service created and clears the map.
     */
    suspend fun setEnabled(on: Boolean): Boolean

    /** Repository seam: merge the latest bookings into last-seen + debounce a reconcile. */
    fun ingest(bookings: List<Booking>)

    /** Apply the plan against the Calendar Provider now (drive a deterministic pass). */
    suspend fun reconcileNow()

    /** Writable calendars the mirror may target (empty without READ permission). */
    suspend fun writableCalendars(): List<CalendarInfo>

    /** Retarget the mirror: move events by delete-from-old + recreate-in-new. */
    suspend fun setTarget(calendarId: Long)

    /** Personal-calendar events overlapping [onDayOf] (excludes our own mirror + all-day). */
    fun clashes(onDayOf: LocalDate, excludingBookingId: String? = null): List<Clash>

    /** Days in [from, from+days) the user's own calendars carry an event (incl. all-day). */
    fun busyDays(from: LocalDate, days: Int = 14): Set<LocalDate>

    /**
     * Delete-account wipe (DPDP §11): best-effort remove every mirrored event, then clear
     * the persisted calendar state. Deliberately NOT called on sign-out — the events are
     * the device owner's own gigs, and the map is the only handle to clean them up later,
     * so a sign-out keeps both (iOS `Persistence` survives sign-out, wiped only by delete).
     */
    suspend fun wipeForAccountDeletion()
}

/** A writable calendar the picker offers (Provider `_ID` + display + owning account). */
data class CalendarInfo(val id: Long, val displayName: String, val accountName: String)

/** A personal-calendar event overlapping a prospective gig day (read direction). */
data class Clash(val title: String, val window: String)

/**
 * One synced event: the Provider event `_ID` + a content fingerprint so an unchanged
 * booking is a no-op on every reconcile. Serialized (with the whole state) to DataStore.
 */
@Serializable
data class SyncedEvent(val eventId: Long, val fingerprint: String)

/** The three reconcile outcomes for a single booking (port of iOS `CalendarSyncService.Action`). */
sealed interface CalendarAction {
    val bookingId: String
    data class Create(override val bookingId: String) : CalendarAction
    data class Update(override val bookingId: String, val eventId: Long) : CalendarAction
    data class Delete(override val bookingId: String, val eventId: Long) : CalendarAction
}

/**
 * PURE reconcile planner — separated from the Calendar Provider so it's unit-testable
 * with zero device dependencies (mirrors iOS's `nonisolated static func plan`).
 *
 * POLICY (verbatim port of iOS):
 *  - confirmed / completed / disputed → mirrored: create if absent, update if the
 *    fingerprint changed, no-op if identical (completed stays — deleting a past gig
 *    would erase the user's history).
 *  - cancelled → delete if we have a mapped event.
 *  - pending_confirm → NEVER mirrored (no tentative noise).
 *  - a booking with no resolvable start → skipped (can't write an undated event).
 *
 * SAFETY-CRITICAL INVARIANT: a booking id present in [persisted] but ABSENT from
 * [desired] is left completely UNTOUCHED — it is never planned for deletion. Only ids
 * the caller passes in [desired], and only when cancelled, are ever deleted. This is
 * what stops a paginated / partial / post-sign-out booking list from mass-deleting the
 * user's calendar. Deletion is opt-in per id, never inferred from absence.
 */
object CalendarPlanner {

    fun plan(desired: List<Booking>, persisted: Map<String, SyncedEvent>): List<CalendarAction> {
        val actions = mutableListOf<CalendarAction>()
        for (b in desired) {
            val entry = persisted[b.id]
            when (b.status) {
                BookingStatus.Confirmed, BookingStatus.Completed, BookingStatus.Disputed -> {
                    // An undated booking can't become an event — skip rather than write a lie.
                    if (b.resolvedStart == null) continue
                    if (entry != null) {
                        if (entry.fingerprint != fingerprint(b)) {
                            actions += CalendarAction.Update(b.id, entry.eventId)
                        } // else: identical → no-op
                    } else {
                        actions += CalendarAction.Create(b.id)
                    }
                }
                BookingStatus.Cancelled -> {
                    if (entry != null) actions += CalendarAction.Delete(b.id, entry.eventId)
                }
                BookingStatus.PendingConfirm -> Unit // never mirrored
            }
            // NB: no branch deletes an id that isn't in `desired` — absence is not deletion.
        }
        return actions
    }

    /**
     * Stable hash of the mirrored fields (title-source / start / end / venue / status).
     * `Instant.toString()` is ISO-8601 and immutable/thread-safe (the Kotlin analogue of
     * iOS's Sendable-safe `Date.ISO8601Format()`) — a plain formatter would be a shared
     * mutable-state hazard. Status is included so a cancel→uncancel round-trip re-renders.
     */
    fun fingerprint(b: Booking): String = listOf(
        b.resolvedStart?.toString().orEmpty(),
        b.resolvedEnd?.toString().orEmpty(),
        b.venue,
        b.clientFullName.orEmpty(),
        b.status.dbValue,
    ).joinToString("|")
}
