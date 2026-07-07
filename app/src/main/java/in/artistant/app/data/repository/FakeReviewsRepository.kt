package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Review

/** In-memory [ReviewsRepository] — returns seeded reviews keyed by artist id. */
class FakeReviewsRepository(
    private val byArtist: Map<String, List<Review>> = emptyMap(),
) : ReviewsRepository {
    override suspend fun listForArtist(artistId: String): List<Review> =
        byArtist[artistId].orEmpty()
}
