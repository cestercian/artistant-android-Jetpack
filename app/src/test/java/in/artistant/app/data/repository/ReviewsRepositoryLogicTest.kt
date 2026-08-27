package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `FakeReviewsRepository.listForArtist` used to leak every inserted review
 * into every artist's list via a no-op `inserted.filter { true }`, and could
 * never raise `AlreadyReviewed` — the one-review-per-booking rule the real
 * insert derives from a 23505. Both pinned here.
 */
class ReviewsRepositoryLogicTest {

    private val artistA = "11111111-1111-1111-1111-111111111111"
    private val artistB = "22222222-2222-2222-2222-222222222222"

    private fun repoWith(vararg bookings: Pair<String, FakeReviewsRepository.FakeBookingMeta>) =
        FakeReviewsRepository(bookings = bookings.toMap())

    @Test
    fun insert_landsOnlyOnItsOwnArtistsList() = runTest {
        val repo = repoWith(
            "booking-a" to FakeReviewsRepository.FakeBookingMeta(artistId = artistA),
            "booking-b" to FakeReviewsRepository.FakeBookingMeta(artistId = artistB),
        )

        repo.insert("booking-a", rating = 5, body = "Great set", categories = null)

        assertEquals(1, repo.listForArtist(artistA).size)
        assertTrue("artist B's list must not see artist A's review", repo.listForArtist(artistB).isEmpty())
    }

    @Test
    fun insert_aSecondTimeForTheSameBooking_throwsAlreadyReviewed() = runTest {
        val repo = repoWith("booking-a" to FakeReviewsRepository.FakeBookingMeta(artistId = artistA))
        repo.insert("booking-a", rating = 5, body = null, categories = null)

        val err = runCatching {
            repo.insert("booking-a", rating = 4, body = null, categories = null)
        }.exceptionOrNull()

        assertTrue("expected AlreadyReviewed, got $err", err is ReviewRepositoryError.AlreadyReviewed)
        // The first review is still the only one on file — a rejected second
        // insert must not itself land as a duplicate.
        assertEquals(1, repo.listForArtist(artistA).size)
    }
}
