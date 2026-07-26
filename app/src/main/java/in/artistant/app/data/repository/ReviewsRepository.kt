package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.data.model.Review
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

sealed class ReviewRepositoryError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object NotSignedIn : ReviewRepositoryError("Sign in to leave a review.")
    data object BookingNotFound : ReviewRepositoryError("Booking not found.")
    data object AlreadyReviewed : ReviewRepositoryError("You already reviewed this booking.")
    data object InvalidRating : ReviewRepositoryError("Rating must be between 1 and 5.")
    data object BookingNotCompleted : ReviewRepositoryError("Reviews are only allowed after the show is completed.")
    class Underlying(cause: Throwable) : ReviewRepositoryError(cause.message ?: "Review failed", cause)
}

/** Reviews seam — port of iOS `ReviewsRepository`. */
interface ReviewsRepository {
    suspend fun listForArtist(artistId: String): List<Review>

    suspend fun insert(
        bookingId: String,
        rating: Int,
        body: String?,
        categories: Map<String, Int>?,
    ): Review
}

@Singleton
class SupabaseReviewsRepository @Inject constructor(
    private val client: SupabaseClient,
) : ReviewsRepository {
    override suspend fun listForArtist(artistId: String): List<Review> {
        val rows = client.from("reviews")
            .select(
                Columns.list(
                    "id", "client_name", "client_org", "rating", "body", "created_at",
                ),
            ) {
                filter { eq("artist_id", artistId.lowercase()) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<ReviewRow>()
        return rows.map { it.toReview() }
    }

    override suspend fun insert(
        bookingId: String,
        rating: Int,
        body: String?,
        categories: Map<String, Int>?,
    ): Review {
        if (rating !in 1..5) throw ReviewRepositoryError.InvalidRating
        val clientId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw ReviewRepositoryError.NotSignedIn

        val booking = try {
            client.from("bookings")
                .select(Columns.list("artist_id", "start_datetime", "status")) {
                    filter { eq("id", bookingId.lowercase()) }
                    limit(1)
                }
                .decodeList<BookingLookup>()
                .firstOrNull()
        } catch (t: Throwable) {
            throw ReviewRepositoryError.Underlying(t)
        } ?: throw ReviewRepositoryError.BookingNotFound

        if (booking.status != "completed") {
            throw ReviewRepositoryError.BookingNotCompleted
        }

        val gigDate = booking.startDatetime?.substringBefore("T")
        val trimmedBody = body?.trim()?.takeIf { it.isNotEmpty() }
        val categoriesPayload = categories?.takeIf { it.isNotEmpty() }

        val row = ReviewInsert(
            bookingId = bookingId.lowercase(),
            artistId = booking.artistId.lowercase(),
            clientId = clientId,
            rating = rating,
            body = trimmedBody,
            clientOrg = null,
            gigDate = gigDate,
            categories = categoriesPayload,
        )

        return try {
            client.from("reviews")
                .insert(row) { select() }
                .decodeSingle<ReviewInsertRow>()
                .toReview(displayName = "You", org = "")
        } catch (t: Throwable) {
            val msg = t.message.orEmpty()
            if (msg.contains("23505") || msg.contains("unique", ignoreCase = true)) {
                throw ReviewRepositoryError.AlreadyReviewed
            }
            // Pre-0073: retry without categories column.
            if (categoriesPayload != null && msg.contains("42703")) {
                return try {
                    client.from("reviews")
                        .insert(row.copy(categories = null)) { select() }
                        .decodeSingle<ReviewInsertRow>()
                        .toReview(displayName = "You", org = "")
                } catch (inner: Throwable) {
                    throw ReviewRepositoryError.Underlying(inner)
                }
            }
            throw ReviewRepositoryError.Underlying(t)
        }
    }
}

class FakeReviewsRepository(
    private val byArtist: Map<String, List<Review>> = emptyMap(),
    private val bookings: Map<String, FakeBookingMeta> = emptyMap(),
) : ReviewsRepository {
    private val inserted = mutableListOf<Review>()

    data class FakeBookingMeta(
        val artistId: String,
        val status: String = "completed",
        val startDatetime: String? = null,
    )

    override suspend fun listForArtist(artistId: String): List<Review> =
        byArtist[artistId.lowercase()].orEmpty() + inserted.filter { true }

    override suspend fun insert(
        bookingId: String,
        rating: Int,
        body: String?,
        categories: Map<String, Int>?,
    ): Review {
        if (rating !in 1..5) throw ReviewRepositoryError.InvalidRating
        val meta = bookings[bookingId.lowercase()] ?: throw ReviewRepositoryError.BookingNotFound
        if (meta.status != "completed") throw ReviewRepositoryError.BookingNotCompleted
        val review = Review(
            id = "fake-review-${inserted.size}",
            name = "You",
            org = "",
            rating = rating,
            body = body.orEmpty(),
            categories = categories,
        )
        inserted.add(review)
        return review
    }
}

@Serializable
private data class BookingLookup(
    @SerialName("artist_id") val artistId: String,
    @SerialName("start_datetime") val startDatetime: String? = null,
    val status: String = "",
)

@Serializable
private data class ReviewInsert(
    @SerialName("booking_id") val bookingId: String,
    @SerialName("artist_id") val artistId: String,
    @SerialName("client_id") val clientId: String,
    val rating: Int,
    val body: String? = null,
    @SerialName("client_org") val clientOrg: String? = null,
    @SerialName("gig_date") val gigDate: String? = null,
    val categories: Map<String, Int>? = null,
)

@Serializable
private data class ReviewInsertRow(
    val id: String,
    val rating: Int = 0,
    val body: String? = null,
    @SerialName("client_org") val clientOrg: String? = null,
    @SerialName("gig_date") val gigDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toReview(displayName: String, org: String) = Review(
        id = id,
        name = displayName,
        org = org,
        rating = rating,
        body = body.orEmpty(),
        createdAt = createdAt,
    )
}

@Serializable
private data class ReviewRow(
    val id: String,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_org") val clientOrg: String? = null,
    val rating: Int = 0,
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toReview() = Review(
        id = id,
        name = clientName.orEmpty().ifBlank { "Client" },
        org = clientOrg.orEmpty(),
        rating = rating,
        body = body,
        createdAt = createdAt,
    )
}
