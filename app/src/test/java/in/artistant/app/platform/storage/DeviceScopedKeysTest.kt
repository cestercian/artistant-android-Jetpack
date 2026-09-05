package `in`.artistant.app.platform.storage

import `in`.artistant.app.feature.profile.DataExportStore
import `in`.artistant.app.platform.preferences.AccessibilityPreferences
import `in`.artistant.app.platform.preferences.NotificationToggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a sign-out is allowed to erase.
 *
 * `wipeAll()` used to be `clear()`, which took the accessibility and notification switches with
 * it. Neither is the account's: somebody who turned on "always show labels" because they cannot
 * read an unlabelled tab bar, or who silenced the two marketing categories, did not undo that by
 * signing out — and the next sign-in handed them a tab bar with no labels and marketing pushes
 * back at their defaults.
 *
 * The predicate is what ships; `wipeAll` needs an Android `Context` and a real DataStore file,
 * so this is where the rule is assertable. It walks the REAL key constants rather than string
 * literals, so a renamed key fails here instead of silently falling out of the exemption.
 */
class DeviceScopedKeysTest {

    @Test
    fun `every accessibility switch survives a sign-out`() {
        assertTrue(isDeviceScopedKey(AccessibilityPreferences.KEY_ALWAYS_SHOW_LABELS))
        assertTrue(isDeviceScopedKey(AccessibilityPreferences.KEY_AUTOPLAY))
    }

    @Test
    fun `every notification switch survives a sign-out`() {
        // All eight, off the enum, so an added toggle is covered without a new assertion.
        NotificationToggle.entries.forEach { toggle ->
            assertTrue("${toggle.key} must survive sign-out", isDeviceScopedKey(toggle.key))
        }
    }

    @Test
    fun `account state is still wiped`() {
        // The exemption is narrow on purpose. Everything that describes the ACCOUNT — the role
        // it routes on, the consents it collected, the DPDP export it was handed — is what
        // DPDP §11 requires the sign-out to take.
        assertFalse(isDeviceScopedKey("role"))
        assertFalse(isDeviceScopedKey("terms.accepted"))
        assertFalse(isDeviceScopedKey("community.agreed"))
        assertFalse(isDeviceScopedKey(DataExportStore.KEY_REQUESTED_AT))
        assertFalse(isDeviceScopedKey("calendar_state"))
        assertFalse(isDeviceScopedKey(""))
    }

    @Test
    fun `the prefix is a prefix, not a substring`() {
        // A key that merely MENTIONS one of the namespaces is account state and goes.
        assertFalse(isDeviceScopedKey("search.notify.recents"))
        assertFalse(isDeviceScopedKey("notifyLater"))
        assertTrue(isDeviceScopedKey("notify.anything_new"))
        assertTrue(isDeviceScopedKey("a11y.anything_new"))
    }
}
