package `in`.artistant.app.data.model

/**
 * Value types for server-side artist search (iOS `SearchTypes.swift`). These back
 * the `search_artists()` / `search_facets()` RPCs. The point of the server path is
 * SCALE: Postgres ranks + filters + paginates on its indexes and hands back one
 * page, so pagination is first-class here as a [SearchCursor].
 */

/**
 * User-selectable ordering. Ignored by the RPC when a text query is present (it
 * ranks by relevance instead) — so this only takes effect for browse (no query).
 * [rpcValue] is the exact string the `p_sort` arg expects.
 */
enum class SearchSort(val rpcValue: String, val label: String) {
    Bookability("bookability", "Top"),  // score desc — hot browse path (keyset-paged)
    Price("price", "Price"),            // min_price asc (nulls last) — offset-paged
    New("new", "New");                  // created_at desc — offset-paged
}

/**
 * Everything that parameterises a search. A null numeric/text filter means "no
 * bound" — the RPC treats null as "don't filter on this" (the Audit-3 P1 rule:
 * a no-package artist with min_price NULL stays in unfiltered results but drops
 * out the moment a real budget bound is set).
 */
data class SearchFilters(
    val text: String = "",
    val city: String? = null,
    val categories: List<String> = emptyList(),
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val minScore: Int? = null,
    val eventType: String? = null,
    val sort: SearchSort = SearchSort.Bookability,
) {
    /** True when the RPC will rank by relevance — i.e. a non-blank text query. */
    val hasTextQuery: Boolean get() = text.isNotBlank()
}

/**
 * Opaque "where the next page starts" token. Modelled as a sealed type so an
 * impossible state (a keyset cursor on an offset-paged sort) is unrepresentable;
 * the repository derives the page from (filters, cursor). The store never inspects
 * it — it just hands [SearchPage.nextCursor] back into the next load.
 *
 * - [Start]  — first page.
 * - [Keyset] — bookability browse: compares (score,id) tuples; O(limit) at depth.
 * - [Offset] — price/new/relevance: bounded OFFSET paging.
 * - [End]    — no more rows (last page returned fewer than the page limit).
 */
sealed interface SearchCursor {
    data object Start : SearchCursor
    data class Keyset(val afterScore: Int, val afterId: String) : SearchCursor
    data class Offset(val value: Int) : SearchCursor
    data object End : SearchCursor
}

/** One page of results plus the token for the next. [nextCursor] == End = bottom. */
data class SearchPage(
    val artists: List<Artist>,
    val nextCursor: SearchCursor,
) {
    companion object {
        val empty = SearchPage(emptyList(), SearchCursor.End)
    }
}

/** One facet bucket — a category/city label and its published count. */
data class SearchFacet(val label: String, val count: Int)

data class SearchFacets(
    val categories: List<SearchFacet>,
    val cities: List<SearchFacet>,
) {
    companion object {
        val empty = SearchFacets(emptyList(), emptyList())
    }
}

object SearchTuning {
    /** Rows per page. The RPC clamps p_limit to 1..50; 20 is a comfortable grid. */
    const val PAGE_LIMIT = 20
}

/**
 * Derives the next-page cursor from the page just returned (iOS
 * `SearchRepository.nextCursor`). A short page (< [limit] rows) means we've
 * bottomed out → [SearchCursor.End] — there is no total count, so "did we get a
 * full page" is the only signal. Kept as a pure function so the paging logic is
 * unit-testable without a live client.
 *
 * @param lastScore/lastId the last row's (score, id) — used only for the
 *   bookability keyset cursor; may be null when the page was empty.
 */
fun nextSearchCursor(
    rowCount: Int,
    limit: Int,
    lastScore: Int?,
    lastId: String?,
    hasQuery: Boolean,
    sort: SearchSort,
    offset: Int,
): SearchCursor {
    if (rowCount < limit || lastId == null) return SearchCursor.End
    if (hasQuery) return SearchCursor.Offset(offset + limit)  // relevance → offset
    return when (sort) {
        SearchSort.Bookability -> SearchCursor.Keyset(lastScore ?: 0, lastId)
        SearchSort.Price, SearchSort.New -> SearchCursor.Offset(offset + limit)
    }
}
