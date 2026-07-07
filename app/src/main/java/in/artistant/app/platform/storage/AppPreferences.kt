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

    /** DPDP §11: wipe all persisted state on delete-account / sign-out. */
    suspend fun wipeAll() {
        context.dataStore.edit { it.clear() }
    }
}
