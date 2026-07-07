package `in`.artistant.app.domain.auth

import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ports iOS `ReturningLoginRouteTests` — the three outcomes must stay distinct. */
class ReturningLoginRouteTest {

    private fun profile(
        role: AppRole? = AppRole.Client,
        handle: String? = "yash",
    ) = SelfProfile(role = role, fullName = "Yash", city = "Bangalore", handle = handle, artistSetupComplete = null)

    @Test
    fun `complete profile routes in with its role`() {
        val route = returningLoginRoute(profile(role = AppRole.Artist), fetchFailed = false)
        assertEquals(ReturningLoginRoute.RouteIn(AppRole.Artist), route)
    }

    @Test
    fun `absent profile with successful fetch onboards`() {
        assertEquals(ReturningLoginRoute.Onboard, returningLoginRoute(null, fetchFailed = false))
    }

    @Test
    fun `incomplete profile (no handle) onboards`() {
        val route = returningLoginRoute(profile(handle = ""), fetchFailed = false)
        assertEquals(ReturningLoginRoute.Onboard, route)
    }

    @Test
    fun `incomplete profile (no role) onboards`() {
        val route = returningLoginRoute(profile(role = null), fetchFailed = false)
        assertEquals(ReturningLoginRoute.Onboard, route)
    }

    @Test
    fun `failed fetch degrades, never onboards`() {
        // The critical case: a null profile from a THROWN fetch must degrade, not onboard —
        // otherwise a complete user is re-asked for role/handle on a transient blip.
        assertEquals(ReturningLoginRoute.Degrade, returningLoginRoute(null, fetchFailed = true))
    }

    @Test
    fun `complete profile wins even if fetchFailed is somehow set`() {
        val route = returningLoginRoute(profile(), fetchFailed = true)
        assertEquals(ReturningLoginRoute.RouteIn(AppRole.Client), route)
    }
}
