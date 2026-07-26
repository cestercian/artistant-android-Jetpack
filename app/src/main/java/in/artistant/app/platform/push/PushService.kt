package `in`.artistant.app.platform.push

import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.navigation.PushPayloadRouter
import `in`.artistant.app.navigation.TabRouter
import `in`.artistant.app.platform.permissions.isNotificationPermissionGranted
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM registration + push-tap routing — Android port of iOS `PushService`.
 *
 * Token persistence goes through SECURITY DEFINER `claim_device_token` (mig 0075)
 * with `p_fcm` set and `p_apns` null (0069 XOR). Soft-fails when Firebase isn't
 * initialised (no `google-services.json` on this machine).
 */
@Singleton
class PushService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: SupabaseClient,
    private val tabRouter: TabRouter,
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cold launch: if permission already granted, refresh/register the FCM token. */
    fun registerOnLaunchIfPermitted() {
        if (!isNotificationPermissionGranted(context)) return
        fetchAndPersistToken()
    }

    /** Signup "Allow notifications" path — permission just granted. */
    fun registerAfterPermission() {
        fetchAndPersistToken()
    }

    /** `FirebaseMessagingService.onNewToken` / rotation. */
    fun handleNewToken(token: String) {
        if (token.isBlank()) return
        scope.launch {
            prefs.setString(LAST_FCM_TOKEN_KEY, token)
            persistToken(token)
        }
    }

    /**
     * Notification tap / data-message routing. Clears prior transients first so
     * a stale pending* can't leak across events (iOS PushService contract).
     */
    fun handleNotificationPayload(data: Map<String, String>) {
        scope.launch {
            val role = prefs.role.first()
            val action = PushPayloadRouter.route(
                event = data["artistant_event"],
                bookingId = data["artistant_booking_id"],
                threadId = data["artistant_thread_id"],
                requestId = data["artistant_request_id"],
                role = role,
            )
            tabRouter.apply(action)
        }
    }

    /** Sign-out: wipe TabRouter pending channels (prefs wipe is SessionManager's job). */
    fun onSignedOut() {
        tabRouter.clearTransients()
    }

    private fun fetchAndPersistToken() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Timber.i("FCM skipped — Firebase not initialised (drop google-services.json)")
            return
        }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Timber.w(task.exception, "FCM token fetch failed")
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            scope.launch {
                prefs.setString(LAST_FCM_TOKEN_KEY, token)
                persistToken(token)
            }
        }
    }

    private suspend fun persistToken(token: String) {
        if (client.auth.currentSessionOrNull()?.user == null) {
            Timber.d("claim_device_token skipped — not signed in")
            return
        }
        // Exactly one of p_apns / p_fcm — Android always sends FCM.
        val params = buildJsonObject {
            put("p_apns", JsonNull)
            put("p_fcm", token)
            put("p_device_model", Build.MODEL)
            put("p_os_version", Build.VERSION.RELEASE)
        }
        runCatching {
            client.postgrest.rpc("claim_device_token", params)
        }.onFailure { Timber.w(it, "claim_device_token failed") }
    }

    companion object {
        const val LAST_FCM_TOKEN_KEY = "push.lastKnownFcmToken"
    }
}
