package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.payments.MockPaymentsService
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.feature.booking.BookingDraftStore
import `in`.artistant.app.feature.booking.CheckoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelLogicTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun draft() = BookingDraft(
        artistId = "11111111-1111-1111-1111-111111111111",
        packageIndex = 0,
        packageName = "Evening set",
        packageDuration = "2h",
        feeInr = 20000,
        date = "Sat, May 16, 2026",
        dateRawEpochMs = 1_747_353_600_000L,
        time = "8:30 PM",
        venue = "Rooftop",
        guests = 80,
        paymentMethod = PaymentMethod.Upi,
    )

    @Test
    fun sendRequest_createsPendingConfirm() = runTest {
        val draftStore = BookingDraftStore()
        draftStore.setDraft(draft())
        val bookings = FakeBookingsRepository()
        val payments = MockPaymentsService()

        val vm = CheckoutViewModel(
            draftStore = draftStore,
            artistsRepository = FakeArtistsRepository(),
            bookingsRepository = bookings,
            paymentsService = payments,
        )

        advanceUntilIdle()
        assertNotNull(vm.state.value.draft)

        vm.sendRequest()
        advanceUntilIdle()

        val confirmedId = vm.state.value.confirmedBookingId
        assertNotNull(confirmedId)
        val stored = bookings.fetchOne(confirmedId!!)
        assertEquals(BookingStatus.PendingConfirm, stored?.status)
        assertEquals(null, draftStore.draft.value)
    }
}
