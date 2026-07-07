package `in`.artistant.app.state

import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.FakeRequestsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Optimistic accept/decline with server-signalled rollback (iOS RequestStore audit P2). */
class RequestStoreTest {

    private fun open(id: String) = StoredRequest(
        raw = GigRequest(id, "Priya", "hi", "Jun 1", 30_000, "Custom", "2h ago"),
        status = GigRequestStatus.Open,
    )

    @Test
    fun `accept optimistically flips to Accepted and the write commits`() = runTest {
        val repo = FakeRequestsRepository(seed = listOf(open("r1")))
        val store = RequestStore(repo)
        store.setRequests(listOf(open("r1")))

        store.accept("r1").join()

        assertEquals(GigRequestStatus.Accepted, store.requests.value.first().status)
        assertEquals(GigRequestStatus.Accepted, repo.requests.first().status)
    }

    @Test
    fun `accept rolls back when the server reports not-found or unauthorized`() = runTest {
        val repo = FakeRequestsRepository(seed = listOf(open("r1")), failNotFound = true)
        val store = RequestStore(repo)
        store.setRequests(listOf(open("r1")))

        store.accept("r1").join()

        // Optimistic flip reverted to the prior Open state after the server signal.
        assertEquals(GigRequestStatus.Open, store.requests.value.first().status)
    }

    @Test
    fun `counter sets the amount optimistically`() = runTest {
        val repo = FakeRequestsRepository(seed = listOf(open("r1")))
        val store = RequestStore(repo)
        store.setRequests(listOf(open("r1")))

        store.counter("r1", 42_000).join()

        val r = store.requests.value.first()
        assertEquals(GigRequestStatus.Countered, r.status)
        assertEquals(42_000, r.counterAmount)
    }

    @Test
    fun `openCount reflects only open requests`() {
        val store = RequestStore(FakeRequestsRepository())
        store.setRequests(listOf(open("r1"), open("r2")))
        assertEquals(2, store.openCount)
    }
}
