package `in`.artistant.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.domain.auth.ReturningLoginRoute
import `in`.artistant.app.domain.auth.authAdvanceKey
import `in`.artistant.app.domain.auth.returningLoginRoute
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.storage.AppPreferences
import `in`.artistant.app.platform.upload.UploadQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the top-level [RootGate] (the iOS `RootView.handleAuthChange` + `authGatedContent`
 * port). Combines the session status with [SessionManager.signInGeneration] so a returning
 * user re-authenticating into the SAME uuid still re-fires the routing (the authAdvanceKey
 * fix), fetches the server profile, and classifies via [returningLoginRoute].
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: SessionManager,
    private val users: UsersRepository,
    private val prefs: AppPreferences,
    private val uploadQueue: UploadQueue,
) : ViewModel() {

    // One-shot guard so the cross-account upload purge runs once per launch, not on every
    // token refresh / re-auth that re-fires the session collector.
    private var uploadsResumed = false

    private val _gate = MutableStateFlow<RootGate>(RootGate.Loading)
    val gate: StateFlow<RootGate> = _gate

    /** The last successfully-fetched profile, so the signup flow (Onboarding tier) can prefill a
     *  returning user's name/city/handle for a personalized Done screen. Null before the first
     *  successful fetch or when the row is genuinely absent. */
    private val _profile = MutableStateFlow<SelfProfile?>(null)
    val profile: StateFlow<SelfProfile?> = _profile

    /** Non-null when the routing fetch FAILED (network/RLS) — surfaced by the signup flow as a
     *  Retry banner. A failed fetch degrades to Onboarding rather than re-onboarding, but the
     *  cost (an artist possibly mis-routed) must be visible, not silent (iOS audit P1). */
    private val _profileHydrationError = MutableStateFlow<String?>(null)
    val profileHydrationError: StateFlow<String?> = _profileHydrationError

    // Tracks the (uuid, generation) we last routed for so we don't re-fetch on every
    // recomposition — the iOS `.task(id: authAdvanceKey)` equivalent.
    private var lastRoutedKey: String? = null

    init {
        viewModelScope.launch {
            // Re-run routing whenever EITHER the session status OR the sign-in generation
            // changes — folding the generation in is what advances a same-uuid re-auth.
            combine(session.sessionStatus, session.signInGeneration) { status, gen -> status to gen }
                .collect { (status, gen) ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val uid = status.session.user?.id?.lowercase()
                            // Drain any wizard uploads stranded by a prior kill + purge tasks
                            // belonging to a different account (iOS `resumeAfterLaunch`). Once
                            // per launch, off the main thread inside the queue.
                            if (!uploadsResumed) {
                                uploadsResumed = true
                                viewModelScope.launch { uploadQueue.resumeAfterLaunch(uid) }
                            }
                            val key = authAdvanceKey(uid, gen)
                            if (key != lastRoutedKey) {
                                lastRoutedKey = key
                                routeSignedIn()
                            }
                        }
                        // Initializing → keep Loading (avoids an auth-screen flash before the
                        // persisted session restores); every other non-auth state → sign in.
                        is SessionStatus.Initializing -> _gate.value = RootGate.Loading
                        else -> {
                            lastRoutedKey = null
                            _gate.value = RootGate.NotSignedIn
                        }
                    }
                }
        }
    }

    /**
     * Fetch the server profile for a returning user and pick the gate. The fetch THROWS on a
     * network/RLS failure but returns null for a genuinely-absent row — these are NOT the
     * same (a `try?`-style collapse would re-onboard a complete user on a blip). Retry to ride
     * out a transient failure, then classify.
     */
    private suspend fun routeSignedIn() {
        // Result.success(null) = genuinely-absent row; Result.failure = a thrown fetch — the
        // distinction returningLoginRoute needs (a failed fetch must NOT re-onboard).
        val result = fetchWithRetry()
        val profile = result.getOrNull()
        _profile.value = profile
        // Surface a failed fetch as a Retry banner (cleared on success); a null row is NOT an
        // error (genuinely-new user), so don't flag it.
        _profileHydrationError.value = if (result.isFailure)
            "Couldn't load your profile. Check your connection and try again." else null
        val route = returningLoginRoute(profile, fetchFailed = result.isFailure)
        // RouteIn(Artist) also hydrates the role gate before we pick the tier; do it here since
        // gateFor is pure and can't touch prefs.
        if (route is ReturningLoginRoute.RouteIn) prefs.setRole(route.role)
        _gate.value = gateFor(route, profile)
    }

    /** Re-run the routing fetch (the signup flow's hydration-error Retry). */
    fun retryRouting() {
        viewModelScope.launch { routeSignedIn() }
    }

    /**
     * The signup flow just wrote a complete profile (Done → "Start exploring"). Re-run routing so
     * the now-complete profile re-fetches and the gate moves Onboarding → Tabs. The session is
     * already live, so `combine` won't re-fire on its own (no generation bump) — this is the
     * explicit nudge. Idempotent: a re-fetch of a complete profile just lands on Tabs again.
     */
    fun markSignupComplete() {
        viewModelScope.launch { routeSignedIn() }
    }

    private suspend fun fetchWithRetry(attempts: Int = 3): Result<SelfProfile?> {
        var last: Throwable? = null
        repeat(attempts) { i ->
            try {
                return Result.success(users.fetchSelfProfile())
            } catch (t: Throwable) {
                last = t
                if (i < attempts - 1) delay(400) // brief backoff; skip after the last attempt
            }
        }
        return Result.failure(last ?: IllegalStateException("fetch failed"))
    }
}

/**
 * Pure routing: map a classified [ReturningLoginRoute] + the fetched profile to a [RootGate].
 * Extracted from [RootViewModel.routeSignedIn] so the three-tier artist gate is unit-testable
 * without a coroutine/StateFlow.
 *
 * parity: iOS gates an incomplete-EPK artist into the wizard (RootView), not the tabs —
 * `role == .artist && !setupComplete → ArtistWizardView`. M5b makes that literal: a
 * base-profile-complete artist whose EPK wizard isn't done lands on [RootGate.ArtistWizard]
 * (the real wizard), NOT on artist tabs where their booking dashboard would render half-built.
 * A user who hasn't even finished the base profile is a different case → [RootGate.Onboarding].
 */
fun gateFor(route: ReturningLoginRoute, profile: SelfProfile?): RootGate = when (route) {
    is ReturningLoginRoute.RouteIn ->
        // An artist whose EPK wizard isn't done (setup_complete false/null) is NOT ready for the
        // artist tabs — route into the wizard. Clients and complete artists go straight in.
        if (route.role == AppRole.Artist && profile?.artistSetupComplete != true) {
            RootGate.ArtistWizard
        } else {
            RootGate.Tabs(route.role)
        }
    // Genuinely new/half-finished, OR a failed fetch: move PAST the auth screen either way so a
    // live session is never wedged there. Degrade skips returning-user hydration but still
    // shows onboarding.
    ReturningLoginRoute.Onboard, ReturningLoginRoute.Degrade -> RootGate.Onboarding
}
