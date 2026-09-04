package `in`.artistant.app.feature.signup

import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two privacy switches on design screen 62, stored on the device.
 *
 * **Why DataStore and not a column.** The canonical schema
 * (`~/Desktop/ios-swift/supabase/migrations`) has neither. `users` carries
 * `phone / full_name / avatar_url / city / role / handle / terms_accepted_at` and nothing
 * about visibility; read receipts are a `thread_reads` row written by `mark_thread_read`
 * (mig 0072) with no per-user opt-out beside them. Adding either would be a schema change,
 * and schema changes originate in the iOS repo — so a device preference is the honest
 * storage, and the screen says so rather than implying the setting follows the account.
 *
 * **What reads it.** Nothing yet, and the PR says so. The read-receipt switch has to be read
 * where `mark_thread_read` is called (`feature/messages`) and the city switch where a profile
 * header renders a city (`feature/artist` / `feature/profile`) — both owned by other sections
 * in this redesign wave. This type is the seam they consume; it lives in `feature/signup`
 * because that is the section that owns screen 62, and should move to `platform/storage` the
 * moment a second package injects it.
 *
 * Both default to ON, which is what the design draws and what the product already does today:
 * a default that differs from current behaviour would silently change how the app works for
 * every existing user the first time this screen shipped.
 */
@Singleton
class PrivacyPreferences @Inject constructor(
    private val prefs: AppPreferences,
) {
    /** "Show when I've read messages". */
    val readReceipts: Flow<Boolean> = prefs.getString(KEY_READ_RECEIPTS).map { it.toBoolOrDefault() }

    suspend fun setReadReceipts(enabled: Boolean) =
        prefs.setString(KEY_READ_RECEIPTS, enabled.toString())

    /** "Show my city on my profile". */
    val showCity: Flow<Boolean> = prefs.getString(KEY_SHOW_CITY).map { it.toBoolOrDefault() }

    suspend fun setShowCity(enabled: Boolean) =
        prefs.setString(KEY_SHOW_CITY, enabled.toString())

    /**
     * Absent means ON.
     *
     * `String?.toBoolean()` answers false for null, which is the opposite of what an unset
     * privacy switch should mean here: these describe what the app ALREADY does, so the
     * un-set state has to agree with it. Anything that is not the literal "false" — a null, a
     * value written by an older build, a corrupted entry — reads as on.
     */
    private fun String?.toBoolOrDefault(): Boolean = this != false.toString()

    private companion object {
        const val KEY_READ_RECEIPTS = "privacy.readReceipts"
        const val KEY_SHOW_CITY = "privacy.showCity"
    }
}
