package `in`.artistant.app.feature.bookings

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.state.BookingStore
import `in`.artistant.app.state.DeepLinkRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * BookingsViewModel groups the client's bookings by day for the month calendar:
 * two bookings on the same day fold into one key, a third on another day is its
 * own key, and an unparseable label is dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun booking(id: String, dateLabel: String) = Booking(
        id = id, artistId = "a1", packageIndex = 0, dateLabel = dateLabel, timeLabel = "8:30 PM",
        startDatetime = null, endDatetime = null, venue = "V", guests = 100, fee = 10_000,
        platformFee = 500, gst = 1_890, total = 12_390, status = BookingStatus.Confirmed,
        escrowStatus = EscrowStatus.Held, paymentMethod = PaymentMethod.Upi, protectionEnabled = true,
        createdAt = null,
    )

    @Test
    fun `bookings fold into per-day keys and bad labels are dropped`() = runTest(dispatcher) {
        val seed = listOf(
            booking("b1", "Sat, May 16, 2026"),
            booking("b2", "Sat, May 16, 2026"),   // same day as b1
            booking("b3", "Sun, May 17, 2026"),
            booking("b4", "not a real date"),       // unparseable → dropped
        )
        val artists = FakeArtistsRepository()
        val store = BookingStore(FakeBookingsRepository(artists, seed = seed), artists)
        val vm = BookingsViewModel(store, artists, DeepLinkRouter())
        advanceUntilIdle() // init refresh pulls the seed into the store

        val byDay = vm.bookingsByDay()
        assertEquals(2, byDay.size) // May 16 + May 17, the bad label dropped
        assertEquals(2, byDay[LocalDate.of(2026, 5, 16)]?.size)
        assertEquals(1, byDay[LocalDate.of(2026, 5, 17)]?.size)
        assertNull(byDay[LocalDate.of(2026, 5, 18)])
    }
}
