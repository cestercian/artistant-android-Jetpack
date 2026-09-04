package `in`.artistant.app.feature.system

import `in`.artistant.app.feature.messages.ViewerIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DataStoreActivityLog] against an in-memory store: who owns a row, and what
 * happens when two pushes land at once.
 *
 * The pure half of the log is [ActivityLogLogicTest]'s. This suite is about the
 * two things only the implementation can get wrong — attribution and ordering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityLogStoreTest {

    private val me = "11111111-1111-1111-1111-111111111111"
    private val someoneElse = "22222222-2222-2222-2222-222222222222"

    private fun entry(id: String, userId: String? = null) = ActivityEntry(
        id = id,
        userId = userId,
        event = "booking_confirmed_client",
        title = "Saanjh accepted your request",
        body = "Fri 25 Oct is held",
        receivedAtMs = 0L,
    )

    private fun log(store: SlowKeyValueStore, viewer: String?) =
        DataStoreActivityLog(store, ViewerIdentity { viewer })

    // ── who the row belongs to ───────────────────────────────────────────────

    @Test
    fun `a push that arrives with nobody signed in is dropped`() = runTest {
        // The FCM token outlives the session, so this is a real arrival — and an
        // ownerless row is one the NEXT account would be shown.
        val store = SlowKeyValueStore()
        log(store, viewer = null).record(entry("orphan"))
        assertTrue(log(store, viewer = me).entries.first().isEmpty())
    }

    @Test
    fun `a row is stamped with the account that was signed in`() = runTest {
        val store = SlowKeyValueStore()
        log(store, viewer = me).record(entry("mine"))
        assertEquals(me, log(store, viewer = me).entries.first().single().userId)
    }

    @Test
    fun `the previous account's rows are never shown to the next one`() = runTest {
        val store = SlowKeyValueStore()
        log(store, viewer = someoneElse).record(entry("theirs"))
        log(store, viewer = me).record(entry("mine"))
        assertEquals(listOf("mine"), log(store, viewer = me).entries.first().map { it.id })
    }

    @Test
    fun `an ownerless row written by an older build is shown to nobody`() = runTest {
        // The one shape this rule has to survive: a blob from before `userId`
        // existed. It decodes, and then it stays invisible until the cap trims it.
        val store = SlowKeyValueStore(
            mapOf(
                "system.activityLog" to
                    """[{"id":"legacy","title":"t","body":"b","receivedAtMs":0}]""",
            ),
        )
        assertTrue(log(store, viewer = me).entries.first().isEmpty())
    }

    @Test
    fun `signed out, the log shows nothing at all`() = runTest {
        val store = SlowKeyValueStore()
        log(store, viewer = me).record(entry("mine"))
        assertTrue(log(store, viewer = null).entries.first().isEmpty())
    }

    @Test
    fun `mark all read touches only this account's rows`() = runTest {
        val store = SlowKeyValueStore()
        log(store, viewer = someoneElse).record(entry("theirs"))
        log(store, viewer = me).record(entry("mine"))

        log(store, viewer = me).markAllRead()

        assertTrue(log(store, viewer = me).entries.first().all { it.read })
        assertTrue(log(store, viewer = someoneElse).entries.first().none { it.read })
    }

    // ── two writers, one string ──────────────────────────────────────────────

    @Test
    fun `two pushes arriving together both survive`() = runTest {
        // The regression this pins: read-modify-write over ONE DataStore string
        // with no lock. Both records read the same empty list and the second
        // write discards the first, so a notification the user was actually
        // shown is missing from the screen that exists to keep it.
        val store = SlowKeyValueStore()
        val log = log(store, viewer = me)

        val first = launch { log.record(entry("a")) }
        val second = launch { log.record(entry("b")) }
        first.join()
        second.join()

        assertEquals(setOf("a", "b"), log.entries.first().map { it.id }.toSet())
    }

    @Test
    fun `a push landing during mark-all-read is not swallowed by it`() = runTest {
        val store = SlowKeyValueStore()
        val log = log(store, viewer = me)
        log.record(entry("old"))

        val marking = launch { log.markAllRead() }
        val arriving = launch { log.record(entry("new")) }
        marking.join()
        arriving.join()

        assertEquals(setOf("old", "new"), log.entries.first().map { it.id }.toSet())
    }
}
