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
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * Key prefixes whose values belong to the DEVICE, not to the signed-in account.
 *
 * `a11y.` is [in.artistant.app.platform.preferences.AccessibilityPreferences], `notify.` is
 * [in.artistant.app.platform.preferences.NotificationPreferences], and `privacy.` is
 * `PrivacyPreferences` — whose own screen says so out loud ("This switch is saved on this
 * device. Artistant has no server-side privacy setting, so it doesn't follow you to another
 * phone"), which made a sign-out that reset it the screen contradicting itself.
 *
 * Kept as prefixes rather than as a list of the seventeen key constants so a new switch does
 * not have to remember to register itself here, and pinned to those classes by a test that
 * walks their real keys.
 */
private val DEVICE_SCOPED_PREFIXES = listOf("a11y.", "notify.", "privacy.")

/**
 * Whether a preference key survives [AppPreferences.wipeAll] — see its contract.
 *
 * A top-level function so the rule is assertable in a JVM test: `wipeAll` itself needs an
 * Android `Context` and a real DataStore file, and the property that matters ("signing out does
 * not undo an accessibility choice") is a property of this predicate.
 */
internal fun isDeviceScopedKey(key: String): Boolean =
    DEVICE_SCOPED_PREFIXES.any { key.startsWith(it) }

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

    /**
     * Generic string read for the small persisted snapshots (search recents, hints).
     *
     * `distinctUntilChanged` because DataStore emits the whole preferences object on every
     * write to ANY key: without it, saving a search recent re-emitted the notification switches,
     * the accessibility flags and the export timestamp, and every collector downstream did its
     * work again over a value that had not moved.
     */
    override fun getString(key: String): Flow<String?> {
        val k = stringPreferencesKey(key)
        return context.dataStore.data.map { it[k] }.distinctUntilChanged()
    }

    override suspend fun setString(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    /**
     * DPDP §11: wipe the persisted ACCOUNT state on delete-account / sign-out.
     *
     * Two exceptions, and both are the same rule — a preference that describes the DEVICE is
     * not the departing account's to take with it.
     *
     * The calendar store is one, and it is a separate DataStore file (see [calendarStore]) that
     * only [wipeCalendar] clears, because the mirrored gigs are the device owner's own events
     * and that map is the only handle left to clean them up.
     *
     * The other is [isDeviceScopedKey] — the accessibility and notification switches. Somebody
     * who turned on "always show labels" because they cannot read an unlabelled tab bar, or who
     * silenced marketing pushes, did not undo that by signing out; erasing it made the app
     * hostile to exactly the person it was for, and the two screens now say out loud that the
     * choices stay. Nothing under these prefixes identifies an account: they are eight booleans
     * about this phone.
     */
    suspend fun wipeAll() {
        context.dataStore.edit { prefs ->
            prefs.asMap().keys.toList()
                .filterNot { isDeviceScopedKey(it.name) }
                .forEach { prefs.remove(it) }
        }
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
