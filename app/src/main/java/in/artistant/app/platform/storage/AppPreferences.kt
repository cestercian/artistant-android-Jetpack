package `in`.artistant.app.platform.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * The two consents the signup flow collects, both persisted.
 *
 * The terms bit has to outlive the process for the same reason the pledge does:
 * the gate can enter the flow directly at the profile step — after a process
 * kill, or for a signed-in user whose `users` row is incomplete — which skips the
 * welcome screen where the checkbox lives. Held only in memory, the profile save
 * then had nothing to assert and the consent the user really did give was never
 * recorded on a DPDP-scoped product. [AppPreferences.wipeAll] clears both on
 * sign-out, so neither consent is ever inherited by the next account on the
 * device.
 */
interface SignupConsentStore {
    /** ACCT-05 — one-time community pledge before role pick. */
    val communityAgreed: Flow<Boolean>
    suspend fun setCommunityAgreed(agreed: Boolean)

    /** Terms + privacy policy accepted on the welcome screen. */
    val termsAccepted: Flow<Boolean>
    suspend fun setTermsAccepted(accepted: Boolean)
}

/**
 * The generic small-snapshot key/value half of [AppPreferences], behind an interface.
 *
 * Same reason as [SignupConsentStore] above it, and the same one implementation: a preference
 * wrapper that reads and writes through this can be round-tripped in a JVM test, where
 * `AppPreferences` itself needs an Android `Context` and a real DataStore file. Keys stay the
 * caller's — this holds no schema of its own.
 */
interface KeyValueStore {
    fun getString(key: String): Flow<String?>
    suspend fun setString(key: String, value: String)
}

/**
 * Thin DataStore (Preferences) wrapper — replaces iOS UserDefaults/Persistence.
 * Holds the role plus a generic string get/set for small snapshots. `wipeAll()`
 * clears the main store on sign-out / delete-account; the calendar store is
 * separate and only `wipeCalendar()` clears it.
 */
@Singleton
class AppPreferences @Inject constructor(
    // Qualified: SingletonComponent binds `@ApplicationContext Context`, never a bare
    // one. Unqualified, this constructor could not be satisfied at all — the class was
    // only constructible because a hand-written `@Provides` in AppModule shadowed it,
    // so the first direct injection would have failed with a missing-binding error.
    @ApplicationContext private val context: Context,
) : SignupConsentStore, KeyValueStore {
    private val roleKey = stringPreferencesKey("role")
    private val communityAgreedKey = booleanPreferencesKey("community.agreed")
    private val communityAgreedDateKey = longPreferencesKey("community.agreedDate")
    private val termsAcceptedKey = booleanPreferencesKey("terms.accepted")

    val role: Flow<AppRole> = context.dataStore.data.map { prefs ->
        when (prefs[roleKey]) {
            AppRole.Artist.name -> AppRole.Artist
            else -> AppRole.Client // default + any unknown value
        }
    }

    override val communityAgreed: Flow<Boolean> =
        context.dataStore.data.map { it[communityAgreedKey] == true }

    override suspend fun setCommunityAgreed(agreed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[communityAgreedKey] = agreed
            if (agreed) prefs[communityAgreedDateKey] = System.currentTimeMillis()
        }
    }

    override val termsAccepted: Flow<Boolean> =
        context.dataStore.data.map { it[termsAcceptedKey] == true }

    override suspend fun setTermsAccepted(accepted: Boolean) {
        context.dataStore.edit { it[termsAcceptedKey] = accepted }
    }

    suspend fun setRole(role: AppRole) {
        context.dataStore.edit { it[roleKey] = role.name }
    }

    /** Generic string read for the small persisted snapshots (search recents, hints). */
    override fun getString(key: String): Flow<String?> {
        val k = stringPreferencesKey(key)
        return context.dataStore.data.map { it[k] }
    }

    override suspend fun setString(key: String, value: String) {
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
