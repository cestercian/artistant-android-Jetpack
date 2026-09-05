package `in`.artistant.app.platform.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The delete that got as far as the server and no further.
 *
 * Design 116 puts the receipt up before the sign-out, deliberately — a 30-second logout must not
 * replace stage 3 while somebody is reading it. That leaves a window: the row is erased, and the
 * process can die before Close with the deleted account's session still on the phone. The next
 * launch then restored a session for a row that does not exist, with the app behaving as if
 * nothing had happened.
 *
 * `SessionManager.onAccountDeleted()` closes it by wiping the device immediately and leaving a
 * marker; this is the decision the next launch makes about that marker. Pure, because the case
 * that matters is a process death between two lines — and because everything around it (a
 * `SupabaseClient`, a DataStore file, a restored session) is what a JVM test cannot own.
 */
class PendingDeleteSignOutTest {

    @Test
    fun `no marker means there is nothing to finish`() {
        assertEquals(PendingDeleteAction.Nothing, pendingDeleteActionFor(null, hasSession = true))
        assertEquals(PendingDeleteAction.Nothing, pendingDeleteActionFor("", hasSession = true))
        assertEquals(PendingDeleteAction.Nothing, pendingDeleteActionFor(null, hasSession = false))
    }

    @Test
    fun `a marker over a live session finishes the sign-out`() {
        // The finding, exactly: the process died between the server delete and Close, and this
        // launch has restored a session belonging to an account that no longer exists.
        assertEquals(
            PendingDeleteAction.SignOut,
            pendingDeleteActionFor(SessionManager.DELETE_SIGN_OUT_PENDING, hasSession = true),
        )
    }

    @Test
    fun `a marker with no session leaves only the note to tidy`() {
        // The session went with the process, or a previous launch already finished the job.
        // Signing out of nothing would be a network call about no account.
        assertEquals(
            PendingDeleteAction.ClearMarker,
            pendingDeleteActionFor(SessionManager.DELETE_SIGN_OUT_PENDING, hasSession = false),
        )
    }

    @Test
    fun `only the exact marker counts`() {
        // The key is written by one call site with one value. Anything else in it is a value
        // from some other build, and signing somebody out over it would be worse than ignoring
        // it — this is the one action that cannot be undone by trying again.
        listOf("TRUE", "1", "pending", " true").forEach { junk ->
            assertEquals(
                "'$junk' must not sign anyone out",
                PendingDeleteAction.Nothing,
                pendingDeleteActionFor(junk, hasSession = true),
            )
        }
    }
}
