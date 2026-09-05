package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The guard in front of "Sign out everywhere else" (design screen 128).
 *
 * `Auth.signOut(SignOutScope.OTHERS)` is not a success signal by itself: supabase-kt swallows
 * 401 / 403 / 404 on the logout POST, and with no session at all it never issues the request and
 * returns normally. The screen then raised "Every other session is signed out" over an account
 * nothing had touched — to somebody who came to that screen because they believe they have been
 * compromised. These are red→green against that.
 */
class SignOutOtherDevicesTest {

    private val now = 1_700_000_000L

    /** A session with plenty of life left in it. */
    @Test
    fun `a live session is revoked without a refresh`() = runTest {
        var refreshed = false

        requireLiveSession(
            expiresAtEpochSeconds = { now + 3600 },
            nowEpochSeconds = { now },
            refresh = { refreshed = true },
        )

        assertFalse("a session in hand needs no round trip", refreshed)
    }

    @Test
    fun `an EXPIRED access token is refreshed rather than used`() = runTest {
        // The finding: `currentSessionOrNull()` keeps returning the cached UserSession after its
        // access token has lapsed, so a presence check reported "live" for exactly the token the
        // revoke was about to be 401'd on — and supabase-kt swallows that 401.
        var expiresAt = now - 5
        var refreshes = 0

        requireLiveSession(
            expiresAtEpochSeconds = { expiresAt },
            nowEpochSeconds = { now },
            refresh = {
                refreshes++
                expiresAt = now + 3600
            },
        )

        assertEquals("an expired token must be refreshed, not used", 1, refreshes)
    }

    @Test
    fun `a token expiring inside the skew window is refreshed too`() = runTest {
        // The check happens here and the token is used a round trip later. A token with seconds
        // left is a token the server will reject by the time it arrives.
        var refreshes = 0
        var expiresAt = now + SESSION_EXPIRY_SKEW_SECONDS - 1

        requireLiveSession(
            expiresAtEpochSeconds = { expiresAt },
            nowEpochSeconds = { now },
            refresh = {
                refreshes++
                expiresAt = now + 3600
            },
        )

        assertEquals(1, refreshes)
    }

    @Test
    fun `the usability rule, stated directly`() {
        assertFalse("no session at all", sessionIsUsable(null, now))
        assertFalse("expired", sessionIsUsable(now - 1, now))
        assertFalse("expiring now", sessionIsUsable(now, now))
        assertFalse("inside the skew", sessionIsUsable(now + SESSION_EXPIRY_SKEW_SECONDS, now))
        assertTrue("outside the skew", sessionIsUsable(now + SESSION_EXPIRY_SKEW_SECONDS + 1, now))
    }

    @Test
    fun `a lapsed token the refresh could not restore is a FAILURE, never a silent success`() =
        runTest {
            val error = runCatching {
                requireLiveSession(
                    expiresAtEpochSeconds = { now - 5 },
                    nowEpochSeconds = { now },
                    refresh = { },
                )
            }.exceptionOrNull()

            assertTrue("expected NoSession, was $error", error is AccountRepositoryError.NoSession)
            // The line screen 128 prints. It says what did not happen, which is the whole point.
            assertEquals("Couldn't reach the server — nothing was signed out yet.", error?.message)
        }

    @Test
    fun `no session at all is the same failure`() = runTest {
        val error = runCatching {
            requireLiveSession(
                expiresAtEpochSeconds = { null },
                nowEpochSeconds = { now },
                refresh = { },
            )
        }.exceptionOrNull()

        assertTrue(error is AccountRepositoryError.NoSession)
    }

    @Test
    fun `a refresh that throws is a failure, not a reason to try the revoke anyway`() = runTest {
        val error = runCatching {
            requireLiveSession(
                expiresAtEpochSeconds = { null },
                nowEpochSeconds = { now },
                refresh = { throw IOException("offline") },
            )
        }.exceptionOrNull()

        assertTrue(error is AccountRepositoryError.NoSession)
    }

    @Test
    fun `the fake still reports what the screen asked it to do`() = runTest {
        val account = FakeAccountRepository()
        account.signOutOtherDevices()
        assertEquals(1, account.signOutOthersCallCount)

        val failing = FakeAccountRepository(failSignOutOthers = true)
        val error = runCatching { failing.signOutOtherDevices() }.exceptionOrNull()
        assertTrue(error is AccountRepositoryError)
        assertNull("a failed revoke reports nothing else", error?.cause?.cause)
    }
}
