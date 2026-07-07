package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.dto.DBReview
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read surface for `public.reviews` (iOS `ReviewsRepository.listForArtist`). The
 * review WRITE loop is part of the booking flow (M4+), not Browse — omitted here.
 *
 * Reads the DENORMALIZED `client_name` column (migration 0030), NOT a `client:users`
 * embed: RLS (`users_select_self`) nulls the embed for every reviewer except the
 * viewer, so an embed would render a generic "Client" for all reviews.
 */
interface ReviewsRepository {
    /** All reviews for the artist, newest-first. `[]` (not an error) when none. */
    suspend fun listForArtist(artistId: String): List<Review>
}

@Singleton
class SupabaseReviewsRepository @Inject constructor(
    private val client: SupabaseClient,
) : ReviewsRepository {

    override suspend fun listForArtist(artistId: String): List<Review> =
        client.postgrest.from("reviews")
            .select(REVIEW_COLUMNS) {
                filter { eq("artist_id", artistId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<DBReview>()
            .map { it.toReview() }

    companion object {
        private val REVIEW_COLUMNS = Columns.list(
            "id", "rating", "body", "client_name", "client_org", "gig_date", "created_at",
        )
    }
}
