package `in`.artistant.app.feature.gigs

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Artist-side gig list. The name column is the interesting bit: the artist must
 * see the CLIENT, and when the embed is nulled by RLS (pre-0080 rows) the label
 * has to degrade to "Client" — never to the venue, which is where the old "TBD"
 * artist-side bug came from.
 */
class ArtistGigsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rowsShowTheClientName() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(listOf(booking(clientFullName = "Asha Rao"))),
        )

        assertEquals("Asha Rao", model.state.value.items.single().clientName)
    }

    @Test
    fun missingClientName_degradesToClient_notToTheVenue() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(listOf(booking(clientFullName = null, venue = "TBD"))),
        )

        assertEquals("Client", model.state.value.items.single().clientName)
    }

    @Test
    fun blankClientName_isTreatedAsMissing() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(listOf(booking(clientFullName = "   "))),
        )

        assertEquals("Client", model.state.value.items.single().clientName)
    }

    @Test
    fun cancelledGigsAreHidden_pendingRequestsAreNot() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(
                listOf(
                    booking(id = "g1", status = BookingStatus.PendingConfirm),
                    booking(id = "g2", status = BookingStatus.Confirmed),
                    booking(id = "g3", status = BookingStatus.Cancelled),
                ),
            ),
        )

        assertEquals(listOf("g1", "g2"), model.state.value.items.map { it.booking.id })
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun signedOut_surfacesAnError() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(listOf(booking())).apply { signedIn = false },
        )

        assertTrue(model.state.value.items.isEmpty())
        assertNotNull(model.state.value.error)
    }

    // ── Overlapping refreshes ───────────────────────────────────────────────
    //
    // Two refreshes overlap here whenever the pull indicator is not up: the
    // `init` load plus a first pull, and repeated pulls or the empty state's
    // Retry after a failure. The screen is a projection of ONE list, so the
    // request that finishes LAST used to win even when it was the older one.

    /**
     * Holds every `listForArtist()` read open until the test resolves it, so two
     * overlapping refreshes can be finished in whichever order the test wants.
     * Same shape as the artist-home suite's gate — only the one read the gig
     * list blocks on needs to be controllable.
     */
    private class GatedBookingsRepository(
        private val delegate: BookingsRepository = FakeBookingsRepository(),
    ) : BookingsRepository by delegate {
        /** One entry per in-flight read, in call order. */
        val reads = mutableListOf<CompletableDeferred<List<Booking>>>()

        override suspend fun listForArtist(): List<Booking> {
            val gate = CompletableDeferred<List<Booking>>()
            reads += gate
            return gate.await()
        }
    }

    private val newerGigs = listOf(
        booking(id = "new-1", status = BookingStatus.Confirmed),
        booking(id = "new-2", status = BookingStatus.Confirmed),
    )
    private val staleGigs = listOf(booking(id = "stale-1", status = BookingStatus.Confirmed))

    @Test
    fun staleRefreshLandingLast_doesNotClobberTheNewerGigList() = runTest {
        val repo = GatedBookingsRepository()
        val model = ArtistGigsViewModel(repo) // init { refresh() } — read #1 blocks
        model.refresh() // read #2 blocks
        assertEquals(2, repo.reads.size)

        // Out of order on purpose: the NEWER read resolves first, the older one
        // lands after it — the gig accepted in between must not disappear.
        repo.reads[1].complete(newerGigs)
        advanceUntilIdle()
        repo.reads[0].complete(staleGigs)
        advanceUntilIdle()

        assertEquals(listOf("new-1", "new-2"), model.state.value.items.map { it.booking.id })
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun staleRefreshFailingLast_doesNotPaintTheErrorOverFreshGigs() = runTest {
        val repo = GatedBookingsRepository()
        val model = ArtistGigsViewModel(repo)
        model.refresh()

        repo.reads[1].complete(newerGigs)
        advanceUntilIdle()
        // The older read dies on a blip AFTER the newer one already succeeded.
        // Its failure is about a request nobody is waiting on any more.
        repo.reads[0].completeExceptionally(IOException("stale connection reset"))
        advanceUntilIdle()

        assertNull(model.state.value.error)
        assertEquals(listOf("new-1", "new-2"), model.state.value.items.map { it.booking.id })
    }
}
