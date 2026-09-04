package `in`.artistant.app.feature.signup

import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen 62's one switch, round-tripped through the store it is kept in.
 *
 * The point of the test is the seam, not the DataStore: `feature/messages` will read
 * [PrivacyPreferences.readReceipts] before calling `mark_thread_read`, and until it does this is
 * the only thing asserting that what the switch wrote is what that read gets back — including
 * the unset case, where "absent" has to mean ON rather than Kotlin's `"null".toBoolean()` false.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyPreferencesTest {

    /** An in-memory [KeyValueStore]: the same string in, the same string out. */
    private class FakeKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
        private val values = MutableStateFlow(initial)
        override fun getString(key: String): Flow<String?> = values.map { it[key] }
        override suspend fun setString(key: String, value: String) {
            values.value = values.value + (key to value)
        }

        fun raw(key: String): String? = values.value[key]
    }

    @Test
    fun `read receipts default to on, because that is what the app already does`() = runTest {
        val prefs = PrivacyPreferences(FakeKeyValueStore())
        assertTrue(prefs.readReceipts.first())
    }

    @Test
    fun `turning read receipts off round-trips, and back on again`() = runTest {
        val store = FakeKeyValueStore()
        val prefs = PrivacyPreferences(store)

        prefs.setReadReceipts(false)
        assertFalse(prefs.readReceipts.first())

        prefs.setReadReceipts(true)
        assertTrue(prefs.readReceipts.first())
    }

    @Test
    fun `it is stored under the key the messaging side reads`() = runTest {
        // The switch and its consumer live in two packages owned by two agents this wave; the
        // key is the contract between them, so it is asserted rather than assumed.
        val store = FakeKeyValueStore()
        PrivacyPreferences(store).setReadReceipts(false)

        assertEquals("privacy.read_receipts", PrivacyPreferences.KEY_READ_RECEIPTS)
        assertEquals("false", store.raw(PrivacyPreferences.KEY_READ_RECEIPTS))
    }

    @Test
    fun `a value an older build left behind never reads as off by accident`() = runTest {
        // Anything that is not the literal "false" is on — a corrupted entry must not silently
        // stop the app doing something it has always done.
        val store = FakeKeyValueStore(mapOf(PrivacyPreferences.KEY_READ_RECEIPTS to "yes"))
        assertTrue(PrivacyPreferences(store).readReceipts.first())
    }
}
