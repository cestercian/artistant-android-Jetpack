package `in`.artistant.app.feature.profile

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * "Switch to artist mode" — one server write, one device write, and which of them is allowed to
 * decide the outcome.
 *
 * The review found the two treated as equals inside a single `runCatching`, so a DataStore edit
 * that threw reported "Couldn't switch to artist mode" and skipped the re-route while
 * `public.users.role` already said Artist. The user was then standing in the client scaffold as
 * an artist, in front of a button whose only offer was to make the same write again.
 */
class RoleSwitchTest {

    @Test
    fun `both writes landing is a switch`() = runTest {
        var persisted = false
        val outcome = switchRoleOnServerThenDevice(
            updateServer = {},
            persistLocally = { persisted = true },
        )
        assertTrue(outcome.switched)
        assertNull(outcome.failure)
        assertTrue(persisted)
    }

    @Test
    fun `a server write that fails is the only thing that fails the switch`() = runTest {
        var persisted = false
        val outcome = switchRoleOnServerThenDevice(
            updateServer = { throw IllegalStateException("row is locked") },
            persistLocally = { persisted = true },
        )
        assertFalse(outcome.switched)
        assertEquals("row is locked", outcome.failure)
        // Nothing may be written locally for a role the account does not have.
        assertFalse(persisted)
    }

    @Test
    fun `the split case — server wrote, device did not — still switches and still re-routes`() =
        runTest {
            // The defect, exactly. `users.role` is the fact; a DataStore IOException cannot
            // un-say it, and reporting failure over it strands the user mid-switch.
            val outcome = switchRoleOnServerThenDevice(
                updateServer = {},
                persistLocally = { throw IOException("preferences file is read-only") },
            )
            assertTrue("the account IS an artist now", outcome.switched)
            assertNull("nothing to apologise for — the switch happened", outcome.failure)
        }

    @Test
    fun `a server failure with no message still says something`() = runTest {
        val outcome = switchRoleOnServerThenDevice(
            updateServer = { throw IllegalStateException() },
            persistLocally = {},
        )
        assertEquals("Couldn't switch to artist mode.", outcome.failure)
    }

    @Test
    fun `the local write is never attempted before the server one succeeds`() = runTest {
        val order = mutableListOf<String>()
        switchRoleOnServerThenDevice(
            updateServer = { order += "server" },
            persistLocally = { order += "device" },
        )
        assertEquals(listOf("server", "device"), order)
    }
}
