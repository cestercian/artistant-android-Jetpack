package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeBlockRepository
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.CLIENT_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blocked-user set (migration 0087).
 *
 * The properties under test are the ones that decide whether a block is safe to
 * rely on: it must never quietly UN-block someone (a set cleared by a network
 * blip would put a conversation the person chose to hide back in their inbox),
 * it must never claim a block that didn't persist, and one person's list must
 * not follow the device to the next person who signs in on it.
 */
class BlockedUsersStoreTest {

    /** In-memory [BlockedUsersMirror] — the disk slot, without a Context. */
    private class FakeMirror(var value: String? = null) : BlockedUsersMirror {
        var writes = 0
            private set

        override suspend fun read(): String? = value
        override suspend fun write(value: String) {
            this.value = value
            writes++
        }
    }

    private fun store(
        repository: FakeBlockRepository = FakeBlockRepository(),
        mirror: FakeMirror = FakeMirror(),
        viewerId: String? = CLIENT_ID,
    ) = ServerBlockedUsersStore(repository, mirror, ViewerIdentity { viewerId })

    @Test
    fun refreshAdoptsTheServerSet() = runTest {
        val store = store(repository = FakeBlockRepository(setOf(ARTIST_ID)))

        store.refresh()

        assertEquals(setOf(ARTIST_ID.lowercase()), store.blocked.value)
    }

    @Test
    fun aFailedRefreshKeepsTheSetRatherThanEmptyingIt() = runTest {
        // The whole point: signed out, offline, or a project the migration
        // hasn't been applied to all fail this fetch, and treating any of them
        // as "nobody is blocked" would un-hide every blocked conversation.
        val mirror = FakeMirror("${CLIENT_ID.lowercase()}\n${ARTIST_ID.lowercase()}")
        val repository = FakeBlockRepository().apply { signedIn = false }
        val store = store(repository = repository, mirror = mirror)

        store.refresh()

        assertEquals(setOf(ARTIST_ID.lowercase()), store.blocked.value)
    }

    @Test
    fun aMirrorOwnedBySomebodyElseIsIgnored() = runTest {
        // Someone else used this device and their block list is still on disk.
        // Adopting it would hide the current user's own conversations with no
        // surface anywhere that could explain why.
        val mirror = FakeMirror("99999999-9999-9999-9999-999999999999\n${ARTIST_ID.lowercase()}")
        val store = store(
            repository = FakeBlockRepository().apply { signedIn = false },
            mirror = mirror,
        )

        store.refresh()

        assertTrue(store.blocked.value.isEmpty())
    }

    @Test
    fun refreshReportsWhetherTheServerCopyWasActuallyRead() = runTest {
        // The inbox can ignore this — filtering with a stale set is safe. A
        // screen that RENDERS the set cannot: an empty set that failed to load
        // and an empty set that loaded look identical and mean opposite things.
        assertTrue(store(repository = FakeBlockRepository(setOf(ARTIST_ID))).refresh())

        val offline = store(repository = FakeBlockRepository().apply { signedIn = false })
        assertFalse(offline.refresh())
    }

    @Test
    fun toggleBlocksOptimisticallyAndPersists() = runTest {
        val repository = FakeBlockRepository()
        val mirror = FakeMirror()
        val store = store(repository = repository, mirror = mirror)

        val ok = store.toggle(ARTIST_ID)

        assertTrue(ok)
        assertEquals(setOf(ARTIST_ID.lowercase()), store.blocked.value)
        assertEquals(listOf(ARTIST_ID.lowercase()), repository.blockCalls)
        // Stamped with the owner so it can't be adopted by the next signer.
        assertEquals("${CLIENT_ID.lowercase()}\n${ARTIST_ID.lowercase()}", mirror.value)
    }

    @Test
    fun aFailedBlockRollsBackRatherThanClaimingItWorked() = runTest {
        val repository = FakeBlockRepository().apply { failWrites = true }
        val store = store(repository = repository)

        val ok = store.toggle(ARTIST_ID)

        assertFalse(ok)
        assertTrue("a block that didn't persist must not look like one", store.blocked.value.isEmpty())
    }

    @Test
    fun aFailedUnblockLeavesThePersonBlocked() = runTest {
        // The safer direction of the same rule: if the unblock write is lost,
        // the person stays hidden rather than silently reappearing.
        val repository = FakeBlockRepository(setOf(ARTIST_ID))
        val store = store(repository = repository)
        store.refresh()
        repository.failWrites = true

        val ok = store.toggle(ARTIST_ID)

        assertFalse(ok)
        assertEquals(setOf(ARTIST_ID.lowercase()), store.blocked.value)
    }
}

/**
 * The pure seat rules blocking depends on ([ThreadCounterpart]).
 *
 * Both are about identifying the OTHER person: block the wrong id and you block
 * yourself, which 0087's `blocked_users_no_self` check rejects outright.
 */
class ThreadCounterpartBlockTest {

    private val thread = Thread(id = "t-1", artistId = ARTIST_ID, clientId = CLIENT_ID)

    @Test
    fun theClientSeatsCounterpartIsTheArtist() {
        assertEquals(ARTIST_ID.lowercase(), ThreadCounterpart.counterpartId(thread, CLIENT_ID))
    }

    @Test
    fun theArtistSeatsCounterpartIsTheClient() {
        assertEquals(CLIENT_ID.lowercase(), ThreadCounterpart.counterpartId(thread, ARTIST_ID))
    }

    @Test
    fun anArtistViewingAThreadWithNoClientIdHasNoOneToBlock() {
        // A locally-minted row that has never been to the server carries no
        // client_id. Returning the artist id here would have the artist block
        // themselves, so the seat resolves to null and the UI hides the action.
        val local = Thread(id = "t-1", artistId = ARTIST_ID)

        assertEquals(null, ThreadCounterpart.counterpartId(local, ARTIST_ID))
    }

    @Test
    fun eitherBlockedSeatHidesTheThread() {
        assertTrue(ThreadCounterpart.isBlocked(thread, setOf(ARTIST_ID.lowercase())))
        assertTrue(ThreadCounterpart.isBlocked(thread, setOf(CLIENT_ID.lowercase())))
        assertFalse(ThreadCounterpart.isBlocked(thread, emptySet()))
    }
}
