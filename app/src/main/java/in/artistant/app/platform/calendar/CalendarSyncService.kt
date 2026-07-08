package `in`.artistant.app.platform.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.resolvedEnd
import `in`.artistant.app.data.model.resolvedStart
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Android calendar mirror — the Calendar-Provider (`CalendarContract`) port of iOS
 * `CalendarSyncService`. Replaces `NoopCalendarSync`. Mirrors the signed-in user's
 * confirmed gigs into the OS calendar store via `ContentResolver`; Android relays them to
 * whatever account (Google / Exchange) the phone already syncs — no Calendar API, no OAuth.
 *
 * Data flow: `SupabaseBookingsRepository` calls [ingest] after every fetch / create / cancel
 * (the single seam every consumer routes through — client + artist), which merges rows into
 * an in-memory cache and debounce-schedules [reconcileNow]. Reconcile diffs desired state
 * against the persisted booking→event map via the PURE [CalendarPlanner] and applies the
 * plan to the Provider.
 *
 * device: every ContentResolver path below is device-dependent (a real Calendar Provider,
 * granted permission, at least one writable calendar) and is exercised on-device, not in the
 * JVM unit tests. The tested core is [CalendarPlanner] (pure) — see CalendarPlannerTest.
 */
@Singleton
class CalendarSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : CalendarSync {

    // Long-lived scope for the debounce timer. SupervisorJob so a failed reconcile child
    // doesn't tear the scope down. IO because every reconcile touches the Provider.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cr: ContentResolver get() = context.contentResolver

    // --- Published toggle surface ---------------------------------------

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _targetCalendarId = MutableStateFlow<Long?>(null)
    override val targetCalendarId: StateFlow<Long?> = _targetCalendarId.asStateFlow()

    // --- Persisted + in-memory state ------------------------------------

    /** Everything worth surviving a relaunch, as one DataStore blob (iOS `State`). Lives in a
     *  DEDICATED DataStore (`AppPreferences.calendarState`) NOT touched by the sign-out
     *  `wipeAll()` — the mirrored events are the device owner's own gigs and the map is the
     *  only handle to clean them up later, so a sign-out must keep both. Only delete-account
     *  ([wipeForAccountDeletion]) wipes it. */
    @Serializable
    private data class PersistedState(
        val enabled: Boolean = false,
        val targetCalendarId: Long? = null,
        val map: Map<String, SyncedEvent> = emptyMap(),
    )

    private var state = PersistedState()
    private var loaded = false

    /** Last-seen bookings by id, merged across every repository fetch this launch (client +
     *  artist). In-memory only — a stale desired-state snapshot is worse than waiting for the
     *  next fetch, so it is never persisted. */
    private val lastSeen = LinkedHashMap<String, Booking>()

    /** Serializes state mutation + the Provider commit so two reconciles can't interleave. */
    private val mutex = Mutex()

    /** Debounce handle — Home + Gigs fetches land back-to-back; one reconcile 500ms later covers both. */
    private var reconcileJob: Job? = null

    init {
        // Hydrate the persisted blob into the live flows at construction (DataStore is async,
        // so unlike iOS's synchronous init we load off-thread). Seeds `_isEnabled` so a mirror
        // that was ON before the app was killed resumes: without this, `ingest` would see the
        // toggle as off and skip scheduling until something else forced a load.
        scope.launch {
            ensureLoaded()
            if (_isEnabled.value) scheduleReconcile() // in case bookings were ingested pre-load
        }
    }

    // --- Repository seam -------------------------------------------------

    override fun ingest(bookings: List<Booking>) {
        // Cheap when the toggle is off — just the merge, no Provider work.
        for (b in bookings) lastSeen[b.id] = b
        if (!_isEnabled.value) return
        scheduleReconcile()
    }

    private fun scheduleReconcile() {
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            delay(500)
            reconcileNow()
        }
    }

    // --- Toggle / target -------------------------------------------------

    override suspend fun setEnabled(on: Boolean): Boolean = withContext(Dispatchers.IO) {
        ensureLoaded()
        if (!on) {
            mutex.withLock {
                removeAllSyncedEventsLocked() // delete only what WE created (the map is the definition of "ours")
                state = state.copy(enabled = false, map = emptyMap())
                persistLocked()
            }
            return@withContext true
        }
        // Turning ON needs the write grant already in hand — the settings row requests it
        // just-in-time before calling this. Deny → false so the toggle snaps back.
        if (!hasWritePermission()) return@withContext false
        mutex.withLock {
            state = state.copy(enabled = true)
            persistLocked()
        }
        reconcileNow()
        true
    }

    override suspend fun setTarget(calendarId: Long) = withContext(Dispatchers.IO) {
        ensureLoaded()
        if (state.targetCalendarId == calendarId) return@withContext
        mutex.withLock {
            // Events move by delete-from-old + recreate-in-new (the Provider has no
            // cross-calendar move that survives every account type).
            removeAllSyncedEventsLocked()
            state = state.copy(targetCalendarId = calendarId, map = emptyMap())
            persistLocked()
        }
        reconcileNow()
    }

    override suspend fun writableCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        queryWritableCalendars().map { CalendarInfo(it.id, it.name, it.account) }
    }

    override suspend fun wipeForAccountDeletion() = withContext(Dispatchers.IO) {
        ensureLoaded()
        mutex.withLock {
            removeAllSyncedEventsLocked() // best-effort: no-op without permission
            state = PersistedState()
            prefs.wipeCalendar()
            _isEnabled.value = false
            _targetCalendarId.value = null
        }
    }

    // --- Reconcile -------------------------------------------------------

    override suspend fun reconcileNow(): Unit = withContext(Dispatchers.IO) {
        ensureLoaded()
        if (!state.enabled || !hasWritePermission()) return@withContext
        mutex.withLock {
            val calId = resolveTargetCalendarIdLocked() ?: return@withLock
            val actions = CalendarPlanner.plan(lastSeen.values.toList(), state.map)
            if (actions.isEmpty()) return@withLock

            // WRITE-MAP-AFTER-COMMIT (the iOS lesson): each Provider op is its own
            // transaction. We touch `newMap` for an id ONLY after that id's op actually
            // succeeded — a failed insert/update/delete leaves the map untouched, so the
            // next reconcile re-plans the same action (self-healing) instead of recording a
            // phantom event or a fingerprint the calendar never received.
            val newMap = state.map.toMutableMap()
            for (action in actions) {
                when (action) {
                    is CalendarAction.Create -> {
                        val b = lastSeen[action.bookingId] ?: continue
                        val eventId = insertEvent(b, calId) ?: continue
                        addReminders(eventId)
                        newMap[action.bookingId] = SyncedEvent(eventId, CalendarPlanner.fingerprint(b))
                    }
                    is CalendarAction.Update -> {
                        val b = lastSeen[action.bookingId] ?: continue
                        // Re-find via the marker if the stored _ID no longer resolves (user
                        // deleted it, or the Provider reassigned it) — recreate as a last resort.
                        val eventId = updateOrRecreate(b, action.eventId, calId) ?: continue
                        newMap[action.bookingId] = SyncedEvent(eventId, CalendarPlanner.fingerprint(b))
                    }
                    is CalendarAction.Delete -> {
                        deleteEvent(action.eventId, action.bookingId)
                        newMap.remove(action.bookingId)
                    }
                }
            }
            state = state.copy(map = newMap)
            persistLocked()
        }
    }

    /** Toggle-off / retarget / delete-account cleanup: delete every event WE created (and only
     *  that — the map IS the definition of "ours"). No-op without the write grant. Caller holds
     *  the lock; does not clear the map itself (callers set the fresh state). */
    private fun removeAllSyncedEventsLocked() {
        if (!hasWritePermission()) return
        for ((bookingId, entry) in state.map) {
            deleteEvent(entry.eventId, bookingId)
        }
    }

    // --- Calendar Provider plumbing (device-dependent) ------------------

    private fun insertEvent(b: Booking, calendarId: Long): Long? {
        val values = eventValues(b, calendarId)
        val uri = runCatching { cr.insert(Events.CONTENT_URI, values) }.getOrNull() ?: return null
        return ContentUris.parseId(uri).takeIf { it > 0 }
    }

    private fun updateOrRecreate(b: Booking, eventId: Long, calendarId: Long): Long? {
        val values = eventValues(b, calendarId)
        val rows = runCatching {
            cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null)
        }.getOrDefault(0)
        if (rows > 0) return eventId
        // Stored _ID gone — try the re-find marker, then recreate.
        val found = findEventIdByMarker(b.id)
        if (found != null) {
            val r = runCatching {
                cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, found), values, null, null)
            }.getOrDefault(0)
            if (r > 0) return found
        }
        val recreated = insertEvent(b, calendarId) ?: return null
        addReminders(recreated) // recreate is a fresh event → restore the default alarms
        return recreated
    }

    private fun deleteEvent(eventId: Long, bookingId: String) {
        val rows = runCatching {
            cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
        }.getOrDefault(0)
        if (rows > 0) return
        // _ID reassigned — fall back to the marker so we don't strand the event.
        val found = findEventIdByMarker(bookingId) ?: return
        runCatching { cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, found), null, null) }
    }

    /** Field mapping shared by insert + update. The CUSTOM_APP_URI marker (+ its required
     *  package) is our re-find handle when a Provider _ID churns; alarms are set separately on
     *  create only so an update can't stomp reminders the user tuned by hand. */
    private fun eventValues(b: Booking, calendarId: Long): ContentValues {
        val start = b.resolvedStart ?: Instant.now()
        val end = b.resolvedEnd ?: start.plusSeconds(2 * 3600)
        return ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            // Artist rows carry the client name; client rows fall back to the venue.
            put(Events.TITLE, "Gig — ${b.clientFullName ?: b.venue}")
            put(Events.EVENT_LOCATION, b.venue)
            put(Events.DTSTART, start.toEpochMilli())
            put(Events.DTEND, end.toEpochMilli())
            // Gigs happen in India regardless of where the phone travels.
            put(Events.EVENT_TIMEZONE, KOLKATA.id)
            put(Events.DESCRIPTION, "Booked on Artistant.")
            put(Events.CUSTOM_APP_URI, marker(b.id))
            put(Events.CUSTOM_APP_PACKAGE, context.packageName)
        }
    }

    private fun addReminders(eventId: Long) {
        // −24h and −2h, matching the cadence cron-show-reminder pushes at (iOS parity).
        for (minutes in intArrayOf(24 * 60, 2 * 60)) {
            val values = ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, minutes)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            }
            runCatching { cr.insert(Reminders.CONTENT_URI, values) } // best-effort
        }
    }

    private fun findEventIdByMarker(bookingId: String): Long? {
        val cursor = runCatching {
            cr.query(
                Events.CONTENT_URI,
                arrayOf(Events._ID),
                "${Events.CUSTOM_APP_URI} = ?",
                arrayOf(marker(bookingId)),
                null,
            )
        }.getOrNull() ?: return null
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    private data class CalRow(val id: Long, val name: String, val account: String, val primary: Boolean)

    private fun queryWritableCalendars(): List<CalRow> {
        val cursor = runCatching {
            cr.query(Calendars.CONTENT_URI, CALENDAR_PROJECTION, null, null, null)
        }.getOrNull() ?: return emptyList()
        val out = mutableListOf<CalRow>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val access = c.getInt(3)
                // CONTRIBUTOR (500) and above can add events; anything lower is read-only.
                if (access < Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                out += CalRow(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Calendar",
                    account = c.getString(2) ?: "",
                    primary = c.getInt(4) == 1,
                )
            }
        }
        return out
    }

    /** The calendar events land in: the stored pick if still writable, else the primary
     *  writable calendar, else the first writable one. Null when none is writable. Persists the
     *  auto-pick so later reconciles stay stable. Unlike iOS we do NOT create a dedicated
     *  "Artistant" calendar — the Provider requires a sync account + CALLER_IS_SYNCADAPTER to
     *  create one, so we target an existing writable calendar and let the picker retarget. */
    private fun resolveTargetCalendarIdLocked(): Long? {
        val writable = queryWritableCalendars()
        val current = state.targetCalendarId
        if (current != null && writable.any { it.id == current }) return current
        val chosen = (writable.firstOrNull { it.primary } ?: writable.firstOrNull())?.id ?: return null
        state = state.copy(targetCalendarId = chosen)
        _targetCalendarId.value = chosen
        return chosen
    }

    // --- Clash / busy reads (the read direction, READ_CALENDAR only) ----

    override fun clashes(onDayOf: LocalDate, excludingBookingId: String?): List<Clash> {
        if (!hasReadPermission()) return emptyList()
        val start = onDayOf.atStartOfDay(KOLKATA)
        val end = start.plusDays(1)
        val excludeMarker = excludingBookingId?.let { marker(it) }
        val out = mutableListOf<Clash>()
        queryInstances(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) == 1) continue // all-day would false-positive every date
                val uri = c.getString(4)
                if (excludeMarker != null && uri == excludeMarker) continue // a gig can't clash with itself
                out += Clash(
                    title = c.getString(0) ?: "Busy",
                    window = windowString(c.getLong(1), c.getLong(2)),
                )
            }
        }
        return out
    }

    override fun busyDays(from: LocalDate, days: Int): Set<LocalDate> {
        if (!hasReadPermission()) return emptySet()
        val rangeStart = from.atStartOfDay(KOLKATA)
        val rangeEnd = rangeStart.plusDays(days.toLong())
        val out = mutableSetOf<LocalDate>()
        queryInstances(rangeStart.toInstant().toEpochMilli(), rangeEnd.toInstant().toEpochMilli())?.use { c ->
            while (c.moveToNext()) {
                // Exclude events WE mirrored (they already render as Booked on the strip).
                val uri = c.getString(4)
                if (uri != null && uri.startsWith(MARKER_PREFIX)) continue
                val beginMs = c.getLong(1)
                val endMs = c.getLong(2)
                var day = maxOf(Instant.ofEpochMilli(beginMs).atZone(KOLKATA).toLocalDate(), from)
                // end is exclusive-ish: subtract 1ms so a midnight-to-midnight event doesn't
                // shade the following day. INCLUDES all-day events (holidays are exactly what
                // an artist forgets when accepting a gig) — unlike clashes().
                val lastDay = Instant.ofEpochMilli(if (endMs > beginMs) endMs - 1 else beginMs)
                    .atZone(KOLKATA).toLocalDate()
                while (!day.isAfter(lastDay) && day.isBefore(rangeEnd.toLocalDate())) {
                    out += day
                    day = day.plusDays(1)
                }
            }
        }
        return out
    }

    private fun queryInstances(beginMs: Long, endMs: Long) =
        runCatching { Instances.query(cr, INSTANCE_PROJECTION, beginMs, endMs) }.getOrNull()

    private fun windowString(beginMs: Long, endMs: Long): String {
        val s = Instant.ofEpochMilli(beginMs).atZone(KOLKATA).format(TIME_FMT)
        if (endMs <= beginMs) return s
        return "$s – ${Instant.ofEpochMilli(endMs).atZone(KOLKATA).format(TIME_FMT)}"
    }

    // --- Persistence + permission helpers -------------------------------

    /** Load the persisted blob once (DataStore is a Flow, so read the first value). Guarded so
     *  concurrent callers load exactly once; seeds the published flows from the loaded state. */
    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            val json = prefs.calendarState.first()
            state = json?.let { runCatching { JSON.decodeFromString<PersistedState>(it) }.getOrNull() }
                ?: PersistedState()
            loaded = true
            _isEnabled.value = state.enabled && hasWritePermission()
            _targetCalendarId.value = state.targetCalendarId
        }
    }

    /** Caller holds the lock. Writes the blob + republishes the toggle/target flows. */
    private suspend fun persistLocked() {
        prefs.setCalendarState(JSON.encodeToString(state))
        _isEnabled.value = state.enabled && hasWritePermission()
        _targetCalendarId.value = state.targetCalendarId
    }

    private fun hasReadPermission() = granted(Manifest.permission.READ_CALENDAR)
    private fun hasWritePermission() =
        granted(Manifest.permission.READ_CALENDAR) && granted(Manifest.permission.WRITE_CALENDAR)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun marker(bookingId: String) = "$MARKER_PREFIX$bookingId"

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private val KOLKATA: ZoneId = ZoneId.of("Asia/Kolkata")
        private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private const val MARKER_PREFIX = "in.artistant.app://booking/"

        // Shared Instances projection — fixed indices used by clashes()/busyDays().
        private val INSTANCE_PROJECTION = arrayOf(
            Instances.TITLE,          // 0
            Instances.BEGIN,          // 1
            Instances.END,            // 2
            Instances.ALL_DAY,        // 3
            Instances.CUSTOM_APP_URI, // 4
        )
        private val CALENDAR_PROJECTION = arrayOf(
            Calendars._ID,                    // 0
            Calendars.CALENDAR_DISPLAY_NAME,  // 1
            Calendars.ACCOUNT_NAME,           // 2
            Calendars.CALENDAR_ACCESS_LEVEL,  // 3
            Calendars.IS_PRIMARY,             // 4
        )
    }
}
