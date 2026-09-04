package `in`.artistant.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.EmailRules
import `in`.artistant.app.data.model.PasswordRules
import `in`.artistant.app.platform.auth.AuthCancelledException
import `in`.artistant.app.platform.auth.AuthException
import `in`.artistant.app.platform.auth.EmailAuthOutcome
import `in`.artistant.app.feature.signup.OtpResend
import `in`.artistant.app.feature.signup.PhoneRules
import `in`.artistant.app.platform.auth.SessionManager
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which address the one-time code went to. Drives the code screen's "Sent to …" line. */
enum class OtpChannel { Sms, Email }

/** Auth-entry UI state (the iOS `isAuthenticating` / `lastError` @Published analogue). */
data class AuthUiState(
    val isAuthenticating: Boolean = false,
    val error: String? = null,
    /** Set after an email sign-up when the project requires confirmation — the sheet shows
     *  "check your inbox" instead of dismissing. */
    val confirmationRequired: Boolean = false,

    // ── The one-time-code path (design screens 12 → 119) ──────────────────────
    /** What the user typed in the phone field — national digits, no country code. */
    val phone: String = "",
    /** What the user typed in the "Or use email" field. */
    val email: String = "",
    /** The address a code was actually SENT to, in the form it was sent in. Empty until then. */
    val codeDestination: String = "",
    val codeChannel: OtpChannel = OtpChannel.Sms,
    /** The digits in the six boxes. */
    val code: String = "",
    /** Seconds until Resend is offered again. Zero means it is. */
    val resendSeconds: Int = 0,
    /** How many times a code has been sent this attempt — two unlocks the email escape. */
    val sendCount: Int = 0,
    val isSendingCode: Boolean = false,
    val isVerifying: Boolean = false,
    /** A wrong or expired code, shown under the boxes and reddening them. */
    val codeError: String? = null,
) {
    /** The phone number in the form GoTrue takes, or "" when what is typed is not one. */
    val phoneE164: String get() = PhoneRules.toE164(phone)

    /** "Send code" is live once ONE of the two fields holds something sendable. */
    val canSendCode: Boolean
        get() = !isSendingCode && (PhoneRules.isValid(phone) || EmailRules.isValid(email))

    val canVerify: Boolean get() = !isVerifying && OtpResend.isComplete(code)

    val canResend: Boolean get() = !isSendingCode && OtpResend.canResend(resendSeconds)

    /** After two sends the code screen offers the password path as a way out. */
    val offersEmailEscape: Boolean get() = OtpResend.offersEmailEscape(sendCount)
}

/**
 * Runs the [SessionManager] sign-in calls with spinner + inline-error plumbing. On success
 * the session lands in sessionStatus and [RootViewModel] advances the gate — this VM only
 * owns the transient auth-entry UI, not routing.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    init {
        // The OAuth (Apple/Google-browser) return lands in SessionManager via MainActivity —
        // OUTSIDE this VM's sign-in calls, so a failed exchange can't surface through their
        // try/catch. Observe SessionManager's one-shot deep-link error channel and fold it into
        // the same state.error the screen already renders (below the OAuth buttons). StateFlow
        // retention covers the cold-launch race: the error may be set before this VM exists, and
        // we still get it on the first collect. Consume it so it never re-surfaces.
        viewModelScope.launch {
            session.deepLinkError.collect { msg ->
                if (msg != null) {
                    _state.update { it.copy(error = msg, isAuthenticating = false) }
                    session.consumeDeepLinkError()
                }
            }
        }

        // Everything this VM holds describes the attempt in front of the user, but the VM
        // itself is Activity-scoped (the auth screen sits outside any NavHost, so
        // `hiltViewModel()` binds it to the Activity) — so nothing ever drops it. A failed
        // Google sign-in the user works around with Apple leaves "Sign-in failed" in
        // `error` for the whole process, and the next person to reach this screen after a
        // sign-out is shown the previous account's error / "check your inbox" note. A
        // COMPLETED sign-in spends every one of those outcomes, so clear them there.
        // `signInGeneration` rather than the session status: it bumps on a genuine sign-in
        // only (never a restore or a background token refresh), and `drop(1)` skips the
        // value the StateFlow replays when this collector starts.
        viewModelScope.launch {
            session.signInGeneration.drop(1).collect { _state.value = AuthUiState() }
        }
    }

    // ── One-time code ────────────────────────────────────────────────────────

    /** The running resend countdown, cancelled and restarted on every send. */
    private var countdownJob: Job? = null

    fun setPhone(raw: String) = _state.update {
        it.copy(phone = PhoneRules.national(raw), error = null)
    }

    fun setEmail(raw: String) = _state.update { it.copy(email = raw, error = null) }

    fun setCode(raw: String) = _state.update {
        it.copy(code = raw.filter(Char::isDigit).take(OtpResend.CODE_LENGTH), codeError = null)
    }

    /**
     * Send (or re-send) a one-time code, then hand control to [onSent].
     *
     * Phone wins when both fields hold something: the design puts it first and labels the
     * second field "Or use email", so the email field is the alternative rather than an
     * addition. [onSent] is what moves the flow to the code step, and it fires only on a
     * successful send — a send that throws leaves the user on the sign-in screen looking at
     * the reason, instead of on a code screen waiting for a message that is not coming.
     */
    fun sendCode(onSent: () -> Unit = {}) {
        val s = _state.value
        if (s.isSendingCode) return
        val usePhone = PhoneRules.isValid(s.phone)
        if (!usePhone && !EmailRules.isValid(s.email)) {
            _state.update { it.copy(error = "Enter a mobile number or an email.") }
            return
        }
        val destination = if (usePhone) s.phoneE164 else s.email.trim()
        _state.update { it.copy(isSendingCode = true, error = null, codeError = null) }
        viewModelScope.launch {
            try {
                if (usePhone) session.sendPhoneOtp(destination) else session.sendEmailOtp(destination)
                _state.update {
                    it.copy(
                        isSendingCode = false,
                        codeDestination = destination,
                        codeChannel = if (usePhone) OtpChannel.Sms else OtpChannel.Email,
                        sendCount = it.sendCount + 1,
                        // A resend does not invalidate what is already typed, but a FIRST
                        // send does: whatever is in the boxes belongs to a previous attempt.
                        code = if (it.sendCount == 0) "" else it.code,
                    )
                }
                startResendCountdown()
                onSent()
            } catch (e: Throwable) {
                errorFor(e, ::friendlyOtp)?.let { msg -> _state.update { it.copy(error = msg) } }
                _state.update { it.copy(isSendingCode = false) }
            }
        }
    }

    /** Exchange the typed code for a session. The gate advances the flow from there. */
    fun verifyCode() {
        val s = _state.value
        if (!s.canVerify || s.codeDestination.isEmpty()) return
        _state.update { it.copy(isVerifying = true, codeError = null) }
        viewModelScope.launch {
            try {
                when (s.codeChannel) {
                    OtpChannel.Sms -> session.verifyPhoneOtp(s.codeDestination, s.code)
                    OtpChannel.Email -> session.verifyEmailOtp(s.codeDestination, s.code)
                }
            } catch (e: Throwable) {
                val msg = errorFor(e) { "That code didn't work. Check it and try again." }
                _state.update { it.copy(codeError = msg) }
            } finally {
                _state.update { it.copy(isVerifying = false) }
            }
        }
    }

    /**
     * Restart the cooldown.
     *
     * `delay` rather than a wall clock, so the whole thing runs on virtual time under
     * `runTest` and the timing is a unit test rather than a thirty-second one. Cancelled
     * first: two overlapping countdowns would race to write the same field and the loser
     * would put the number back up.
     */
    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            OtpResend.countdown().collect { seconds ->
                _state.update { it.copy(resendSeconds = seconds) }
            }
        }
    }

    /**
     * Drop everything the code path is holding — the number, the code, the countdown.
     *
     * Called when the user leaves the code screen for good ("Change number", the escape to
     * the password form). Without it a later send would inherit a spent [AuthUiState.sendCount]
     * and offer the escape hatch before the first message had a chance to arrive.
     */
    fun resetCodeAttempt() {
        countdownJob?.cancel()
        _state.update {
            it.copy(
                code = "",
                codeError = null,
                codeDestination = "",
                resendSeconds = 0,
                sendCount = 0,
                isSendingCode = false,
                isVerifying = false,
            )
        }
    }

    /** GoTrue's phone/email OTP failures, said in words a person can act on. */
    private fun friendlyOtp(e: Throwable): String {
        val raw = (e.message ?: "").lowercase()
        return when {
            "sms provider" in raw || "phone provider" in raw || "unsupported phone provider" in raw ->
                "We can't text a code right now. Use email instead."
            "signups not allowed" in raw || "signup is disabled" in raw ->
                "New accounts are paused right now."
            "rate limit" in raw || "too many requests" in raw ->
                "Too many codes requested. Wait a minute and try again."
            "invalid" in raw && "phone" in raw -> "That doesn't look like an Indian mobile number."
            else -> e.userMessage()
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        _state.update { it.copy(isAuthenticating = true, error = null) }
        viewModelScope.launch {
            try {
                session.signInWithGoogle(activityContext)
            } catch (e: Throwable) {
                errorFor(e)?.let { msg -> _state.update { it.copy(error = msg) } }
            } finally {
                _state.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    fun signInWithApple() {
        _state.update { it.copy(isAuthenticating = true, error = null) }
        viewModelScope.launch {
            try {
                session.signInWithApple()
            } catch (e: Throwable) {
                errorFor(e)?.let { msg -> _state.update { it.copy(error = msg) } }
            } finally {
                _state.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    /** Email sign-in. Client-validates first, then defers to GoTrue. */
    fun signInWithEmail(email: String, password: String) {
        beginEmailAttempt()
        if (!EmailRules.isValid(email)) {
            _state.update { it.copy(error = "Enter a valid email.") }
            return
        }
        // parity: iOS gates submit on passwordValid (>=6) for BOTH modes (EmailAuthView) —
        // sign-in must guard too so we don't fire a doomed request GoTrue would reject anyway.
        if (!PasswordRules.isValid(password)) {
            _state.update { it.copy(error = "Password must be at least 6 characters.") }
            return
        }
        _state.update { it.copy(isAuthenticating = true, error = null) }
        viewModelScope.launch {
            try {
                session.signInWithEmail(email, password)
            } catch (e: Throwable) {
                errorFor(e, ::friendly)?.let { msg -> _state.update { it.copy(error = msg) } }
            } finally {
                _state.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    /** Email sign-up. On confirmation-required, flips [AuthUiState.confirmationRequired]. */
    fun signUpWithEmail(email: String, password: String, fullName: String?) {
        beginEmailAttempt()
        if (!EmailRules.isValid(email)) {
            _state.update { it.copy(error = "Enter a valid email.") }
            return
        }
        if (!PasswordRules.isValid(password)) {
            _state.update { it.copy(error = "Password must be at least 6 characters.") }
            return
        }
        _state.update { it.copy(isAuthenticating = true, error = null) }
        viewModelScope.launch {
            try {
                when (session.signUpWithEmail(email, password, fullName)) {
                    EmailAuthOutcome.SignedIn -> Unit // RootViewModel advances the gate
                    EmailAuthOutcome.ConfirmationRequired ->
                        _state.update { it.copy(confirmationRequired = true) }
                }
            } catch (e: Throwable) {
                errorFor(e, ::friendly)?.let { msg -> _state.update { it.copy(error = msg) } }
            } finally {
                _state.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    /**
     * Mail a password-reset link for [email] (design screen 28's "Forgot password?").
     *
     * Reuses [AuthUiState.confirmationRequired] as the "we sent you something, go look" signal
     * rather than adding a second flag that means the same thing: the screen renders one
     * notice slot, and only one of the two can be true at a time — you cannot be waiting on a
     * confirmation mail for an account whose password you are trying to recover.
     */
    fun sendPasswordReset(email: String) {
        beginEmailAttempt()
        if (!EmailRules.isValid(email)) {
            _state.update { it.copy(error = "Enter your email first.") }
            return
        }
        _state.update { it.copy(isAuthenticating = true, error = null) }
        viewModelScope.launch {
            try {
                session.sendPasswordReset(email)
                _state.update { it.copy(confirmationRequired = true) }
            } catch (e: Throwable) {
                errorFor(e, ::friendly)?.let { msg -> _state.update { it.copy(error = msg) } }
            } finally {
                _state.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    /**
     * A new email submit supersedes the previous one's outcome. The "check your inbox" note
     * is the outcome of ONE sign-up, and nothing else clears it — every later `_state.update`
     * copies the flag forward — so a second address, a switch to sign-in or a re-opened sheet
     * would keep claiming a confirmation mail is waiting for an email that never got one.
     * parity: iOS clears the shared notice slot at the top of `submit()` (EmailAuthView).
     */
    private fun beginEmailAttempt() =
        _state.update { it.copy(error = null, confirmationRequired = false) }

    /** Sheet dismissed — drop both inline messages so a re-open starts clean. */
    fun clearError() = _state.update { it.copy(error = null, confirmationRequired = false) }

    /**
     * Map a caught throwable to the error message to show, or null to stay silent.
     * parity: iOS treats user-cancel as silent (AuthService.swift `catch is CancellationError`).
     * A [kotlinx.coroutines.CancellationException] is re-thrown — swallowing it would break
     * structured concurrency (the scope must observe its own cancellation).
     */
    private fun errorFor(e: Throwable, map: (Throwable) -> String = { it.userMessage() }): String? =
        when {
            e is CancellationException -> throw e        // structured concurrency: never swallow
            e is AuthCancelledException -> null          // user dismissed the picker — silent
            isNetworkError(e) -> NETWORK_ERROR_MESSAGE   // slow/flaky network — friendly, not the raw timeout text
            else -> map(e)
        }

    private fun Throwable.userMessage(): String =
        (this as? AuthException)?.message ?: message ?: "Sign-in failed. Try again."

    /** Smooth the two GoTrue errors the user will actually hit (iOS friendlyAuthMessage). */
    private fun friendly(e: Throwable): String {
        val raw = (e.message ?: "").lowercase()
        return when {
            "invalid login" in raw || "invalid credentials" in raw -> "Wrong email or password."
            "already registered" in raw || "already been registered" in raw ->
                "That email already has an account — sign in instead."
            else -> e.userMessage()
        }
    }
}

/** Shown for any connectivity failure so the user never sees a raw Ktor timeout string. */
internal const val NETWORK_ERROR_MESSAGE = "Couldn't reach the server. Check your connection and try again."

/**
 * True if [error] (or anything in its cause chain) is a network / connectivity failure.
 *
 * All four types the task cares about — Ktor's `HttpRequestTimeoutException` (the reported
 * signup-timeout symptom), `ConnectTimeoutException`, `SocketTimeoutException`, and a bare
 * `java.io.IOException` — extend `java.io.IOException`, so the single `is IOException` check
 * subsumes them all (no per-type branch needed; they'd map to the same message anyway).
 *
 * supabase-kt rethrows `HttpRequestTimeoutException` unwrapped, but wraps *other* transport
 * failures (connect/socket timeout, DNS, refused) in `HttpRequestException` and drops the
 * cause — so the IOException type is lost. We match `HttpRequestException` explicitly to keep
 * those covered. GoTrue auth errors (wrong password, already-registered) come back as parsed
 * HTTP responses, not `HttpRequestException`, so they keep their own friendly mapping.
 *
 * We walk the whole cause chain rather than checking only the top frame, for the paths that do
 * preserve the cause.
 */
internal fun isNetworkError(error: Throwable): Boolean {
    var t: Throwable? = error
    while (t != null) {
        if (t is IOException || t is HttpRequestException) return true
        t = t.cause
    }
    return false
}
