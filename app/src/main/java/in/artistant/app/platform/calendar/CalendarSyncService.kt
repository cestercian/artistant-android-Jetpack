package `in`.artistant.app.platform.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.platform.auth.SessionManager
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
import timber.log.Timber
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CalendarContract mirror of confirmed gigs — Android port of iOS CalendarSyncService.
 * Persist toggle + bookingId→event map in DataStore; write map only after a successful
 * ContentResolver batch. Owner-user gate prevents cross-account PII leaks.
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
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var persisted = PersistedState()
    private val lastSeen = mutableMapOf<String, Booking>()
    private var reconcileJob: Job? = null

    init {
        scope.launch { load() }
    }

    suspend fun load() {
        val raw = prefs.getString(PREF_KEY).first()
        persisted = raw?.let { runCatching { json.decodeFromString<PersistedState>(it) }.getOrNull() }
            ?: PersistedState()
        refreshUi()
    }

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Called after every bookings list/create/accept/cancel. */
    fun ingest(bookings: List<Booking>) {
        val owner = persisted.ownerUserId
        val me = session.currentUserId
        if (owner != me) return
        for (b in bookings) lastSeen[b.id.lowercase()] = b
        if (!persisted.enabled || !hasWritePermission()) return
        scheduleReconcile()
    }

    fun clearSessionState() {
        lastSeen.clear()
        reconcileJob?.cancel()
        reconcileJob = null
    }

    /**
     * Enable/disable. Returns false when permission is missing so the UI can
     * request it / point at Settings. Disabling removes mirrored events then clears map.
     */
    suspend fun setEnabled(on: Boolean): Boolean {
        if (!on) {
            withContext(Dispatchers.IO) { removeAllSyncedEvents() }
            persisted = persisted.copy(enabled = false)
            save()
            refreshUi()
            return true
        }
        if (!hasWritePermission()) return false
        persisted = persisted.copy(enabled = true, ownerUserId = session.currentUserId)
        save()
        refreshUi()
        reconcileNow()
        return true
    }

    /** Wipe mirrored events + persisted map — call before delete-account wipeAll. */
    suspend fun wipeForAccountDelete() {
        withContext(Dispatchers.IO) { removeAllSyncedEvents() }
        persisted = PersistedState()
        save()
        lastSeen.clear()
        refreshUi()
    }

    private fun scheduleReconcile() {
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            delay(500)
            reconcileNow()
        }
    }

    suspend fun reconcileNow() = mutex.withLock {
        if (!persisted.enabled || !hasWritePermission()) return
        val calendarId = resolveOrCreateCalendarId() ?: return
        if (persisted.calendarId != calendarId) {
            persisted = persisted.copy(calendarId = calendarId)
        }
        val map = persisted.map.mapValues {
            CalendarSyncPlanner.SyncedEvent(it.value.eventId, it.value.fingerprint)
        }
        val actions = CalendarSyncPlanner.plan(lastSeen.values.toList(), map)
        if (actions.isEmpty()) {
            save()
            refreshUi()
            return
        }
        val staged = persisted.map.toMutableMap()
        try {
            withContext(Dispatchers.IO) {
                for (action in actions) {
                    when (action) {
                        is CalendarSyncPlanner.Action.Create -> {
                            val booking = lastSeen[action.bookingId] ?: continue
                            val eventId = insertEvent(calendarId, booking) ?: continue
                            staged[action.bookingId] = SyncedEntry(
                                eventId = eventId.toString(),
                                fingerprint = CalendarSyncPlanner.fingerprint(booking),
                            )
                        }
                        is CalendarSyncPlanner.Action.Update -> {
                            val booking = lastSeen[action.bookingId] ?: continue
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
            }
            persisted = persisted.copy(map = staged, calendarId = calendarId)
            save()
            refreshUi()
        } catch (t: Throwable) {
            Timber.w(t, "CalendarSync reconcile failed — map untouched")
        }
    }

    private suspend fun save() {
        prefs.setString(PREF_KEY, json.encodeToString(persisted))
    }

    private fun refreshUi() {
        _ui.value = UiState(
            enabled = persisted.enabled && hasWritePermission(),
            hasPermission = hasWritePermission(),
            calendarTitle = "Artistant",
        )
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
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xC8FF00.toInt())
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
        const val PREF_KEY = "calendarSync"
        private const val CALENDAR_NAME = "Artistant"
        private const val ACCOUNT_NAME = "artistant@local"
        private val TZ: String = TimeZone.getTimeZone("Asia/Kolkata").id
    }
}
