package `in`.artistant.app.platform.billing

import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local persistence for the per-user obfuscatedAccountId (iOS keeps it in UserDefaults under
 * `iap.accountToken.<uid>`). A seam so the stable-token generation in [EntitlementStore] is
 * unit-testable with an in-memory fake (the real impl needs a DataStore-backed Context).
 */
interface AccountTokenStore {
    suspend fun read(userId: String): String?
    suspend fun write(userId: String, token: String)
}

@Singleton
class DataStoreAccountTokenStore @Inject constructor(
    private val prefs: AppPreferences,
) : AccountTokenStore {
    // Namespaced per user so re-login as a different account never inherits a token. Not a
    // secret — it only maps to the user via the authenticated binding row — so DataStore is fine.
    private fun key(userId: String) = "iap.accountToken.${userId.lowercase()}"

    override suspend fun read(userId: String): String? = prefs.getString(key(userId)).first()

    override suspend fun write(userId: String, token: String) =
        prefs.setString(key(userId), token)
}
