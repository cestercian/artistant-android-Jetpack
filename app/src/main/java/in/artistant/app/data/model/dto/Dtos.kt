package `in`.artistant.app.data.model.dto

import `in`.artistant.app.common.util.SupabaseISO8601
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.ScoreHistoryPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Postgres wire rows for the Browse read path (ports of the iOS `DB*` structs).
 * EXACT snake_case column names — repositories select these columns explicitly
 * (never `select("*")`) and map INTO the domain models here. String-label /
 * numeric fields carry safe defaults so a minimal projection that omits a column
 * decodes to a benign zero-state rather than throwing.
 */

@Serializable
data class DBArtist(
    val id: String,
    val handle: String,
    val stage_name: String,
    val category: String,
    val genre: String? = null,
    val base_city: String,
    val bio: String? = null,
    val cover_gradient_index: Int = 0,
    val followers_label: String = "",
    val streams_label: String = "",
    val response_label: String = "",
    val on_time_rate: Int = 0,
    val total_gigs: Int = 0,
    val rating: Double = 0.0,
    val score: Int = 0,
    val spotify_artist_url: String? = null,
    val instagram_handle: String? = null,
    val youtube_channel_url: String? = null,
    val days_available: List<String>? = null,
    val default_time_slots: List<String>? = null,
) {
    /**
     * Stitches child rows onto the artist row. Headline price/duration come from
     * the popular package, else the first, else 0/"set" (iOS `DBArtist.toArtist`).
     * `reviews` is empty here — loaded separately via ReviewsRepository.
     */
    fun toArtist(
        packages: List<ArtistPackage>,
        tech: List<String>,
        samples: List<Sample>,
        coverUrl: String?,
    ): Artist {
        val primary = packages.firstOrNull { it.popular } ?: packages.firstOrNull()
        return Artist(
            id = id,
            name = stage_name,
            handle = handle,
            category = category,
            genre = genre ?: "",
            city = base_city,
            price = primary?.price ?: 0,
            duration = primary?.duration ?: "set",
            score = score,
            gradient = ArtistGradient.palette(cover_gradient_index),
            bio = bio ?: "",
            followers = followers_label,
            streams = streams_label,
            response = response_label,
            onTime = on_time_rate,
            gigs = total_gigs,
            rating = rating,
            packages = packages,
            tech = tech,
            samples = samples,
            reviews = emptyList(),
            spotifyArtistUrl = spotify_artist_url,
            instagramHandle = instagram_handle,
            youtubeChannelUrl = youtube_channel_url,
            daysAvailable = days_available ?: emptyList(),
            timeSlots = default_time_slots ?: emptyList(),
            coverUrl = coverUrl,
        )
    }
}

@Serializable
data class DBPackage(
    val artist_id: String,
    val position: Int,
    val name: String,
    val duration_label: String,
    val price_inr: Int,
    val includes: List<String> = emptyList(),
    val popular: Boolean = false,
) {
    fun toPackage() = ArtistPackage(
        name = name,
        duration = duration_label,
        price = price_inr,
        includes = includes,
        popular = popular,
    )
}

@Serializable
data class DBTechItem(
    val artist_id: String,
    val item: String,
)

@Serializable
data class DBSample(
    val artist_id: String,
    val title: String,
    val duration_label: String,
    val spotify_track_url: String? = null,
    val cover_art_url: String? = null,
) {
    fun toSample() = Sample(title = title, duration = duration_label)
}

/** Slimmed `artist_media` projection used to resolve each artist's cover photo. */
@Serializable
data class DBArtistCover(
    val artist_id: String,
    val storage_path: String,
    val position: Int,
)

/**
 * One row of the `search_artists` projection — a TILE-LEVEL subset. Upgraded to a
 * full profile on tap via `ArtistsRepository.fetchArtist`; the tile never reads
 * the placeholder fields.
 */
@Serializable
data class SearchArtistRow(
    val id: String,
    val stage_name: String,
    val handle: String,
    val category: String,
    val genre: String? = null,
    val base_city: String,
    val min_price: Int? = null,   // null when the artist has no packages
    val score: Int = 0,
    val total_gigs: Int = 0,
    val cover_gradient_index: Int = 0,
    val days_available: List<String>? = null,
    val rank: Double = 0.0,       // Postgres `real`; 0 for browse (no text query)
) {
    fun toPartialArtist(coverUrl: String?): Artist = Artist(
        id = id,
        name = stage_name,
        handle = handle,
        category = category,
        genre = genre ?: "",
        city = base_city,
        price = min_price ?: 0,
        duration = "",
        score = score,
        gradient = ArtistGradient.palette(cover_gradient_index),
        bio = "",
        followers = "",
        streams = "",
        response = "",
        onTime = 0,
        gigs = total_gigs,
        rating = 0.0,
        packages = emptyList(),
        tech = emptyList(),
        samples = emptyList(),
        reviews = emptyList(),
        daysAvailable = days_available ?: emptyList(),
        coverUrl = coverUrl,
    )
}

/** One `search_facets` row: a (category|city) label and its published count. */
@Serializable
data class SearchFacetRow(
    val kind: String,
    val label: String,
    val n: Int,
)

/**
 * `public.artists` score + per-metric columns (iOS `DBScoreMetrics`). Column names
 * are the REAL schema names (`metric_show_up` / `metric_cancellations`, NOT the
 * `_rate` variants that don't exist — the Audit-3 P1 lesson).
 */
@Serializable
data class DBScoreMetrics(
    val score: Int = 0,
    val metric_show_up: Int = 0,
    val metric_review_score: Int = 0,
    val metric_reply_speed: Int = 0,
    val metric_cancellations: Int = 0,
    val metric_social_proof: Int = 0,
    val total_gigs: Int = 0,
) {
    fun toDomain(): ScoreBreakdown = ScoreBreakdown.from(
        score = score,
        showUpRate = metric_show_up,
        reviewScore = metric_review_score,
        replySpeed = metric_reply_speed,
        cancellationRate = metric_cancellations,
        socialProof = metric_social_proof,
        totalGigs = total_gigs,
    )
}

/** Wire shape for `public.score_history` rows. */
@Serializable
data class DBScoreHistoryRow(
    val score: Int,
    val computed_at: String,
) {
    fun toDomain(): ScoreHistoryPoint? =
        SupabaseISO8601.parse(computed_at)?.let { ScoreHistoryPoint(score = score, computedAt = it) }
}

/**
 * Read shape for `public.reviews`. Reads the DENORMALIZED `client_name` column
 * (migration 0030), NOT a `client:users` embed — RLS (`users_select_self`) nulls
 * that for every reviewer except the viewer, so an embed rendered a generic
 * "Client" for all reviews.
 */
@Serializable
data class DBReview(
    val id: String,
    val rating: Int,
    val body: String? = null,
    val client_name: String? = null,
    val client_org: String? = null,
    val gig_date: String? = null,
    val created_at: String,
) {
    fun toReview(): Review = Review(
        id = id,
        name = client_name?.takeIf { it.isNotEmpty() } ?: "Client",
        org = client_org ?: "",
        rating = rating,
        body = body ?: "",
        createdAt = SupabaseISO8601.parse(created_at),
    )
}
