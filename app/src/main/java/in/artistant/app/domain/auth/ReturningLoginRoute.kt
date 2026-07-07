package `in`.artistant.app.domain.auth

import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole

/**
 * Routing decision for a returning user who lands with a live session but isn't yet
 * marked signed-in locally. Pure + exhaustive so it's unit-testable and the THREE
 * distinct outcomes are explicit (port of iOS `RootView.ReturningLoginRoute`).
 *
 * The critical distinction the iOS code learned the hard way: a FAILED profile fetch
 * is NOT the same as an absent profile. Collapsing them re-onboards a complete user on
 * a transient network blip — [Degrade] exists precisely so a failed fetch is never
 * mistaken for a genuinely-absent profile.
 */
sealed interface ReturningLoginRoute {
    /** Present, complete server profile → skip onboarding, go straight into the app. */
    data class RouteIn(val role: AppRole) : ReturningLoginRoute
    /** Genuinely new or half-finished account → run the normal onboarding flow. */
    data object Onboard : ReturningLoginRoute
    /** The profile fetch FAILED — make no routing change and retry later. NOT [Onboard]:
     *  a failed fetch must never re-onboard a possibly-complete user. */
    data object Degrade : ReturningLoginRoute
}

/**
 * Classify a returning login. See [ReturningLoginRoute] for the why.
 *
 * @param profile the fetched profile, or null for a genuinely-absent row.
 * @param fetchFailed true iff the fetch threw (network/RLS) — distinct from a null row.
 */
fun returningLoginRoute(profile: SelfProfile?, fetchFailed: Boolean): ReturningLoginRoute {
    // A present, complete profile wins regardless of anything else — route in.
    val role = profile?.role
    if (profile != null && profile.isComplete && role != null) {
        return ReturningLoginRoute.RouteIn(role)
    }
    // No usable profile. If the fetch FAILED we don't actually know whether the user is
    // complete, so make no change (retry later). Only a SUCCESSFUL fetch that returned an
    // absent/incomplete row is real evidence of a new or half-finished account.
    return if (fetchFailed) ReturningLoginRoute.Degrade else ReturningLoginRoute.Onboard
}
