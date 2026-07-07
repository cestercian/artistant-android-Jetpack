package `in`.artistant.app.ui

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
    data class Tabs(val role: AppRole) : RootGate
}
