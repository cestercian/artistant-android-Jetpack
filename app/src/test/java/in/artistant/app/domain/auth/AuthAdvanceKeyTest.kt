package `in`.artistant.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The signInGeneration behaviour, tested at the pure key it feeds (the iOS `authAdvanceKey`
 * regression). RootViewModel re-routes iff this key changes.
 */
class AuthAdvanceKeyTest {

    @Test
    fun `same uuid, bumped generation, changes the key`() {
        // The whole point: a returning user re-authenticating into the SAME uuid must still
        // advance — identity alone wouldn't change, but the generation does.
        val before = authAdvanceKey("abc", generation = 0)
        val after = authAdvanceKey("abc", generation = 1)
        assertNotEquals(before, after)
    }

    @Test
    fun `identity change alone changes the key`() {
        assertNotEquals(authAdvanceKey("a", 0), authAdvanceKey("b", 0))
        assertNotEquals(authAdvanceKey(null, 0), authAdvanceKey("a", 0))
    }

    @Test
    fun `same uuid and generation is stable (no spurious re-route)`() {
        // A background refresh keeps uuid + generation identical → key unchanged → no re-route.
        assertEquals(authAdvanceKey("abc", 3), authAdvanceKey("abc", 3))
    }

    @Test
    fun `null user id renders as nil`() {
        assertEquals("nil:0", authAdvanceKey(null, 0))
    }
}
