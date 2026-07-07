package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review write gates: completed-only + one-per-booking (23505 → AlreadyReviewed). */
class ReviewInsertTest {

    @Test
    fun `first review on a completed booking succeeds`() = runTest {
        val repo = FakeReviewsRepository(completedBookingIds = setOf("b1"))
        val review = repo.insert("b1", rating = 5, body = "Incredible set")
        assertEquals(5, review.rating)
        assertEquals("Incredible set", review.body)
    }

    @Test
    fun `a second review on the same booking is rejected as AlreadyReviewed`() = runTest {
        val repo = FakeReviewsRepository(completedBookingIds = setOf("b1"))
        repo.insert("b1", 4, null)
        val error = runCatching { repo.insert("b1", 3, "changed my mind") }.exceptionOrNull()
        assertTrue(error is ReviewError.AlreadyReviewed)
    }

    @Test
    fun `reviewing a non-completed booking is rejected`() = runTest {
        val repo = FakeReviewsRepository(completedBookingIds = emptySet())
        val error = runCatching { repo.insert("b1", 5, null) }.exceptionOrNull()
        assertTrue(error is ReviewError.BookingNotCompleted)
    }

    @Test
    fun `rating outside 1 to 5 is rejected`() = runTest {
        val repo = FakeReviewsRepository(completedBookingIds = setOf("b1"))
        val error = runCatching { repo.insert("b1", 6, null) }.exceptionOrNull()
        assertTrue(error is ReviewError.InvalidRating)
    }
}
