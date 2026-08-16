package `in`.artistant.app.platform.calendar

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bookingId→eventId blob, which is the only handle the app has on the events
 * it wrote into the device calendar.
 *
 * The bug (audit p03): it was persisted through `AppPreferences.setString`, i.e.
 * into the MAIN store that `SessionManager.signOut()` clears with `wipeAll()`.
 * An artist who mirrored six gigs and then signed out lost the map while the six
 * events stayed in their calendar — `removeAllSyncedEvents()` had nothing left to
 * iterate, so neither disabling sync nor deleting the account could ever retract
 * them. It now lives in the purpose-built `calendarStore`, which sign-out keeps
 * and only delete-account (`wipeCalendar()`) clears.
 *
 * The DataStore round trip needs a Context, so what's pinned here is the blob
 * shape the one-time lift out of the old store depends on: whatever an earlier
 * build wrote must still decode, with its event ids intact.
 */
class CalendarSyncPersistenceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theEventMapSurvivesARoundTrip() {
        val state = CalendarSyncService.PersistedState(
            enabled = true,
            calendarId = 7L,
            map = mapOf("b1" to CalendarSyncService.SyncedEntry("42", "fp-1")),
            ownerUserId = "aaaaaaaa-0000-4000-8000-000000000001",
        )

        val restored = json.decodeFromString<CalendarSyncService.PersistedState>(
            json.encodeToString(state),
        )

        assertEquals(state, restored)
        assertEquals("42", restored.map.getValue("b1").eventId)
    }

    /**
     * The migration case: a blob written before `ownerUserId` existed, carrying a
     * key this build doesn't know. It has to decode rather than throw — a failed
     * decode falls back to an empty state, which is exactly the stranding the
     * store move is meant to prevent.
     */
    @Test
    fun aBlobFromAnEarlierBuildDecodesWithItsEventMapIntact() {
        val legacy = """
            {"enabled":true,"calendarId":7,
             "map":{"b1":{"eventId":"42","fingerprint":"fp-1"}},
             "retiredFlag":true}
        """.trimIndent()

        val restored = json.decodeFromString<CalendarSyncService.PersistedState>(legacy)

        assertEquals("42", restored.map.getValue("b1").eventId)
        assertTrue(restored.enabled)
        assertEquals(7L, restored.calendarId)
        assertNull("a pre-owner blob must not invent an owner", restored.ownerUserId)
    }

    @Test
    fun anEmptyBlobDecodesToTheDefaultState() {
        val restored = json.decodeFromString<CalendarSyncService.PersistedState>("{}")

        assertEquals(CalendarSyncService.PersistedState(), restored)
        assertTrue(restored.map.isEmpty())
    }
}
