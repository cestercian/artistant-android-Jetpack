package `in`.artistant.app.platform.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// One process-wide DataStore under the `artistant.state.*` namespace (the iOS
// Persistence port). Top-level delegate = DataStore's required singleton-per-file rule.
private val Context.dataStore by preferencesDataStore(name = "artistant.state")

// Calendar-sync state lives in its OWN DataStore, deliberately SEPARATE from the one
// wipeAll() clears: the mirrored gigs are the device owner's own events and the map is
// the only handle to clean them up later, so a sign-out must keep both. Only
// delete-account (wipeCalendar) clears it. This mirrors iOS keeping the calendar
// Persistence blob across sign-out and wiping it only on account deletion.
private val Context.calendarStore by preferencesDataStore(name = "artistant.calendar")

/**
 * Thin DataStore (Preferences) wrapper — replaces iOS UserDefaults/Persistence.
 * Holds the role plus a generic string get/set for small snapshots. `wipeAll()`
 * clears everything on sign-out / delete-account.
 */
@Singleton
class AppPreferences @Inject constructor(
    private val context: Context,
) {
    private val roleKey = stringPreferencesKey("role")

    val role: Flow<AppRole> = context.dataStore.data.map { prefs ->
        when (prefs[roleKey]) {
            AppRole.Artist.name -> AppRole.Artist
            else -> AppRole.Client // default + any unknown value
        }
    }

    suspend fun setRole(role: AppRole) {
        context.dataStore.edit { it[roleKey] = role.name }
    }

    /** Generic string read for the small persisted snapshots (search recents, hints). */
    fun getString(key: String): Flow<String?> {
        val k = stringPreferencesKey(key)
        return context.dataStore.data.map { it[k] }
    }

    suspend fun setString(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    /** DPDP §11: wipe all persisted state on delete-account / sign-out. Does NOT touch the
     *  calendar store — that survives sign-out on purpose (see [calendarStore]); use
     *  [wipeCalendar] for the delete-account path. */
    suspend fun wipeAll() {
        context.dataStore.edit { it.clear() }
    }

    // --- Calendar-sync blob (CalendarSyncService's PersistedState as one JSON string) ---

    /** The persisted calendar-sync state blob (null before the first write). */
    val calendarState: Flow<String?> =
        context.calendarStore.data.map { it[calendarStateKey] }

    suspend fun setCalendarState(json: String) {
        context.calendarStore.edit { it[calendarStateKey] = json }
    }

    /** Delete-account only: wipe the calendar-sync state (sign-out deliberately keeps it). */
    suspend fun wipeCalendar() {
        context.calendarStore.edit { it.clear() }
    }

    private val calendarStateKey = stringPreferencesKey("calendar_state")
}
