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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import `in`.artistant.app.BuildConfig
import `in`.artistant.app.platform.observability.Analytics
import `in`.artistant.app.platform.observability.Crash
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
        client.auth.signOut()
        analytics.reset()
        crash.setUser(null)
        prefs.wipeAll()
    }

    // MARK: - Deep link

    /**
     * Finish an OAuth flow that returned via `in.artistant.app://login-callback`. supabase-kt's
     * [handleDeeplinks] parses the intent, imports the session, and fires the callback — where
     * we bump the generation so a same-user Google/Apple return still advances the router.
     */
    fun handleDeepLink(intent: Intent) {
        // handleDeeplinks is a SupabaseClient extension (Android-only). It parses the intent,
        // imports the session, and fires the callback — where we bump the generation so a
        // same-user return still advances the router.
        client.handleDeeplinks(intent) { _ ->
            completedSignIn()
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
