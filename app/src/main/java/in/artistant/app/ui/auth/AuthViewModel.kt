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
import `in`.artistant.app.platform.auth.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Auth-entry UI state (the iOS `isAuthenticating` / `lastError` @Published analogue). */
data class AuthUiState(
    val isAuthenticating: Boolean = false,
    val error: String? = null,
    /** Set after an email sign-up when the project requires confirmation — the sheet shows
     *  "check your inbox" instead of dismissing. */
    val confirmationRequired: Boolean = false,
)

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

    fun clearError() = _state.update { it.copy(error = null) }

    /**
     * Map a caught throwable to the error message to show, or null to stay silent.
     * parity: iOS treats user-cancel as silent (AuthService.swift `catch is CancellationError`).
     * A [kotlinx.coroutines.CancellationException] is re-thrown — swallowing it would break
     * structured concurrency (the scope must observe its own cancellation).
     */
    private fun errorFor(e: Throwable, map: (Throwable) -> String = { it.userMessage() }): String? =
        when (e) {
            is CancellationException -> throw e          // structured concurrency: never swallow
            is AuthCancelledException -> null            // user dismissed the picker — silent
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
