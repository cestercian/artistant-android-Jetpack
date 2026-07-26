package `in`.artistant.app.data.model

/**
 * Search contract — port of iOS `SearchTypes.swift`. Backs `search_artists` /
 * `search_facets` (migrations 0044/0045 + 0073 dims on the live impl).
 */

enum class SearchSort(val rpcValue: String, val label: String) {
    Bookability("bookability", "Top"),
    Price("price", "Price"),
    New("new", "New"),
}

/**
 * Everything that parameterises a search. A null numeric filter means "no bound"
 * — the RPC treats null as don't-filter (Audit-3: no-package artists stay in
 * unfiltered results).
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
    val hasTextQuery: Boolean
        get() = text.trim().isNotEmpty()
}

/** Opaque next-page token. The UI never inspects it — just hands it back. */
sealed class SearchCursor {
    data object Start : SearchCursor()
    data class Keyset(val afterScore: Int, val afterId: String) : SearchCursor()
    data class Offset(val offset: Int) : SearchCursor()
    data object End : SearchCursor()
}

data class SearchPage(
    val artists: List<Artist>,
    val nextCursor: SearchCursor,
) {
    companion object {
        val Empty = SearchPage(emptyList(), SearchCursor.End)
    }
}

data class SearchFacet(val label: String, val count: Int)

data class SearchFacets(
    val categories: List<SearchFacet> = emptyList(),
    val cities: List<SearchFacet> = emptyList(),
) {
    companion object {
        val Empty = SearchFacets()
    }
}

object SearchTuning {
    /** Rows per page. RPC clamps `p_limit` to 1…50. */
    const val PAGE_LIMIT = 20
}
