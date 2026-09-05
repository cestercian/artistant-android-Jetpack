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

    /** A session that is simply there. */
    @Test
    fun `a live session is revoked without a refresh`() = runTest {
        var refreshed = false

        requireLiveSession(hasSession = { true }, refresh = { refreshed = true })

        assertFalse("a session in hand needs no round trip", refreshed)
    }

    @Test
    fun `a lapsed access token is refreshed, and the revoke goes ahead`() = runTest {
        // The ordinary case: the app was in the background long enough for the access token to
        // expire, and the refresh token is exactly what supabase-kt is holding for it.
        var session = false
        var refreshes = 0

        requireLiveSession(
            hasSession = { session },
            refresh = {
                refreshes++
                session = true
            },
        )

        assertEquals(1, refreshes)
    }

    @Test
    fun `no session and no refresh is a FAILURE, never a silent success`() = runTest {
        val error = runCatching {
            requireLiveSession(hasSession = { false }, refresh = { })
        }.exceptionOrNull()

        assertTrue("expected NoSession, was $error", error is AccountRepositoryError.NoSession)
        // The line screen 128 prints. It says what did not happen, which is the whole point.
        assertEquals("Couldn't reach the server — nothing was signed out yet.", error?.message)
    }

    @Test
    fun `a refresh that throws is a failure, not a reason to try the revoke anyway`() = runTest {
        val error = runCatching {
            requireLiveSession(
                hasSession = { false },
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
