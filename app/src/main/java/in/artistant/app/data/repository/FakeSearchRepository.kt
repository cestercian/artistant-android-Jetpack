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
    /**
     * Fed every page this fake returns, the same way [SupabaseSearchRepository
     * .search] feeds [ArtistsRepository.cache] — the contract that makes a
     * search result tappable without a further fetch (`ArtistProfileViewModel
     * .refresh` resolves purely through `find`/`ensureFull`, never a search).
     * Optional and defaulted to null so every existing seed-only caller keeps
     * behaving exactly as before.
     */
    private val artistsRepository: ArtistsRepository? = null,
) : SearchRepository {

    /** Convenience ctor that holds a fixed list. */
    constructor(artists: List<Artist>, artistsRepository: ArtistsRepository? = null) :
        this({ artists }, artistsRepository)

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
                // `score desc, id desc` — the server's bookability order, and the
                // tuple its keyset cursor walks (mig 0073 `search_artists`).
                SearchSort.Bookability ->
                    matched.sortedWith(
                        compareByDescending<Artist> { it.score }.thenByDescending { it.id },
                    )
                SearchSort.Price -> matched.sortedBy { it.price }
                SearchSort.New -> matched
            }
        }
        val limit = SearchTuning.PAGE_LIMIT
        // Mirror the real cursor policy: the default (no-query) bookability sort
        // pages by keyset, everything else by offset. A fake that only ever spoke
        // Offset made a keyset regression uncatchable through the seam.
        val keysetPaging = !filters.hasTextQuery && filters.sort == SearchSort.Bookability
        val offset = if (keysetPaging) 0 else ((cursor as? SearchCursor.Offset)?.offset ?: 0)
        val page = if (keysetPaging) {
            val after = cursor as? SearchCursor.Keyset
            val rest = if (after == null) matched else matched.filter { it.isAfter(after) }
            rest.take(limit)
        } else {
            matched.drop(offset).take(limit)
        }
        // A short page means the roster ran out — same End rule as the real one.
        val next: SearchCursor = when {
            page.size < limit -> SearchCursor.End
            keysetPaging -> page.last().let {
                SearchCursor.Keyset(afterScore = it.score, afterId = it.id)
            }
            else -> SearchCursor.Offset(offset + limit)
        }
        // Same order as the real repository: feed the by-id cache before handing
        // the page back, so a caller with no ArtistsRepository of its own (the
        // Search screen — see the doc on the constructor param) can still resolve
        // a tapped result.
        artistsRepository?.cache(page)
        return SearchPage(artists = page, nextCursor = next)
    }

    /** `(score, id) < (after_score, after_id)` — the server's keyset predicate. */
    private fun Artist.isAfter(cursor: SearchCursor.Keyset): Boolean =
        score < cursor.afterScore || (score == cursor.afterScore && id < cursor.afterId)

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
