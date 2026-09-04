package `in`.artistant.app.ui

import io.github.jan.supabase.auth.status.SessionStatus
import `in`.artistant.app.designsystem.theme.AppRole

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
