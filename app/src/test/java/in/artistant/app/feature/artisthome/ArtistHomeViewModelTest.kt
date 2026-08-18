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
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.domain.score.ScoreTier
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        score: FakeScoreRepository = FakeScoreRepository(),
    ) = ArtistHomeViewModel(
        bookingsRepository = bookings,
        requestsRepository = FakeRequestsRepository(),
        usersRepository = users,
        artistsRepository = artists,
        scoreRepository = score,
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

    // ── A failed fetch is not a missing profile ─────────────────────────────
    //
    // The completeness banner is derived from two independent reads: the users
    // row says whether the wizard was finished, the artist row carries genre and
    // bio. When the artist read FAILS both come back null — and null-because-the
    // -request-died reads exactly like null-because-the-artist-never-filled-it-in.
    // Collapsing the two tells a complete artist their profile is thin and points
    // them back at the wizard, on nothing more than a dropped connection.

    @Test
    fun failedDetailFetch_isNotReportedAsAnIncompleteProfile() = runTest {
        val model = vm(
            bookings = FakeBookingsRepository(),
            artists = FakeArtistsRepository(listOf(artist(id = ARTIST_ID))).apply { failFetch = true },
        )
        advanceUntilIdle()

        assertNull("a failed artist read must not read as a thin profile", model.state.value.profileGaps)
        // Silence would be its own bug: the artist is looking at a screen whose
        // profile half didn't load, so the existing refresh-failure banner says so.
        assertNotNull("the failure banner must say the profile half didn't load", model.state.value.error)
    }

    @Test
    fun failedDetailFetch_doesNotUndoWhatAnEarlierRefreshAlreadyLoaded() = runTest {
        val artists = FakeArtistsRepository(listOf(artist(id = ARTIST_ID, name = "Nova Beats")))
        val model = vm(bookings = FakeBookingsRepository(), artists = artists)
        advanceUntilIdle()
        assertEquals("Nova", model.state.value.greetingName)

        // Second refresh, artist read blips.
        artists.failFetch = true
        model.refresh()
        advanceUntilIdle()

        assertEquals("Nova", model.state.value.greetingName)
        // The last good refresh said nothing was missing; a blipped read is not
        // new information, so the completeness banner must stay down.
        assertFalse(
            "a blipped read must not raise the completeness banner",
            model.state.value.profileGaps?.needsWork == true,
        )
        assertNotNull("the failure banner must say the profile half didn't load", model.state.value.error)
    }

    @Test
    fun thinProfile_stillGetsTheCompletenessBanner() = runTest {
        val model = vm(
            bookings = FakeBookingsRepository(),
            artists = FakeArtistsRepository(
                listOf(artist(id = ARTIST_ID).copy(genre = "", bio = "")),
            ),
        )
        advanceUntilIdle()

        val gaps = model.state.value.profileGaps
        assertNotNull("a genuinely thin profile must still be flagged", gaps)
        assertTrue(gaps!!.needsWork)
        assertEquals(listOf("your genre", "a short bio"), gaps.strengthGaps)
        assertNull(model.state.value.error)
    }

    @Test
    fun unfinishedWizard_stillBlocksDiscovery_evenWhenTheDetailFetchFails() = runTest {
        // Whether the wizard was finished is knowable from the users row alone, so
        // suppressing the banner on a failed artist read must not suppress this one.
        val model = vm(
            bookings = FakeBookingsRepository(),
            artists = FakeArtistsRepository(listOf(artist(id = ARTIST_ID))).apply { failFetch = true },
            users = FakeUsersRepository(selfProfile = completeProfile.copy(artistSetupComplete = false)),
        )
        advanceUntilIdle()

        assertTrue(
            "an unfinished wizard is knowable without the artist row",
            model.state.value.profileGaps?.blocksDiscovery == true,
        )
    }

    @Test
    fun failedProfileFetch_surfacesTheBanner_ratherThanGuessingAtCompleteness() = runTest {
        val model = vm(
            bookings = FakeBookingsRepository(),
            users = FakeUsersRepository(selfProfile = completeProfile, failFetch = true),
        )
        advanceUntilIdle()

        assertNull("no users row means nothing is knowable about gaps", model.state.value.profileGaps)
        assertNotNull("the failure banner must say the profile half didn't load", model.state.value.error)
    }

    // ── A failed score read is not a new artist ─────────────────────────────
    //
    // The same trap one field over. `breakdownForSelf()` THROWS on a network/RLS
    // failure and returns a zeros row for a genuine newcomer; flattening both to
    // null and substituting `ScoreBreakdown.NewArtist` told a ranked artist their
    // standing had reset. Bookability is the hero metric on this screen — an
    // Elite 92 repainted as "—" / "NEW" with all four metric rows em-dashed is
    // the single most alarming thing the dashboard can say, and it said it on
    // nothing more than a dropped connection.

    @Test
    fun blippedScoreRead_doesNotDemoteARankedArtistToNew() = runTest {
        val score = FakeScoreRepository(self = eliteBreakdown)
        val model = vm(bookings = FakeBookingsRepository(), score = score)
        advanceUntilIdle()
        assertEquals(ScoreTier.Elite, model.state.value.breakdown.tier)

        // Second refresh, score read blips.
        score.failSelf = true
        model.refresh()
        advanceUntilIdle()

        assertEquals(
            "a blipped score read must not reset the standing card",
            92,
            model.state.value.breakdown.numericScore,
        )
        assertEquals(ScoreTier.Elite, model.state.value.breakdown.tier)
        // …and the metric rows stay real rather than collapsing to em-dashes,
        // which is what `totalGigs = 0` would have done to all four.
        assertEquals(40, model.state.value.breakdown.totalGigs)
    }

    @Test
    fun failedScoreRead_surfacesTheBanner_ratherThanSayingNothing() = runTest {
        val model = vm(
            bookings = FakeBookingsRepository(),
            score = FakeScoreRepository(self = eliteBreakdown, failSelf = true),
        )
        advanceUntilIdle()

        assertNotNull("the failure banner must say the score half didn't load", model.state.value.error)
    }

    /** The other side of the fix: a real newcomer must still read as New. */
    @Test
    fun aGenuineNewcomerStillReadsAsNew_withNoFailureBanner() = runTest {
        val model = vm(
            bookings = FakeBookingsRepository(),
            score = FakeScoreRepository(self = ScoreBreakdown.NewArtist),
        )
        advanceUntilIdle()

        assertEquals(ScoreTier.New, model.state.value.breakdown.tier)
        assertNull(model.state.value.breakdown.numericScore)
        assertNull("a successful read of zeros is not a failure", model.state.value.error)
    }

    /** Ranked and well clear of the `<5 gigs` New short-circuit. */
    private val eliteBreakdown = ScoreBreakdown(
        score = 92,
        showUpRate = 98,
        reviewScore = 94,
        replySpeed = 90,
        cancellationRate = 2,
        socialProof = 80,
        totalGigs = 40,
    )
}
