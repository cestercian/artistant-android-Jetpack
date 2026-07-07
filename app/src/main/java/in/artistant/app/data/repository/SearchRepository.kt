package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import `in`.artistant.app.common.util.artistMediaPublicUrl
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacet
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.model.SearchTuning
import `in`.artistant.app.data.model.dto.DBArtistCover
import `in`.artistant.app.data.model.dto.SearchArtistRow
import `in`.artistant.app.data.model.dto.SearchFacetRow
import `in`.artistant.app.data.model.nextSearchCursor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-side artist search (iOS `SearchRepository`) — the `search_artists()` /
 * `search_facets()` RPCs. Both are SECURITY INVOKER, so `public.artists` RLS still
 * gates to published rows. Postgres ranks + filters + paginates and hands back one
 * page; the device never holds the whole table.
 */
interface SearchRepository {
    /** One page of filtered, ranked, paginated results. Pass [SearchCursor.Start] first. */
    suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage

    /** Category + city counts for the browse rails / filter chips. */
    suspend fun facets(): SearchFacets
}

@Singleton
class SupabaseSearchRepository @Inject constructor(
    private val client: SupabaseClient,
    // Injected so a returned page's tile-level partials feed the shared cache and
    // by-id lookups (a tap, a saved card) resolve without another round trip.
    private val artists: ArtistsRepository,
) : SearchRepository {

    override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage {
        val limit = SearchTuning.PAGE_LIMIT
        val hasQuery = filters.hasTextQuery
        val q = filters.text.trim()

        // Translate the opaque cursor → RPC paging args, honouring the EFFECTIVE
        // paging mode. The RPC ignores p_sort when a text query is present (ranks
        // by relevance + offset-pages), so a query forces offset regardless of the
        // selected sort. Browse keyset-pages on bookability, offset-pages on
        // price/new (price keyset is a v2 win).
        var afterScore: Int? = null
        var afterId: String? = null
        var offset = 0
        if (hasQuery) {
            (cursor as? SearchCursor.Offset)?.let { offset = it.value }
        } else when (filters.sort) {
            SearchSort.Bookability -> (cursor as? SearchCursor.Keyset)?.let {
                afterScore = it.afterScore; afterId = it.afterId
            }
            SearchSort.Price, SearchSort.New -> (cursor as? SearchCursor.Offset)?.let { offset = it.value }
        }

        val params = SearchArtistsParams(
            // Empty query → null so the RPC takes the browse branch instead of an
            // empty tsquery.
            p_q = if (hasQuery) q else null,
            p_city = filters.city,
            p_categories = filters.categories.ifEmpty { null },
            p_min_price = filters.minPrice,
            p_max_price = filters.maxPrice,
            p_min_score = filters.minScore,
            p_event_type = filters.eventType,
            p_sort = filters.sort.rpcValue,
            p_limit = limit,
            p_offset = offset,
            p_after_score = afterScore,
            p_after_id = afterId,
            p_after_price = null,  // v1: price uses offset paging; kept for the v2 keyset upgrade
        )

        val rows = client.postgrest.rpc("search_artists", params).decodeList<SearchArtistRow>()

        val covers = resolveCovers(rows.map { it.id })
        val page = rows.map { it.toPartialArtist(coverUrl = covers[it.id]) }

        // Feed the shared cache; cache() never downgrades a full profile.
        artists.cache(page)

        val last = rows.lastOrNull()
        val next = nextSearchCursor(
            rowCount = rows.size, limit = limit,
            lastScore = last?.score, lastId = last?.id,
            hasQuery = hasQuery, sort = filters.sort, offset = offset,
        )
        return SearchPage(artists = page, nextCursor = next)
    }

    override suspend fun facets(): SearchFacets {
        val rows = client.postgrest.rpc("search_facets").decodeList<SearchFacetRow>()
        return SearchFacets(
            categories = rows.filter { it.kind == "category" }.map { SearchFacet(it.label, it.n) },
            cities = rows.filter { it.kind == "city" }.map { SearchFacet(it.label, it.n) },
        )
    }

    /** Second batched query: position-0 photo per artist in the page → public URL. */
    private suspend fun resolveCovers(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val rows = client.postgrest.from("artist_media").select(COVER_COLUMNS) {
            filter { eq("kind", "photo"); isIn("artist_id", ids) }
            order("artist_id", Order.ASCENDING); order("position", Order.ASCENDING)
        }.decodeList<DBArtistCover>()
        // (artist_id, position) ordering guarantees rows.first is position-0.
        return rows.groupBy { it.artist_id }
            .mapNotNull { (id, group) ->
                group.firstOrNull()?.let { artistMediaPublicUrl(it.storage_path)?.let { url -> id to url } }
            }.toMap()
    }

    companion object {
        private val COVER_COLUMNS = Columns.list("artist_id", "storage_path", "position")
    }
}

/**
 * `search_artists` arguments. Keys MUST match the SQL arg names exactly — PostgREST
 * binds RPC params by name. Nulls encode as JSON null, which the function treats
 * identically to its `default null`.
 */
@Serializable
private data class SearchArtistsParams(
    @SerialName("p_q") val p_q: String?,
    @SerialName("p_city") val p_city: String?,
    @SerialName("p_categories") val p_categories: List<String>?,
    @SerialName("p_min_price") val p_min_price: Int?,
    @SerialName("p_max_price") val p_max_price: Int?,
    @SerialName("p_min_score") val p_min_score: Int?,
    @SerialName("p_event_type") val p_event_type: String?,
    @SerialName("p_sort") val p_sort: String,
    @SerialName("p_limit") val p_limit: Int,
    @SerialName("p_offset") val p_offset: Int,
    @SerialName("p_after_score") val p_after_score: Int?,
    @SerialName("p_after_id") val p_after_id: String?,
    @SerialName("p_after_price") val p_after_price: Int?,
)
