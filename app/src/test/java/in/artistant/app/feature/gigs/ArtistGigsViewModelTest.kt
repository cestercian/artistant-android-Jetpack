package `in`.artistant.app.feature.gigs

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import `in`.artistant.app.testsupport.booking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Artist-side gig list. The name column is the interesting bit: the artist must
 * see the CLIENT, and when the embed is nulled by RLS (pre-0080 rows) the label
 * has to degrade to "Client" — never to the venue, which is where the old "TBD"
 * artist-side bug came from.
 */
class ArtistGigsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rowsShowTheClientName() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(listOf(booking(clientFullName = "Asha Rao"))),
        )

        assertEquals("Asha Rao", model.state.value.items.single().clientName)
    }

    @Test
    fun missingClientName_degradesToClient_notToTheVenue() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(listOf(booking(clientFullName = null, venue = "TBD"))),
        )

        assertEquals("Client", model.state.value.items.single().clientName)
    }

    @Test
    fun blankClientName_isTreatedAsMissing() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(listOf(booking(clientFullName = "   "))),
        )

        assertEquals("Client", model.state.value.items.single().clientName)
    }

    @Test
    fun cancelledGigsAreHidden_pendingRequestsAreNot() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(
                listOf(
                    booking(id = "g1", status = BookingStatus.PendingConfirm),
                    booking(id = "g2", status = BookingStatus.Confirmed),
                    booking(id = "g3", status = BookingStatus.Cancelled),
                ),
            ),
        )

        assertEquals(listOf("g1", "g2"), model.state.value.items.map { it.booking.id })
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun signedOut_surfacesAnError() = runTest {
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(listOf(booking())).apply { signedIn = false },
        )

        assertTrue(model.state.value.items.isEmpty())
        assertNotNull(model.state.value.error)
    }

    @Test
    fun groupingNeverLosesRows() = runTest {
        // See the Findings section of the PR: `monthLabelFromDateLabel` does not
        // currently reduce the canonical "EEE, MMM d, yyyy" label to "MMM yyyy",
        // so only the row-preservation half of the contract is asserted here.
        val model = ArtistGigsViewModel(
            FakeBookingsRepository(
                listOf(
                    booking(id = "g1", date = "Sat, May 16, 2026"),
                    booking(id = "g2", date = "Fri, Jun 5, 2026"),
                ),
            ),
        )

        assertEquals(
            listOf("g1", "g2"),
            model.groupedByMonth().flatMap { it.second }.map { it.booking.id },
        )
    }
}
