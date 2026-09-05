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
 *
 * The two halves are separate functions because they now happen at different TIMES: the
 * calendar wipe runs before the receipt so its row can report a real answer, and the sign-out
 * waits for the receipt's Close button so a 30-second logout does not replace stage 3 while
 * somebody is still reading it.
 */
class AccountDeleteCleanupTest {

    // ── The calendar wipe: before the receipt, and honest about what it managed ──────────

    @Test
    fun `a clean wipe reports no reason, so the receipt may tick`() = runTest {
        var wiped = false

        val failure = wipeMirroredCalendar { wiped = true }

        assertTrue(wiped)
        assertNull("nothing failed, so the receipt may tick", failure)
    }

    @Test
    fun `a revoked calendar permission is reported rather than swallowed`() = runTest {
        // wipeForAccountDelete deletes through the calendar provider with no
        // permission check of its own, so a permission revoked since the toggle
        // was enabled throws SecurityException here.
        val failure = wipeMirroredCalendar { throw SecurityException("no calendar permission") }

        // Those events are still sitting in the device owner's calendar, and the receipt's
        // third row says so instead of ticking over an erasure that did not happen.
        assertEquals("no calendar permission", failure)
    }

    @Test
    fun `a failure with no message still gets a reason the receipt can print`() = runTest {
        val failure = wipeMirroredCalendar { throw IllegalStateException() }

        // "Calendar not cleaned — null." is not a sentence anybody should read on this screen.
        assertEquals("this device wouldn't let us", failure)
    }

    @Test
    fun `a blank message is treated as no message`() = runTest {
        assertEquals("this device wouldn't let us", wipeMirroredCalendar { error("   ") })
    }

    // ── The sign-out: on Close, with the DPDP §11 backstop behind it ────────────────────

    @Test
    fun `the happy path reports nothing to the user`() = runTest {
        var signedOut = false
        var localWiped = false

        val message = cleanUpAfterAccountDelete(
            signOut = { signedOut = true },
            wipeLocalState = { localWiped = true },
        )

        assertNull(message)
        assertTrue(signedOut)
        // The local wipe is SessionManager.signOut()'s job on this path — doing it
        // twice would be the only way this could go wrong.
        assertFalse(localWiped)
    }

    @Test
    fun `a failed sign-out wipes local state itself and says so`() = runTest {
        // The logout POST goes out with a JWT whose user was just deleted, and
        // SessionManager does that network call BEFORE clearing prefs/saved ids —
        // so when it throws, nothing else on the device has been wiped.
        var localWiped = false

        val message = cleanUpAfterAccountDelete(
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
            signOut = { throw IOException("airplane mode") },
            wipeLocalState = { throw IOException("datastore gone") },
        )

        assertEquals("Account deleted. Restart the app to finish signing out.", message)
    }
}
