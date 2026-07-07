package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.dto.DBReview
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read + write surface for `public.reviews` (iOS `ReviewsRepository`). Clients
 * insert one review per COMPLETED booking; the artist profile reads the full list.
 *
 * Reads the DENORMALIZED `client_name` column (migration 0030), NOT a `client:users`
 * embed: RLS (`users_select_self`) nulls the embed for every reviewer except the
 * viewer, so an embed would render a generic "Client" for all reviews.
 */
interface ReviewsRepository {
    /** All reviews for the artist, newest-first. `[]` (not an error) when none. */
    suspend fun listForArtist(artistId: String): List<Review>

    /**
     * Insert a review for the signed-in client against a completed booking. The
     * artist_id is resolved from the booking row, so the caller supplies only the
     * booking. Throws [ReviewError] (invalid rating / not signed in / booking not
     * found / not completed / already reviewed).
     */
    suspend fun insert(bookingId: String, rating: Int, body: String?): Review
}

/** Typed review-write failures (iOS `ReviewRepositoryError`). */
sealed class ReviewError(message: String) : Exception(message) {
    data object NotSignedIn : ReviewError("Sign in to leave a review.")
    data object InvalidRating : ReviewError("Rating must be 1–5.")
    data object BookingNotFound : ReviewError("Booking not found or not yours.")
    data object BookingNotCompleted : ReviewError("You can only review a completed booking.")
    data object AlreadyReviewed : ReviewError("You already reviewed this booking.")
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

    override suspend fun insert(bookingId: String, rating: Int, body: String?): Review {
        if (rating !in 1..5) throw ReviewError.InvalidRating
        val clientId = client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid()
            ?: throw ReviewError.NotSignedIn

        // Resolve the booking's artist + gig date + status in one round trip. A
        // zero-row result (missing OR RLS-hidden) is PGRST116 → BookingNotFound.
        val booking = try {
            client.postgrest.from("bookings")
                .select(Columns.list("artist_id", "start_datetime", "status")) {
                    filter { eq("id", bookingId.lowercaseUuid()) }
                    single()
                }
                .decodeSingle<BookingLookup>()
        } catch (t: Throwable) {
            if (isNotFound(t)) throw ReviewError.BookingNotFound else throw t
        }

        // Server-side gate (iOS PR #62): the "Leave a review" CTA only shows for
        // completed bookings, but a push-driven sheet can reach here directly, so
        // re-check the fetched status. Defense in depth against a forged/early write.
        if (booking.status != "completed") throw ReviewError.BookingNotCompleted

        val gigDate = booking.start_datetime?.substringBefore("T")
        val row = ReviewInsert(
            booking_id = bookingId.lowercaseUuid(),
            artist_id = booking.artist_id,
            client_id = clientId,
            rating = rating,
            body = body?.trim()?.takeIf { it.isNotEmpty() },
            client_org = null,
            gig_date = gigDate,
        )

        return try {
            client.postgrest.from("reviews")
                .insert(row) { select(REVIEW_COLUMNS) }
                .decodeSingle<DBReview>()
                .toReview()
        } catch (t: Throwable) {
            // The unique index on reviews.booking_id → 23505 when re-reviewing.
            val msg = t.message.orEmpty().lowercase()
            if ("23505" in msg || "unique_violation" in msg) throw ReviewError.AlreadyReviewed
            throw t
        }
    }

    private fun isNotFound(t: Throwable): Boolean {
        val m = t.message.orEmpty().lowercase()
        return "pgrst116" in m || "no rows" in m
    }

    @Serializable
    private data class BookingLookup(
        val artist_id: String,
        val start_datetime: String? = null,
        val status: String,
    )

    @Serializable
    private data class ReviewInsert(
        val booking_id: String,
        val artist_id: String,
        val client_id: String,
        val rating: Int,
        val body: String?,
        val client_org: String?,
        val gig_date: String?,
    )

    companion object {
        private val REVIEW_COLUMNS = Columns.list(
            "id", "rating", "body", "client_name", "client_org", "gig_date", "created_at",
        )
    }
}
