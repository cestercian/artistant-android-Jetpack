package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.FakeRequestsRepository
import `in`.artistant.app.data.repository.FakeScoreRepository
import `in`.artistant.app.data.repository.FakeUsersRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.feature.messages.ViewerIdentity
import `in`.artistant.app.feature.paywall.EntitlementStore
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The dashboard's *stateful* behaviour — the parts a pure-function test in
 * [ArtistHomeLogicTest] cannot reach: what happens when two refreshes overlap.
 *
 * Refreshes overlap easily on this screen. Pull-to-refresh, a resume and the
 * failure banner's Retry can all fire within a second of each other, and the
 * whole screen is a projection of ONE bookings list — so whichever refresh
 * writes last decides the counts, the request rail, the upcoming gigs and which
 * days the availability strip shades as booked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistHomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // ── Fixtures ────────────────────────────────────────────────────────────

    /** An artist who has finished the wizard — the "nothing is missing" baseline. */
    private val completeProfile = SelfProfile(
        role = AppRole.Artist,
        fullName = "Nova Beats",
        city = "Bangalore",
        handle = "novabeats",
        artistSetupComplete = true,
    )

    /**
     * Holds every `listForArtist()` read open until the test resolves it, so two
     * overlapping refreshes can be finished in whichever order the test wants.
     * Delegates the rest of the interface to the shared fake — only the one read
     * the dashboard blocks on needs to be controllable.
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

    private fun vm(
        bookings: BookingsRepository,
        artists: ArtistsRepository = FakeArtistsRepository(listOf(artist(id = ARTIST_ID))),
        users: UsersRepository = FakeUsersRepository(selfProfile = completeProfile),
    ) = ArtistHomeViewModel(
        bookingsRepository = bookings,
        requestsRepository = FakeRequestsRepository(),
        usersRepository = users,
        artistsRepository = artists,
        scoreRepository = FakeScoreRepository(),
        entitlements = EntitlementStore(),
        viewer = ViewerIdentity { ARTIST_ID },
    )

    private val newerBookings = listOf(
        booking(id = "new-1", status = BookingStatus.PendingConfirm),
        booking(id = "new-2", status = BookingStatus.PendingConfirm),
    )
    private val staleBookings = listOf(booking(id = "stale-1", status = BookingStatus.PendingConfirm))

    // ── Overlapping refreshes ───────────────────────────────────────────────

    @Test
    fun staleRefreshLandingLast_doesNotClobberTheNewerState() = runTest {
        val repo = GatedBookingsRepository()
        val model = vm(repo) // init { refresh() } — read #1 opens and blocks
        model.refresh() // read #2 opens and blocks
        assertEquals(2, repo.reads.size)

        // Finish them out of order: the NEWER request resolves first, the older
        // one lands after it.
        repo.reads[1].complete(newerBookings)
        advanceUntilIdle()
        repo.reads[0].complete(staleBookings)
        advanceUntilIdle()

        assertEquals(
            listOf("new-1", "new-2"),
            model.state.value.pendingRequests.map { it.id },
        )
    }

    @Test
    fun staleRefreshLandingLast_doesNotLeaveTheRangePickerProjectingStaleBookings() = runTest {
        val repo = GatedBookingsRepository()
        val model = vm(repo)
        model.refresh()

        repo.reads[1].complete(newerBookings)
        advanceUntilIdle()
        repo.reads[0].complete(staleBookings)
        advanceUntilIdle()

        // The range switch re-derives from the list the ViewModel kept, so this
        // catches a stale write to that field even if the state write was guarded.
        model.setRange(EarningsRange.SevenDays)

        assertEquals(
            listOf("new-1", "new-2"),
            model.state.value.pendingRequests.map { it.id },
        )
    }

    @Test
    fun staleRefreshFailingLast_doesNotPaintTheFailureBannerOverFreshData() = runTest {
        val repo = GatedBookingsRepository()
        val model = vm(repo)
        model.refresh()

        repo.reads[1].complete(newerBookings)
        advanceUntilIdle()
        // The older refresh dies on a blip AFTER the newer one already succeeded.
        // Its failure is about a request nobody is waiting on any more.
        repo.reads[0].completeExceptionally(IOException("stale connection reset"))
        advanceUntilIdle()

        assertNull(model.state.value.error)
        assertEquals(
            listOf("new-1", "new-2"),
            model.state.value.pendingRequests.map { it.id },
        )
    }
}
