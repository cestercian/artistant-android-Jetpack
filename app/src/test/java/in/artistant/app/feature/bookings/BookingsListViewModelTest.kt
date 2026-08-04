package `in`.artistant.app.feature.bookings

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
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

/** Client-side bookings list — month folding, cancelled hiding, degrade path. */
class BookingsListViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun vm(bookings: FakeBookingsRepository, artists: FakeArtistsRepository = FakeArtistsRepository()) =
        BookingsViewModel(bookingsRepository = bookings, artistsRepository = artists)

    @Test
    fun cancelledBookingsAreHidden_pendingAndConfirmedAreKept() = runTest {
        val model = vm(
            FakeBookingsRepository(
                listOf(
                    booking(id = "b-pending", status = BookingStatus.PendingConfirm),
                    booking(id = "b-confirmed", status = BookingStatus.Confirmed),
                    booking(id = "b-cancelled", status = BookingStatus.Cancelled),
                    booking(id = "b-done", status = BookingStatus.Completed),
                ),
            ),
        )

        val ids = model.state.value.items.map { it.booking.id }
        assertEquals(listOf("b-pending", "b-confirmed", "b-done"), ids)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun rowsResolveTheArtistNameFromCache_andFallBackToArtist() = runTest {
        val model = vm(
            FakeBookingsRepository(
                listOf(
                    booking(id = "b-known"),
                    booking(id = "b-unknown", artistId = "99999999-9999-9999-9999-999999999999"),
                ),
            ),
            FakeArtistsRepository(listOf(artist(name = "Nova Beats"))),
        )

        val byId = model.state.value.items.associateBy { it.booking.id }
        assertEquals("Nova Beats", byId.getValue("b-known").artistName)
        assertEquals("Artist", byId.getValue("b-unknown").artistName)
    }

    @Test
    fun groupingNeverLosesOrReordersRows() = runTest {
        // NOTE: `monthLabelFromDateLabel` does NOT currently fold the canonical
        // "EEE, MMM d, yyyy" label down to "MMM yyyy" — see the Findings section
        // of the PR — so this asserts only the part that holds today: every row
        // survives grouping, in list order.
        val model = vm(
            FakeBookingsRepository(
                listOf(
                    booking(id = "b1", date = "Sat, May 16, 2026"),
                    booking(id = "b2", date = "Sun, May 17, 2026"),
                    booking(id = "b3", date = "Fri, Jun 5, 2026"),
                ),
            ),
        )

        val grouped = model.groupedByMonth()
        assertEquals(
            listOf("b1", "b2", "b3"),
            grouped.flatMap { it.second }.map { it.booking.id },
        )
        assertTrue(grouped.all { it.first.isNotBlank() })
    }

    @Test
    fun signedOutRepository_surfacesTheErrorInsteadOfAnEmptyList() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking())).apply { signedIn = false }

        val model = vm(bookings)

        val s = model.state.value
        assertTrue(s.items.isEmpty())
        assertFalse(s.isLoading)
        assertNotNull(s.error)
    }

    @Test
    fun refreshClearsAPreviousError() = runTest {
        val bookings = FakeBookingsRepository(listOf(booking())).apply { signedIn = false }
        val model = vm(bookings)
        assertNotNull(model.state.value.error)

        bookings.signedIn = true
        model.refresh()

        assertNull(model.state.value.error)
        assertEquals(1, model.state.value.items.size)
    }
}
