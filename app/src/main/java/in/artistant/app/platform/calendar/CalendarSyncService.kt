package `in`.artistant.app.platform.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.designsystem.theme.AppColors
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CompletableDeferred
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
import timber.log.Timber
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CalendarContract mirror of confirmed gigs — Android port of iOS CalendarSyncService.
 * Persist toggle + bookingId→event map in the CALENDAR DataStore (the one sign-out
 * keeps — the map is the only handle on events living outside the app); write the map
 * only after a successful ContentResolver batch. Owner-user gate on the mirror prevents
 * cross-account PII leaks. Read surfaces answer from [CalendarSyncCache], not from here.
 */
@Singleton
class CalendarSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val session: SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class PersistedState(
        val enabled: Boolean = false,
        val calendarId: Long? = null,
        val map: Map<String, SyncedEntry> = emptyMap(),
        val ownerUserId: String? = null,
    )

    @Serializable
    data class SyncedEntry(val eventId: String, val fingerprint: String)

    data class UiState(
        val enabled: Boolean = false,
        val hasPermission: Boolean = false,
        val calendarTitle: String = "Artistant",
        val calendars: List<CalendarOption> = emptyList(),
        val selectedCalendarId: Long? = null,
    )

    data class CalendarOption(val id: Long, val title: String)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /**
     * Toggle + calendarId + bookingId→eventId map. Written from the reconcile's
     * IO batch and read from Main, so it is `@Volatile`; every mutation happens
     * under [mutex] so a toggle landing inside a running reconcile can't have
     * its wipe overwritten by the batch that was already in flight.
     */
    @Volatile
    private var persisted = PersistedState()
    private val cache = CalendarSyncCache()
    private var reconcileJob: Job? = null

    /**
     * Completes once [load] has put the on-disk state into [persisted]. Every
     * mutator awaits it: until the DataStore read lands `persisted` is the
     * default (empty map), and saving THAT over the stored blob would strand
     * every mirrored event — the map is the only handle we have on them.
     */
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch { load() }
    }

    /** Busy day keys (yyyy-MM-dd IST) from ingested confirmed bookings. */
    fun busyDays(): Set<String> = cache.busyDays()

    /** Clashes on the local calendar day containing [epochMs]. */
    fun clashes(onDayOfEpochMs: Long): List<CalendarSyncPlanner.Clash> =
        cache.clashes(onDayOfEpochMs)

    suspend fun listWritableCalendars(): List<CalendarOption> = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext emptyList()
        val out = mutableListOf<CalendarOption>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            ),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                out += CalendarOption(cursor.getLong(idIdx), cursor.getString(nameIdx) ?: "Calendar")
            }
        }
        out
    }

    suspend fun selectCalendar(id: Long): Boolean {
        if (!hasWritePermission()) return false
        loaded.await()
        mutex.withLock {
            persisted = persisted.copy(
                calendarId = id,
                enabled = true,
                ownerUserId = session.currentUserId,
            )
            save()
        }
        refreshUi()
        reconcileNow()
        return true
    }

    suspend fun load() {
        // The blob lives in the calendar store, which sign-out deliberately
        // KEEPS (AppPreferences.calendarStore): the mirrored gigs are the device
        // owner's own calendar events and this map is the only handle to retract
        // them later, so wiping it with the rest of the account state strands
        // them forever. Only delete-account clears it (wipeForAccountDelete).
        var raw = runCatching { prefs.calendarState.first() }.getOrNull()
        // One-time lift: earlier builds wrote the blob into the main store,
        // where the next sign-out's wipeAll() would take it with everything else.
        var migrated = false
        if (raw == null) {
            raw = runCatching { prefs.getString(LEGACY_PREF_KEY).first() }.getOrNull()
            migrated = raw != null
        }
        persisted = raw?.let { runCatching { json.decodeFromString<PersistedState>(it) }.getOrNull() }
            ?: PersistedState()
        // runCatching, like every read above it: a throw on the way to
        // [loaded] would leave it pending and hang every mutator forever.
        if (migrated) runCatching { save() }
        // Only now may a toggle run: before this, `persisted` is the default and
        // saving it would overwrite the stored map with an empty one.
        loaded.complete(Unit)
        refreshUi()
    }

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Called after every bookings list/create/accept/cancel. */
    fun ingest(bookings: List<Booking>) {
        val me = session.currentUserId
        // Cache FIRST, gate second. The clash + busy-day reads answer from the
        // cache and have nothing to do with writing to CalendarContract, so
        // gating it on the sync owner — which stays null until someone turns the
        // mirror on, i.e. for every user by default — meant the double-booking
        // warning on a gig request was always empty. Only the MIRROR carries the
        // owner check (iOS plan-016: a second account's gigs must never reach the
        // first account's calendar).
        cache.ingest(me, bookings)
        if (persisted.ownerUserId != me) return
        if (!persisted.enabled || !hasWritePermission()) return
        scheduleReconcile()
    }

    /**
     * Sign-out / account switch. Drops the in-memory bookings and the pending
     * debounce; deliberately does NOT touch [persisted] — the mirrored events
     * outlive the session and that map is the only way to retract them (the
     * owner gate in [ingest] is what keeps the next account out of them).
     */
    fun clearSessionState() {
        cache.clear()
        reconcileJob?.cancel()
        reconcileJob = null
    }

    /**
     * Enable/disable. Returns false when permission is missing so the UI can
     * request it / point at Settings. Disabling removes mirrored events then clears map.
     */
    suspend fun setEnabled(on: Boolean): Boolean {
        loaded.await()
        if (!on) {
            // Under the same lock as the reconcile: a toggle-off landing inside a
            // running batch used to delete the events and empty the map, then have
            // the resuming batch write its staged map — full of ids that no longer
            // exist — back over it. Re-enabling then planned nothing at all for
            // those gigs, because plan() only Creates when the map has no entry.
            mutex.withLock {
                withContext(Dispatchers.IO) { removeAllSyncedEvents() }
                persisted = persisted.copy(enabled = false)
                save()
            }
            refreshUi()
            return true
        }
        if (!hasWritePermission()) return false
        mutex.withLock {
            persisted = persisted.copy(enabled = true, ownerUserId = session.currentUserId)
            save()
        }
        refreshUi()
        reconcileNow()
        return true
    }

    /** Wipe mirrored events + persisted map — call before delete-account wipeAll. */
    suspend fun wipeForAccountDelete() {
        loaded.await()
        mutex.withLock {
            withContext(Dispatchers.IO) { removeAllSyncedEvents() }
            persisted = PersistedState()
            // The calendar store survives sign-out on purpose, so delete-account
            // is the one path that has to clear it explicitly.
            prefs.wipeCalendar()
        }
        cache.clear()
        refreshUi()
    }

    private fun scheduleReconcile() {
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            delay(500)
            reconcileNow()
        }
    }

    suspend fun reconcileNow() {
        loaded.await()
        mutex.withLock { reconcileLocked() }
    }

    /**
     * The whole body runs on IO: `resolveOrCreateCalendarId` alone is up to four
     * ContentResolver round trips plus a calendar INSERT, and every caller
     * (the debounce on [scope], the Profile toggle on viewModelScope) is Main.
     */
    private suspend fun reconcileLocked(): Unit = withContext(Dispatchers.IO) {
        if (!persisted.enabled || !hasWritePermission()) return@withContext
        val calendarId = resolveOrCreateCalendarId() ?: return@withContext
        if (persisted.calendarId != calendarId) {
            persisted = persisted.copy(calendarId = calendarId)
        }
        // One stable read of the desired state: Main keeps ingesting while this
        // batch runs, and an entry that vanished mid-loop would silently skip.
        val desired = cache.snapshot()
        val map = persisted.map.mapValues {
            CalendarSyncPlanner.SyncedEvent(it.value.eventId, it.value.fingerprint)
        }
        val actions = CalendarSyncPlanner.plan(desired.values.toList(), map)
        if (actions.isEmpty()) {
            save()
            refreshUi()
            return@withContext
        }
        val staged = persisted.map.toMutableMap()
        try {
            for (action in actions) {
                when (action) {
                    is CalendarSyncPlanner.Action.Create -> {
                        val booking = desired[action.bookingId] ?: continue
                        val eventId = insertEvent(calendarId, booking) ?: continue
                        staged[action.bookingId] = SyncedEntry(
                            eventId = eventId.toString(),
                            fingerprint = CalendarSyncPlanner.fingerprint(booking),
                        )
                    }
                    is CalendarSyncPlanner.Action.Update -> {
                        val booking = desired[action.bookingId] ?: continue
                        val eventId = action.eventId.toLongOrNull() ?: continue
                        updateEvent(eventId, calendarId, booking)
                        staged[action.bookingId] = SyncedEntry(
                            eventId = action.eventId,
                            fingerprint = CalendarSyncPlanner.fingerprint(booking),
                        )
                    }
                    is CalendarSyncPlanner.Action.Delete -> {
                        val eventId = action.eventId.toLongOrNull() ?: continue
                        deleteEvent(eventId)
                        staged.remove(action.bookingId)
                    }
                }
            }
            persisted = persisted.copy(map = staged, calendarId = calendarId)
            save()
            refreshUi()
        } catch (t: Throwable) {
            Timber.w(t, "CalendarSync reconcile failed — map untouched")
        }
    }

    private suspend fun save() {
        prefs.setCalendarState(json.encodeToString(persisted))
    }

    private fun refreshUi() {
        scope.launch {
            val calendars = if (hasWritePermission()) listWritableCalendars() else emptyList()
            val title = calendars.firstOrNull { it.id == persisted.calendarId }?.title
                ?: calendars.firstOrNull()?.title
                ?: "Artistant"
            _ui.value = UiState(
                enabled = persisted.enabled && hasWritePermission(),
                hasPermission = hasWritePermission(),
                calendarTitle = title,
                calendars = calendars,
                selectedCalendarId = persisted.calendarId,
            )
        }
    }

    private fun resolveOrCreateCalendarId(): Long? {
        persisted.calendarId?.let { id ->
            if (calendarExists(id)) return id
        }
        // Prefer an existing writable calendar titled Artistant; else primary; else create local.
        val existing = findCalendarByName(CALENDAR_NAME)
        if (existing != null) return existing
        val primary = findPrimaryCalendarId()
        if (primary != null) {
            // Create Artistant calendar under the same account when possible.
            createArtistantCalendar()?.let { return it }
            return primary
        }
        return createArtistantCalendar()
    }

    private fun calendarExists(id: Long): Boolean {
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
            arrayOf(CalendarContract.Calendars._ID),
            null,
            null,
            null,
        )?.use { return it.moveToFirst() }
        return false
    }

    private fun findCalendarByName(name: String): Long? {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == name) return cursor.getLong(idIdx)
            }
        }
        return null
    }

    private fun findPrimaryCalendarId(): Long? {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.IS_PRIMARY} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        // Fallback: any writable calendar.
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun createArtistantCalendar(): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR_ARGB)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TZ)
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        return try {
            val result = context.contentResolver.insert(uri, values)
            result?.let { ContentUris.parseId(it) }
        } catch (t: Throwable) {
            Timber.w(t, "Could not create Artistant calendar")
            null
        }
    }

    private fun insertEvent(calendarId: Long, booking: Booking): Long? {
        val start = CalendarSyncPlanner.resolvedStartEpochMs(booking) ?: return null
        val end = CalendarSyncPlanner.resolvedEndEpochMs(booking) ?: (start + 2 * 60 * 60 * 1000L)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, CalendarSyncPlanner.eventTitle(booking))
            put(CalendarContract.Events.DESCRIPTION, CalendarSyncPlanner.eventNotes(booking.id))
            put(CalendarContract.Events.EVENT_LOCATION, booking.venue)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TZ)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = ContentUris.parseId(uri)
        // Alarms: −24h, −2h (create only — matches iOS).
        insertReminder(eventId, minutesBefore = 24 * 60)
        insertReminder(eventId, minutesBefore = 2 * 60)
        return eventId
    }

    private fun updateEvent(eventId: Long, calendarId: Long, booking: Booking) {
        val start = CalendarSyncPlanner.resolvedStartEpochMs(booking) ?: return
        val end = CalendarSyncPlanner.resolvedEndEpochMs(booking) ?: (start + 2 * 60 * 60 * 1000L)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, CalendarSyncPlanner.eventTitle(booking))
            put(CalendarContract.Events.DESCRIPTION, CalendarSyncPlanner.eventNotes(booking.id))
            put(CalendarContract.Events.EVENT_LOCATION, booking.venue)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TZ)
        }
        context.contentResolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            values,
            null,
            null,
        )
    }

    private fun deleteEvent(eventId: Long) {
        context.contentResolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            null,
            null,
        )
    }

    private fun insertReminder(eventId: Long, minutesBefore: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        runCatching {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        }
    }

    private fun removeAllSyncedEvents() {
        for ((_, entry) in persisted.map) {
            entry.eventId.toLongOrNull()?.let { deleteEvent(it) }
        }
        persisted = persisted.copy(map = emptyMap())
    }

    companion object {
        /**
         * Where the blob used to live: a key in the MAIN store, which sign-out
         * wipes. Read once by [load] to lift a pre-existing map into the calendar
         * store; nothing writes it any more.
         */
        const val LEGACY_PREF_KEY = "calendarSync"
        private const val CALENDAR_NAME = "Artistant"
        private const val ACCOUNT_NAME = "artistant@local"
        private val TZ: String = TimeZone.getTimeZone("Asia/Kolkata").id

        /**
         * The swatch the calendar app paints beside every mirrored gig — the fixed
         * violet accent, taken from the token rather than typed out here.
         *
         * ARGB, and the alpha byte is the point: `CALENDAR_COLOR` is read as ARGB,
         * so a bare RGB literal carries alpha 0 and the provider stores a fully
         * transparent colour — the swatch then reads as nothing at all.
         */
        private val CALENDAR_COLOR_ARGB: Int = AppColors().accent.toArgb()
    }
}
