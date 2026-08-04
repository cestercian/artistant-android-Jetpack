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
    fun artistWithNoPublishedSlots_fallsBackToTheDefaultTimeGrid() = runTest {
        val model = vm(FakeArtistsRepository(listOf(artist(timeSlots = emptyList()))))

        assertEquals(DefaultTimeSlots, model.state.value.timeSlots)
        assertEquals("8:30 PM", model.state.value.selectedTime)
    }

    @Test
    fun draftStore_clearIsUnconditional_soNoDraftSurvivesIntoTheNextSession() {
        val store = BookingDraftStore()
        store.setDraft(`in`.artistant.app.testsupport.bookingDraft())
        assertNotNull(store.draft.value)

        store.clear()

        assertNull(store.draft.value)
    }
}
