package `in`.artistant.app.feature.profile

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * What happens on the device AFTER the server has erased the account.
 *
 * Every case here is a step that used to run bare inside
 * `runCatching { deleteAccount() }.onSuccess { }` — an inline lambda that catches
 * nothing. A throw there escaped `viewModelScope` (no CoroutineExceptionHandler)
 * and crashed the app seconds after the account was deleted, which is the worst
 * possible moment for the local wipe to stop half-way.
 */
class AccountDeleteCleanupTest {

    @Test
    fun `the happy path reports nothing to the user`() = runTest {
        var wiped = false
        var signedOut = false
        var localWiped = false

        val message = cleanUpAfterAccountDelete(
            wipeCalendar = { wiped = true },
            signOut = { signedOut = true },
            wipeLocalState = { localWiped = true },
        )

        assertNull(message)
        assertTrue(wiped)
        assertTrue(signedOut)
        // The local wipe is SessionManager.signOut()'s job on this path — doing it
        // twice would be the only way this could go wrong.
        assertFalse(localWiped)
    }

    @Test
    fun `a revoked calendar permission does not stop the sign-out`() = runTest {
        // wipeForAccountDelete deletes through the calendar provider with no
        // permission check of its own, so a permission revoked since the toggle
        // was enabled throws SecurityException here.
        var signedOut = false

        val message = cleanUpAfterAccountDelete(
            wipeCalendar = { throw SecurityException("no calendar permission") },
            signOut = { signedOut = true },
            wipeLocalState = {},
        )

        assertNull(message)
        assertTrue(signedOut)
    }

    @Test
    fun `a failed sign-out wipes local state itself and says so`() = runTest {
        // The logout POST goes out with a JWT whose user was just deleted, and
        // SessionManager does that network call BEFORE clearing prefs/saved ids —
        // so when it throws, nothing else on the device has been wiped.
        var localWiped = false

        val message = cleanUpAfterAccountDelete(
            wipeCalendar = {},
            signOut = { throw IOException("airplane mode") },
            wipeLocalState = { localWiped = true },
        )

        assertTrue(localWiped)
        assertNotNull(message)
        assertTrue(message!!.contains("Restart"))
    }

    @Test
    fun `a local wipe that throws still reports the failed sign-out`() = runTest {
        // DataStore edits throw IOException too. Nothing here may propagate: the
        // account is already gone, so a crash would be pure collateral.
        val message = cleanUpAfterAccountDelete(
            wipeCalendar = { throw SecurityException("no calendar permission") },
            signOut = { throw IOException("airplane mode") },
            wipeLocalState = { throw IOException("datastore gone") },
        )

        assertEquals("Account deleted. Restart the app to finish signing out.", message)
    }
}
