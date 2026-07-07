package `in`.artistant.app.feature.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.data.model.HandleRules
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.platform.observability.Analytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Live handle-availability status, driving the profile-screen indicator (iOS `HandleStatus`). */
enum class HandleStatus { Empty, Invalid, Checking, Available, Taken, Error }

/**
 * The signup flow's observable state (the iOS `OnboardingStore` @Published surface, minus the
 * `isSignedIn` bit — that lives in the gate). One immutable snapshot the container renders from.
 */
data class SignupUiState(
    val step: SignupStep = SignupStep.Welcome,
    val mode: SignupMode = SignupMode.Signup,
    val role: AppRole = AppRole.Client,
    val name: String = "",
    val city: String = "",
    val handle: String = "",
    val handleStatus: HandleStatus = HandleStatus.Empty,
    val termsAccepted: Boolean = false,
    val isSaving: Boolean = false,
    /** Inline profile-save error (handle-just-taken / generic). Cleared on retry. */
    val saveError: String? = null,
    /** "Sign in again" banner shown on the auth screen after a session-lost bounce. */
    val authNotice: String? = null,
) {
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name.trim() }

    /** `.Error` counts as available: a transient RPC blip shouldn't wedge Continue — the
     *  upsert's unique constraint is the real backstop (iOS `handleIsAvailable`). */
    val handleAvailable: Boolean
        get() = handleStatus == HandleStatus.Available || handleStatus == HandleStatus.Error

    val profileValid: Boolean
        get() = handleAvailable && name.isNotBlank() && city.isNotBlank()
}

/** One-shot side effects (nav out of the flow, haptics) — a Channel so they fire once, not
 *  re-derived from state on recomposition (ARCHITECTURE §3). */
sealed interface SignupEvent {
    /** Signup complete → the gate should route into the app. */
    data object Finished : SignupEvent
    data object SelectionHaptic : SignupEvent
    data object SuccessHaptic : SignupEvent
}

/**
 * The signup step machine + draft-profile state (port of iOS `OnboardingStore`). Owns step
 * transitions, live handle availability (debounced), and the profile upsert. Auth itself is
 * delegated to [SessionManager] via [AuthViewModel] on the auth screen — this VM only reacts
 * to a completed sign-in to advance past `.Auth`.
 *
 * The pure transition/order logic lives in SignupStep.kt so it's testable without Hilt.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SignupViewModel @Inject constructor(
    private val users: UsersRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state

    private val _events = Channel<SignupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Live handle availability: debounce the handle field, drop anything that isn't a valid
        // format synchronously (no RPC for those), then check the rest. `distinctUntilChanged`
        // so re-emitting the same handle (e.g. an unrelated state copy) doesn't re-hit the RPC.
        // The 350ms window matches iOS `handleDebounceNS`.
        viewModelScope.launch {
            _state
                .map { it.handle }
                .distinctUntilChanged()
                .debounce(350)
                .filter { HandleRules.isValidFormat(it) }
                .collect { handle ->
                    // The synchronous status set in `onHandleChanged` already showed Checking;
                    // guard against a race where the user kept typing past this emission.
                    if (_state.value.handle != handle) return@collect
                    val result = users.handleIsAvailable(HandleRules.normalize(handle))
                    if (_state.value.handle != handle) return@collect
                    _state.update {
                        it.copy(
                            handleStatus = when (result) {
                                HandleAvailability.Available -> HandleStatus.Available
                                HandleAvailability.Unavailable -> HandleStatus.Taken
                                is HandleAvailability.Failure -> HandleStatus.Error
                            },
                        )
                    }
                }
        }
    }

    // --- Field setters (mirror the iOS @Published didSet side effects) ---

    fun setHandle(raw: String) {
        // Live-clean like iOS: strip anything that can't be in a valid handle so the field never
        // holds a value the regex would silently reject, then recompute the synchronous status.
        val cleaned = raw.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        _state.update { it.copy(handle = cleaned, handleStatus = syncStatus(cleaned)) }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setCity(value: String) = _state.update { it.copy(city = value) }
    fun setTerms(accepted: Boolean) = _state.update { it.copy(termsAccepted = accepted) }

    /** Format-only status the moment the field changes; the debounced check upgrades
     *  Checking → Available/Taken/Error asynchronously. */
    private fun syncStatus(handle: String): HandleStatus = when {
        handle.isEmpty() -> HandleStatus.Empty
        !HandleRules.isValidFormat(handle) -> HandleStatus.Invalid
        else -> HandleStatus.Checking
    }

    // --- Flow control ---

    fun startSignup() = _state.update { it.copy(mode = SignupMode.Signup, step = SignupStep.Role) }
    fun startLogin() = _state.update { it.copy(mode = SignupMode.Login, step = SignupStep.Auth) }

    fun advance() = _state.update { it.copy(step = nextStep(it.step, it.mode)) }
    fun back() = _state.update { it.copy(step = prevStep(it.step, it.mode)) }

    /**
     * Role picker commit: set the role + fire the haptic. The container themes off this state
     * (`signupState.role` in ArtistantNavHost), so the next screen renders in the picked accent;
     * persistence to prefs isn't needed here — the role is written to the server by the profile
     * upsert and re-read into prefs by the gate's routing when it lands on Tabs. The 0.34s
     * visual-hold before advance lives in the screen.
     */
    fun pickRole(role: AppRole) {
        _state.update { it.copy(role = role) }
        viewModelScope.launch { _events.send(SignupEvent.SelectionHaptic) }
    }

    /**
     * Called when the auth screen reports a completed sign-in. Advances past `.Auth` and clears
     * the session-lost banner. On LOGIN, RootViewModel's routing already hydrates role/name/city
     * from the server, so we don't re-fetch here (that split is the Android gate's job, not the
     * flow's — see RootViewModel.routeSignedIn).
     */
    fun onAuthCompleted() = _state.update {
        if (it.step == SignupStep.Auth) it.copy(step = nextStep(it.step, it.mode), authNotice = null)
        else it.copy(authNotice = null)
    }

    /** Seed the flow at a specific step (the gate presents the container at welcome for
     *  NotSignedIn, or at profile for a signed-in-but-incomplete user). Idempotent. */
    fun resumeAt(step: SignupStep, mode: SignupMode = SignupMode.Signup) {
        _state.update {
            if (it.step == step && it.mode == mode) it else it.copy(step = step, mode = mode)
        }
    }

    /** Prefill draft fields from a returning user's server profile (login hydration parity —
     *  keeps the Done screen personalized even though login skips the profile step). */
    fun hydrate(role: AppRole?, name: String?, city: String?, handle: String?) = _state.update {
        it.copy(
            role = role ?: it.role,
            name = name?.takeIf { n -> n.isNotBlank() } ?: it.name,
            city = city?.takeIf { c -> c.isNotBlank() } ?: it.city,
            handle = handle?.takeIf { h -> h.isNotBlank() } ?: it.handle,
        )
    }

    // --- Profile save ---

    /**
     * Upsert the drafted profile then advance (iOS `saveAndAdvance`). Distinguishes the two
     * recoverable failures: a raced handle bounces the user back to the handle field; a lost
     * session bounces to the auth step with a banner (the session lives in supabase-kt's store,
     * the step in our state — they can desync on a relaunch, exactly as iOS documents).
     */
    fun saveProfile() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                users.upsertSelfProfile(
                    handle = HandleRules.normalize(s.handle),
                    fullName = s.name.trim(),
                    city = s.city,
                    role = s.role,
                    termsAccepted = s.termsAccepted,
                )
                _state.update { it.copy(isSaving = false, step = nextStep(it.step, it.mode)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError.UniqueViolation) {
                // Raced: someone took the handle between the availability RPC and the upsert.
                // Re-run the sync status so the indicator flips to Taken and Continue disables.
                _state.update {
                    it.copy(
                        isSaving = false,
                        saveError = "That handle was just taken — try another.",
                        handleStatus = HandleStatus.Taken,
                    )
                }
            } catch (e: AppError.NotFoundOrUnauthorized) {
                // No live session at save time — bounce to auth with an explainer. The draft
                // fields persist in state so the flow lands right back here after re-auth.
                _state.update {
                    it.copy(
                        isSaving = false,
                        step = SignupStep.Auth,
                        authNotice = "Please sign in again to save your profile.",
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(isSaving = false, saveError = e.message ?: "Couldn't save. Try again.") }
            }
        }
    }

    // --- Done ---

    /** Fire the completion analytics + success haptic, then tell the gate we're done. The gate
     *  (RootViewModel) already sees the live session; this event lets the container stop showing
     *  the flow and hand off to the tabs. */
    fun finish() {
        analytics.capture("signup_complete", mapOf("role" to roleDbValue(_state.value.role)))
        viewModelScope.launch {
            _events.send(SignupEvent.SuccessHaptic)
            _events.send(SignupEvent.Finished)
        }
    }

    private fun roleDbValue(role: AppRole) = when (role) {
        AppRole.Client -> "client"
        AppRole.Artist -> "artist"
    }
}
