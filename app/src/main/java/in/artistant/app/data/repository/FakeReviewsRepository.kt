package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Review
import java.time.Instant
import java.util.UUID

/**
 * In-memory [ReviewsRepository] — seeded reads keyed by artist id, plus a write
 * path that honours the same gates as the real repo: [completedBookingIds] gates
 * insert (not in the set → BookingNotCompleted), and a second insert for the same
 * booking throws [ReviewError.AlreadyReviewed] (the unique-on-booking constraint).
 */
class FakeReviewsRepository(
    private val byArtist: Map<String, List<Review>> = emptyMap(),
    private val completedBookingIds: Set<String> = emptySet(),
) : ReviewsRepository {

    private val reviewedBookingIds = mutableSetOf<String>()

    override suspend fun listForArtist(artistId: String): List<Review> =
        byArtist[artistId].orEmpty()

    override suspend fun insert(bookingId: String, rating: Int, body: String?): Review {
        if (rating !in 1..5) throw ReviewError.InvalidRating
        if (bookingId !in completedBookingIds) throw ReviewError.BookingNotCompleted
        if (!reviewedBookingIds.add(bookingId)) throw ReviewError.AlreadyReviewed
        return Review(
            id = UUID.randomUUID().toString(),
            name = "You",
            org = "",
            rating = rating,
            body = body?.trim().orEmpty(),
            createdAt = Instant.now(),
        )
    }
}
