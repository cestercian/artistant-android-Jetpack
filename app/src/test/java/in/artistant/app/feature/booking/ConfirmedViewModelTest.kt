package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.artist
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The receipt screen — and the one thing it must never do.
 *
 * The booking already exists by the time this ViewModel runs: the only route
 * here is a `create` that came back with an id. So the read in `init` is
 * decoration (artist name, date), and every way it can fail has to end with the
 * client still being told their request went through.
 */
class ConfirmedViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        bookings: BookingsRepository,
        bookingId: String = "b-1",
    ) = ConfirmedViewModel(
        savedStateHandle = SavedStateHandle(mapOf("bookingId" to bookingId)),
        bookingsRepository = bookings,
        artistsRepository = FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
    )

    @Test
    fun load_namesTheArtistAndTheDate() = runTest {
        val s = vm(FakeBookingsRepository(listOf(booking()))).state.value

        assertEquals("Nova Beats", s.artistName)
        // The whole row is held now, not a date string: screen 07 renders the
        // terms card off it (when / set / where / agreed fee).
        assertEquals("Sat, May 16, 2026", s.booking?.date)
        assertEquals(BookingStatus.PendingConfirm, s.status)
    }

    @Test
    fun load_carriesTheBookingsOwnStatus_ratherThanAssumingPendingConfirm() = runTest {
        // Also what keeps the two degrade tests below honest: pending_confirm is
        // a fallback the failure paths land on, not a value this screen hardcodes.
        val s = vm(FakeBookingsRepository(listOf(booking(status = BookingStatus.Confirmed)))).state.value

        assertEquals(BookingStatus.Confirmed, s.status)
    }

    @Test
    fun missingBooking_stillReadsAsRequestSent() = runTest {
        val s = vm(FakeBookingsRepository(emptyList())).state.value

        assertEquals("", s.artistName)
        assertNull(s.booking)
        assertEquals(BookingStatus.PendingConfirm, s.status)
    }

    /**
     * A dropped connection here used to kill the process.
     *
     * `fetchOne` rethrows every transport failure as
     * [BookingRepositoryError.Underlying] — it returns null only for a genuine
     * zero-row read — and `viewModelScope` carries no CoroutineExceptionHandler,
     * so the unguarded throw in `init` reached the thread's uncaught handler.
     *
     * That escape is what this test catches: `runTest` fails on exceptions that
     * surface out of coroutines it didn't launch. The state assertions can't
     * catch it on their own, and shouldn't — a failed decoration is *supposed*
     * to leave the reassuring defaults exactly where they started.
     */
    @Test
    fun fetchFailure_leavesTheRequestSentDefaultsStanding_insteadOfCrashing() = runTest {
        val bookings = ThrowingFetch()

        val s = vm(bookings).state.value

        assertEquals("the failing read has to actually run", 1, bookings.fetchCalls)
        assertEquals("", s.artistName)
        assertNull(s.booking)
        assertEquals(BookingStatus.PendingConfirm, s.status)
    }

    /**
     * A repository whose single read is down.
     *
     * [FakeBookingsRepository.fetchOne] can only return null; null and "the
     * request died in flight" are different states and only the second one was
     * ever fatal. Everything else delegates, so the double stays honest about
     * the rest of the seam.
     */
    private class ThrowingFetch(
        delegate: FakeBookingsRepository = FakeBookingsRepository(),
    ) : BookingsRepository by delegate {
        var fetchCalls = 0
            private set

        override suspend fun fetchOne(id: String): Booking? {
            fetchCalls++
            throw BookingRepositoryError.Underlying(IOException("connection reset"))
        }
    }
}
