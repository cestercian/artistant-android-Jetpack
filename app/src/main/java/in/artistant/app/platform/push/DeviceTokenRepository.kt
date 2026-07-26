package `in`.artistant.app.platform.push

import android.os.Build
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import `in`.artistant.app.common.util.lowercaseUuid
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists this device's FCM registration token to `public.device_tokens` so the backend's
 * `send-push` path can address it (port of the iOS APNs upsert in `PushService.persistToken`).
 *
 * SEAM STATUS (issue #38, phase P2a): INERT until P2b (a Firebase `FirebaseMessagingService`
 * produces the token) + P1 (backend adds the `device_tokens.fcm_token` column). It COMPILES now
 * and is covered by [FakeDeviceTokenRepository] in tests; the real impl only runs at runtime in
 * P2b/P4, so the not-yet-existing `fcm_token` column is fine (the write is never issued yet).
 */
interface DeviceTokenRepository {
    /** Upsert this device's row for [fcmToken]. Idempotent. No-op when not signed in. */
    suspend fun register(fcmToken: String)

    /** Delete this device's row (e.g. on sign-out / token invalidation). Idempotent. */
    suspend fun unregister(fcmToken: String)
}

@Singleton
class SupabaseDeviceTokenRepository @Inject constructor(
    private val client: SupabaseClient,
) : DeviceTokenRepository {

    // Explicit-column write (no `*`), matching the house rule + the iOS `Row` shape.
    @Serializable
    private data class Row(
        val user_id: String,
        val fcm_token: String,
        val device_model: String?,
        val os_version: String?,
    )

    override suspend fun register(fcmToken: String) {
        // No session → can't attribute the token to a user; skip silently (iOS `persistToken` does
        // the same). P2b re-registers on the next sign-in, so nothing is lost.
        val userId = selfId() ?: return
        client.postgrest.from("device_tokens").upsert(
            Row(
                user_id = userId,
                fcm_token = fcmToken,
                device_model = Build.MODEL,
                os_version = Build.VERSION.RELEASE,
            ),
        ) {
            // Conflict on the token, UPDATE (don't ignore): when the SAME device token is upserted
            // by a different user_id (account switch on this device), the row's ownership MOVES to
            // the new user. `ignoreDuplicates` would leave the token mapped to the prior user and
            // misroute their pushes — the exact iOS CodeRabbit-Critical fix on PR #44.
            onConflict = "fcm_token"
        }
    }

    override suspend fun unregister(fcmToken: String) {
        client.postgrest.from("device_tokens").delete {
            filter { eq("fcm_token", fcmToken) }
        }
    }

    private fun selfId(): String? = client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid()
}

/**
 * In-memory [DeviceTokenRepository] for tests (mirrors the other `Fake*` repos). Records the last
 * registered/unregistered token so a test can assert the seam was exercised, without a Supabase.
 */
class FakeDeviceTokenRepository : DeviceTokenRepository {
    val registered = mutableSetOf<String>()
    override suspend fun register(fcmToken: String) { registered.add(fcmToken) }
    override suspend fun unregister(fcmToken: String) { registered.remove(fcmToken) }
}
