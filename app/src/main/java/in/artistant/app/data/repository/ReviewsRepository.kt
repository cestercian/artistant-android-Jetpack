package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.data.model.Review
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** Reader-side reviews for an artist profile — port of iOS `ReviewsRepository.listForArtist`. */
interface ReviewsRepository {
    suspend fun listForArtist(artistId: String): List<Review>
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
}

class FakeReviewsRepository(
    private val byArtist: Map<String, List<Review>> = emptyMap(),
) : ReviewsRepository {
    override suspend fun listForArtist(artistId: String): List<Review> =
        byArtist[artistId.lowercase()].orEmpty()
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
