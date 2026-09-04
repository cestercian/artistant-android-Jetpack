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
import kotlinx.coroutines.Job
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
    private val uploadQueue: `in`.artistant.app.platform.media.UploadQueue,
) : ViewModel() {

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

    /**
     * Newest routing pass wins, however the network resolves.
     *
     * Routing is re-entered from four places — the session collector below, the hydration
     * banner's Retry, and the two flow-finished nudges — and every pass ends in an
     * unconditional write of `_profile`, `_profileHydrationError` and `_gate` after up to
     * three fetch attempts (over a second of wall clock). The Retry is a plain tappable
     * label with no in-flight state, so overlapping passes are the expected case, not a
     * rarity: without a stamp the pass that finishes LAST wins even when it is the older
     * one, and a slow failure landing after a fast success re-raises the error banner and
     * drops a user who had already routed into Tabs back to Onboarding.
     *
     * Same shape ArtistHomeViewModel uses for its overlapping refreshes: cancel the
     * in-flight job AND stamp each pass. The cancel only stops work nobody wants — a
     * pass already past its last suspension point runs on to its writes regardless — so the
     * stamp is what orders them. Sign-out bumps it too, or a pass still in flight could
     * write the departed account's profile back over a NotSignedIn gate.
     */
    private var routingJob: Job? = null
    private var routingGeneration = 0

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
                                // LAUNCHED, not awaited. Awaiting it here parked this
                                // collector inside `fetchWithRetry`, so the sign-out
                                // emission queued behind it and could not reach the
                                // `else` branch below to cancel the pass or bump
                                // [routingGeneration] — the two things that are supposed
                                // to invalidate it. The departed pass then resumed with
                                // its generation still current and wrote `prefs.setRole`
                                // and `uploadQueue.resumeAfterLaunch()` AFTER
                                // `SessionManager.signOut()` had wiped prefs and cleared
                                // the queue, handing the next account on this device the
                                // previous one's role and re-queued uploads. `_profile`
                                // and `_gate` were survivable — the else branch resets
                                // them a moment later — but those two are not.
                                launchRouting()
                            }
                        }
                        else -> {
                            // Tear the session's payload down ONLY for the status that
                            // actually ended it. Initializing and RefreshFailure are
                            // still-hydrating states — supabase-kt has not dropped the
                            // session in either — so they must not cancel the routing
                            // pass, bump the generation, or clear the profile. Doing
                            // that on a RefreshFailure is what turned a token refresh
                            // that could not reach the server into a sign-out.
                            if (status is SessionStatus.NotAuthenticated) {
                                lastRoutedKey = null
                                // A pass still in flight belongs to the session that just
                                // ended: stop it and invalidate its writes (routingGeneration).
                                routingJob?.cancel()
                                routingGeneration++
                                // The hydration payload leaves with the session. `profile`
                                // feeds the signup flow's prefill and the error feeds its
                                // Retry banner, so keeping either past a sign-out means the
                                // NEXT person to reach onboarding on this device can be
                                // shown the previous account's name, city and @handle
                                // (see SignupViewModel.reset).
                                _profile.value = null
                                _profileHydrationError.value = null
                            }
                            // One place decides the gate, and it is pure and tested.
                            gateForSessionStatus(status, _gate.value)?.let { _gate.value = it }
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
        val gen = ++routingGeneration
        // Result.success(null) = genuinely-absent row; Result.failure = a thrown fetch — the
        // distinction returningLoginRoute needs (a failed fetch must NOT re-onboard).
        val result = fetchWithRetry()
        // Everything below WRITES. A pass that has been superseded — by a newer pass or by
        // the sign-out that invalidates all of them — stops here instead of reinstating its
        // own answer over the current one.
        if (gen != routingGeneration) return
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
        // That write suspends, so re-check before the one that actually moves the user.
        if (gen != routingGeneration) return
        _gate.value = gateFor(route, profile)
        // Auth is hydrated — resume any UploadQueue snapshot left by a killed session.
        uploadQueue.resumeAfterLaunch()
    }

    /** Newest pass wins: cancel whatever is in flight, then run one (see [routingGeneration]). */
    private fun launchRouting() {
        routingJob?.cancel()
        routingJob = viewModelScope.launch { routeSignedIn() }
    }

    /** Re-run the routing fetch (the signup flow's hydration-error Retry). */
    fun retryRouting() {
        launchRouting()
    }

    /**
     * The signup flow just wrote a complete profile (Done → "Start exploring"). Re-run routing so
     * the now-complete profile re-fetches and the gate moves Onboarding → Tabs. The session is
     * already live, so `combine` won't re-fire on its own (no generation bump) — this is the
     * explicit nudge. Idempotent: a re-fetch of a complete profile just lands on Tabs again.
     */
    fun markSignupComplete() {
        launchRouting()
    }

    /** Wizard Done → re-fetch profile so setup_complete routes the artist into tabs. */
    fun markWizardComplete() {
        launchRouting()
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
 * Extracted from [RootViewModel.routeSignedIn] so the artist EPK gate is unit-testable
 * without a coroutine/StateFlow.
 *
 * parity: iOS gates an incomplete-EPK artist into the wizard (RootView), not the tabs —
 * `role == .artist && !setupComplete → ArtistWizardView`. A base-profile-complete artist
 * whose EPK isn't done lands on [RootGate.ArtistWizard]; a half-finished users-row still
 * lands on [RootGate.Onboarding] (signup Profile step). Never artist tabs half-built.
 */
fun gateFor(route: ReturningLoginRoute, profile: SelfProfile?): RootGate = when (route) {
    is ReturningLoginRoute.RouteIn ->
        when {
            // Users-row incomplete → finish signup Profile/notif/done first.
            profile == null || !profile.isComplete -> RootGate.Onboarding
            // Base profile done, EPK wizard not → dedicated wizard tier (not signup, not tabs).
            route.role == AppRole.Artist && profile.artistSetupComplete != true ->
                RootGate.ArtistWizard
            else -> RootGate.Tabs(route.role)
        }
    // Genuinely new/half-finished, OR a failed fetch: move PAST the auth screen either way so a
    // live session is never wedged there. Degrade skips returning-user hydration but still
    // shows onboarding.
    ReturningLoginRoute.Onboard, ReturningLoginRoute.Degrade -> RootGate.Onboarding
}
