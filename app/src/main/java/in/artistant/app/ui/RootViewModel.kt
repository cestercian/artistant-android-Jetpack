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
) : ViewModel() {

    private val _gate = MutableStateFlow<RootGate>(RootGate.Loading)
    val gate: StateFlow<RootGate> = _gate

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
        val route = returningLoginRoute(profile, fetchFailed = result.isFailure)
        // RouteIn(Artist) also hydrates the role gate before we pick the tier; do it here since
        // gateFor is pure and can't touch prefs.
        if (route is ReturningLoginRoute.RouteIn) prefs.setRole(route.role)
        _gate.value = gateFor(route, profile)
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
 * `role == .artist && !setupComplete → ArtistWizardView`. Since M1a has no wizard tier yet,
 * an incomplete artist lands on [RootGate.Onboarding] (the placeholder that becomes the wizard
 * in a later phase), NOT on artist tabs where their booking dashboard would render half-built.
 */
fun gateFor(route: ReturningLoginRoute, profile: SelfProfile?): RootGate = when (route) {
    is ReturningLoginRoute.RouteIn ->
        // An artist whose EPK wizard isn't done (setup_complete false/null) is NOT ready for the
        // artist tabs — route to onboarding/wizard. Clients and complete artists go straight in.
        if (route.role == AppRole.Artist && profile?.artistSetupComplete != true) {
            RootGate.Onboarding
        } else {
            RootGate.Tabs(route.role)
        }
    // Genuinely new/half-finished, OR a failed fetch: move PAST the auth screen either way so a
    // live session is never wedged there. Degrade skips returning-user hydration but still
    // shows onboarding.
    ReturningLoginRoute.Onboard, ReturningLoginRoute.Degrade -> RootGate.Onboarding
}
