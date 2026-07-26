package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacet
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.model.SearchTuning

/**
 * In-memory [SearchRepository] — filters/sorts/paginates a seeded roster so
 * Discover/Search ViewModels stay offline + deterministic (iOS Fake twin).
 */
class FakeSearchRepository(
    private val roster: () -> List<Artist> = { emptyList() },
) : SearchRepository {

    /** Convenience ctor that holds a fixed list. */
    constructor(artists: List<Artist>) : this({ artists })

    var failSearch: Boolean = false

    override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage {
        if (failSearch) throw IllegalStateException("fake search failure")
        var matched = roster().filter { a ->
            if (filters.hasTextQuery) {
                val q = filters.text.lowercase()
                val hay = "${a.name} ${a.handle} ${a.category} ${a.genre} ${a.city}".lowercase()
                if (!hay.contains(q)) return@filter false
            }
            if (filters.city != null && a.city != filters.city) return@filter false
            if (filters.categories.isNotEmpty() && a.category !in filters.categories) return@filter false
            if (filters.minPrice != null && a.price < filters.minPrice) return@filter false
            if (filters.maxPrice != null && a.price > filters.maxPrice) return@filter false
            if (filters.minScore != null && a.score < filters.minScore) return@filter false
            true
        }
        if (!filters.hasTextQuery) {
            matched = when (filters.sort) {
                SearchSort.Bookability -> matched.sortedByDescending { it.score }
                SearchSort.Price -> matched.sortedBy { it.price }
                SearchSort.New -> matched
            }
        }
        val limit = SearchTuning.PAGE_LIMIT
        val offset = when (cursor) {
            is SearchCursor.Offset -> cursor.offset
            else -> 0
        }
        val page = matched.drop(offset).take(limit)
        val next: SearchCursor =
            if (page.size < limit) SearchCursor.End else SearchCursor.Offset(offset + limit)
        return SearchPage(artists = page, nextCursor = next)
    }

    override suspend fun facets(): SearchFacets {
        val list = roster()
        val categories = list.groupBy { it.category }
            .map { SearchFacet(it.key, it.value.size) }
            .sortedBy { it.label }
        val cities = list.groupBy { it.city }
            .map { SearchFacet(it.key, it.value.size) }
            .sortedBy { it.label }
        return SearchFacets(categories = categories, cities = cities)
    }
}
