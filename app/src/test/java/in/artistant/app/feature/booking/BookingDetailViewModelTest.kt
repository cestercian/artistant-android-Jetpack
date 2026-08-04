package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.FakeReviewsRepository
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
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
 * The request→accept spine, from the screen's point of view.
 *
 * This is the one flow the server also guards (mig 0083: only the artist may
 * flip pending_confirm→confirmed), so the client must never offer the artist's
 * CTAs to a client and must never leave a stale `booking` in state when an
 * action fails. Both halves are pinned here.
 */
class BookingDetailViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        bookings: FakeBookingsRepository,
        bookingId: String = "b-1",
    ) = BookingDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("bookingId" to bookingId)),
        bookingsRepository = bookings,
        artistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        reviewsRepository = FakeReviewsRepository(),
    )

    // --- load ---------------------------------------------------------------

    @Test
    fun load_hydratesBookingAndArtistName() = runTest {
        val model = vm(FakeBookingsRepository(listOf(booking())))

        val s = model.state.value
        assertEquals("b-1", s.booking?.id)
        assertEquals("Nova Beats", s.artistName)
        assertFalse(s.isLoading)
        assertNull(s.loadError)
    }

    @Test
    fun load_missingBooking_surfacesLoadError_ratherThanAnEmptyShell() = runTest {
        val model = vm(FakeBookingsRepository(emptyList()))

        val s = model.state.value
        assertNull(s.booking)
        assertEquals("Booking not found.", s.loadError)
        assertFalse(s.isLoading)
    }

    // --- role gating (mirrors the 0083 artist-only guard) --------------------

    @Test
    fun pendingConfirm_showsAcceptDeclineToTheArtistOnly() = runTest {
        val model = vm(FakeBookingsRepository(listOf(booking(status = BookingStatus.PendingConfirm))))

        assertTrue(model.showAcceptDecline(isArtistViewer = true))
        assertFalse(model.showAcceptDecline(isArtistViewer = false))
    }

    @Test
    fun pendingConfirm_showsCancelToTheClientOnly() = runTest {
        val model = vm(FakeBookingsRepository(listOf(booking(status = BookingStatus.PendingConfirm))))

        assertTrue(model.showClientCancel(isArtistViewer = false))
        assertFalse(model.showClientCancel(isArtistViewer = true))
    }

    @Test
    fun confirmedBooking_hidesAcceptDeclineFromBothSides() = runTest {
        val model = vm(FakeBookingsRepository(listOf(booking(status = BookingStatus.Confirmed))))

        assertFalse(model.showAcceptDecline(isArtistViewer = true))
        assertFalse(model.showAcceptDecline(isArtistViewer = false))
        assertFalse(model.showClientCancel(isArtistViewer = false))
    }

    @Test
    fun counterpartyName_flipsWithTheViewerSide() = runTest {
        val model = vm(FakeBookingsRepository(listOf(booking(clientFullName = "Asha Rao"))))

        assertEquals("Asha Rao", model.counterpartyName(isArtistViewer = true))
        assertEquals("Nova Beats", model.counterpartyName(isArtistViewer = false))
    }

    @Test
    fun counterpartyName_fallsBackToRoleNouns_neverToVenueOrTbd() = runTest {
        // Pre-0080 rows arrive with no client name; the artist side must read
        // "Client", not the venue string (the old "TBD" bug).
        val model = vm(FakeBookingsRepository(listOf(booking(clientFullName = null))))

        assertEquals("Client", model.counterpartyName(isArtistViewer = true))
    }

    // --- accept / decline / cancel ------------------------------------------

    @Test
    fun accept_flipsToConfirmed_inStateAndInTheStore() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking()))
        val model = vm(bookings)

        model.acceptRequest()

        assertEquals(BookingStatus.Confirmed, model.state.value.booking?.status)
        assertEquals(BookingStatus.Confirmed, bookings.fetchOne("b-1")?.status)
        assertFalse(model.state.value.isActing)
        assertNull(model.state.value.actionError)
    }

    @Test
    fun decline_cancelsAndRefundsEscrow() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking()))
        val model = vm(bookings)

        model.declineRequest()

        assertEquals(BookingStatus.Cancelled, model.state.value.booking?.status)
        assertEquals(EscrowStatus.Refunded, model.state.value.booking?.escrowStatus)
    }

    @Test
    fun clientCancel_cancelsTheRow() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking()))
        val model = vm(bookings)

        model.cancelBooking()

        assertEquals(BookingStatus.Cancelled, bookings.fetchOne("b-1")?.status)
    }

    @Test
    fun accept_whenSignedOut_surfacesTheErrorAndLeavesTheRowUntouched() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking()))
        val model = vm(bookings)
        bookings.signedIn = false

        model.acceptRequest()

        val s = model.state.value
        assertNotNull(s.actionError)
        assertFalse(s.isActing)
        // The optimism trap: a failed accept must NOT leave a Confirmed row on screen.
        assertEquals(BookingStatus.PendingConfirm, s.booking?.status)
        bookings.signedIn = true
        assertEquals(BookingStatus.PendingConfirm, bookings.fetchOne("b-1")?.status)
    }

    @Test
    fun actionError_clearsOnTheNextSuccessfulAction() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking()))
        val model = vm(bookings)
        bookings.signedIn = false
        model.acceptRequest()
        assertNotNull(model.state.value.actionError)

        bookings.signedIn = true
        model.acceptRequest()

        assertNull(model.state.value.actionError)
        assertEquals(BookingStatus.Confirmed, model.state.value.booking?.status)
    }

    @Test
    fun reportActionError_surfacesAMessageWithoutTouchingTheBooking() = runTest {
        val model = vm(FakeBookingsRepository(listOf(booking())))

        model.reportActionError("Couldn't open the calendar.")

        assertEquals("Couldn't open the calendar.", model.state.value.actionError)
        assertEquals(BookingStatus.PendingConfirm, model.state.value.booking?.status)
    }

    @Test
    fun artistIdIsPreservedThroughAccept_soTheThreadStillResolves() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking(artistId = ARTIST_ID)))
        val model = vm(bookings)

        model.acceptRequest()

        assertEquals(ARTIST_ID, model.state.value.booking?.artistId)
    }
}
