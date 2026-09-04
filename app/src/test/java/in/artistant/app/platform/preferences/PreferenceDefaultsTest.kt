package `in`.artistant.app.platform.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How an UNSET preference reads, and why the default has to be the caller's.
 *
 * `KeyValueStore` holds strings, and `String?.toBoolean()` answers false for null — which would
 * make every switch on design screen 124 read as OFF before anyone had touched it. Six of those
 * eight describe what the app already does and have to default ON; the two marketing ones have
 * to default OFF (the design's note, and a consent the signup flow never collected). One
 * coercion cannot serve both, so the default is passed in.
 */
class PreferenceDefaultsTest {

    @Test
    fun `an unset value takes the caller's default, either way`() {
        assertTrue(null.toBoolOrDefault(default = true))
        assertFalse(null.toBoolOrDefault(default = false))
    }

    @Test
    fun `a written value round-trips exactly`() {
        assertTrue(true.toString().toBoolOrDefault(default = false))
        assertFalse(false.toString().toBoolOrDefault(default = true))
    }

    @Test
    fun `an unparseable value falls back rather than silently reading as off`() {
        // A value written by an older build, or a corrupted entry. `toBoolean()` would answer
        // false for all of these and silently switch six notification categories off.
        listOf("TRUE", "1", "yes", "", "  ", "null").forEach { junk ->
            assertTrue("'$junk' should fall back to the default", junk.toBoolOrDefault(default = true))
            assertFalse("'$junk' should fall back to the default", junk.toBoolOrDefault(default = false))
        }
    }

    // ── Screen 124's defaults ─────────────────────────────────────────────────────────

    @Test
    fun `marketing is off by default and everything transactional is on`() {
        val defaults = NotificationSettings()
        // The design's whole note on this screen.
        assertFalse("new-acts is marketing", defaults.newActs)
        assertFalse("tips and offers is marketing", defaults.tipsAndOffers)

        assertTrue(defaults.quotesAndReplies)
        assertTrue(defaults.bookingUpdates)
        assertTrue(defaults.showDayReminder)
        assertTrue(defaults.loadInReminder)
        assertTrue(defaults.reviewReminders)
        assertTrue(defaults.quietHours)
    }

    @Test
    fun `every toggle reads and writes its own field`() {
        // The `get`/`with` pair is what lets the screen pass a toggle around instead of eight
        // lambdas; a copy-paste slip in either would silently wire two rows to one setting.
        NotificationToggle.entries.forEach { toggle ->
            val base = NotificationSettings()
            val flipped = base.with(toggle, !base[toggle])
            assertEquals("$toggle did not flip", !base[toggle], flipped[toggle])
            NotificationToggle.entries
                .filter { it != toggle }
                .forEach { other ->
                    assertEquals("$toggle also moved $other", base[other], flipped[other])
                }
        }
    }

    @Test
    fun `every toggle has its own storage key`() {
        val keys = NotificationToggle.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `accessibility preferences default to the design's drawn state`() {
        val defaults = AccessibilitySettings()
        // The design draws an unlabelled tab bar and autoplay off. Both are opt-INs.
        assertFalse(defaults.alwaysShowLabels)
        assertFalse(defaults.autoplayVideos)
    }
}
