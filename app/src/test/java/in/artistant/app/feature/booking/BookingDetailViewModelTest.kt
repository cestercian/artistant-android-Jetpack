package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.FakeReviewsRepository
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.OTHER_ARTIST_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import `in`.artistant.app.testsupport.booking
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

    // --- cancel routing (which `cancelled_by` stamp reaches the server) -------

    /**
     * Records which repository entry point a mutation took.
     *
     * The stamp is invisible from [FakeBookingsRepository] — its `declineByArtist`
     * delegates straight to `cancel`, so both paths produce an identical row — but
     * the stamp is the whole point: `cancel()` reaches `cancel-booking` as
     * `cancelled_by = "client"` and `declineByArtist()` as `"artist"`, and mig 0083
     * rejects a client claiming the artist's. So the assertion has to be on the
     * call, not on the result.
     */
    private class RoutingSpy(
        private val delegate: FakeBookingsRepository,
    ) : BookingsRepository by delegate {
        val calls = mutableListOf<String>()
        var lastReason: String? = null

        override suspend fun cancel(id: String, reason: String?): Booking {
            calls += "cancel"
            lastReason = reason
            return delegate.cancel(id, reason)
        }

        override suspend fun declineByArtist(id: String, reason: String?): Booking {
            calls += "declineByArtist"
            lastReason = reason
            return delegate.declineByArtist(id, reason)
        }
    }

    private fun spy(vararg seed: Booking) = RoutingSpy(FakeBookingsRepository(seed.toList()))

    private fun spyVm(spy: RoutingSpy) = BookingDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("bookingId" to "b-1")),
        bookingsRepository = spy,
        artistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        reviewsRepository = FakeReviewsRepository(),
    )

    @Test
    fun clientCancel_routesThroughCancel_soTheRowIsBlamedOnTheClient() = runTest {
        val spy = spy(booking(status = BookingStatus.Confirmed))
        val model = spyVm(spy)

        model.cancelBooking(BookingViewer.Client, reason = "Event cancelled")

        assertEquals(listOf("cancel"), spy.calls)
        assertEquals("Event cancelled", spy.lastReason)
    }

    @Test
    fun artistCancel_routesThroughDeclineByArtist_soEscrowIsNotStrandedAndBlameIsCorrect() = runTest {
        val spy = spy(booking(status = BookingStatus.Confirmed))
        val model = spyVm(spy)

        model.cancelBooking(BookingViewer.Artist, reason = "Double-booked that date")

        assertEquals(listOf("declineByArtist"), spy.calls)
        assertEquals("Double-booked that date", spy.lastReason)
    }

    @Test
    fun declineCarriesItsReasonToTheEdgeFunction() = runTest {
        val spy = spy(booking())
        val model = spyVm(spy)

        model.declineRequest("Not the right fit for this event")

        assertEquals(listOf("declineByArtist"), spy.calls)
        assertEquals("Not the right fit for this event", spy.lastReason)
    }

    @Test
    fun bareCancelBooking_staysTheClientPath_soExistingCallSitesDontFlipBlame() = runTest {
        val spy = spy(booking())
        val model = spyVm(spy)

        model.cancelBooking()

        assertEquals(listOf("cancel"), spy.calls)
    }

    // --- package resolution --------------------------------------------------

    @Test
    fun packageName_resolvesByIndexIntoTheArtistsPackages() = runTest {
        val model = BookingDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookingId" to "b-1")),
            bookingsRepository = FakeBookingsRepository(listOf(booking())),
            artistsRepository = FakeArtistsRepository(
                listOf(artist(packages = listOf(pkg("p-1", name = "Evening set", price = 20_000)))),
            ),
            reviewsRepository = FakeReviewsRepository(),
        )

        assertEquals("Evening set", model.packageName())
    }

    @Test
    fun packageName_isNullWhenTheIndexDangles_ratherThanMislabellingTheGig() = runTest {
        // The artist republished their packages after the booking was made. The
        // stored index now points past the end — the row must disappear, not
        // borrow whatever tier happens to sit at that position now.
        val model = BookingDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookingId" to "b-1")),
            bookingsRepository = FakeBookingsRepository(
                listOf(booking().copy(packageIndex = 7)),
            ),
            artistsRepository = FakeArtistsRepository(
                listOf(artist(packages = listOf(pkg("p-1", name = "Evening set", price = 20_000)))),
            ),
            reviewsRepository = FakeReviewsRepository(),
        )

        assertNull(model.packageName())
    }

    @Test
    fun packageName_isNullWhenTheArtistCouldNotBeFetched() = runTest {
        val model = vm(FakeBookingsRepository(listOf(booking(artistId = OTHER_ARTIST_ID))))

        assertNull(model.packageName())
    }

    @Test
    fun packageName_survivesTheArtistReorderingTheirPackages() = runTest {
        // The gig was booked as "Evening set" at index 0. The artist has since
        // reordered their tiers, so index 0 is now "Acoustic hour" — resolving by
        // index would silently relabel a booking that was already agreed.
        // `bookings.package_name` is the server's snapshot of the tier at insert
        // time and must win.
        val model = BookingDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookingId" to "b-1")),
            bookingsRepository = FakeBookingsRepository(
                listOf(booking().copy(packageIndex = 0, packageName = "Evening set")),
            ),
            artistsRepository = FakeArtistsRepository(
                listOf(
                    artist(
                        packages = listOf(
                            pkg("p-2", name = "Acoustic hour", price = 12_000),
                            pkg("p-1", name = "Evening set", price = 20_000),
                        ),
                    ),
                ),
            ),
            reviewsRepository = FakeReviewsRepository(),
        )

        assertEquals("Evening set", model.packageName())
    }

    @Test
    fun packageName_snapshotWinsEvenWhenTheIndexDangles() = runTest {
        // The artist deleted tiers after the booking. The index points past the
        // end, but the snapshot still names the gig honestly — this is the case
        // that used to drop the Package row entirely.
        val model = BookingDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookingId" to "b-1")),
            bookingsRepository = FakeBookingsRepository(
                listOf(booking().copy(packageIndex = 7, packageName = "Evening set")),
            ),
            artistsRepository = FakeArtistsRepository(
                listOf(artist(packages = listOf(pkg("p-1", name = "Acoustic hour", price = 12_000)))),
            ),
            reviewsRepository = FakeReviewsRepository(),
        )

        assertEquals("Evening set", model.packageName())
    }

    @Test
    fun packageName_fallsBackToTheIndexWhenTheSnapshotIsBlank() = runTest {
        // A projection that didn't request `package_name` decodes to null; the
        // index is still the best available answer rather than dropping the row.
        val model = BookingDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookingId" to "b-1")),
            bookingsRepository = FakeBookingsRepository(
                listOf(booking().copy(packageIndex = 0, packageName = "   ")),
            ),
            artistsRepository = FakeArtistsRepository(
                listOf(artist(packages = listOf(pkg("p-1", name = "Evening set", price = 20_000)))),
            ),
            reviewsRepository = FakeReviewsRepository(),
        )

        assertEquals("Evening set", model.packageName())
    }

    // --- action sets (the screen reads these; the matrix itself is covered in
    //     BookingDetailLogicTest) ---------------------------------------------

    @Test
    fun actions_areEmptyUntilABookingLoads_soNoCtaRendersOverNothing() = runTest {
        val model = vm(FakeBookingsRepository(emptyList()))

        assertTrue(model.actions(BookingViewer.Client).isEmpty())
        assertTrue(model.primaryActions(BookingViewer.Artist).isEmpty())
        assertTrue(model.manageActions(BookingViewer.Artist).isEmpty())
    }

    @Test
    fun actions_trackTheStatusAcrossAnAccept() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking()))
        val model = vm(bookings)
        assertEquals(
            listOf(BookingAction.Accept, BookingAction.Decline),
            model.primaryActions(BookingViewer.Artist),
        )

        model.acceptRequest()

        // Once accepted the artist can message and manage, and can no longer
        // accept a request that is already confirmed.
        assertEquals(listOf(BookingAction.Message), model.primaryActions(BookingViewer.Artist))
        assertTrue(BookingAction.Cancel in model.manageActions(BookingViewer.Artist))
        assertFalse(model.showAcceptDecline(isArtistViewer = true))
    }

    @Test
    fun dismissActionError_clearsTheBannerWithoutTouchingTheBooking() = runTest {
        val model = vm(FakeBookingsRepository(listOf(booking())))
        model.reportActionError("Couldn't open a maps app on this device.")

        model.dismissActionError()

        assertNull(model.state.value.actionError)
        assertEquals(BookingStatus.PendingConfirm, model.state.value.booking?.status)
    }
}
