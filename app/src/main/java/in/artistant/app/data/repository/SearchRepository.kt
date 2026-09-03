package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacet
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.model.SearchTuning
import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.designsystem.theme.ArtistGradient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server search — port of iOS `SearchRepository`. Calls `search_artists` /
 * `search_facets`; resolves covers with a second batched `artist_media` query;
 * feeds partials into [ArtistsRepository.cache].
 */
interface SearchRepository {
    suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage

    /**
     * Extended search with migration-0073 dims (`p_services` / `p_date` /
     * `p_flex_days`). Default impl ignores the extras so Fake twins stay simple.
     */
    suspend fun search(
        filters: SearchFilters,
        cursor: SearchCursor,
        services: List<String>?,
        date: String?,
        flexDays: Int?,
    ): SearchPage = search(filters, cursor)

    suspend fun facets(): SearchFacets

    /** `price_histogram` RPC — 16 buckets; empty/fail → empty list (legacy sliders). */
    suspend fun priceHistogram(city: String?): List<PriceBucket> = emptyList()
}

@Singleton
class SupabaseSearchRepository @Inject constructor(
    private val client: SupabaseClient,
    private val artistsRepository: ArtistsRepository,
) : SearchRepository {

    override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage =
        search(filters, cursor, services = null, date = null, flexDays = null)

    override suspend fun search(
        filters: SearchFilters,
        cursor: SearchCursor,
        services: List<String>?,
        date: String?,
        flexDays: Int?,
    ): SearchPage {
        val limit = SearchTuning.PAGE_LIMIT
        val hasQuery = filters.hasTextQuery
        val q = filters.text.trim()

        var afterScore: Int? = null
        var afterId: String? = null
        var offset = 0
        if (hasQuery) {
            if (cursor is SearchCursor.Offset) offset = cursor.offset
        } else {
            when (filters.sort) {
                SearchSort.Bookability ->
                    if (cursor is SearchCursor.Keyset) {
                        afterScore = cursor.afterScore
                        afterId = cursor.afterId
                    }
                SearchSort.Price, SearchSort.New ->
                    if (cursor is SearchCursor.Offset) offset = cursor.offset
            }
        }

        val params = buildJsonObject {
            put("p_q", if (hasQuery) JsonPrimitive(q) else JsonNull)
            put("p_city", filters.city?.let { JsonPrimitive(it) } ?: JsonNull)
            if (filters.categories.isEmpty()) {
                put("p_categories", JsonNull)
            } else {
                putJsonArray("p_categories") {
                    filters.categories.forEach { add(JsonPrimitive(it)) }
                }
            }
            put("p_min_price", filters.minPrice?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_max_price", filters.maxPrice?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_min_score", filters.minScore?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_event_type", filters.eventType?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_sort", filters.sort.rpcValue)
            put("p_limit", limit)
            put("p_offset", offset)
            put("p_after_score", afterScore?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_after_id", afterId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_after_price", JsonNull)
            // 0073 dims — omit-as-null keeps the payload compatible with older
            // servers when all are unset; when set, executeSearch retries once
            // without them on a missing-function error.
            if (services.isNullOrEmpty()) {
                put("p_services", JsonNull)
            } else {
                putJsonArray("p_services") { services.forEach { add(JsonPrimitive(it)) } }
            }
            put("p_date", date?.let { JsonPrimitive(it) } ?: JsonNull)
            put(
                "p_flex_days",
                if (date == null) JsonNull else (flexDays?.let { JsonPrimitive(it) } ?: JsonNull),
            )
        }

        val hasNewDims = !services.isNullOrEmpty() || date != null
        val rows = executeSearch(params, hasNewDims)
        val covers = resolveCovers(rows.map { it.id })
        val artists = rows.map { it.toPartialArtist(covers[it.id.lowercase()]) }
        artistsRepository.cache(artists)

        val next = nextCursor(rows, limit, hasQuery, filters.sort, offset)
        return SearchPage(artists = artists, nextCursor = next)
    }

    override suspend fun facets(): SearchFacets {
        val rows: List<SearchFacetRow> = client.postgrest.rpc("search_facets").decodeList()
        val categories = rows.filter { it.kind == "category" }
            .map { SearchFacet(label = it.label, count = it.n) }
        val cities = rows.filter { it.kind == "city" }
            .map { SearchFacet(label = it.label, count = it.n) }
        return SearchFacets(categories = categories, cities = cities)
    }

    override suspend fun priceHistogram(city: String?): List<PriceBucket> {
        val params = buildJsonObject {
            put("p_city", city?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        return try {
            client.postgrest.rpc("price_histogram", params)
                .decodeList<PriceBucketRow>()
                .map { PriceBucket(it.bucketMin, it.bucketMax, it.n) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun executeSearch(
        params: kotlinx.serialization.json.JsonObject,
        hasNewDims: Boolean,
    ): List<SearchArtistRow> {
        return try {
            client.postgrest.rpc("search_artists", params).decodeList()
        } catch (t: Throwable) {
            if (!hasNewDims || !isMissingFunction(t)) throw t
            // Retry without 0073 dims — same as pre-0073 payload.
            val base = buildJsonObject {
                params.forEach { (k, v) ->
                    when (k) {
                        "p_services", "p_date", "p_flex_days" -> put(k, JsonNull)
                        else -> put(k, v)
                    }
                }
            }
            client.postgrest.rpc("search_artists", base).decodeList()
        }
    }

    private suspend fun resolveCovers(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val rows = client.from("artist_media")
            .select(SupabaseArtistsRepository.ARTIST_PHOTO_COLUMNS) {
                filter {
                    isIn("artist_id", ids)
                    eq("kind", "photo")
                }
                order("artist_id", Order.ASCENDING)
                order("position", Order.ASCENDING)
            }
            .decodeList<DbArtistPhoto>()
        return rows.groupBy { it.artistId.lowercase() }
            .mapNotNull { (id, group) ->
                // Same cover rule as the profile stitch, from the same function:
                // a tile and the page it opens showing different photos of the
                // same artist is exactly the drift a shared split prevents.
                val url = SupabaseArtistsRepository.artistPhotos(group).coverUrl
                    ?: return@mapNotNull null
                id to url
            }
            .toMap()
    }

    companion object {
        fun nextCursor(
            rows: List<SearchArtistRow>,
            limit: Int,
            hasQuery: Boolean,
            sort: SearchSort,
            offset: Int,
        ): SearchCursor {
            if (rows.size < limit) return SearchCursor.End
            val last = rows.lastOrNull() ?: return SearchCursor.End
            if (hasQuery) return SearchCursor.Offset(offset + limit)
            return when (sort) {
                SearchSort.Bookability ->
                    SearchCursor.Keyset(afterScore = last.score, afterId = last.id)
                SearchSort.Price, SearchSort.New ->
                    SearchCursor.Offset(offset + limit)
            }
        }

        fun isMissingFunction(error: Throwable): Boolean {
            val m = error.message.orEmpty().lowercase()
            return m.contains("could not find the function") ||
                m.contains("pgrst202") ||
                m.contains("42883") ||
                m.contains("does not exist") ||
                m.contains("no function matches")
        }
    }
}

@Serializable
data class SearchArtistRow(
    val id: String,
    @SerialName("stage_name") val stageName: String,
    val handle: String,
    val category: String,
    val genre: String? = null,
    @SerialName("base_city") val baseCity: String,
    @SerialName("min_price") val minPrice: Int? = null,
    val score: Int = 0,
    @SerialName("total_gigs") val totalGigs: Int = 0,
    @SerialName("cover_gradient_index") val coverGradientIndex: Int = 0,
    @SerialName("days_available") val daysAvailable: List<String>? = null,
    val rank: Double = 0.0,
) {
    fun toPartialArtist(coverUrl: String?): Artist = Artist(
        id = id.lowercase(),
        name = stageName,
        handle = handle,
        category = category,
        genre = genre.orEmpty(),
        city = baseCity,
        price = minPrice ?: 0,
        duration = "",
        score = score,
        gradient = ArtistGradient.palette(coverGradientIndex),
        gigs = totalGigs,
        daysAvailable = daysAvailable.orEmpty(),
        coverUrl = coverUrl,
    )
}

@Serializable
private data class SearchFacetRow(
    val kind: String,
    val label: String,
    val n: Int,
)

@Serializable
private data class PriceBucketRow(
    @SerialName("bucket_min") val bucketMin: Int,
    @SerialName("bucket_max") val bucketMax: Int,
    val n: Int,
)
