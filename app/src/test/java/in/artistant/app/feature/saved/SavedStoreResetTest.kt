package `in`.artistant.app.feature.saved

import `in`.artistant.app.data.repository.FakeSavedArtistsRepository
import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What `reset()` promises the caller that is waiting on it.
 *
 * It used to post `Command.Reset` and return. `SessionManager.wipeLocalState()` then finished,
 * `signOut()` returned, and the root gate swapped to the auth screen while [SavedStore.ids] still
 * held the departing account's saved artists — visible to whoever signed in next for as long as
 * the consumer took to reach the message, and written back to disk by any read that landed in
 * between. A teardown that returns before it has torn anything down is not a teardown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedStoreResetTest {

    /** In-memory [KeyValueStore] — the same string in, the same string out. */
    private class FakeKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
        private val values = MutableStateFlow(initial)
        override fun getString(key: String): Flow<String?> = values.map { it[key] }
        override suspend fun setString(key: String, value: String) {
            values.value = values.value + (key to value)
        }

        fun raw(key: String): String? = values.value[key]
    }

    /** A read that does not answer until the test says so — for the in-flight cases. */
    private class GatedSavedRepository(
        private val gate: CompletableDeferred<Unit>,
    ) : SavedArtistsRepository {
        val ids = linkedSetOf<String>()
        override suspend fun add(artistId: String) { ids.add(artistId.lowercase()) }
        override suspend fun remove(artistId: String) { ids.remove(artistId.lowercase()) }
        override suspend fun list(): List<String> {
            gate.await()
            return ids.toList()
        }
    }

    /**
     * A store on the test's own scheduler, so `advanceUntilIdle()` drives its consumer.
     *
     * `+ Job()` rather than the test's own: the consumer is a `for (command in commands)` over a
     * channel nothing closes, so as a CHILD of the test job it would keep `runTest` waiting
     * forever. Detaching the job leaves the dispatcher — which is what the test actually needs.
     */
    private fun TestScope.store(
        repository: SavedArtistsRepository = FakeSavedArtistsRepository(),
        prefs: KeyValueStore = FakeKeyValueStore(),
    ) = SavedStore(repository, prefs, CoroutineScope(coroutineContext + Job()))

    @Test
    fun `reset returns with the set already empty`() = runTest {
        val repository = FakeSavedArtistsRepository()
        repository.add("11111111-1111-1111-1111-111111111111")
        repository.add("22222222-2222-2222-2222-222222222222")
        val s = store(repository)
        advanceUntilIdle()
        assertEquals(2, s.ids.value.size)

        s.reset()

        // Deliberately NO advanceUntilIdle between the call and the assertion: the point of the
        // acknowledgement is that the caller is not allowed to observe the old set at all.
        assertTrue("the departing account's hearts must be gone", s.ids.value.isEmpty())
    }

    @Test
    fun `reset also lands the empty set on disk`() = runTest {
        val prefs = FakeKeyValueStore()
        val repository = FakeSavedArtistsRepository()
        repository.add("33333333-3333-3333-3333-333333333333")
        val s = store(repository, prefs)
        advanceUntilIdle()

        s.reset()
        advanceUntilIdle()

        assertEquals("", prefs.raw(SavedStore.PREFS_KEY))
    }

    @Test
    fun `an answer issued BEFORE the reset cannot repopulate the set`() = runTest {
        // The session counter is bumped inside the same applied-and-acknowledged step, so a read
        // that was already in flight when the sign-out happened is dropped rather than written
        // into the next account's hearts.
        val gate = CompletableDeferred<Unit>()
        val repository = GatedSavedRepository(gate)
        repository.ids.add("44444444-4444-4444-4444-444444444444")
        val s = store(repository)
        advanceUntilIdle()
        assertTrue("the read has not answered yet", s.ids.value.isEmpty())

        s.reset()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue("an answer for the departed account must be dropped", s.ids.value.isEmpty())
    }

    @Test
    fun `a toggle after a reset belongs to the new session`() = runTest {
        val s = store()
        advanceUntilIdle()
        s.reset()

        s.toggle("55555555-5555-5555-5555-555555555555")
        advanceUntilIdle()

        assertEquals(setOf("55555555-5555-5555-5555-555555555555"), s.ids.value)
    }
}
