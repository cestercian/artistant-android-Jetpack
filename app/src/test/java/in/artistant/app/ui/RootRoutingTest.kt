package `in`.artistant.app.ui

import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.domain.auth.ReturningLoginRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the three-tier artist gate in [gateFor] (parity: iOS RootView.authGatedContent):
 * an incomplete-EPK artist must NOT land on the artist tabs.
 */
class RootRoutingTest {

    private fun profile(
        role: AppRole? = AppRole.Artist,
        artistSetupComplete: Boolean? = null,
    ) = SelfProfile(
        role = role,
        fullName = "Yash",
        city = "Bangalore",
        handle = "yash",
        artistSetupComplete = artistSetupComplete,
    )

    @Test
    fun `RouteIn artist with incomplete EPK goes to the wizard, not tabs`() {
        // M5b: a base-profile-complete artist whose EPK wizard isn't done lands on the real
        // wizard tier (was RootGate.Onboarding when the wizard was a placeholder).
        val gate = gateFor(
            ReturningLoginRoute.RouteIn(AppRole.Artist),
            profile(role = AppRole.Artist, artistSetupComplete = false),
        )
        assertEquals(RootGate.ArtistWizard, gate)
    }

    @Test
    fun `RouteIn artist with null EPK flag also goes to the wizard`() {
        // null (no artists row / not hydrated) is treated the same as incomplete.
        val gate = gateFor(
            ReturningLoginRoute.RouteIn(AppRole.Artist),
            profile(role = AppRole.Artist, artistSetupComplete = null),
        )
        assertEquals(RootGate.ArtistWizard, gate)
    }

    @Test
    fun `RouteIn artist with complete EPK goes to artist tabs`() {
        val gate = gateFor(
            ReturningLoginRoute.RouteIn(AppRole.Artist),
            profile(role = AppRole.Artist, artistSetupComplete = true),
        )
        assertEquals(RootGate.Tabs(AppRole.Artist), gate)
    }

    @Test
    fun `RouteIn client goes straight to client tabs`() {
        val gate = gateFor(
            ReturningLoginRoute.RouteIn(AppRole.Client),
            profile(role = AppRole.Client),
        )
        assertEquals(RootGate.Tabs(AppRole.Client), gate)
    }

    @Test
    fun `Onboard and Degrade both land on onboarding`() {
        assertEquals(RootGate.Onboarding, gateFor(ReturningLoginRoute.Onboard, null))
        assertEquals(RootGate.Onboarding, gateFor(ReturningLoginRoute.Degrade, null))
    }
}
