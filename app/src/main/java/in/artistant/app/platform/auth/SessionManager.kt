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
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import `in`.artistant.app.BuildConfig
import `in`.artistant.app.platform.observability.Analytics
import `in`.artistant.app.platform.observability.Crash
import `in`.artistant.app.platform.push.PushService
import `in`.artistant.app.platform.storage.AccountScopedStore
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
    private val pushService: PushService,
    /**
     * Every singleton holding state that belongs to the signed-in account, filled by Hilt.
     *
     * Was three named fields, which is how the three teardown lists in this app drifted apart —
     * see [AccountScopedStore]. It also let the auth layer import `feature.profile`, naming one
     * screen's store from inside the session machinery.
     */
    private val accountScopedStores: Set<@JvmSuppressWildcards AccountScopedStore>,
) : AuthGateway {
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
    override val signInGeneration: StateFlow<Int> = _signInGeneration

    /**
     * One-shot error channel for a FAILED OAuth deep-link completion (closes #12).
     * The Apple/Google browser return lands here via [handleDeepLink] from MainActivity,
     * OUTSIDE any ViewModel try/catch. Auth UI observes this and calls [consumeDeepLinkError].
     */
    private val _deepLinkError = MutableStateFlow<String?>(null)
    override val deepLinkError: StateFlow<String?> = _deepLinkError

    /** The auth UI calls this once it has shown [deepLinkError], so it never re-surfaces. */
    override fun consumeDeepLinkError() { _deepLinkError.value = null }

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
    override suspend fun signInWithGoogle(activityContext: Context) {
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
    override suspend fun signInWithApple() {
        // TODO(on-device): register the Apple provider + `in.artistant.app://login-callback`
        // redirect in the Supabase dashboard. The external browser opens here; the session
        // completes in handleDeepLink() on return, which is where completedSignIn() fires.
        // The web-OAuth flow's nonce/PKCE is handled by Supabase internally (the external-auth
        // config exposes only scopes/queryParams), so unlike the Google ID-token path we don't
        // pass a raw nonce here.
        client.auth.signInWith(Apple)
    }

    // MARK: - One-time code (the primary path)

    /**
     * Text a six-digit code to [phoneE164] (design screen 12 → 119).
     *
     * Phone is the identity in India, which is why the redesign makes this the first control on
     * the sign-in screen and the password path a fallback. The dev Supabase project has an SMS
     * provider configured, so this is a real send, not a stub.
     *
     * [createUser] is the caller's, not this method's. On the SIGNUP entrance it is true and
     * the one call is both "sign up" and "sign in" — a number that has never been seen becomes
     * an account, a number that has signs in — which is what lets screen 12 have a single
     * button under a single field.
     *
     * On the LOGIN entrance it is false, and that is a consent rule rather than a preference.
     * "I already have an account" sits on the welcome screen ABOVE the terms tick and is
     * deliberately not gated on it (someone who already agreed should not have to agree again
     * to get back in). With `createUser = true` behind it, a number that had NEVER signed up
     * created an account through that door — an account whose owner was never shown the terms.
     * With false, GoTrue refuses the send for an unknown user and the screen offers to start
     * the signup walk, which collects the tick.
     *
     * Throws on a send failure (no SMS provider, a rejected number, no network, or — on login —
     * no such user); the caller shows it inline and the code screen is never reached.
     */
    override suspend fun sendPhoneOtp(phoneE164: String, createUser: Boolean) {
        client.auth.signInWith(OTP) {
            phone = phoneE164
            this.createUser = createUser
        }
    }

    /**
     * Exchange a texted code for a session. Bumps the generation on success, exactly like the
     * password and Google paths, so the router advances a returning user whose uuid has not
     * changed (see [completedSignIn]).
     */
    override suspend fun verifyPhoneOtp(phoneE164: String, token: String) {
        client.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = phoneE164, token = token)
        completedSignIn()
    }

    /**
     * The same one-time-code flow over email.
     *
     * Offered because the design's sign-in screen has an "Or use email" field beside the phone
     * one, and because App Review needs a path that does not require an Indian SIM. Whether it
     * WORKS is a project setting we cannot read from here: GoTrue only mails a code when the
     * project has SMTP configured, and on a project without it this call throws. That failure
     * surfaces inline on the sign-in screen with the password path beside it, rather than
     * being swallowed into a code screen for a mail that is never coming.
     */
    override suspend fun sendEmailOtp(email: String, createUser: Boolean) {
        client.auth.signInWith(OTP) {
            this.email = normalizeEmail(email)
            this.createUser = createUser
        }
    }

    /** Exchange an emailed code for a session. */
    override suspend fun verifyEmailOtp(email: String, token: String) {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = normalizeEmail(email),
            token = token,
        )
        completedSignIn()
    }

    // MARK: - Email / password

    /** Email + password sign-in. On success the session lands in sessionStatus; we bump the
     *  generation so a returning-same-user re-auth still advances the router. */
    override suspend fun signInWithEmail(email: String, password: String) {
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
     * - [EmailAuthOutcome.AlreadyRegistered] when the address is taken.
     * - [EmailAuthOutcome.ConfirmationRequired] when confirmation is ON (no session yet).
     * `fullName` is stored as `full_name` user metadata for downstream denormalization.
     */
    override suspend fun signUpWithEmail(email: String, password: String, fullName: String?): EmailAuthOutcome {
        val user = client.auth.signUpWith(Email) {
            this.email = normalizeEmail(email)
            this.password = password
            fullName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                data = buildJsonObject { put("full_name", it) }
            }
        }
        // supabase-kt drops the session into sessionStatus when confirmation is OFF; when it's
        // ON there's no session. Distinguish on the live session rather than the return value.
        if (client.auth.currentSessionOrNull() != null) {
            completedSignIn()
            return EmailAuthOutcome.SignedIn
        }
        // An EMPTY identities array is GoTrue's anti-enumeration answer for "that address
        // already has a confirmed account": with confirmations ON it does not error, it returns
        // an obfuscated user and mails nothing. Without this branch the screen told a user who
        // had merely mistyped their own password to go and check an inbox for a message that
        // was never sent. It is the documented signal, and it is the only one there is.
        return if (user?.identities?.isEmpty() == true) {
            EmailAuthOutcome.AlreadyRegistered
        } else {
            EmailAuthOutcome.ConfirmationRequired
        }
    }

    /**
     * Mail a password-reset link (design screen 28's "Forgot password?").
     *
     * Real, not decorative: GoTrue sends the mail when the project has SMTP configured, and
     * throws when it does not — which the screen reports where the link was tapped. No
     * redirect URL is passed, so the link lands on the project's configured Site URL (the web
     * client's reset page) rather than on a deep link this app has no screen for. The password
     * this app collects is a fallback path for App Review and for anyone without a platform
     * account; the recovery for it living on the web is the honest shape of that.
     */
    override suspend fun sendPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(normalizeEmail(email))
    }

    // MARK: - Sign out

    /** Sign out + drop the analytics identity + wipe local prefs (DPDP §11 parity). The
     *  status observer also resets analytics, but wiping prefs is this method's job. */
    suspend fun signOut() {
        // FIRST, before anything else: hand this device's push token back. The delete is
        // RLS-scoped to the caller's own `device_tokens` row, so it needs the session
        // `client.auth.signOut()` is about to tear down, and it needs the cached FCM token
        // that `prefs.wipeAll()` is about to clear. Ports the iOS `AuthService.signOut()`
        // ordering — without it the departing account keeps receiving this phone's message
        // previews, gig requests and booking pushes.
        pushService.onSigningOut()
        client.auth.signOut()
        analytics.reset()
        crash.setUser(null)
        wipeLocalState()
    }

    /**
     * Erase everything on this device that belongs to the account — the DPDP §11 half.
     *
     * Public because it is also the BACKSTOP the two account screens apply when the network
     * logout above throws: supabase-kt only reaches `clearSession()` on a logout it swallowed,
     * so a logout that threw skipped every line under it and left the departed account's role,
     * saved ids, export payload and staged uploads sitting on the phone. Both call sites used to
     * hand-write that list — and both had already fallen a store behind this one.
     *
     * Preferences first, then the stores, which is the order [DataExportStore.reset] documents
     * itself against: it clears whatever a cancelled request managed to write AFTER the wipe.
     *
     * Every step is guarded. A DataStore edit throws IOException on a preferences file it cannot
     * write, and this runs on a path where the account may already be erased server-side — one
     * store that throws must not cost the others their turn, and none of it may propagate to a
     * `viewModelScope` that installs no handler.
     */
    suspend fun wipeLocalState() {
        runCatching { prefs.wipeAll() }
        // What each of these is holding, and why none of it may be inherited: the saved-artist
        // ids; the DPDP export — a Ready state is the DEPARTING account's whole record, inline
        // JSON or a live signed URL to it; and the staged-media queue, whose every task carries
        // the artist id it was enqueued for, so a snapshot left behind is resumed by whoever
        // signs in next, fails against an RLS policy that (rightly) refuses another artist's
        // media, and strands itself in `failed` — where the EPK offers the new artist a Retry
        // for a cover photo they never picked.
        accountScopedStores.forEach { store -> runCatching { store.reset() } }
    }

    // MARK: - Deep link

    /**
     * Finish an OAuth flow that returned via `in.artistant.app://login-callback`.
     *
     * Provider denials arrive as `?error=` / `#error=` on the callback URL BEFORE any
     * session exchange — we surface those to [deepLinkError] (closes #12; ports iOS
     * AuthService.handleDeepLink → lastError). Successful returns still go through
     * supabase-kt 3.0.3's `handleDeeplinks` (PKCE/IMPLICIT APIs like
     * `parseSessionFromFragment` / `SessionSource` aren't public on this BOM).
     */
    fun handleDeepLink(intent: Intent) {
        val data = intent.data
        if (data != null &&
            data.scheme == client.auth.config.scheme &&
            data.host == client.auth.config.host
        ) {
            val error = data.getQueryParameter("error") ?: fragmentParam(data.fragment, "error")
            if (error != null) {
                // Bare access_denied (no error_code) == user dismissed consent — silent cancel.
                // access_denied WITH error_code is a real denial and must surface.
                val errorCode = data.getQueryParameter("error_code")
                    ?: fragmentParam(data.fragment, "error_code")
                if (error == "access_denied" && errorCode == null) return
                val desc = data.getQueryParameter("error_description")
                    ?: fragmentParam(data.fragment, "error_description")
                _deepLinkError.value = desc?.takeIf { it.isNotBlank() }
                    ?: "Couldn't complete sign-in. Try again."
                return
            }
        }

        // Success / non-error callbacks: let supabase-kt import the session, then bump
        // generation so a same-user Google/Apple return still advances the router.
        try {
            client.handleDeeplinks(intent) { _ ->
                _deepLinkError.value = null
                completedSignIn()
            }
        } catch (t: Throwable) {
            Timber.e(t, "OAuth deep-link completion failed")
            crash.record(t)
            _deepLinkError.value = (t as? AuthException)?.message
                ?: "Couldn't complete sign-in. Check your connection and try again."
        }
    }

    // MARK: - Helpers

    /**
     * Bump the generation on a genuine (non-refresh) completed sign-in.
     *
     * `update` rather than `value += 1`: the read-modify-write is not atomic, and this is
     * not called from one thread. The email/Google paths reach it from `viewModelScope`
     * (Main), while the OAuth path reaches it from [handleDeepLink]'s `handleDeeplinks`
     * callback, which supabase-kt completes on whatever thread finished the exchange. A
     * lost increment leaves [signInGeneration] unchanged, and an unchanged generation is
     * exactly the state this counter exists to rule out — the router keyed on
     * `(uid, generation)` never re-fires and a returning user re-authenticating into the
     * same uuid stays on the auth screen.
     */
    private fun completedSignIn() {
        _signInGeneration.update { it + 1 }
        // Re-claim this device's push token for whoever just signed in. Every sign-in path
        // funnels through here, and none of them passes through `ArtistantApplication.onCreate`
        // — the only other registration site — so without this a returning user gets no pushes
        // at all, and an in-process account switch leaves the token still mapped to the account
        // that just left. `claim_device_token` is SECURITY DEFINER, so the new owner takes the
        // row regardless of who held it (mig 0075). Deliberately here rather than on the
        // sessionStatus observer: a background token Refresh must not re-issue the claim.
        pushService.registerIfPermitted()
    }

    /** GoTrue matches the stored (lowercased, trimmed) email; normalize before every call so a
     *  stray capital/space doesn't produce a spurious "invalid credentials". */
    private fun normalizeEmail(email: String): String = email.trim().lowercase()
}

/**
 * Read a key from an OAuth callback URL fragment (`a=1&b=2`). Pure / JVM-testable.
 * Malformed percent-escape falls back to the raw value.
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

    /** The address already has an account, so the password offered was simply the wrong one. */
    data object AlreadyRegistered : EmailAuthOutcome
}

/** A user-facing auth failure the UI surfaces inline. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The user dismissed the credential picker / OAuth sheet — a normal no-op, not a failure.
 * The ViewModel catches this and returns silently (no error banner).
 * parity: iOS treats user-cancel as silent (AuthService.swift `catch is CancellationError`).
 */
class AuthCancelledException(cause: Throwable? = null) : Exception(cause)
