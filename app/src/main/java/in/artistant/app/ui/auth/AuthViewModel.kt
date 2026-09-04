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
import `in`.artistant.app.platform.auth.AuthGateway
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
    /**
     * A LOGIN-mode send GoTrue refused because the address has no account — which is what
     * `createUser = false` is for. Not an error: the address is fine, it simply has never been
     * here, so the screen offers the signup walk rather than a red banner nobody can act on.
     * The channel is carried because the sentence names what was typed ("this number" /
     * "this email"), and the two fields sit one above the other.
     */
    val noAccountFor: OtpChannel? = null,
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
 * Runs the [AuthGateway] sign-in calls with spinner + inline-error plumbing. On success
 * the session lands in sessionStatus and [RootViewModel] advances the gate — this VM only
 * owns the transient auth-entry UI, not routing.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val session: AuthGateway,
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
        it.copy(phone = PhoneRules.national(raw), error = null, noAccountFor = null)
    }

    fun setEmail(raw: String) = _state.update {
        it.copy(email = raw, error = null, noAccountFor = null)
    }

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
     *
     * @param createUser false on the LOGIN entrance, so an unknown address cannot become an
     *   account through a door that never showed anyone the terms. GoTrue answers that refusal
     *   with `otp_disabled`, which becomes [AuthUiState.noAccountFor] and an offer to sign up
     *   properly — not an error, because nothing went wrong.
     */
    fun sendCode(createUser: Boolean = true, onSent: () -> Unit = {}) {
        val s = _state.value
        if (s.isSendingCode) return
        val usePhone = PhoneRules.isValid(s.phone)
        if (!usePhone && !EmailRules.isValid(s.email)) {
            _state.update { it.copy(error = "Enter a mobile number or an email.") }
            return
        }
        val channel = if (usePhone) OtpChannel.Sms else OtpChannel.Email
        val destination = if (usePhone) s.phoneE164 else s.email.trim()
        _state.update {
            it.copy(isSendingCode = true, error = null, codeError = null, noAccountFor = null)
        }
        viewModelScope.launch {
            try {
                if (usePhone) {
                    session.sendPhoneOtp(destination, createUser)
                } else {
                    session.sendEmailOtp(destination, createUser)
                }
                _state.update {
                    it.copy(
                        isSendingCode = false,
                        codeDestination = destination,
                        codeChannel = channel,
                        sendCount = it.sendCount + 1,
                        // A resend does not invalidate what is already typed, but a FIRST
                        // send does: whatever is in the boxes belongs to a previous attempt.
                        code = if (it.sendCount == 0) "" else it.code,
                    )
                }
                startResendCountdown()
                onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!createUser && isNoSuchUser(e)) {
                    // The one refusal that is not a failure. Same GoTrue string as "signups are
                    // paused", which is why it is only read this way when we ASKED for
                    // createUser = false — in signup mode the identical message means the
                    // opposite thing and keeps its own wording.
                    _state.update { it.copy(noAccountFor = channel) }
                } else {
                    errorFor(e, ::friendlyOtp)?.let { msg -> _state.update { it.copy(error = msg) } }
                }
                _state.update { it.copy(isSendingCode = false) }
            }
        }
    }

    /**
     * GoTrue's answer to a `create_user = false` send for an address it has never seen.
     *
     * It reports it as `otp_disabled` / "Signups not allowed for otp" — the sign-up refusal,
     * not a "no such user", because telling an anonymous caller which numbers have accounts is
     * exactly the enumeration oracle it declines to be. Only the login path asks the question,
     * so only the login path reads the answer this way.
     */
    private fun isNoSuchUser(e: Throwable): Boolean {
        val raw = (e.message ?: "").lowercase()
        return "otp_disabled" in raw ||
            "signups not allowed" in raw ||
            "signup is disabled" in raw ||
            "signups are disabled" in raw ||
            "user not found" in raw
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
     * Drop everything the code path is holding — the destination, the digits, the send count,
     * the countdown.
     *
     * Called on every way OUT of the code screen: the header's "Change number", the system back
     * gesture, and the escape to the password form. This ViewModel is Activity-scoped (the auth
     * screens sit outside any NavHost), so nothing else ever drops it — back only moved
     * `SignupStep`, and the next number the user typed inherited a spent [sendCount]. The
     * consequences were all visible: the first send for the NEW number counted as a resend, so
     * the screen offered "use email instead" before a single message had had a chance to
     * arrive, the cooldown was still running from the previous number, and the six boxes still
     * held the digits typed for it.
     */
    fun clearOtp() {
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
                noAccountFor = null,
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

    /**
     * Screen 28's one button, doing what the banner above it promises.
     *
     * "Already have an account with this email? We'll sign you in instead of creating a second
     * one" is the design's own copy, and for a while it was simply untrue: the sheet called
     * the sign-UP gateway unconditionally, so a returning user with a confirmed account could
     * not
     * get in with their own password — the screen either told them the address was already
     * registered or (with confirmations on) sent them to look for a mail GoTrue never sent.
     *
     * So the password is offered to sign-IN first, and only a genuine "those credentials don't
     * match anything" turns into a sign-UP. Everything else — an unconfirmed address, a rate
     * limit, no network — stops there and is said, because falling through to a sign-up would
     * answer a temporary problem by making a second account.
     *
     * The ambiguity is GoTrue's and cannot be resolved from here: `invalid_credentials` means
     * BOTH "no such user" and "wrong password", deliberately, so that an anonymous caller
     * cannot use this screen to discover who has an account. That is why the sign-up fallback
     * has to handle coming back with "that address is taken" — see [createAccount] — which is
     * the shape a mistyped password takes on the second call.
     */
    fun submitEmailAuth(email: String, password: String, fullName: String?) {
        beginEmailAttempt()
        if (!EmailRules.isValid(email)) {
            _state.update { it.copy(error = "Enter a valid email.") }
            return
        }
        // The SIGN-IN floor, not screen 28's stricter new-account rule: an account made before
        // that rule existed still has to be able to get in. The 8-character line is enforced
        // below, on the branch that actually creates something.
        if (!PasswordRules.isValid(password)) {
            _state.update {
                it.copy(error = "Password must be at least ${PasswordRules.MIN_LENGTH} characters.")
            }
            return
        }
        _state.update { it.copy(isAuthenticating = true, error = null) }
        viewModelScope.launch {
            try {
                session.signInWithEmail(email, password)
            } catch (e: CancellationException) {
                throw e
            } catch (signInFailed: Throwable) {
                when {
                    !isUnknownCredentials(signInFailed) || isNetworkError(signInFailed) ->
                        errorFor(signInFailed, ::friendly)?.let { msg ->
                            _state.update { it.copy(error = msg) }
                        }
                    // Nothing matched those credentials, so this may be a new account — but
                    // screen 28 says a new one needs eight characters, and a rule the tick
                    // draws is a rule the button honours.
                    !PasswordRules.isValidForNewAccount(password) ->
                        _state.update {
                            it.copy(
                                error = "Wrong password — or, if you're new here, pick one " +
                                    "with at least ${PasswordRules.NEW_ACCOUNT_MIN_LENGTH} characters.",
                            )
                        }
                    else -> createAccount(email, password, fullName)
                }
            } finally {
                _state.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    /** The sign-up half of [submitEmailAuth]. Only reached when nothing matched the password. */
    private suspend fun createAccount(email: String, password: String, fullName: String?) {
        try {
            when (session.signUpWithEmail(email, password, fullName)) {
                EmailAuthOutcome.SignedIn -> Unit // RootViewModel advances the gate
                EmailAuthOutcome.ConfirmationRequired ->
                    _state.update { it.copy(confirmationRequired = true) }
                // The address is taken, and we have just proved the password does not open it.
                EmailAuthOutcome.AlreadyRegistered ->
                    _state.update { it.copy(error = WRONG_PASSWORD) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Same fact by the other route: with confirmations OFF, GoTrue says it out loud.
            if ("already registered" in (e.message ?: "").lowercase()) {
                _state.update { it.copy(error = WRONG_PASSWORD) }
            } else {
                errorFor(e, ::friendly)?.let { msg -> _state.update { it.copy(error = msg) } }
            }
        }
    }

    /** GoTrue's one answer for both "no such user" and "wrong password" (it will not say which). */
    private fun isUnknownCredentials(e: Throwable): Boolean {
        val raw = (e.message ?: "").lowercase()
        return "invalid login" in raw || "invalid credentials" in raw || "user not found" in raw
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

/**
 * Said when the address exists and the password did not open it.
 *
 * Deliberately NOT "that email already has an account — sign in instead": the user has just
 * tried to sign in, on the only screen that offers it, so an instruction to do the thing they
 * did is a dead end. Both halves are named because GoTrue will not say which one was wrong.
 */
internal const val WRONG_PASSWORD = "Wrong email or password."

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
