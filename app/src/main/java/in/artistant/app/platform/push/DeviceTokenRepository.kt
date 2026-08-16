package `in`.artistant.app.platform.push

import android.os.Build
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This device's row in `public.device_tokens` — the address the backend's `send-push` path
 * delivers to. Ports the iOS claim/delete pair (`PushService.persistToken` +
 * `AuthService.unregisterPushToken`).
 *
 * The two calls are a matched pair and BOTH have to fire, or pushes follow the wrong account:
 * [unregister] on sign-out (while the leaving user's session is still live — RLS scopes the
 * delete to their own row), [register] on every completed sign-in (so the device is re-claimed
 * for whoever is here now). [PushService] owns that lifecycle.
 */
interface DeviceTokenRepository {
    /** Claim this device's row for the signed-in user. Idempotent. No-op when not signed in. */
    suspend fun register(fcmToken: String)

    /** Delete this device's row (e.g. on sign-out / token invalidation). Idempotent. */
    suspend fun unregister(fcmToken: String)
}

@Singleton
class SupabaseDeviceTokenRepository @Inject constructor(
    private val client: SupabaseClient,
) : DeviceTokenRepository {

    override suspend fun register(fcmToken: String) {
        // No session → the RPC derives the owner from auth.uid(), so there's nothing to claim
        // the row TO; skip silently (iOS `persistToken` does the same). PushService re-registers
        // on the next completed sign-in, so nothing is lost.
        if (client.auth.currentSessionOrNull()?.user == null) return
        // Deliberately NOT a plain `device_tokens` upsert. Under the device_tokens_owner policy
        // (0002: `USING (auth.uid() = user_id)`) an ON CONFLICT DO UPDATE that targets a row
        // still owned by the PRIOR user fails the UPDATE USING check → 42501, so on an account
        // switch the new user's registration silently failed and they got NO pushes while the
        // stale row survived. `claim_device_token` (mig 0075) is SECURITY DEFINER and bypasses
        // that check: possession of the opaque push token IS the proof of device ownership, so
        // the signed-in caller reclaims the row regardless of who held it. Exactly one of
        // p_apns / p_fcm (0069's XOR) — Android always sends FCM.
        val params = buildJsonObject {
            put("p_apns", JsonNull)
            put("p_fcm", fcmToken)
            put("p_device_model", Build.MODEL)
            put("p_os_version", Build.VERSION.RELEASE)
        }
        client.postgrest.rpc("claim_device_token", params)
    }

    override suspend fun unregister(fcmToken: String) {
        // No SECURITY DEFINER escape hatch here: device_tokens_owner scopes the delete to the
        // caller's own row, which is exactly what we want (one account can't unregister
        // another's device) and exactly why this has to run BEFORE `auth.signOut()`.
        client.postgrest.from("device_tokens").delete {
            filter { eq("fcm_token", fcmToken) }
        }
    }
}

/**
 * In-memory [DeviceTokenRepository] for tests (mirrors the other `Fake*` repos). Models the one
 * thing about `device_tokens` that decides where a push lands: which user a token currently
 * addresses.
 *
 * [currentUserId] stands in for the session the real impl reads through `auth.uid()`, so a test
 * can replay an account switch on one device — including the RLS rule that a delete only reaches
 * the caller's OWN row, which is the reason the release has to beat the sign-out.
 */
class FakeDeviceTokenRepository : DeviceTokenRepository {
    /** The signed-in user, or null once signed out. */
    var currentUserId: String? = null

    /** fcm_token → owning user id. Empty means the backend has nowhere to send. */
    val owners = mutableMapOf<String, String>()

    override suspend fun register(fcmToken: String) {
        val uid = currentUserId ?: return // no session → nothing to claim to
        owners[fcmToken] = uid // claim_device_token reclaims regardless of the prior owner
    }

    override suspend fun unregister(fcmToken: String) {
        val uid = currentUserId ?: return // no session → RLS shows the caller no rows at all
        if (owners[fcmToken] == uid) owners.remove(fcmToken)
    }
}
