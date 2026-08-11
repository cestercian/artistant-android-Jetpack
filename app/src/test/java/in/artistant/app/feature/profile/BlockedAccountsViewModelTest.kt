package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.feature.messages.FakeBlockedUsersStore
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.CLIENT_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The screen that makes a block reversible.
 *
 * What matters here is not that a list renders — it's that the list tells the
 * truth about itself (loaded-and-empty vs couldn't-load), that unblocking goes
 * through the SHARED store so the inbox hears about it, and that a failed
 * unblock is reported rather than silently reverted.
 */
class BlockedAccountsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /** Serves a fixed thread list, or fails it. Nothing else is ever called. */
    private class StaticThreads(
        private val threads: List<Thread> = emptyList(),
        private val failList: Boolean = false,
    ) : MessagesRepository {
        var calls = 0
            private set

        override suspend fun listThreadsForUser(): List<Thread> {
            calls++
            if (failList) throw IllegalStateException("offline")
            return threads
        }

        override suspend fun listMessages(threadId: String, limit: Int): List<Message> = emptyList()
        override suspend fun send(threadId: String, body: String): Message = error("unused")
        override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String =
            error("unused")
        override suspend fun markThreadRead(threadId: String) = Unit
        override suspend fun markThreadReadReceipt(threadId: String) = Unit
        override suspend fun counterpartLastRead(threadId: String): Long? = null
        override suspend fun setMuted(threadId: String, muted: Boolean) = Unit
        override suspend fun subscribeMessages(
            threadId: String,
            onInsert: (Message) -> Unit,
        ): MessagesSubscription = MessagesSubscription {}
    }

    private fun vm(
        blocked: FakeBlockedUsersStore = FakeBlockedUsersStore(),
        messages: MessagesRepository = StaticThreads(
            listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientId = CLIENT_ID, clientName = "Meera Iyer")),
        ),
        artists: FakeArtistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
    ) = BlockedAccountsViewModel(
        blockedUsers = blocked,
        messagesRepository = messages,
        artistsRepository = artists,
    )

    @Test
    fun aLoadedEmptyListSaysNobodyIsBlocked() = runTest {
        val model = vm()

        assertEquals(BlockedAccountsStatus.Ready, model.state.value.status)
        assertTrue(model.state.value.rows.isEmpty())
    }

    @Test
    fun aFailedListReadIsUnavailableSoTheScreenCannotClaimTheListIsEmpty() = runTest {
        val model = vm(blocked = FakeBlockedUsersStore().apply { refreshSucceeds = false })

        assertEquals(BlockedAccountsStatus.Unavailable, model.state.value.status)
        assertTrue(model.state.value.rows.isEmpty())
    }

    @Test
    fun aFailedReadOverAMirroredSetShowsTheRowsMarkedStale() = runTest {
        // The store keeps its last known set on a failed fetch (that's the
        // 0087 posture). Those rows are still actionable — unblocking offline
        // is exactly what someone stuck with an accidental block wants.
        val store = FakeBlockedUsersStore(setOf(ARTIST_ID.lowercase())).apply {
            refreshSucceeds = false
        }

        val model = vm(blocked = store)

        assertEquals(BlockedAccountsStatus.Stale, model.state.value.status)
        assertEquals(1, model.state.value.rows.size)
    }

    @Test
    fun aRetryAfterTheServerComesBackFlipsUnavailableToReady() = runTest {
        val store = FakeBlockedUsersStore().apply { refreshSucceeds = false }
        val model = vm(blocked = store)
        assertEquals(BlockedAccountsStatus.Unavailable, model.state.value.status)

        store.refreshSucceeds = true
        model.refresh()

        assertEquals(BlockedAccountsStatus.Ready, model.state.value.status)
    }

    @Test
    fun blockedPeopleAreNamedFromTheConversationTheyWereBlockedIn() = runTest {
        val model = vm(blocked = FakeBlockedUsersStore(setOf(ARTIST_ID.lowercase())))

        assertEquals("Nova Beats", model.state.value.rows.single().displayName)
        assertFalse(model.state.value.hasUnnamedRows)
    }

    @Test
    fun aBlockWeCannotPutANameToIsShownAsUnnamedRatherThanHidden() = runTest {
        // A block whose thread we can't read is still a block, and hiding the
        // row would recreate the dead end this screen exists to close.
        val model = vm(
            blocked = FakeBlockedUsersStore(setOf("99999999-9999-9999-9999-999999999999")),
            messages = StaticThreads(failList = true),
        )

        assertEquals(1, model.state.value.rows.size)
        assertNull(model.state.value.rows.single().displayName)
        assertTrue(model.state.value.hasUnnamedRows)
    }

    @Test
    fun unblockingGoesThroughTheSharedStoreSoEveryOtherSurfaceHearsIt() = runTest {
        val store = FakeBlockedUsersStore(setOf(ARTIST_ID.lowercase()))
        val model = vm(blocked = store)

        model.unblock(ARTIST_ID)

        assertTrue(store.blocked.value.isEmpty())
        assertTrue(model.state.value.rows.isEmpty())
    }

    @Test
    fun aFailedUnblockRestoresTheRowAndSaysWhy() = runTest {
        // The store rolls the optimistic removal back on a failed write, so the
        // row reappears. Unexplained, that reads as a bug in the button.
        val store = FakeBlockedUsersStore(setOf(ARTIST_ID.lowercase())).apply { failWrites = true }
        val model = vm(blocked = store)

        model.unblock(ARTIST_ID)

        assertEquals(1, model.state.value.rows.size)
        assertNotNull(model.state.value.actionError)
    }

    @Test
    fun unblockingSomebodyAlreadyUnblockedIsANoOpNotAReBlock() = runTest {
        // toggle() FLIPS. A second tap landing on a stale frame would otherwise
        // block the person the user had just released — the exact bug this
        // screen exists to fix, reintroduced by its own control.
        val store = FakeBlockedUsersStore(setOf(ARTIST_ID.lowercase()))
        val model = vm(blocked = store)

        model.unblock(ARTIST_ID)
        model.unblock(ARTIST_ID)

        assertTrue(store.blocked.value.isEmpty())
    }

    @Test
    fun aBlockMadeElsewhereLandsOnTheListWithoutAReload() = runTest {
        // Blocking happens in a chat. If this screen is on the back stack it
        // has to hear about it from the store rather than refetching.
        val store = FakeBlockedUsersStore()
        val messages = StaticThreads(
            listOf(Thread(id = "t-1", artistId = ARTIST_ID, clientId = CLIENT_ID)),
        )
        val model = vm(blocked = store, messages = messages)
        val loadsBefore = messages.calls

        store.toggle(ARTIST_ID)

        assertEquals(1, model.state.value.rows.size)
        assertEquals("no refetch — the store is the shared source", loadsBefore, messages.calls)
    }
}
