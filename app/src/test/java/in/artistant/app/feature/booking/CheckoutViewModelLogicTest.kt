package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.payments.MockPaymentsService
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.feature.paywall.EntitlementStore
import `in`.artistant.app.testsupport.artist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            entitlements = EntitlementStore(),
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
        // The narrated takeover has to come down once the write lands, or it
        // sits over the confirmation screen the nav is about to push.
        assertNull(vm.state.value.waitPhase)
        assertFalse(vm.state.value.isSubmitting)
    }

    private fun vm(
        draftStore: BookingDraftStore,
        bookings: FakeBookingsRepository = FakeBookingsRepository(),
        artists: FakeArtistsRepository = FakeArtistsRepository(),
    ) = CheckoutViewModel(
        draftStore = draftStore,
        artistsRepository = artists,
        bookingsRepository = bookings,
        paymentsService = MockPaymentsService(),
        entitlements = EntitlementStore(),
    )

    private fun storeWithDraft() = BookingDraftStore().apply { setDraft(draft()) }

    @Test
    fun sendIsNotBlocked_whenTheArtistCouldNotBeLoaded() = runTest {
        // The draft carries a real fee, so the request is valid. Blocking on a
        // failed decoration is how a CTA ends up permanently grey.
        val vm = vm(storeWithDraft(), artists = FakeArtistsRepository(emptyList()))
        advanceUntilIdle()

        assertFalse(vm.state.value.artistHasNoPackages)
        assertFalse(vm.state.value.blocked)
    }

    @Test
    fun sendIsBlocked_forAnArtistWhoPublishesNoTiers() = runTest {
        val vm = vm(
            storeWithDraft(),
            artists = FakeArtistsRepository(listOf(artist(packages = emptyList()))),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.artistHasNoPackages)
        assertTrue(vm.state.value.blocked)
    }

    @Test
    fun aBlockedCheckoutNeverFilesTheRequest() = runTest {
        val bookings = FakeBookingsRepository()
        val vm = vm(
            storeWithDraft(),
            bookings = bookings,
            artists = FakeArtistsRepository(listOf(artist(packages = emptyList()))),
        )
        advanceUntilIdle()

        vm.sendRequest()
        advanceUntilIdle()

        assertNull(vm.state.value.confirmedBookingId)
        assertTrue(bookings.listForClient().isEmpty())
    }

    @Test
    fun theArtistHydratesTheHeader_withNameAndScore() = runTest {
        val vm = vm(
            storeWithDraft(),
            artists = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        )
        advanceUntilIdle()

        assertEquals("Nova Beats", vm.state.value.artistName)
        assertEquals(82, vm.state.value.artist?.score)
    }

    @Test
    fun aFailedWrite_dropsTheWaitOverlay_soTheRetryBannerIsReachable() = runTest {
        val bookings = FakeBookingsRepository().apply { failCreate = true }
        val vm = vm(storeWithDraft(), bookings = bookings)
        advanceUntilIdle()

        vm.sendRequest()
        advanceUntilIdle()

        val s = vm.state.value
        assertNotNull(s.lastCreateErrorMessage)
        assertNull("a failure hidden behind a full-screen wait is a dead end", s.waitPhase)
        assertFalse(s.isSubmitting)
        assertNull(s.confirmedBookingId)
        // The draft survives so Retry has something to send.
        assertNotNull(s.draft)
    }

    @Test
    fun retryAfterAFailure_clearsTheBannerAndLandsTheBooking() = runTest {
        val bookings = FakeBookingsRepository().apply { failCreate = true }
        val vm = vm(storeWithDraft(), bookings = bookings)
        advanceUntilIdle()
        vm.sendRequest()
        advanceUntilIdle()
        assertNotNull(vm.state.value.lastCreateErrorMessage)

        bookings.failCreate = false
        vm.sendRequest()
        advanceUntilIdle()

        assertNull(vm.state.value.lastCreateErrorMessage)
        assertNotNull(vm.state.value.confirmedBookingId)
    }

    @Test
    fun signedOutSend_surfacesTheRealReason_ratherThanAGenericLine() = runTest {
        val bookings = FakeBookingsRepository().apply { signedIn = false }
        val vm = vm(storeWithDraft(), bookings = bookings)
        advanceUntilIdle()

        vm.sendRequest()
        advanceUntilIdle()

        assertNotNull(vm.state.value.lastCreateErrorMessage)
        assertNull(vm.state.value.confirmedBookingId)
    }

    @Test
    fun noDraft_reportsIt_ratherThanRenderingAnEmptyConfirmScreen() = runTest {
        val vm = vm(BookingDraftStore())
        advanceUntilIdle()

        assertNull(vm.state.value.draft)
        assertNotNull(vm.state.value.lastCreateErrorMessage)

        vm.sendRequest()
        advanceUntilIdle()
        assertNull(vm.state.value.confirmedBookingId)
    }

    @Test
    fun dismissError_clearsTheBannerWithoutTouchingTheDraft() = runTest {
        val vm = vm(storeWithDraft(), bookings = FakeBookingsRepository().apply { failCreate = true })
        advanceUntilIdle()
        vm.sendRequest()
        advanceUntilIdle()

        vm.dismissError()

        assertNull(vm.state.value.lastCreateErrorMessage)
        assertNotNull(vm.state.value.draft)
    }
}
