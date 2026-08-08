package `in`.artistant.app.harness

import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the launch-argument parser.
 *
 * Lives in `src/testDebug/` rather than `src/test/` on purpose: `HarnessFlags` is a debug-only
 * class, so a test referencing it from the shared `test` source set would fail to COMPILE for
 * any release-variant unit-test task. Scoping the test to the debug variant keeps the release
 * side genuinely free of every harness symbol, test code included.
 */
class HarnessFlagsTest {

    // --- The off switch. The single most important property: no extra == no harness. ---

    @Test
    fun `null extra yields no flags`() {
        val flags = HarnessFlags.parse(null)
        assertFalse(flags.active)
        assertEquals(HarnessFlags.NONE, flags)
    }

    @Test
    fun `blank extra yields no flags`() {
        assertFalse(HarnessFlags.parse("   ").active)
        assertFalse(HarnessFlags.parse("").active)
    }

    @Test
    fun `entirely unrecognised tokens stay inert rather than half-activating`() {
        // An operator typo must not boot a half-configured harness, and must not crash.
        val flags = HarnessFlags.parse("nonsense,--whatever")
        assertFalse(flags.active)
        assertNull(flags.skipSignupAs)
    }

    // --- Role selection ---

    @Test
    fun `skip signup as artist selects the artist role`() {
        val flags = HarnessFlags.parse("skip-signup-as-artist")
        assertTrue(flags.active)
        assertEquals(AppRole.Artist, flags.skipSignupAs)
    }

    @Test
    fun `skip signup as client selects the client role`() {
        assertEquals(AppRole.Client, HarnessFlags.parse("skip-signup-as-client").skipSignupAs)
    }

    @Test
    fun `client wins when both roles are passed`() {
        // Mirrors the iOS harness precedence, so a command copied between the two behaves
        // identically instead of depending on token order.
        val both = "skip-signup-as-artist,skip-signup-as-client"
        assertEquals(AppRole.Client, HarnessFlags.parse(both).skipSignupAs)
        assertEquals(AppRole.Client, HarnessFlags.parse(both.split(",").reversed().joinToString(",")).skipSignupAs)
    }

    @Test
    fun `a wizard landing implies the artist role`() {
        val flags = HarnessFlags.parse("land-in-wizard-at-identity")
        assertEquals(AppRole.Artist, flags.skipSignupAs)
        assertEquals("identity", flags.landInWizardAt)
    }

    @Test
    fun `an explicit role still wins over the wizard implication`() {
        val flags = HarnessFlags.parse("land-in-wizard-at-bio,skip-signup-as-client")
        assertEquals(AppRole.Client, flags.skipSignupAs)
        assertEquals("bio", flags.landInWizardAt)
    }

    @Test
    fun `a bare wizard prefix with no step is not a wizard landing`() {
        assertNull(HarnessFlags.parse("land-in-wizard-at-").landInWizardAt)
    }

    // --- Combinations + separators ---

    @Test
    fun `comma separated tokens all register`() {
        val flags = HarnessFlags.parse("skip-signup-as-artist,seed-fixture-data,seed-pending-request")
        assertEquals(AppRole.Artist, flags.skipSignupAs)
        assertTrue(flags.seedFixtureData)
        assertTrue(flags.seedPendingRequest)
    }

    @Test
    fun `whitespace separated tokens all register`() {
        // Shell quoting sometimes turns the comma form into a space-separated one.
        val flags = HarnessFlags.parse("skip-signup-as-client seed-fixture-data")
        assertEquals(AppRole.Client, flags.skipSignupAs)
        assertTrue(flags.seedFixtureData)
    }

    @Test
    fun `surrounding whitespace and empty segments are tolerated`() {
        val flags = HarnessFlags.parse(" skip-signup-as-artist , , seed-fixture-data ")
        assertEquals(AppRole.Artist, flags.skipSignupAs)
        assertTrue(flags.seedFixtureData)
    }

    // --- iOS spelling compatibility ---

    @Test
    fun `iOS style dash-prefixed tokens are accepted verbatim`() {
        val flags = HarnessFlags.parse("-uitest-skip-signup-as-artist,-uitest-seed-fixture-data,-uitest-reset")
        assertEquals(AppRole.Artist, flags.skipSignupAs)
        assertTrue(flags.seedFixtureData)
        assertTrue(flags.reset)
    }

    @Test
    fun `the bare uitest marker activates the harness without choosing a role`() {
        val flags = HarnessFlags.parse("-uitest")
        assertTrue(flags.active)
        assertNull(flags.skipSignupAs)
    }

    @Test
    fun `token casing is ignored`() {
        assertEquals(AppRole.Artist, HarnessFlags.parse("SKIP-SIGNUP-AS-ARTIST").skipSignupAs)
    }

    // --- HarnessState wiring ---

    @Test
    fun `useFakes is off until flags are installed`() {
        HarnessState.install(HarnessFlags.NONE)
        assertFalse(HarnessState.active)
        assertFalse(HarnessState.useFakes)
    }

    @Test
    fun `useFakes turns on for a role boot or an explicit seed request`() {
        HarnessState.install(HarnessFlags.parse("skip-signup-as-artist"))
        assertTrue(HarnessState.useFakes)

        HarnessState.install(HarnessFlags.parse("-uitest,seed-fixture-data"))
        assertTrue(HarnessState.useFakes)

        // Reset the shared holder so ordering between tests can't leak state.
        HarnessState.install(HarnessFlags.NONE)
    }
}
