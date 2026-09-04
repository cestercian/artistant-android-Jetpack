package `in`.artistant.app.ui

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [routingStep] — when an observed session status has to start a routing pass.
 *
 * The pairing with [gateForSessionStatus] is the point. That one says a
 * [SessionStatus.RefreshFailure] changes nothing (the session is still in memory, so it is a
 * blip and not a sign-out); this one says it still has to FORGET which key we routed for,
 * because a pass that ran through the outage can have settled a complete account on
 * [RootGate.Onboarding] and the recovery is the only thing that will correct it.
 */
class RoutingStepTest {

    private val uuid = "9F8E7D6C-0000-4000-8000-000000000001"

    private fun authenticated(id: String = uuid) = SessionStatus.Authenticated(
        UserSession(
            accessToken = "access",
            refreshToken = "refresh",
            expiresIn = 3600L,
            tokenType = "bearer",
            user = UserInfo(id = id, aud = "authenticated"),
        ),
        SessionSource.Storage,
    )

    private fun refreshFailure() =
        SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(java.io.IOException("offline")))

    @Test
    fun `a first authenticated session routes and is remembered`() {
        val step = routingStep(authenticated(), generation = 0, lastRoutedKey = null)
        assertTrue(step.route)
        assertNotNull(step.key)
    }

    @Test
    fun `the same session at the same generation does not route twice`() {
        // Supabase-kt re-emits Authenticated on every background token refresh; the generation
        // only moves on a real sign-in. Re-fetching the profile on that timer is the thing the
        // key exists to prevent.
        val first = routingStep(authenticated(), generation = 0, lastRoutedKey = null)
        val second = routingStep(authenticated(), generation = 0, lastRoutedKey = first.key)
        assertFalse(second.route)
        assertEquals(first.key, second.key)
    }

    @Test
    fun `the same uuid at a new generation routes again`() {
        // A returning user re-authenticating into the SAME account — the authAdvanceKey fix.
        val first = routingStep(authenticated(), generation = 0, lastRoutedKey = null)
        val reAuth = routingStep(authenticated(), generation = 1, lastRoutedKey = first.key)
        assertTrue(reAuth.route)
    }

    @Test
    fun `the key is lowercased, so a re-cased uuid is the same user`() {
        val upper = routingStep(authenticated(uuid), generation = 0, lastRoutedKey = null)
        val lower = routingStep(authenticated(uuid.lowercase()), generation = 0, lastRoutedKey = upper.key)
        assertFalse(lower.route)
    }

    @Test
    fun `a refresh failure forgets the key`() {
        val routed = routingStep(authenticated(), generation = 0, lastRoutedKey = null)
        val failed = routingStep(refreshFailure(), generation = 0, lastRoutedKey = routed.key)
        assertNull(failed.key)
        assertFalse(failed.route)
    }

    @Test
    fun `refresh failure then recovery re-routes the same user`() {
        // The regression. A routing pass in flight when the network died fetches, fails all
        // three attempts and settles the user on Onboarding with a Retry banner. The refresh
        // failure deliberately leaves that pass and the gate alone, so the ONLY thing that can
        // put a complete account back on its tabs is the Authenticated emission that arrives
        // when the network returns — same uuid, same generation, so it used to be skipped as
        // "already routed" and the user stayed on onboarding until they relaunched.
        var key: String? = null

        key = routingStep(authenticated(), generation = 0, lastRoutedKey = key)
            .also { assertTrue("first sign-in routes", it.route) }.key

        key = routingStep(refreshFailure(), generation = 0, lastRoutedKey = key).key

        val recovery = routingStep(authenticated(), generation = 0, lastRoutedKey = key)
        assertTrue("recovery must re-route the same user", recovery.route)
        assertNotNull(recovery.key)
    }

    @Test
    fun `initializing forgets the key too`() {
        // Initializing parks the gate on Loading (gateForSessionStatus), so a skipped re-route
        // would hold the splash over a live session.
        val routed = routingStep(authenticated(), generation = 0, lastRoutedKey = null)
        assertNull(routingStep(SessionStatus.Initializing, 0, routed.key).key)
    }

    @Test
    fun `not authenticated forgets the key`() {
        val routed = routingStep(authenticated(), generation = 0, lastRoutedKey = null)
        val out = routingStep(SessionStatus.NotAuthenticated(isSignOut = true), 0, routed.key)
        assertNull(out.key)
        assertFalse(out.route)
    }

    @Test
    fun `a different user always routes`() {
        val first = routingStep(authenticated(), generation = 0, lastRoutedKey = null)
        val other = routingStep(
            authenticated("11111111-0000-4000-8000-000000000002"),
            generation = 0,
            lastRoutedKey = first.key,
        )
        assertTrue(other.route)
    }
}
