package `in`.artistant.app.ui

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** Offline. The only RefreshFailure cause the app meets in the field. */
    private val cause = RefreshFailureCause.NetworkError(java.io.IOException("no route to host"))

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
    fun `a refresh failure before any routing gets a screen with a way out`() {
        // The cold-start case: an expired token and no network. It used to hold `Loading`,
        // which is the splash — and the splash has no controls, while auth-kt re-emits
        // RefreshFailure every ten seconds offline and publishes nothing else. That is a dead
        // end, so it gets [RootGate.Reconnecting]: the same splash, plus a banner that says
        // what is happening and a "Sign in again" that ends the session cleanly.
        assertEquals(
            RootGate.Reconnecting,
            gateForSessionStatus(SessionStatus.RefreshFailure(cause), RootGate.Loading),
        )
    }

    @Test
    fun `a refresh failure at the auth screen is still not a session`() {
        // Barely reachable — nothing refreshes once the session is gone — but the rule has to
        // answer it, and the answer must not be a gate that claims a session. Reconnecting
        // says exactly as much as we know and its action lands back here.
        assertEquals(
            RootGate.Reconnecting,
            gateForSessionStatus(SessionStatus.RefreshFailure(cause), RootGate.NotSignedIn),
        )
    }

    @Test
    fun `the reconnect screen stays put while the failures repeat`() {
        // auth-kt re-emits RefreshFailure on its own timer for as long as the device is
        // offline. Each one must be a no-op, not a re-entry that resets anything.
        assertEquals(
            RootGate.Reconnecting,
            gateForSessionStatus(SessionStatus.RefreshFailure(cause), RootGate.Reconnecting),
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

    @Test
    fun `recovery from a refresh failure re-routes rather than resuming`() {
        // The whole loop, in the order the collector sees it: a routed artist loses the
        // refresh, keeps their tabs, and comes back.
        val authenticated = SessionStatus.Authenticated(session, SessionSource.Storage)
        val routed = routingStep(authenticated, generation = 3, lastRoutedKey = null)
        assertTrue("the first authenticated emission routes", routed.route)

        // Offline. The gate holds and the key is forgotten — that second half is what makes
        // the recovery re-fetch instead of skipping a key it is still holding.
        val degraded = routingStep(SessionStatus.RefreshFailure(cause), 3, routed.key)
        assertNull(degraded.key)
        assertFalse(degraded.route)
        assertEquals(
            RootGate.Tabs(AppRole.Artist),
            gateForSessionStatus(SessionStatus.RefreshFailure(cause), RootGate.Tabs(AppRole.Artist)),
        )

        // Back. Same uuid, same generation — supabase-kt just re-emits Authenticated — and it
        // routes because nothing is remembered.
        val recovered = routingStep(authenticated, generation = 3, lastRoutedKey = degraded.key)
        assertTrue("the recovery must route, or the user is stuck on the degraded read", recovered.route)
        assertEquals(routed.key, recovered.key)
        assertNull(gateForSessionStatus(authenticated, RootGate.Tabs(AppRole.Artist)))
    }

    @Test
    fun `a harness import in flight decides nothing`() {
        // `SessionBootstrapHold` — the debug harness is about to replace whatever the session
        // says. Rendering the signup flow into that gap is the bug the blocking wait in
        // HarnessInstaller used to paper over; now the gate simply holds the splash.
        val authenticated = SessionStatus.Authenticated(session, SessionSource.Storage)
        for (status in listOf(
            authenticated,
            SessionStatus.NotAuthenticated(isSignOut = false),
            SessionStatus.RefreshFailure(cause),
            SessionStatus.Initializing,
        )) {
            assertEquals(
                RootGate.Loading,
                gateForSessionStatus(status, RootGate.Loading, bootstrapPending = true),
            )
            assertFalse(
                "a session about to be replaced must not start a routing pass",
                routingStep(status, generation = 1, lastRoutedKey = null, bootstrapPending = true).route,
            )
        }
    }
}
