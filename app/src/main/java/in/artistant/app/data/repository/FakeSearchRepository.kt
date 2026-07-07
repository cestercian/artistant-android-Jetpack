package `in`.artistant.app.data.repository

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacet
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchPage
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.model.SearchTuning
import `in`.artistant.app.data.model.nextSearchCursor

/**
 * In-memory [SearchRepository] for tests / previews (iOS `FakeSearchRepository`).
 * Filters a fixed roster with `contains`, applies the same sort + pagination shape
 * as the server path so cursor behaviour is exercised offline + deterministic.
 */
class FakeSearchRepository(
    private val roster: List<Artist> = emptyList(),
) : SearchRepository {

    override suspend fun search(filters: SearchFilters, cursor: SearchCursor): SearchPage {
        val hasQuery = filters.hasTextQuery
        val q = filters.text.trim().lowercase()

        var matches = roster.asSequence()
            .filter { !hasQuery || it.name.lowercase().contains(q) || it.category.lowercase().contains(q) }
            .filter { filters.city == null || it.city.equals(filters.city, ignoreCase = true) }
            .filter { filters.categories.isEmpty() || it.category in filters.categories }
            .filter { filters.minPrice == null || it.price >= filters.minPrice }
            .filter { filters.maxPrice == null || it.price <= filters.maxPrice }
            .filter { filters.minScore == null || it.score >= filters.minScore }
            .toList()

        matches = when {
            hasQuery -> matches  // "relevance": leave insertion order
            else -> when (filters.sort) {
                SearchSort.Bookability -> matches.sortedWith(compareByDescending<Artist> { it.score }.thenBy { it.id })
                SearchSort.Price -> matches.sortedBy { it.price }
                SearchSort.New -> matches
            }
        }

        val limit = SearchTuning.PAGE_LIMIT
        val offset = when (cursor) {
            is SearchCursor.Offset -> cursor.value
            is SearchCursor.Keyset -> matches.indexOfFirst { it.id == cursor.afterId } + 1
            else -> 0
        }.coerceIn(0, matches.size)

        val page = matches.drop(offset).take(limit)
        val last = page.lastOrNull()
        val next = nextSearchCursor(
            rowCount = page.size, limit = limit,
            lastScore = last?.score, lastId = last?.id,
            hasQuery = hasQuery, sort = filters.sort, offset = offset,
        )
        return SearchPage(artists = page, nextCursor = next)
    }

    override suspend fun facets(): SearchFacets = SearchFacets(
        categories = roster.groupingBy { it.category }.eachCount().map { SearchFacet(it.key, it.value) },
        cities = roster.groupingBy { it.city }.eachCount().map { SearchFacet(it.key, it.value) },
    )
}
