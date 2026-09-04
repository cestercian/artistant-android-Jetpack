package `in`.artistant.app.ui

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [gateForSessionStatus] — which session statuses are allowed to say "signed out".
 *
 * The rule under test is one-directional and the reason it matters is asymmetric: showing the
 * splash to a session that turns out to be gone costs a moment, while showing the auth screen
 * to a session that is merely mid-hydration costs the session — `ArtistantNavHost` resets the
 * signup flow on every arrival at [RootGate.NotSignedIn], so the user loses what they typed
 * as well as their place.
 */
class SessionGateOrderingTest {

    private val session = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600L,
        tokenType = "bearer",
        user = UserInfo(id = "9f8e7d6c-0000-4000-8000-000000000001", aud = "authenticated"),
    )

    @Test
    fun `initializing holds the splash`() {
        assertEquals(
            RootGate.Loading,
            gateForSessionStatus(SessionStatus.Initializing, RootGate.Loading),
        )
    }

    @Test
    fun `initializing after a route still holds the splash, never the tabs`() {
        // Initializing only happens before the first settled value, so this is a defensive
        // case rather than a reachable one — but "restoring" is not "still signed in", and
        // leaving the tabs up over a session we have stopped believing in is the worse half.
        assertEquals(
            RootGate.Loading,
            gateForSessionStatus(SessionStatus.Initializing, RootGate.Tabs(AppRole.Artist)),
        )
    }

    @Test
    fun `not authenticated is the one status that signs the user out`() {
        assertEquals(
            RootGate.NotSignedIn,
            gateForSessionStatus(SessionStatus.NotAuthenticated(isSignOut = false), RootGate.Loading),
        )
        assertEquals(
            RootGate.NotSignedIn,
            gateForSessionStatus(
                SessionStatus.NotAuthenticated(isSignOut = true),
                RootGate.Tabs(AppRole.Client),
            ),
        )
    }

    @Test
    fun `a refresh failure leaves a routed user exactly where they were`() {
        // The regression this is written against: supabase-kt still holds the session and is
        // still retrying, so a token refresh that could not reach the server is a network
        // blip, not a sign-out. It used to fall into the same branch as NotAuthenticated and
        // drop a working session onto the auth screen.
        val cause = RefreshFailureCause.NetworkError(java.io.IOException("no route to host"))
        for (current in listOf(
            RootGate.Tabs(AppRole.Artist),
            RootGate.Tabs(AppRole.Client),
            RootGate.Onboarding,
            RootGate.ArtistWizard,
        )) {
            assertEquals(current, gateForSessionStatus(SessionStatus.RefreshFailure(cause), current))
        }
    }

    @Test
    fun `a refresh failure before any routing holds the splash`() {
        val cause = RefreshFailureCause.NetworkError(java.io.IOException("no route to host"))
        assertEquals(
            RootGate.Loading,
            gateForSessionStatus(SessionStatus.RefreshFailure(cause), RootGate.Loading),
        )
    }

    @Test
    fun `a refresh failure never promotes the auth screen back to a session`() {
        // The other direction of the hold: if we are legitimately signed out and a stale
        // refresh fails afterwards, "keep the current gate" must not be read as "stay on
        // NotSignedIn forever" in a way that hides a genuine re-auth — it degrades to
        // Loading, which the next settled status immediately corrects.
        val cause = RefreshFailureCause.NetworkError(java.io.IOException("no route to host"))
        assertEquals(
            RootGate.Loading,
            gateForSessionStatus(SessionStatus.RefreshFailure(cause), RootGate.NotSignedIn),
        )
    }

    @Test
    fun `an authenticated session is not a gate on its own`() {
        // null means "the caller fetches the profile and asks gateFor" — the routing pass.
        assertNull(
            gateForSessionStatus(
                SessionStatus.Authenticated(session, SessionSource.Storage),
                RootGate.Loading,
            ),
        )
    }
}
