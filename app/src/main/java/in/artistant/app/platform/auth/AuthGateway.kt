package `in`.artistant.app.platform.auth

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * The auth calls the sign-in UI makes, behind an interface.
 *
 * [SessionManager] is the one implementation and will stay the one implementation — this is not
 * a hook for a second auth backend. It exists because `AuthViewModel` decides real things now
 * (try the password against sign-IN before sign-UP; send a login-mode code with
 * `createUser = false`; drop the whole OTP attempt on back) and every one of those decisions is
 * a branch that has to be asserted somewhere. The ViewModel could not be constructed in a JVM
 * test at all while it depended on the concrete manager, whose own constructor wants a
 * `SupabaseClient`, an Android `Context`, PostHog, Sentry, DataStore, FCM and the upload queue.
 *
 * Same shape as `SignupConsentStore` over `AppPreferences`: a narrow interface over one
 * singleton, bound with `@Binds`, with a `Fake` twin in the test source set.
 */
interface AuthGateway {

    /** A FAILED OAuth deep-link completion, which lands outside any ViewModel's try/catch. */
    val deepLinkError: StateFlow<String?>

    /** Called once the UI has shown [deepLinkError], so it never re-surfaces. */
    fun consumeDeepLinkError()

    /** Bumped on every COMPLETED sign-in (never on a restore or a background refresh). */
    val signInGeneration: StateFlow<Int>

    /**
     * Text a one-time code to [phoneE164].
     *
     * [createUser] is the difference between the two entrances. Signup passes true, so an
     * unknown number becomes an account. Login passes false, so it does not — the login button
     * sits on the welcome screen ABOVE the consent tick, and a path that can create an account
     * without that tick is a path that collects no consent at all.
     */
    suspend fun sendPhoneOtp(phoneE164: String, createUser: Boolean)

    /** The same one-time-code flow over email. [createUser] means what it means above. */
    suspend fun sendEmailOtp(email: String, createUser: Boolean)

    suspend fun verifyPhoneOtp(phoneE164: String, token: String)

    suspend fun verifyEmailOtp(email: String, token: String)

    suspend fun signInWithGoogle(activityContext: Context)

    suspend fun signInWithApple()

    suspend fun signInWithEmail(email: String, password: String)

    suspend fun signUpWithEmail(email: String, password: String, fullName: String?): EmailAuthOutcome

    suspend fun sendPasswordReset(email: String)
}
