package `in`.artistant.app.feature.paywall

import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.data.payments.MockPaymentsService
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.feature.booking.BookingDraftStore
import `in`.artistant.app.feature.booking.CheckoutViewModel
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.bookingDraft
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The ₹99/mo subscription system ships BUILT AND INERT — `subscriptionsEnabled`
 * is a compile-time `false`, and nothing about booking may change until the
 * operator flips it. These tests pin "inert", because the failure mode is
 * silent and severe: a paywall appearing in front of a v1 booking request.
 */
class EntitlementGateTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun subscriptionsAreOffInThisBuild() {
        assertFalse(AppEnvironment.subscriptionsEnabled)
    }

    @Test
    fun theStoreReportsItselfInactive() {
        val store = EntitlementStore()

        assertEquals(AppEnvironment.subscriptionsEnabled, store.subscriptionsActive)
        assertFalse(store.isEntitled.value)
    }

    @Test
    fun nobodyIsEntitledWhileTheFlagIsOff() {
        val store = EntitlementStore()

        assertFalse(store.isEntitled(AppEnvironment.artistMonthlyProductId))
        assertFalse(store.isEntitled(AppEnvironment.clientMonthlyProductId))
        assertFalse(store.isEntitled("anything.at.all"))
    }

    @Test
    fun refreshWithoutABillingClientLeavesTheMirrorFalse() = runTest {
        val store = EntitlementStore()

        store.refresh()

        assertFalse(store.isEntitled.value)
    }

    @Test
    fun productIdsAreRoleSpecificAndDistinct() {
        val artistId = AppEnvironment.subscriptionProductId(AppRole.Artist)
        val clientId = AppEnvironment.subscriptionProductId(AppRole.Client)

        assertEquals(AppEnvironment.artistMonthlyProductId, artistId)
        assertEquals(AppEnvironment.clientMonthlyProductId, clientId)
        assertTrue(artistId != clientId)
    }

    // --- the gate as Checkout actually reaches it ---------------------------

    private fun checkout(
        bookings: FakeBookingsRepository = FakeBookingsRepository(),
        draftStore: BookingDraftStore = BookingDraftStore().apply { setDraft(bookingDraft()) },
    ) = CheckoutViewModel(
        draftStore = draftStore,
        artistsRepository = FakeArtistsRepository(),
        bookingsRepository = bookings,
        paymentsService = MockPaymentsService(),
        entitlements = EntitlementStore(),
    ) to draftStore

    @Test
    fun theQuotaGateNeverFiresWhileSubscriptionsAreOff() = runTest {
        val (model, _) = checkout()

        model.sendRequest()
        advanceUntilIdle()

        assertFalse(model.state.value.needsPaywall)
        assertNotNull(model.state.value.confirmedBookingId)
    }

    @Test
    fun aFailedCreateSurfacesAnErrorAndKeepsTheDraft() = runTest {
        val bookings = FakeBookingsRepository().apply { failCreate = true }
        val (model, draftStore) = checkout(bookings)

        model.sendRequest()
        advanceUntilIdle()

        assertNotNull(model.state.value.lastCreateErrorMessage)
        assertNull(model.state.value.confirmedBookingId)
        assertFalse(model.state.value.isSubmitting)
        assertNotNull("a failed send must not lose the composed request", draftStore.draft.value)
    }

    @Test
    fun aSignedOutCreateSurfacesTheSignInPrompt() = runTest {
        val bookings = FakeBookingsRepository().apply { signedIn = false }
        val (model, _) = checkout(bookings)

        model.sendRequest()
        advanceUntilIdle()

        assertTrue(model.state.value.lastCreateErrorMessage.orEmpty().contains("signed in"))
    }

    @Test
    fun checkoutWithNoDraftRefusesToSend() = runTest {
        val (model, _) = checkout(draftStore = BookingDraftStore())

        model.sendRequest()
        advanceUntilIdle()

        assertNull(model.state.value.confirmedBookingId)
        assertNotNull(model.state.value.lastCreateErrorMessage)
    }

    @Test
    fun clearNavigationDropsBothOneShotSignals() = runTest {
        val (model, _) = checkout()
        model.sendRequest()
        advanceUntilIdle()
        assertNotNull(model.state.value.confirmedBookingId)

        model.clearNavigation()

        assertNull(model.state.value.confirmedBookingId)
        assertFalse(model.state.value.needsPaywall)
    }
}
