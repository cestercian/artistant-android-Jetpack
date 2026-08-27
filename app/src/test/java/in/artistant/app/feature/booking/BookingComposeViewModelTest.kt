package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import `in`.artistant.app.testsupport.pkg
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The compose-a-request screen: what ends up in [BookingDraftStore] is what
 * Checkout sends to the server, so the fee/package snapshot is load-bearing —
 * `BookingsRepository.create` never re-reads the artist cache.
 */
class BookingComposeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        artists: FakeArtistsRepository,
        draftStore: BookingDraftStore = BookingDraftStore(),
        artistId: String = ARTIST_ID,
    ) = BookingViewModel(
        savedStateHandle = SavedStateHandle(mapOf("artistId" to artistId)),
        artistsRepository = artists,
        draftStore = draftStore,
    )

    private fun seededArtists() = FakeArtistsRepository(
        listOf(
            artist(
                price = 25_000,
                packages = listOf(
                    pkg("p0", "Acoustic hour", 12_000, duration = "1h"),
                    pkg("p1", "Evening set", 20_000, duration = "2h", popular = true),
                ),
                timeSlots = listOf("7:30 PM", "8:30 PM"),
            ),
        ),
    )

    /** Three tiers so a handed-over index can differ from the popular default (1). */
    private fun threeTierArtist() = FakeArtistsRepository(
        listOf(
            artist(
                price = 25_000,
                packages = listOf(
                    pkg("p0", "Acoustic hour", 12_000, duration = "1h"),
                    pkg("p1", "Evening set", 20_000, duration = "2h", popular = true),
                    pkg("p2", "Headline set", 35_000, duration = "3h"),
                ),
                timeSlots = listOf("7:30 PM", "8:30 PM"),
            ),
        ),
    )

    @Test
    fun refresh_selectsThePopularPackageAndAnAvailableDate() = runTest {
        val model = vm(seededArtists())

        val s = model.state.value
        assertEquals(1, s.packageIndex) // "Evening set" is the popular tier
        assertTrue(s.canContinue)
        assertFalse(s.isLoading)
        assertTrue(s.dateChips.isNotEmpty())
        assertEquals(s.dateChips.first { it.available }.label, s.selectedDateLabel)
        assertEquals("8:30 PM", s.selectedTime)
    }

    @Test
    fun refresh_withNoPopularPackage_fallsBackToTheFirstPackage() = runTest {
        // `popular` is now false on everything the wizard/EPK publish, so this is
        // the ordinary path, not an edge case: `indexOfFirst` returns -1 and the
        // selection must land on a real package (0), never on -1.
        val artists = FakeArtistsRepository(
            listOf(
                artist(
                    packages = listOf(
                        pkg("p0", "Acoustic hour", 12_000, duration = "1h"),
                        pkg("p1", "Evening set", 20_000, duration = "2h"),
                    ),
                    timeSlots = listOf("7:30 PM"),
                ),
            ),
        )

        val s = vm(artists).state.value

        assertEquals(0, s.packageIndex)
        assertTrue(s.canContinue)
    }

    /**
     * The regression these three pin: the profile's package selection was purely
     * cosmetic. Tapping "Headline set" then Check availability landed on the
     * popular tier, because the route carries only the artist id and this screen
     * re-derived its own default. `ArtistProfileViewModel.startBooking()` now
     * hands the tapped index over through [BookingDraftStore] — the same call
     * these tests make — mirroring how iOS `ArtistView` seeds the booking store
     * before its NavigationLink pushes.
     */
    @Test
    fun refresh_opensOnThePackageTheProfileHandedOver() = runTest {
        val store = BookingDraftStore()
        // What ArtistProfileViewModel.startBooking() does when the client taps
        // "Headline set" (index 2) and then Check availability.
        store.seedPackageIndex(ARTIST_ID, 2)

        val s = vm(threeTierArtist(), store).state.value

        assertEquals(2, s.packageIndex)
    }

    @Test
    fun refresh_ignoresAHandoverLeftBehindByADifferentArtist() = runTest {
        // Seeds outlive one navigation, so a stale one from another profile must
        // not decide this artist's opening tier — it falls back to the default.
        val store = BookingDraftStore()
        store.seedPackageIndex("some-other-artist", 2)

        val s = vm(threeTierArtist(), store).state.value

        assertEquals(1, s.packageIndex) // the popular tier, not the stale seed
    }

    @Test
    fun refresh_ignoresAHandoverThatNoLongerAddressesARealPackage() = runTest {
        // The artist can republish between the profile read and this one, so an
        // index that is now out of range must not reach `packages[index]`.
        val store = BookingDraftStore()
        store.seedPackageIndex(ARTIST_ID, 7)

        val s = vm(threeTierArtist(), store).state.value

        assertEquals(1, s.packageIndex)
    }

    @Test
    fun refresh_unknownArtist_surfacesLoadErrorAndBlocksContinue() = runTest {
        val model = vm(FakeArtistsRepository(emptyList()))

        assertEquals("Artist not found.", model.state.value.loadError)
        assertFalse(model.state.value.canContinue)
        assertFalse(model.onContinue())
    }

    @Test
    fun onContinue_snapshotsTheSelectedPackageFeeIntoTheDraft() = runTest {
        val store = BookingDraftStore()
        val model = vm(seededArtists(), store)

        model.selectPackage(0)
        model.setVenue("Rooftop, Indiranagar")
        model.setGuests(120)
        model.setVenueNotes("Load-in via the service lift.")
        assertTrue(model.onContinue())

        val draft = store.draft.value
        assertNotNull(draft)
        assertEquals(ARTIST_ID, draft!!.artistId)
        assertEquals(0, draft.packageIndex)
        assertEquals("Acoustic hour", draft.packageName)
        assertEquals("1h", draft.packageDuration)
        assertEquals(12_000, draft.feeInr)
        assertEquals("Rooftop, Indiranagar", draft.venue)
        assertEquals(120, draft.guests)
        assertEquals("Load-in via the service lift.", draft.venueNotes)
    }

    @Test
    fun onContinue_withNoPackages_fallsBackToTheArtistHeadlinePrice() = runTest {
        val store = BookingDraftStore()
        val model = vm(FakeArtistsRepository(listOf(artist(price = 18_000, packages = emptyList()))), store)

        assertTrue(model.onContinue())

        assertEquals(18_000, store.draft.value?.feeInr)
        assertEquals("Custom", store.draft.value?.packageName)
    }

    @Test
    fun selectDate_ignoresChipsTheArtistMarkedUnavailable() = runTest {
        // Only Saturdays are available, so at least one chip in the 14-day window is not.
        val model = vm(
            FakeArtistsRepository(listOf(artist(daysAvailable = listOf("Sat")))),
        )
        val before = model.state.value.selectedDateLabel
        val blocked = model.state.value.dateChips.first { !it.available }

        model.selectDate(blocked)

        assertEquals(before, model.state.value.selectedDateLabel)
    }

    @Test
    fun selectDate_acceptsAnAvailableChip() = runTest {
        val model = vm(seededArtists())
        val open = model.state.value.dateChips.last { it.available }

        model.selectDate(open)

        assertEquals(open.label, model.state.value.selectedDateLabel)
        assertEquals(open.epochMs, model.state.value.selectedDateEpochMs)
    }

    @Test
    fun setGuests_clampsToTheSupportedRange() = runTest {
        val model = vm(seededArtists())

        model.setGuests(0)
        assertEquals(10, model.state.value.guests)
        model.setGuests(999_999)
        assertEquals(5_000, model.state.value.guests)
    }

    @Test
    fun setVenueNotes_isBoundedHere_notAtWhicheverFieldHappensToCallIt() = runTest {
        // The cap used to live in the composable's onValueChange, so the server
        // column's bound held for exactly one caller. Anything else writing
        // notes — a restore, a paste handler, a test — put an unbounded string
        // into the draft and on to the wire.
        val store = BookingDraftStore()
        val model = vm(seededArtists(), store)

        model.setVenueNotes("x".repeat(VENUE_NOTES_MAX + 200))

        assertEquals(VENUE_NOTES_MAX, model.state.value.venueNotes.length)
        assertTrue(model.onContinue())
        assertEquals(VENUE_NOTES_MAX, store.draft.value?.venueNotes?.length)
    }

    @Test
    fun setVenueNotes_leavesAnOrdinaryNoteAlone() = runTest {
        val model = vm(seededArtists())

        model.setVenueNotes("Gate 3, load-in at the back.")

        assertEquals("Gate 3, load-in at the back.", model.state.value.venueNotes)
    }

    @Test
    fun artistWithNoPublishedSlots_fallsBackToTheDefaultTimeGrid() = runTest {
        val model = vm(FakeArtistsRepository(listOf(artist(timeSlots = emptyList()))))

        // Asserted on a day that cannot be today. Today's grid is now trimmed to
        // the slots the clock hasn't passed, so a suite run after 6pm would see a
        // short list through no fault of the fallback. Clock behaviour itself is
        // pinned deterministically in BookingSlotsTest.
        model.selectDate(model.state.value.dateChips[1])

        assertEquals(DefaultTimeSlots, model.state.value.timeSlots)
        assertTrue(model.state.value.selectedTime in DefaultTimeSlots)
    }

    /**
     * The past-slot regression, at the ViewModel seam.
     *
     * `upcomingDateChips` starts at today and `resolveTimeSlots` returned the
     * artist's whole list regardless of the hour, so opening the funnel at 23:10
     * preselected "8:30 PM" and Continue snapshotted a draft for a show that had
     * already ended. Both halves are asserted without touching the clock: the day
     * the screen opens on must be one the strip calls available, and the time it
     * preselects must be one of the times it is actually offering.
     */
    @Test
    fun refresh_opensOnABookableDayWithATimeItIsStillOffering() = runTest {
        val model = vm(FakeArtistsRepository(listOf(artist(timeSlots = DefaultTimeSlots))))

        val s = model.state.value

        assertEquals(s.dateChips.first { it.available }.epochMs, s.selectedDateEpochMs)
        assertTrue(s.timeSlots.isNotEmpty())
        assertTrue(s.selectedTime in s.timeSlots)
    }

    @Test
    fun selectDate_movingOffTodayRestoresTheArtistsWholeGrid() = runTest {
        // Only today hides passed slots, so a later day has to come back with the
        // full published list — a trimmed grid must not follow the user forward.
        val model = vm(FakeArtistsRepository(listOf(artist(timeSlots = DefaultTimeSlots))))

        model.selectDate(model.state.value.dateChips[7])

        assertEquals(DefaultTimeSlots, model.state.value.timeSlots)
        assertTrue(model.state.value.selectedTime in DefaultTimeSlots)
    }

    @Test
    fun draftStore_clearIsUnconditional_soNoDraftSurvivesIntoTheNextSession() {
        val store = BookingDraftStore()
        store.setDraft(`in`.artistant.app.testsupport.bookingDraft())
        assertNotNull(store.draft.value)

        store.clear()

        assertNull(store.draft.value)
    }

    @Test
    fun draftStore_clearAlsoDropsTheTappedTier_soANewAccountDoesNotOpenOnIt() {
        // The store is a @Singleton, so it outlives the session; sign-out and
        // account delete both call clear() (ProfileViewModel). The package
        // handover is the half that shows: without it dropped, the next account
        // to open this artist would find the previous one's tier preselected.
        val store = BookingDraftStore()
        store.seedPackageIndex(ARTIST_ID, 2)
        assertEquals(2, store.pendingPackageIndex(ARTIST_ID))

        store.clear()

        assertNull(store.pendingPackageIndex(ARTIST_ID))
    }
}
