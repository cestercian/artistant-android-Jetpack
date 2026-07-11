package `in`.artistant.app.platform.auth

import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromFragment
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import com.google.firebase.messaging.FirebaseMessaging
import `in`.artistant.app.BuildConfig
import `in`.artistant.app.platform.observability.Analytics
import `in`.artistant.app.platform.observability.Crash
import `in`.artistant.app.platform.push.DeviceTokenRepository
import `in`.artistant.app.platform.storage.AppPreferences
import `in`.artistant.app.platform.upload.UploadQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The auth state machine (port of iOS `AuthService`). Wraps supabase-kt `Auth`, which
 * owns session persistence + token refresh (the iOS Keychain analogue) — we don't cache
 * a session ourselves, we observe [Auth.sessionStatus].
 *
 * A single @Singleton, created once and injected into the root ViewModel + MainActivity
 * (deep link). Observes the status flow for the app's lifetime and drives the analytics
 * identity on sign-in / sign-out.
 */
@Singleton
class SessionManager @Inject constructor(
    private val client: SupabaseClient,
    @ApplicationContext private val appContext: Context,
    private val analytics: Analytics,
    private val crash: Crash,
    private val prefs: AppPreferences,
    private val uploadQueue: UploadQueue,
    private val deviceTokens: DeviceTokenRepository,
) {
    // Long-lived scope for the status observer + prefs wipe. SupervisorJob so one failed
    // child (a stray analytics call) doesn't tear the observer down.
    private val scope = CoroutineScope(SupervisorJob())

    /**
     * Monotonic counter bumped on every COMPLETED sign-in. Ports the iOS `signInGeneration`
     * fix: a RETURNING user relaunches with a valid cached session, so their UUID is already
     * present at mount. When they re-authenticate into the SAME account the UUID doesn't
     * change, so a router keyed on identity alone never re-fires and the flow wedges on the
     * auth screen. The generation gives the router a component that changes on a real
     * sign-in even when the UUID is identical. Deliberately NOT bumped on a background token
     * refresh (source=Refresh) — that must not re-trigger the advance.
     */
    private val _signInGeneration = MutableStateFlow(0)
    val signInGeneration: StateFlow<Int> = _signInGeneration

    /**
     * One-shot error channel for a FAILED OAuth deep-link completion. The Apple/Google browser
     * return lands here (via [handleDeepLink] from MainActivity), OUTSIDE any ViewModel's
     * sign-in call — so a failed exchange can't surface through their try/catch. Non-null when
     * the completion failed; the auth UI ([AuthViewModel]) observes it, folds it into its
     * `state.error`, and calls [consumeDeepLinkError] to clear it. Being a StateFlow, its
     * retained value also covers the cold-launch race (set before the VM exists, read on first
     * collect). Ports the intent of iOS `AuthService.lastError`.
     */
    private val _deepLinkError = MutableStateFlow<String?>(null)
    val deepLinkError: StateFlow<String?> = _deepLinkError

    /** The auth UI calls this once it has shown [deepLinkError], so it never re-surfaces. */
    fun consumeDeepLinkError() { _deepLinkError.value = null }

    /**
     * Sign-in state as a Flow (the iOS `isSignedIn` @Published analogue). Maps the raw
     * supabase status: Authenticated → true, everything else (Initializing, NotAuthenticated,
     * RefreshFailure) → false. Started eagerly + kept while subscribed.
     */
    val sessionStatus: StateFlow<SessionStatus> = client.auth.sessionStatus
    val isSignedIn: Flow<Boolean> = client.auth.sessionStatus.map { it is SessionStatus.Authenticated }

    /** The signed-in user's lowercase UUID, or null. Read synchronously from the cached session. */
    val currentUserId: String?
        get() = client.auth.currentSessionOrNull()?.user?.id?.lowercase()

    /** Snapshot of the current user (for metadata reads during onboarding hydration). */
    val currentUser: UserInfo?
        get() = client.auth.currentSessionOrNull()?.user

    init {
        // Drive analytics identity off the session status for the app's lifetime. Mirrors the
        // iOS observeAuthState: identify on a live session, reset on sign-out. Also bumps the
        // generation on a genuine sign-in (SignIn/SignUp source) but NOT on a background
        // Refresh — the same rule as iOS.
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val uid = status.session.user?.id?.lowercase()
                        if (uid != null) {
                            analytics.identify(uid)
                            crash.setUser(uid)
                        }
                    }
                    else -> {
                        analytics.reset()
                        crash.setUser(null)
                    }
                }
            }
        }
    }

    // MARK: - Google

    /**
     * Google sign-in via Credential Manager → Google **ID token** → Supabase IDToken flow.
     * The nonce is generated here (SHA-256 sent to Google in the request, raw nonce sent to
     * Supabase). Requires [BuildConfig.GOOGLE_WEB_CLIENT_ID] — a REPLACE placeholder makes
     * this a no-op with a clear log until the operator drops the real web-client id.
     */
    suspend fun signInWithGoogle(activityContext: Context) {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId == "REPLACE") {
            // TODO(on-device): needs GOOGLE_WEB_CLIENT_ID in secrets.properties + the SHA-1 of
            // the signing cert registered on the GCP Android OAuth client. Wired but inert.
            Timber.w("Google sign-in skipped: GOOGLE_WEB_CLIENT_ID is unset (placeholder).")
            throw AuthException("Google sign-in isn't configured yet.")
        }
        val rawNonce = AuthNonce.random()
        val hashedNonce = AuthNonce.sha256Hex(rawNonce)

        val googleOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setNonce(hashedNonce)
            .setFilterByAuthorizedAccounts(false) // let a first-time user pick any account
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val idToken = try {
            val response = CredentialManager.create(appContext)
                .getCredential(activityContext, request)
            GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        } catch (e: GetCredentialCancellationException) {
            // parity: iOS treats user-cancel as silent (AuthService.swift). A dismissed
            // account picker is not a failure — signal cancellation with a dedicated type
            // the ViewModel swallows, so no red error banner appears.
            throw AuthCancelledException(e)
        } catch (e: GetCredentialException) {
            throw AuthException("Couldn't complete Google sign-in.", e)
        }

        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
        completedSignIn()
    }

    // MARK: - Apple

    /**
     * Apple sign-in. Android has no native Apple SDK, so this is the Supabase OAuth
     * external-browser flow (Custom Tab) — control returns via the `login-callback` deep
     * link, which [handleDeepLink] finishes. The nonce dance still applies.
     *
     * Compile-only for M1a: on-device this needs the `Sign in with Apple` provider enabled in
     * the Supabase dashboard + the Android callback registered. `signInWith(Apple)` launches
     * the browser and returns; the session lands via the deep link, not this call's return.
     */
    suspend fun signInWithApple() {
        // TODO(on-device): register the Apple provider + `in.artistant.app://login-callback`
        // redirect in the Supabase dashboard. The external browser opens here; the session
        // completes in handleDeepLink() on return, which is where completedSignIn() fires.
        // The web-OAuth flow's nonce/PKCE is handled by Supabase internally (the external-auth
        // config exposes only scopes/queryParams), so unlike the Google ID-token path we don't
        // pass a raw nonce here.
        client.auth.signInWith(Apple)
    }

    // MARK: - Email / password

    /** Email + password sign-in. On success the session lands in sessionStatus; we bump the
     *  generation so a returning-same-user re-auth still advances the router. */
    suspend fun signInWithEmail(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = normalizeEmail(email)
            this.password = password
        }
        completedSignIn()
    }

    /**
     * Email + password sign-up. Returns [EmailAuthOutcome]:
     * - [EmailAuthOutcome.SignedIn] when the project has confirmation OFF (signUpWith returns
     *   a user AND a session is now active) — advance immediately, like sign-in.
     * - [EmailAuthOutcome.ConfirmationRequired] when confirmation is ON (no session yet).
     * `fullName` is stored as `full_name` user metadata for downstream denormalization.
     */
    suspend fun signUpWithEmail(email: String, password: String, fullName: String?): EmailAuthOutcome {
        client.auth.signUpWith(Email) {
            this.email = normalizeEmail(email)
            this.password = password
            fullName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                data = buildJsonObject { put("full_name", it) }
            }
        }
        // supabase-kt drops the session into sessionStatus when confirmation is OFF; when it's
        // ON there's no session. Distinguish on the live session rather than the return value.
        return if (client.auth.currentSessionOrNull() != null) {
            completedSignIn()
            EmailAuthOutcome.SignedIn
        } else {
            EmailAuthOutcome.ConfirmationRequired
        }
    }

    // MARK: - Sign out

    /** Sign out + drop the analytics identity + wipe local prefs (DPDP §11 parity). The
     *  status observer also resets analytics, but wiping prefs is this method's job. */
    suspend fun signOut() {
        // Push cleanup FIRST, and SYNCHRONOUSLY, while STILL AUTHENTICATED. The device_tokens
        // DELETE is RLS-gated (`auth.uid() = user_id`), so it MUST complete before signOut() drops
        // the session — a fire-and-forget unregister could race the teardown, match zero rows, and
        // orphan this device's row (the signed-out user would then keep getting pushes here after
        // an account switch). Await the token + the unregister, THEN invalidate the token so the
        // next user on this device gets a fresh one mapped to THEM. Best-effort — a push-cleanup
        // failure must never block sign-out.
        runCatching {
            awaitFcmToken()?.let { deviceTokens.unregister(it) }
            FirebaseMessaging.getInstance().deleteToken()
        }
        client.auth.signOut()
        analytics.reset()
        crash.setUser(null)
        prefs.wipeAll()
        // Cancel any in-flight wizard uploads + wipe the staged media so the next account
        // that signs in on this device inherits nothing (iOS `UploadQueue.cancelAll`).
        uploadQueue.cancelAll()
    }

    /** Await FCM's current token (its Play-services Task, bridged to a suspend without the
     *  kotlinx-coroutines-play-services dep); null on any failure so sign-out cleanup degrades
     *  gracefully rather than throwing. */
    private suspend fun awaitFcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }

    // MARK: - Deep link

    /**
     * Finish an OAuth flow that returned via `in.artistant.app://login-callback`.
     *
     * WHY NOT supabase-kt's `handleDeeplinks`: in 3.0.3 it runs the PKCE `exchangeCodeForSession`
     * (or the implicit session import) on its own internal auth scope with NO try/catch and no
     * error callback. On a failed completion (flaky network / expired-or-invalid code / provider
     * error) `onSessionSuccess` never fires, [signInGeneration] never bumps, and the router stays
     * WEDGED on the auth screen with no feedback. So we replicate its two-flow logic here on OUR
     * scope, wrapped, and surface any failure to [deepLinkError] — the Android port of iOS
     * `AuthService.handleDeepLink`'s do/catch → `lastError` (whose doc names this exact
     * "server logged a success but the app is stuck on auth" symptom).
     */
    fun handleDeepLink(intent: Intent) {
        val data = intent.data ?: return
        // Only OUR OAuth callback — ignore any unrelated intent that reaches the activity (the
        // same scheme/host guard supabase-kt's handleDeeplinks applies before touching auth).
        if (data.scheme != client.auth.config.scheme || data.host != client.auth.config.host) return

        // (1) The provider can return an explicit denial instead of a token/code — the user backed
        // out of the consent screen, or the provider rejected the request. Supabase puts it in the
        // query (?error=…) or the URL fragment (#error=…). We MUST catch it here: the PKCE branch
        // below does `getQueryParameter("code") ?: return` and would silently wedge on a null code.
        val error = data.getQueryParameter("error") ?: fragmentParam(data.fragment, "error")
        if (error != null) {
            // A BARE access_denied (no error_code) == the user dismissed the consent screen — a
            // silent cancel, like a dismissed Google picker, no banner. But GoTrue REUSES
            // access_denied for real server-side denials that DO carry an error_code
            // (signup_disabled, a banned/blocked account, a provider-policy rejection). Silencing
            // those would reintroduce the very wedge this method exists to kill — so only the
            // code-less access_denied is treated as a cancel; everything else is surfaced.
            val errorCode = data.getQueryParameter("error_code") ?: fragmentParam(data.fragment, "error_code")
            if (error == "access_denied" && errorCode == null) return
            val desc = data.getQueryParameter("error_description")
                ?: fragmentParam(data.fragment, "error_description")
            _deepLinkError.value = desc?.takeIf { it.isNotBlank() }
                ?: "Couldn't complete sign-in. Try again."
            return
        }

        // (2) The happy path AND the failure the library was silently swallowing. Run the
        // flow-appropriate completion on OUR scope inside try/catch. Branching on the configured
        // flowType (not assuming one) keeps this correct if the operator later switches to PKCE.
        scope.launch {
            try {
                when (client.auth.config.flowType) {
                    FlowType.PKCE -> {
                        val code = data.getQueryParameter("code")
                            ?: throw AuthException("Sign-in link was malformed.")
                        client.auth.exchangeCodeForSession(code) // persists the session on success
                    }
                    FlowType.IMPLICIT -> {
                        val fragment = data.fragment
                            ?: throw AuthException("Sign-in link was malformed.")
                        // parseSessionFromFragment leaves user=null (per its doc) — fetch the user
                        // and import the complete session, exactly as the library's implicit path.
                        val parsed = client.auth.parseSessionFromFragment(fragment)
                        val user = client.auth.retrieveUser(parsed.accessToken)
                        client.auth.importSession(parsed.copy(user = user), source = SessionSource.External)
                    }
                }
                _deepLinkError.value = null
                completedSignIn()
            } catch (t: Throwable) {
                // The wedge symptom, now surfaced: log for triage, record to crash, and hand the
                // auth UI a friendly message so the user can retry instead of a dead screen.
                Timber.e(t, "OAuth deep-link completion failed")
                crash.record(t)
                _deepLinkError.value = (t as? AuthException)?.message
                    ?: "Couldn't complete sign-in. Check your connection and try again."
            }
        }
    }

    // MARK: - Helpers

    /** Bump the generation on a genuine (non-refresh) completed sign-in. Idempotent per call. */
    private fun completedSignIn() {
        _signInGeneration.value += 1
    }

    /** GoTrue matches the stored (lowercased, trimmed) email; normalize before every call so a
     *  stray capital/space doesn't produce a spurious "invalid credentials". */
    private fun normalizeEmail(email: String): String = email.trim().lowercase()
}

/**
 * Read a key from an OAuth callback URL fragment (`a=1&b=2`, the part after `#`), percent-decoded.
 * Used for the implicit-flow `#error=…&error_description=…` return. Kept a pure top-level fn (no
 * Android `Uri`) so it's JVM-unit-testable — `URLDecoder` over `Uri.decode` for the same reason.
 * A malformed percent-escape falls back to the raw value rather than throwing.
 */
internal fun fragmentParam(fragment: String?, key: String): String? =
    fragment?.split("&")
        ?.firstOrNull { it.substringBefore("=") == key }
        ?.substringAfter("=", "")
        ?.takeIf { it.isNotEmpty() }
        ?.let { raw -> runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw) }

/** Result of an email sign-in / sign-up (port of iOS `EmailAuthOutcome`). */
sealed interface EmailAuthOutcome {
    data object SignedIn : EmailAuthOutcome
    data object ConfirmationRequired : EmailAuthOutcome
}

/** A user-facing auth failure the UI surfaces inline. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The user dismissed the credential picker / OAuth sheet — a normal no-op, not a failure.
 * The ViewModel catches this and returns silently (no error banner).
 * parity: iOS treats user-cancel as silent (AuthService.swift `catch is CancellationError`).
 */
class AuthCancelledException(cause: Throwable? = null) : Exception(cause)
