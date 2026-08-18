package `in`.artistant.app.platform.push

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cross-account push leak (audit p02-fcm).
 *
 * The orchestration itself — `SessionManager.signOut()` releasing the token BEFORE
 * `auth.signOut()`, and `completedSignIn()` re-claiming it — isn't reachable from a JVM test
 * (both need a real `SupabaseClient` and an Android `Context`), so what's pinned here is the
 * seam it depends on, in the AccountErasureTest style: the [DeviceTokenRepository] contract
 * whose ordering rules are the reason the fix is shaped the way it is.
 *
 * The bug: a token claimed for user A was never released on sign-out and never re-claimed on a
 * later sign-in, so after an in-process account switch A's message previews and booking pushes
 * were delivered to B's phone, and B got nothing until a cold start.
 */
class DeviceTokenLifecycleTest {

    private val tokenT = "fcm-token-for-this-device"
    private val userA = "aaaaaaaa-0000-4000-8000-000000000001"
    private val userB = "bbbbbbbb-0000-4000-8000-000000000002"

    @Test
    fun signingInClaimsThisDeviceForTheUserWhoJustArrived() = runTest {
        val tokens = FakeDeviceTokenRepository()
        tokens.currentUserId = userA

        tokens.register(tokenT)

        assertEquals(userA, tokens.owners[tokenT])
    }

    /** The half that stops the leak: sign-out hands the row back, so nobody is addressed. */
    @Test
    fun signingOutReleasesTheRowSoTheDepartingAccountStopsGettingThisPhonesPushes() = runTest {
        val tokens = FakeDeviceTokenRepository()
        tokens.currentUserId = userA
        tokens.register(tokenT)

        tokens.unregister(tokenT)

        assertNull("a released token must address nobody", tokens.owners[tokenT])
    }

    /**
     * Why `onSigningOut()` runs BEFORE `client.auth.signOut()`: the delete is RLS-scoped to the
     * caller's own row, so once the session is gone it silently reaches nothing and the stale
     * row survives — the original defect.
     */
    @Test
    fun releasingAfterTheSessionIsGoneIsANoOp() = runTest {
        val tokens = FakeDeviceTokenRepository()
        tokens.currentUserId = userA
        tokens.register(tokenT)

        tokens.currentUserId = null // auth.signOut() already ran
        tokens.unregister(tokenT)

        assertEquals(
            "a post-signOut delete can't see the row, so the release must beat the sign-out",
            userA,
            tokens.owners[tokenT],
        )
    }

    /** Nor can the next account clean up after the previous one — RLS won't show it the row. */
    @Test
    fun anotherAccountCannotReleaseSomeoneElsesRow() = runTest {
        val tokens = FakeDeviceTokenRepository()
        tokens.currentUserId = userA
        tokens.register(tokenT)

        tokens.currentUserId = userB
        tokens.unregister(tokenT)

        assertEquals(userA, tokens.owners[tokenT])
    }

    /**
     * The full in-process account switch: A signs out (release while still authenticated), B
     * signs in and re-claims. The token must end up addressing B — and, crucially, not A.
     */
    @Test
    fun anInProcessAccountSwitchMovesTheTokenToTheNewUser() = runTest {
        val tokens = FakeDeviceTokenRepository()
        tokens.currentUserId = userA
        tokens.register(tokenT)

        // sign-out: release first, then the session goes
        tokens.unregister(tokenT)
        tokens.currentUserId = null

        // B signs in on the same device, same install → same FCM token
        tokens.currentUserId = userB
        tokens.register(tokenT)

        assertEquals("pushes must follow the account that is actually signed in", userB, tokens.owners[tokenT])
    }

    /**
     * Without the `completedSignIn()` re-claim the device is addressed by nobody after a
     * sign-out — B would receive no pushes at all until a cold start re-ran
     * `ArtistantApplication.onCreate`.
     */
    @Test
    fun aSignInThatNeverReClaimsLeavesTheDeviceAddressingNobody() = runTest {
        val tokens = FakeDeviceTokenRepository()
        tokens.currentUserId = userA
        tokens.register(tokenT)
        tokens.unregister(tokenT)

        tokens.currentUserId = userB // signed in, but no re-registration

        assertNull(tokens.owners[tokenT])
    }

    /**
     * `claim_device_token` is SECURITY DEFINER precisely so it can take a row still owned by
     * someone else — the belt to the release's braces when a sign-out never got to run (crash,
     * force-stop, offline delete failure).
     */
    @Test
    fun aClaimTakesOverARowStillHeldByThePriorOwner() = runTest {
        val tokens = FakeDeviceTokenRepository()
        tokens.currentUserId = userA
        tokens.register(tokenT)

        tokens.currentUserId = userB
        tokens.register(tokenT) // no release ever happened

        assertEquals(userB, tokens.owners[tokenT])
    }

    /** No session → nothing to claim to. Registration is skipped, not attributed to a guess. */
    @Test
    fun claimingWithNoSessionIsSkipped() = runTest {
        val tokens = FakeDeviceTokenRepository()

        tokens.register(tokenT)

        assertNull(tokens.owners[tokenT])
    }
}
