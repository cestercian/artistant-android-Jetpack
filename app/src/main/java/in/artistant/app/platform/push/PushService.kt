package `in`.artistant.app.platform.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.navigation.PushPayloadRouter
import `in`.artistant.app.navigation.TabRouter
import `in`.artistant.app.platform.permissions.isNotificationPermissionGranted
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM registration + push-tap routing — Android port of iOS `PushService`.
 *
 * Owns this device's `device_tokens` lifecycle through [DeviceTokenRepository]: claim on every
 * completed sign-in (and cold launch), release on sign-out. Both halves are load-bearing — a
 * token that is never released keeps delivering the signed-out account's message previews and
 * booking pushes to whoever holds the phone next. Soft-fails when Firebase isn't initialised
 * (no `google-services.json` on this machine).
 */
@Singleton
class PushService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceTokens: DeviceTokenRepository,
    private val tabRouter: TabRouter,
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Cold launch or a completed sign-in: if permission is already granted, refresh the FCM
     * token and (re-)claim this device's row for whoever is signed in now. Cheap and idempotent,
     * so it's safe on every sign-in — which is the point: a returning user's sign-in doesn't
     * pass through `ArtistantApplication.onCreate`, and without a re-claim the device stays
     * addressed by the previous account (or by nobody, after a sign-out released the row).
     */
    fun registerIfPermitted() {
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
     * Notification **TAP** routing — `MainActivity`'s launcher/`onNewIntent` extras, and
     * nothing else. A routable event clears prior transients before arming its own, so a
     * stale pending* can't leak across events (iOS PushService contract); a payload that
     * routes to `Ignore` leaves them alone rather than cancelling an earlier tap's deep
     * link on its way to doing nothing — see [TabRouter.apply].
     *
     * Deliberately NOT called when a push arrives: this ends in [TabRouter.apply], which
     * selects a tab and sets the pending deep-link id, so calling it from
     * `FirebaseMessagingService.onMessageReceived` navigated the user on RECEIPT — off a
     * half-finished booking form and into a thread they never asked for. Receipt posts a
     * notification instead ([ArtistantMessagingService]); this runs when the user taps it.
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

    /**
     * Sign-out teardown, called BEFORE `auth.signOut()`: release this device's `device_tokens`
     * row so the leaving account stops addressing this phone, then wipe the TabRouter pending
     * channels. (The prefs wipe stays SessionManager's job.)
     *
     * The ordering is the whole fix, and it's the iOS `AuthService.signOut()` ordering: the
     * delete is RLS-scoped to the caller's own row so it needs the session that `signOut()` is
     * about to tear down, and it has to beat `prefs.wipeAll()`, which clears
     * [LAST_FCM_TOKEN_KEY] and would leave nothing to unregister WITH. Best-effort — an offline
     * sign-out must still succeed, and a surviving stale row is the pre-existing behaviour.
     */
    suspend fun onSigningOut() {
        runCatching {
            val token = prefs.getString(LAST_FCM_TOKEN_KEY).first()
            if (!token.isNullOrBlank()) deviceTokens.unregister(token)
        }.onFailure { Timber.w(it, "device_tokens release failed (ignored)") }
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
        runCatching { deviceTokens.register(token) }
            .onFailure { Timber.w(it, "claim_device_token failed") }
    }

    companion object {
        const val LAST_FCM_TOKEN_KEY = "push.lastKnownFcmToken"
    }
}
