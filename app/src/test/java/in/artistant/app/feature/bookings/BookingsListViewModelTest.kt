package `in`.artistant.app.feature.bookings

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.FakeUsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.OTHER_ARTIST_ID
import `in`.artistant.app.testsupport.artist
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Client-side bookings list — artist hydration, the Upcoming/Past split, the
 * offline snapshot, and the profile nudge.
 */
class BookingsListViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /**
     * A gig far enough ahead that the segment split cannot drift with the wall
     * clock. `booking()`'s default date is in 2026 and this suite outlives it.
     */
    private val FUTURE = "2036-05-16T15:00:00Z"

    private fun vm(
        bookings: FakeBookingsRepository,
        artists: FakeArtistsRepository = FakeArtistsRepository(),
        store: FakeBookingsLocalStore = FakeBookingsLocalStore(),
        users: FakeUsersRepository = FakeUsersRepository(),
    ) = BookingsViewModel(
        bookingsRepository = bookings,
        artistsRepository = artists,
        localStore = store,
        usersRepository = users,
    )

    @Test
    fun cancelledBookingsAreKept_andFileUnderPast() = runTest {
        // They used to be filtered out of the list entirely, which left screen 83
        // (the cancelled record, with its rebooking offer) unreachable and the
        // client with a booking that had simply vanished. Past is where the
        // record lives.
        val model = vm(
            FakeBookingsRepository(
                listOf(
                    booking(id = "b-pending", status = BookingStatus.PendingConfirm, startIso = FUTURE),
                    booking(id = "b-cancelled", status = BookingStatus.Cancelled, startIso = FUTURE),
                    booking(id = "b-done", status = BookingStatus.Completed, startIso = FUTURE),
                ),
            ),
        )

        val s = model.state.value
        assertEquals(3, s.items.size)
        assertEquals(listOf("b-pending"), s.upcoming.map { it.booking.id })
        assertEquals(listOf("b-cancelled", "b-done"), s.past.map { it.booking.id })
        assertFalse(s.isLoading)
    }

    @Test
    fun theVisibleListFollowsTheSelectedSegment() = runTest {
        val model = vm(
            FakeBookingsRepository(
                listOf(
                    booking(id = "b-live", status = BookingStatus.Confirmed, startIso = FUTURE),
                    booking(id = "b-done", status = BookingStatus.Completed, startIso = FUTURE),
                ),
            ),
        )

        assertEquals(BookingsTab.Upcoming, model.state.value.tab)
        assertEquals(listOf("b-live"), model.state.value.visible.map { it.booking.id })

        model.selectTab(BookingsTab.Past)

        assertEquals(listOf("b-done"), model.state.value.visible.map { it.booking.id })
    }

    @Test
    fun rowsResolveTheArtistNameFromCache_andFallBackToArtist() = runTest {
        val model = vm(
            FakeBookingsRepository(
                listOf(
                    booking(id = "b-known"),
                    booking(id = "b-unknown", artistId = "99999999-9999-9999-9999-999999999999"),
                ),
            ),
            FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        )

        val byId = model.state.value.items.associateBy { it.booking.id }
        assertEquals("Nova Beats", byId.getValue("b-known").artistName)
        assertEquals("Artist", byId.getValue("b-unknown").artistName)
    }

    @Test
    fun aColdStartFetchesTheArtistsTheCacheHasNeverSeen() = runTest {
        // The Bookings tab can be the first screen composed — a push deep link
        // opens it directly, and a process-death restore can land on it — so
        // Discover has never run and the by-id cache is empty. `find` is a pure
        // map read and `listForClient()` returns bookings only, so without a
        // hydration step every row's headline printed the "Artist" placeholder.
        val model = vm(
            FakeBookingsRepository(
                listOf(
                    booking(id = "b-nova", artistId = ARTIST_ID),
                    booking(id = "b-kabir", artistId = OTHER_ARTIST_ID),
                ),
            ),
            FakeArtistsRepository(
                remote = listOf(
                    artist(id = ARTIST_ID, name = "Nova Beats"),
                    artist(id = OTHER_ARTIST_ID, name = "Kabir Rao"),
                ),
            ),
        )

        val byId = model.state.value.items.associateBy { it.booking.id }
        assertEquals("Nova Beats", byId.getValue("b-nova").artistName)
        assertEquals("Kabir Rao", byId.getValue("b-kabir").artistName)
    }

    @Test
    fun aFailedArtistFetchCostsTheNameNotTheList() = runTest {
        val artists = FakeArtistsRepository(remote = listOf(artist(name = "Nova Beats")))
            .apply { failFetch = true }

        val model = vm(FakeBookingsRepository(listOf(booking(id = "b-1"))), artists)

        val s = model.state.value
        assertNull(s.error)
        assertEquals("Artist", s.items.single().artistName)
    }

    @Test
    fun signedOutRepository_surfacesTheErrorInsteadOfAnEmptyList() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking())).apply { signedIn = false }

        val model = vm(bookings)

        val s = model.state.value
        assertTrue(s.items.isEmpty())
        assertFalse(s.isLoading)
        assertNotNull(s.error)
    }

    @Test
    fun refreshClearsAPreviousError() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking())).apply { signedIn = false }
        val model = vm(bookings)
        assertNotNull(model.state.value.error)

        bookings.signedIn = true
        model.refresh()

        assertNull(model.state.value.error)
        assertEquals(1, model.state.value.items.size)
    }

    // --- the offline snapshot (screen 122) -----------------------------------

    @Test
    fun everySuccessfulReadWritesTheEssentialsOfTheNight() = runTest {
        val store = FakeBookingsLocalStore()

        vm(
            FakeBookingsRepository(
                listOf(booking(id = "b-1", venue = "12th Main", status = BookingStatus.Confirmed)),
            ),
            store = store,
        )

        val cached = checkNotNull(store.saved).items.single()
        assertEquals("b-1", cached.id)
        assertEquals("12th Main", cached.venue)
        assertEquals("confirmed", cached.status)
    }

    @Test
    fun aFailedReadFallsBackToTheSnapshot() = runTest {
        val store = FakeBookingsLocalStore(
            snapshot = BookingsSnapshot(
                cachedAtMs = 1_000L,
                items = listOf(
                    CachedBooking(
                        id = "b-1",
                        artistName = "The Tilt Collective",
                        status = "confirmed",
                        date = "Sat, Oct 12, 2026",
                        time = "8:00 PM",
                        venue = "12th Main",
                    ),
                ),
            ),
        )
        val bookings = FakeBookingsRepository(listOf(booking())).apply { signedIn = false }

        val s = vm(bookings, store = store).state.value

        assertTrue(s.showsCached)
        assertEquals("The Tilt Collective", s.cached?.items?.single()?.artistName)
    }

    @Test
    fun anUnreadableCacheDegradesToNothingCached() = runTest {
        // A DataStore read can fail on its own. It must not turn a network blip
        // into a crash on the Bookings tab.
        val store = FakeBookingsLocalStore().apply { failLoad = true }
        val bookings = FakeBookingsRepository(listOf(booking())).apply { signedIn = false }

        val s = vm(bookings, store = store).state.value

        assertFalse(s.showsCached)
        assertNull(s.cached)
        assertNotNull(s.error)
    }

    @Test
    fun aSuccessfulReadClearsAnEarlierCachedList() = runTest {
        val store = FakeBookingsLocalStore(
            snapshot = BookingsSnapshot(cachedAtMs = 1_000L, items = emptyList()),
        )
        val bookings = FakeBookingsRepository(listOf(booking())).apply { signedIn = false }
        val model = vm(bookings, store = store)
        assertTrue(model.state.value.showsCached)

        bookings.signedIn = true
        model.refresh()

        assertFalse(model.state.value.showsCached)
        assertNull(model.state.value.cached)
    }

    // --- the profile nudge (screen 89) ---------------------------------------

    @Test
    fun theNameNudgeAppearsOnlyForAGenuinelyBlankName() = runTest {
        val store = FakeBookingsLocalStore(dismissed = false)
        val users = FakeUsersRepository(
            selfProfile = SelfProfile(
                role = AppRole.Client,
                fullName = "  ",
                city = "Bengaluru",
                handle = "host",
                artistSetupComplete = null,
            ),
        )

        assertTrue(vm(FakeBookingsRepository(), store = store, users = users).state.value.showNameNudge)

        users.selfProfile = users.selfProfile!!.copy(fullName = "Riya")
        assertFalse(vm(FakeBookingsRepository(), store = store, users = users).state.value.showNameNudge)
    }

    @Test
    fun aProfileWeCouldNotReadNeverRaisesTheNudge() = runTest {
        // Prompting someone to fix a gap we could not confirm exists is worse
        // than not prompting at all.
        val store = FakeBookingsLocalStore(dismissed = false)
        val users = FakeUsersRepository().apply { failFetch = true }

        assertFalse(vm(FakeBookingsRepository(), store = store, users = users).state.value.showNameNudge)
    }

    @Test
    fun dismissingTheNudgeSticks() = runTest {
        val store = FakeBookingsLocalStore(dismissed = false)
        val users = FakeUsersRepository(
            selfProfile = SelfProfile(
                role = AppRole.Client,
                fullName = null,
                city = null,
                handle = "host",
                artistSetupComplete = null,
            ),
        )
        val model = vm(FakeBookingsRepository(), store = store, users = users)
        assertTrue(model.state.value.showNameNudge)

        model.dismissNameNudge()

        assertFalse(model.state.value.showNameNudge)
        assertTrue(store.dismissed)
        assertFalse(vm(FakeBookingsRepository(), store = store, users = users).state.value.showNameNudge)
    }
}
