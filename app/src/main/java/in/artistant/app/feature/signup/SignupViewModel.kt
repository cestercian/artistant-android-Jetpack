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
import `in`.artistant.app.platform.storage.SignupConsentStore
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
 * The signup flow's observable state (the iOS `OnboardingStore` @Published surface). One
 * immutable snapshot the container renders from. [signedIn] mirrors the iOS store's own
 * `isSignedIn` bit, but the GATE still owns the truth — this copy is only what it told us.
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
    /** ACCT-05 — community pledge agreed (persisted). Gates Role → RoleScreen. */
    val communityAgreed: Boolean = false,
    /** A live session exists. Owned by the gate (RootViewModel) and fed in by [SignupFlow] —
     *  the flow itself never reads supabase-kt. Retires the two pre-auth steps so nothing can
     *  park a signed-in user on `.Auth` (or send them back to `.Welcome`, whose "Sign in"
     *  would drop them into the LOGIN order and skip the profile step they still owe). */
    val signedIn: Boolean = false,
) {
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name.trim() }

    /** Whether `back()` can actually move. False once every earlier step is retired (a signed-in
     *  user standing on `.Role` has nothing behind them), so the system back handler can stand
     *  down instead of swallowing the gesture and doing nothing. */
    val canGoBack: Boolean get() = prevStep(step, mode, signedIn) != step

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
    private val prefs: SignupConsentStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state

    private val _events = Channel<SignupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * The handle the signed-in user ALREADY owns, hydrated from their own `users` row.
     *
     * `handle_is_available` (mig 0007) is a bare "does any row hold this handle" existence check —
     * it has to be, since it is granted to anon and runs before sign-in — so it answers "taken" for
     * the user's own handle. Without this the live check reported a red "Taken" chip on the handle
     * hydrate had just filled in, and Continue stayed disabled until the user picked a different
     * one. Wiped by [reset] with the rest of the departing account's draft.
     */
    private var ownHandle: String? = null

    init {
        viewModelScope.launch {
            prefs.communityAgreed.collect { agreed ->
                _state.update { it.copy(communityAgreed = agreed) }
            }
        }
        // Terms acceptance is persisted for one reason: the gate can present this
        // flow directly at `.Profile` (ArtistantNavHost's Onboarding tier), which
        // skips the welcome screen the checkbox lives on. After a process kill
        // mid-signup, an in-memory-only flag meant `saveProfile` asserted no
        // consent at all for a user who had given it.
        viewModelScope.launch {
            prefs.termsAccepted.collect { accepted ->
                _state.update { it.copy(termsAccepted = accepted) }
            }
        }
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
                    // Their own handle needs no round-trip — and would fail one (see [ownHandle]).
                    if (HandleRules.normalize(handle) == ownHandle) {
                        _state.update { it.copy(handleStatus = HandleStatus.Available) }
                        return@collect
                    }
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

    /** State first so the checkbox answers the tap, then persist — see the `init` collector.
     *  The write is guarded for the reason spelled out on [agreeCommunity]. */
    fun setTerms(accepted: Boolean) {
        _state.update { it.copy(termsAccepted = accepted) }
        viewModelScope.launch { runCatching { prefs.setTermsAccepted(accepted) } }
    }

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

    fun advance() = _state.update { it.copy(step = nextStep(it.step, it.mode, it.signedIn)) }
    fun back() = _state.update { it.copy(step = prevStep(it.step, it.mode, it.signedIn)) }

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
     * ACCT-05 — pledge agreed; step stays `.Role` and re-renders the role picker.
     *
     * The mirror is set here rather than waited for, and the write is guarded, for the two
     * halves of the same reason. `DataStore.edit` throws `IOException` on a preferences file it
     * cannot read or write, and this ran bare inside `viewModelScope.launch` — no
     * `CoroutineExceptionHandler` on that scope, so the throw reached the thread's default one
     * and took the app down on the pledge screen. Guarded but still waiting on the store, the
     * same failure reads as an Agree button that does nothing, because the screen only swaps
     * when the collector reports the flag back. The pledge is re-askable: if the write never
     * landed, the store answers false next launch and this screen simply asks again.
     */
    fun agreeCommunity() = viewModelScope.launch {
        _state.update { it.copy(communityAgreed = true) }
        runCatching { prefs.setCommunityAgreed(true) }
        _events.send(SignupEvent.SelectionHaptic)
    }

    /**
     * The gate's live "a session exists" bit (see [SignupUiState.signedIn]). Called by
     * [SignupFlow] whenever the bit OR the step changes — this is the Android call site for
     * [onAuthCompleted], the port of iOS `RootView.handleAuthChange` → `didCompleteAuth`.
     * Without it nothing in production ever moved the flow off `.Auth`: the gate re-routes to
     * an equal `RootGate.Onboarding`, which MutableStateFlow conflates, so a re-auth re-fired
     * no keys and the user was stranded on the auth screen with no forward control.
     */
    fun setSignedIn(signedIn: Boolean) {
        _state.update { it.copy(signedIn = signedIn) }
        if (signedIn) onAuthCompleted()
    }

    /**
     * Called when the auth screen reports a completed sign-in. Advances past `.Auth` and clears
     * the session-lost banner. On LOGIN, RootViewModel's routing already hydrates role/name/city
     * from the server, so we don't re-fetch here (that split is the Android gate's job, not the
     * flow's — see RootViewModel.routeSignedIn).
     */
    fun onAuthCompleted() = _state.update {
        if (it.step == SignupStep.Auth) it.copy(step = nextStep(it.step, it.mode, it.signedIn), authNotice = null)
        else it.copy(authNotice = null)
    }

    /** Seed the flow at a specific step (the gate presents the container at welcome for
     *  NotSignedIn, or at profile for a signed-in-but-incomplete user). Idempotent. */
    fun resumeAt(step: SignupStep, mode: SignupMode = SignupMode.Signup) {
        _state.update {
            if (it.step == step && it.mode == mode) it else it.copy(step = step, mode = mode)
        }
    }

    /**
     * Back to a pristine flow — the sign-out / delete-account wipe (iOS
     * `OnboardingStore.reset()`, called from both paths in `AccountView`).
     *
     * This ViewModel is hoisted above the gate in `ArtistantNavHost`, so its store
     * owner is the Activity and ONE instance serves every signup this process
     * renders. Without a wipe the departing account's draft — full name, city,
     * @handle, role, accepted terms — is still here when the next person signs up
     * on the same device: [hydrate] fills those fields from the gate's login
     * hydration even for a returning user who never walked the flow, so B would
     * meet a profile form pre-filled with A's PII and a pre-ticked terms box.
     *
     * The accepted-terms bit is cleared even though it is persisted: it is an
     * affirmative act by whoever is holding the phone, and after this wipe we can
     * no longer say that was the same person. The store's copy exists to survive a
     * process death INSIDE the signed-in profile step (the gate's Onboarding tier),
     * which this reset never touches; a fresh signup starts on the welcome screen
     * and collects its own tick.
     *
     * [SignupUiState.communityAgreed] is the one field kept, because it is not an
     * act, it is a MIRROR of the DataStore pledge flag this VM subscribes to — and
     * sign-out wipes that store, so the subscription reports false a moment later
     * on its own. Clearing the mirror while the store still said agreed would
     * re-present the pledge screen whose Agree button writes the value the store
     * already holds; DataStore emits nothing for an unchanged write, so nothing
     * would move that screen on.
     */
    fun reset() {
        // Including the departing account's own handle: to the next person it is just another
        // handle the RPC gets to answer for, and answering Available for it would hand them a
        // Continue button the upsert's unique constraint then rejects.
        ownHandle = null
        _state.value = SignupUiState(communityAgreed = _state.value.communityAgreed)
    }

    /**
     * Prefill draft fields from a returning user's server profile (login hydration parity — keeps
     * the Done screen personalized even though login skips the profile step).
     *
     * A hydrated handle arrives WITH its status, because (`handle`, `handleStatus`) is a pair every
     * other writer keeps consistent — `setHandle` recomputes it through [syncStatus]. Filling the
     * field and leaving the status at `Empty` left `profileValid` false, so the profile screen's
     * Continue sat disabled under a handle the user could plainly see, with nothing on screen
     * saying why. It is `Available` rather than `Checking` because the row it came from is theirs:
     * see [ownHandle] for why asking the RPC instead returns the opposite answer.
     */
    fun hydrate(role: AppRole?, name: String?, city: String?, handle: String?) {
        val own = handle?.takeIf { it.isNotBlank() }?.let { HandleRules.normalize(it) }
        if (own != null) ownHandle = own
        _state.update {
            it.copy(
                role = role ?: it.role,
                name = name?.takeIf { n -> n.isNotBlank() } ?: it.name,
                city = city?.takeIf { c -> c.isNotBlank() } ?: it.city,
                handle = own ?: it.handle,
                handleStatus = when {
                    own == null -> it.handleStatus
                    // A stored handle that no longer passes the format rules has to be re-picked.
                    HandleRules.isValidFormat(own) -> HandleStatus.Available
                    else -> syncStatus(own)
                },
            )
        }
    }

    // --- Profile save ---

    /**
     * Upsert the drafted profile then advance (iOS `saveAndAdvance`). Distinguishes the two
     * recoverable failures: a raced handle bounces the user back to the handle field; a lost
     * session bounces to the auth step with a banner (the session lives in supabase-kt's store,
     * the step in our state — they can desync on a relaunch, exactly as iOS documents), unless
     * the gate says the session IS live, in which case the same error is an RLS denial and the
     * auth step would be a dead end (see the catch below).
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
                _state.update { it.copy(isSaving = false, step = nextStep(it.step, it.mode, it.signedIn)) }
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
                // Two causes read identically here — PGRST116 is "row missing OR blocked by
                // RLS", by design. Only the dead-session one is worth bouncing to `.Auth` for:
                // there the auth screen is the recovery, and the draft fields persist in state
                // so the flow lands right back here after re-auth. With a session still live
                // that step has nothing to offer (and the signed-in flow retires it), so an
                // RLS denial surfaces inline instead of flashing a banner nobody can read.
                _state.update {
                    if (it.signedIn) it.copy(isSaving = false, saveError = "Couldn't save your profile. Try again.")
                    else it.copy(
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
