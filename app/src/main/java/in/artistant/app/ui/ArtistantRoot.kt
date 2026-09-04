package `in`.artistant.app.ui

import io.github.jan.supabase.auth.status.SessionStatus
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.domain.auth.authAdvanceKey

/**
 * Auth gate — the ArtistantRoot / iOS `authGatedContent` analogue. Three tiers, decided
 * by [RootViewModel] from the session status + the fetched server profile:
 *
 *   NotSignedIn ......... signup flow at Welcome (Apple / Google / Email live on its Auth step)
 *   Onboarding .......... signup flow resumed at Profile (signed in, profile not yet complete)
 *   Tabs(role) .......... role tabs
 *
 * Both signup tiers present `feature/signup/SignupFlow` (wired in ArtistantNavHost); the M1b
 * milestone replaced the M1a onboarding placeholder with the real 8-screen flow.
 */
sealed interface RootGate {
    /** Still restoring the persisted session — show nothing (avoids an auth-screen flash). */
    data object Loading : RootGate
    data object NotSignedIn : RootGate
    data object Onboarding : RootGate

    /** Signed-in artist whose base profile is done but whose EPK wizard isn't (`artists`
     *  row missing / `setup_complete=false`) → the M5b onboarding wizard, NOT the profile
     *  step (which they've already finished) and NOT the artist tabs (half-built dashboard).
     *  iOS parity: `role == .artist && !setupComplete → ArtistWizardView`. */
    data object ArtistWizard : RootGate

    data class Tabs(val role: AppRole) : RootGate
}

/**
 * What a session status that is NOT [SessionStatus.Authenticated] means for the gate.
 *
 * Returns null for an authenticated session, which is not a gate on its own: it starts a
 * profile fetch and the answer comes from [gateFor] several hundred milliseconds later.
 *
 * The rule this encodes is that **only [SessionStatus.NotAuthenticated] means signed out**.
 * It used to be written as `Initializing -> Loading` with an `else` that swallowed the other
 * two, and the two it swallowed are not sign-outs:
 *
 *  - [SessionStatus.RefreshFailure] is a token refresh that could not reach the server. The
 *    session is still in memory, supabase-kt is still retrying, and `currentSessionOrNull()`
 *    still answers — the user is signed in and momentarily un-refreshed. Routing them to
 *    [RootGate.NotSignedIn] over a network blip dropped a working session onto the auth
 *    screen, and because `ArtistantNavHost` resets the signup flow on every arrival at that
 *    gate, it also threw away whatever they had typed. So it HOLDS: [current] if we have
 *    already routed, [RootGate.Loading] if we never got that far.
 *  - An unknown future status is the same call. Holding a hydrating session costs a moment
 *    on the splash; calling it a sign-out costs the session.
 *
 * [current] is the gate on screen. Passing it in rather than reading it keeps this pure, and
 * makes "a refresh failure changes nothing" a property a test can state directly.
 */
fun gateForSessionStatus(status: SessionStatus, current: RootGate): RootGate? = when (status) {
    // Not ours to answer — the caller fetches the profile and calls gateFor.
    is SessionStatus.Authenticated -> null
    // The persisted session is still being restored: hold screen 01 rather than flash the
    // auth screen at a user who turns out to be signed in.
    is SessionStatus.Initializing -> RootGate.Loading
    // The one status that actually means "no session".
    is SessionStatus.NotAuthenticated -> RootGate.NotSignedIn
    // RefreshFailure and anything supabase-kt adds later: still hydrating, never signed out.
    else -> if (current == RootGate.NotSignedIn) RootGate.Loading else current
}

/**
 * What one observed session status does to the routing pass: which `(uuid, generation)` key
 * we hold afterwards, and whether that status has to START a pass.
 *
 * @property key the key to remember as "the one we have routed for", or null for "we are not
 *   routed for anyone".
 * @property route true when [RootViewModel] must launch a routing pass for [key].
 */
data class RoutingStep(val key: String?, val route: Boolean)

/**
 * Fold a session status into the routing key ([RoutingStep]). Pure half of the collector in
 * [RootViewModel]; [lastRoutedKey] is the key it is currently holding.
 *
 * Two rules, and the second one is a fix:
 *
 *  - An authenticated session routes **once** per `(uuid, generation)`. Supabase-kt re-emits
 *    [SessionStatus.Authenticated] on every background token refresh, and the generation only
 *    moves on a real sign-in, so without this the profile would be re-fetched on a timer.
 *  - **Every other status forgets the key**, so the next authenticated emission routes again
 *    even though nothing about the user changed. [SessionStatus.RefreshFailure] deliberately
 *    does NOT cancel an in-flight pass or clear the profile (a refresh that could not reach
 *    the server is a blip, not a sign-out — see [gateForSessionStatus]), and that is exactly
 *    what made keeping the key wrong: the pass running through the outage fetches, fails all
 *    three attempts, and settles a complete account on [RootGate.Onboarding] with a Retry
 *    banner. When the network comes back supabase-kt re-emits Authenticated for the same uuid
 *    at the same generation — the key we were still holding — so the collector skipped it and
 *    the user stayed on onboarding until they tapped Retry or relaunched. Forgetting costs one
 *    idempotent re-fetch on recovery; holding costs the session's routing.
 *    [SessionStatus.Initializing] is the same shape (it parks the gate on
 *    [RootGate.Loading], so a skipped re-route would hold the splash), and an unknown future
 *    status gets the safe half of the trade.
 */
fun routingStep(status: SessionStatus, generation: Int, lastRoutedKey: String?): RoutingStep =
    when (status) {
        is SessionStatus.Authenticated -> {
            val key = authAdvanceKey(status.session.user?.id?.lowercase(), generation)
            RoutingStep(key = key, route = key != lastRoutedKey)
        }
        else -> RoutingStep(key = null, route = false)
    }
