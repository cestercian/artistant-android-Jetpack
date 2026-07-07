package `in`.artistant.app.data.model

import `in`.artistant.app.designsystem.theme.AppRole

/**
 * Snapshot of the signed-in user's persisted `public.users` row, read on LOGIN to
 * hydrate role/name/city/handle (port of iOS `SelfProfile`). The login flow skips
 * the role + profile steps, so without this a returning artist would be dropped
 * into the client app.
 *
 * [role] is nullable because the auth trigger seeds the row with only `(id, phone)`
 * — everything else is filled in when the user finishes the profile step.
 */
data class SelfProfile(
    val role: AppRole?,
    val fullName: String?,
    val city: String?,
    /** Username. null/blank means the user never finished the profile step. */
    val handle: String?,
    /** Only meaningful for artists — whether the EPK wizard was completed
     *  (`public.artists.setup_complete`). null for clients / no artist row. */
    val artistSetupComplete: Boolean?,
) {
    /**
     * "Finished the basic profile" = has a role + a non-blank username. A returning
     * user who is complete must NEVER be re-asked to pick a role or set a username,
     * even if they entered via the "Get started" (signup) path. Artists may still
     * resume the EPK wizard — that's a separate gate the caller checks via
     * [artistSetupComplete].
     */
    val isComplete: Boolean
        get() = role != null && !handle.isNullOrBlank()
}

/**
 * DB rawValue for an [AppRole] — the `public.users.role` text column stores
 * lowercase "client"/"artist" (iOS `AppRole.rawValue`). Kept next to the model
 * so both the repository (write) and the row-decoder (read) share one mapping.
 */
val AppRole.dbValue: String
    get() = when (this) {
        AppRole.Client -> "client"
        AppRole.Artist -> "artist"
    }

/** Parse a `public.users.role` text value back into an [AppRole]; null on unknown. */
fun appRoleFromDb(raw: String?): AppRole? = when (raw) {
    "client" -> AppRole.Client
    "artist" -> AppRole.Artist
    else -> null
}
