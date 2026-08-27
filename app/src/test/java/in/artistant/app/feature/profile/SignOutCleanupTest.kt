package `in`.artistant.app.feature.profile

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * What happens on the device when a PLAIN sign-out doesn't finish.
 *
 * `SessionManager.signOut()` posts the network logout first and clears prefs /
 * saved ids only after it returns, so a throw anywhere in that sequence skips
 * the local wipe. The delete-account path answers that with an unconditional
 * backstop, because a deleted account is gone whatever the logout did. Sign-out
 * cannot copy it: supabase-kt 3.0.3 only reaches `clearSession()` on a logout it
 * did not rethrow, so a logout that threw leaves the user SIGNED IN — and wiping
 * a live session's own role, saved ids and thread flags is destruction, not
 * hygiene. Whether the session actually went away is the discriminator.
 */
class SignOutCleanupTest {

    @Test
    fun `the happy path reports nothing and leaves the wipe to SessionManager`() = runTest {
        var signedOut = false
        var localWiped = false

        val message = cleanUpAfterSignOut(
            signOut = { signedOut = true },
            stillSignedIn = { false },
            wipeLocalState = { localWiped = true },
        )

        assertNull(message)
        assertTrue(signedOut)
        // Doing it twice is the only way this path could go wrong.
        assertFalse(localWiped)
    }

    @Test
    fun `a logout that never landed leaves the device alone and says so`() = runTest {
        // Offline: the logout POST throws, supabase-kt never clears its stored
        // session, and the user is still signed in. Nothing was cleared, so
        // nothing may be — the settings row is the retry.
        var localWiped = false

        val message = cleanUpAfterSignOut(
            signOut = { throw IOException("airplane mode") },
            stillSignedIn = { true },
            wipeLocalState = { localWiped = true },
        )

        assertEquals("airplane mode", message)
        assertFalse(localWiped)
    }

    @Test
    fun `a failure with no message still says something`() = runTest {
        val message = cleanUpAfterSignOut(
            signOut = { throw IOException() },
            stillSignedIn = { true },
            wipeLocalState = {},
        )

        assertEquals("Sign out failed", message)
    }

    @Test
    fun `a logout that landed before a later step threw finishes the wipe`() = runTest {
        // The session IS gone and prefs.wipeAll() — a DataStore edit — threw
        // behind it, so the departed account's role, saved ids and thread flags
        // were left on a device with no session left to reach them. DPDP §11.
        var localWiped = false

        val message = cleanUpAfterSignOut(
            signOut = { throw IOException("datastore gone") },
            stillSignedIn = { false },
            wipeLocalState = { localWiped = true },
        )

        assertTrue(localWiped)
        // Silence: the user got exactly what they asked for, and the auth screen
        // is already replacing this one as the cleared session propagates.
        assertNull(message)
    }

    @Test
    fun `a local wipe that throws does not propagate`() = runTest {
        // Same IOException that got us here can hit the retry. Nothing may
        // escape: the call site is a bare viewModelScope.launch with no
        // CoroutineExceptionHandler behind it.
        val message = cleanUpAfterSignOut(
            signOut = { throw IOException("datastore gone") },
            stillSignedIn = { false },
            wipeLocalState = { throw IOException("still gone") },
        )

        assertNull(message)
    }

    @Test
    fun `the signed-in check is read after the attempt, not before`() = runTest {
        // The whole point: SessionManager tears the session down inside the call
        // this helper wraps, so a check taken beforehand would always say
        // "signed in" and the backstop would never run.
        var signedOut = false
        var localWiped = false

        val message = cleanUpAfterSignOut(
            signOut = {
                signedOut = true
                throw IOException("prefs wipe blew up")
            },
            stillSignedIn = { !signedOut },
            wipeLocalState = { localWiped = true },
        )

        assertTrue(localWiped)
        assertNull(message)
    }
}
