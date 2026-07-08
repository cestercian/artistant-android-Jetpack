package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.platform.billing.MockSubscriptionService
import `in`.artistant.app.platform.payments.MockPaymentsService
import `in`.artistant.app.state.BookingStore
import `in`.artistant.app.state.EntitlementStore
import `in`.artistant.app.state.InMemoryAccountTokenStore
import `in`.artistant.app.state.RecordingTokenWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * The booking funnel end-to-end through the fakes: BookingViewModel seeds a draft
 * from the artist's popular package, the field mutators update it, and Checkout's
 * confirm turns it into a persisted booking with the right money math + status.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val artistId = "11111111-1111-1111-1111-111111111111"

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun artist() = Artist(
        id = artistId, name = "DJ Neon", handle = "neon", category = "DJ", genre = "House",
        city = "Mumbai", price = 10_000, duration = "2 hr", score = 80, gradient = ArtistGradient.palette(0),
        bio = "", followers = "", streams = "", response = "", onTime = 0, gigs = 0, rating = 0.0,
        packages = listOf(
            ArtistPackage("Standard", "1 hr", 8_000, emptyList()),
            ArtistPackage("Headline", "2 hr", 10_000, emptyList(), popular = true),
        ),
        tech = emptyList(), samples = emptyList(), reviews = emptyList(),
        timeSlots = listOf("8:30 PM"),
    )

    @Test
    fun `draft seeds from the popular package and confirm persists a booking`() = runTest(dispatcher) {
        val artists = FakeArtistsRepository(full = listOf(artist()))
        val store = BookingStore(FakeBookingsRepository(artists), artists)
        val booking = BookingViewModel(SavedStateHandle(mapOf("artistId" to artistId)), store, artists)
        advanceUntilIdle()

        // Draft seeded from the artist's popular slot (index 1) → fee 10,000.
        val draft = store.draft.value
        assertNotNull(draft)
        assertEquals(1, draft!!.packageIndex)
        assertEquals(10_000, booking.draftFee())

        // Edit venue + guests through the mutators.
        booking.setVenue("Blue Frog")
        booking.setGuests(240)

        // Confirm through the checkout path (mock payment, instant). The M7 entitlement gate is
        // dormant (enabled = false), so confirm goes straight through — no paywall.
        val entitlements = EntitlementStore(
            MockSubscriptionService(), InMemoryAccountTokenStore(), RecordingTokenWriter(), enabled = false,
        )
        val checkout = CheckoutViewModel(store, MockPaymentsService().apply { simulatedLatencyMillis = 0 }, artists, entitlements)
        checkout.confirm()
        advanceUntilIdle()

        val persisted = store.bookingsFlow.value
        assertEquals(1, persisted.size)
        val b = persisted.first()
        assertEquals(BookingStatus.Confirmed, b.status)
        assertEquals(10_000, b.fee)
        assertEquals(500, b.platformFee)       // 5%
        assertEquals(1_890, b.gst)             // 18% of (10000 + 500)
        assertEquals("Blue Frog", b.venue)
        assertEquals(240, b.guests)
        // Draft consumed once the booking lands.
        assertEquals(null, store.draft.value)
    }
}
