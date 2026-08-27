package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.FakeReviewsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The review write, now that it has a ViewModel.
 *
 * It used to run on the sheet's `rememberCoroutineScope()`, so a scrim tap or a
 * downward drag during a slow insert removed `ReviewSheet` from composition and
 * cancelled the request — silently, since the catch never fired and the state
 * writes landed in a dead composition. The client believed they had reviewed the
 * artist and nothing had been persisted.
 *
 * A JVM suite can't dismiss a sheet, so what these pin is the shape that fixes
 * it: the submit, its in-flight flag, its error and its one-shot completion all
 * belong to the ViewModel, whose scope is the booking screen's — not the sheet's.
 */
class ReviewSheetViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val bookingId = "b-1"

    private fun reviews(status: String = "completed") = FakeReviewsRepository(
        bookings = mapOf(
            bookingId to FakeReviewsRepository.FakeBookingMeta(
                artistId = ARTIST_ID,
                status = status,
            ),
        ),
    )

    @Test
    fun submit_persistsTheReviewAndRaisesTheOneShot() = runTest {
        val repo = reviews()
        val vm = ReviewSheetViewModel(repo)

        vm.setRating(4)
        vm.setBody("Packed the room.")
        vm.submit(bookingId)

        val stored = repo.listForArtist(ARTIST_ID).single()
        assertEquals(4, stored.rating)
        assertEquals("Packed the room.", stored.body)
        assertTrue(vm.state.value.submitted)
        assertFalse(vm.state.value.isSubmitting)
        assertNull(vm.state.value.error)
    }

    @Test
    fun submit_sendsABlankBodyAsNoBodyAtAll() = runTest {
        val repo = reviews()
        val vm = ReviewSheetViewModel(repo)

        vm.setBody("   ")
        vm.submit(bookingId)

        // The column is nullable and a whitespace review is not a review.
        assertEquals("", repo.listForArtist(ARTIST_ID).single().body)
    }

    @Test
    fun submit_clearsTheFormOnSuccess_soReopeningTheSheetStartsFresh() = runTest {
        // This VM is scoped to the booking screen, not to the sheet, so it
        // outlives a dismissal — leaving the submitted stars and text behind
        // would re-offer a review that has already been filed.
        val vm = ReviewSheetViewModel(reviews())

        vm.setRating(2)
        vm.setBody("Ran short.")
        vm.submit(bookingId)
        vm.consumeSubmitted()

        assertEquals(5, vm.state.value.rating)
        assertEquals("", vm.state.value.body)
        assertFalse(vm.state.value.submitted)
    }

    @Test
    fun submit_isIgnoredWhileOneIsAlreadyInFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = GatedReviews(reviews(), gate)
        val vm = ReviewSheetViewModel(repo)

        vm.submit(bookingId)
        assertTrue("the first write is in flight", vm.state.value.isSubmitting)
        vm.submit(bookingId)
        gate.complete(Unit)

        assertEquals("a second tap must not file a second review", 1, repo.attempts)
        assertTrue(vm.state.value.submitted)
    }

    @Test
    fun submit_surfacesTheTypedRepositoryError_andKeepsWhatWasTyped() = runTest {
        val vm = ReviewSheetViewModel(reviews(status = "confirmed"))

        vm.setBody("Great night.")
        vm.submit(bookingId)

        assertEquals(
            "Reviews are only allowed after the show is completed.",
            vm.state.value.error,
        )
        assertFalse(vm.state.value.submitted)
        assertFalse(vm.state.value.isSubmitting)
        // Nothing was consumed, so a retry has the same review to send.
        assertEquals("Great night.", vm.state.value.body)
    }

    @Test
    fun submit_reportsAnUnknownBooking_ratherThanFailingSilently() = runTest {
        val vm = ReviewSheetViewModel(reviews())

        vm.submit("no-such-booking")

        assertEquals("Booking not found.", vm.state.value.error)
        assertFalse(vm.state.value.submitted)
    }

    @Test
    fun setRating_clampsToTheFiveStarRange() = runTest {
        val vm = ReviewSheetViewModel(reviews())

        vm.setRating(0)
        assertEquals(1, vm.state.value.rating)
        vm.setRating(9)
        assertEquals(5, vm.state.value.rating)
    }

    /** Holds the insert open so a second submit can be attempted against it. */
    private class GatedReviews(
        private val delegate: FakeReviewsRepository,
        private val gate: CompletableDeferred<Unit>,
    ) : ReviewsRepository by delegate {
        var attempts = 0
            private set

        override suspend fun insert(
            bookingId: String,
            rating: Int,
            body: String?,
            categories: Map<String, Int>?,
        ): Review {
            attempts++
            gate.await()
            return delegate.insert(bookingId, rating, body, categories)
        }
    }
}
