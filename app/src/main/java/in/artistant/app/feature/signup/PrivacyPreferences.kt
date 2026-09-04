package `in`.artistant.app.feature.signup

import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one privacy switch on design screen 62 that a device preference can actually honour.
 *
 * **Why DataStore and not a column.** The canonical schema
 * (`~/Desktop/ios-swift/supabase/migrations`) has no per-user opt-out for read receipts: they
 * are a `thread_reads` row written by `mark_thread_read` (mig 0072) and nothing sits beside it.
 * Adding one would be a schema change, and schema changes originate in the iOS repo — so a
 * device preference is the honest storage, and the screen says so rather than implying the
 * setting follows the account.
 *
 * A device preference works here because the write is the client's to WITHHOLD: not calling
 * `mark_thread_read` produces no row, and no row is exactly what "don't show when I've read
 * this" means. The city switch that used to live beside it had no such property and is gone —
 * see [PrivacyScreen] for why a local flag cannot hide a column other people read.
 *
 * **What reads it.** Nothing on this branch, and the PR says so: the switch has to be consulted
 * where `mark_thread_read` is called (`feature/messages`, section MS), which is another agent's
 * package this wave. This type is the seam that lands consumes, and [KEY_READ_RECEIPTS] is the
 * key it agrees on. It lives in `feature/signup` because that is the section that owns screen
 * 62, and should move to `platform/storage` the moment a second package injects it.
 *
 * Defaults to ON, which is what the design draws and what the product already does today: a
 * default that differs from current behaviour would silently change how the app works for every
 * existing user the first time this screen shipped.
 */
@Singleton
class PrivacyPreferences @Inject constructor(
    private val prefs: KeyValueStore,
) {
    /** "Show when I've read messages". */
    val readReceipts: Flow<Boolean> = prefs.getString(KEY_READ_RECEIPTS).map { it.toBoolOrDefault() }

    suspend fun setReadReceipts(enabled: Boolean) =
        prefs.setString(KEY_READ_RECEIPTS, enabled.toString())

    /**
     * Absent means ON.
     *
     * `String?.toBoolean()` answers false for null, which is the opposite of what an unset
     * privacy switch should mean here: this describes what the app ALREADY does, so the un-set
     * state has to agree with it. Anything that is not the literal "false" — a null, a value
     * written by an older build, a corrupted entry — reads as on.
     */
    private fun String?.toBoolOrDefault(): Boolean = this != false.toString()

    companion object {
        /** The DataStore key `feature/messages` reads before calling `mark_thread_read`. */
        const val KEY_READ_RECEIPTS = "privacy.read_receipts"
    }
}
