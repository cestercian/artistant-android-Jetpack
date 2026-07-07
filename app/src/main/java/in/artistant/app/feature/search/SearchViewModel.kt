package `in`.artistant.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.repository.SearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All Search inputs + outputs (port of iOS `SearchStore`'s @Published surface). */
data class SearchUiState(
    // Inputs
    val query: String = "",
    val city: String? = null,
    val minPrice: Int = PRICE_FLOOR,
    val maxPrice: Int = PRICE_CEILING,
    val minScore: Int = 0,
    val categories: Set<String> = emptySet(),
    val eventType: String? = null,
    val sort: SearchSort = SearchSort.Bookability,
    // Outputs
    val results: List<Artist> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadError: String? = null,
    val facets: SearchFacets = SearchFacets.empty,
    val recents: List<String> = emptyList(),
) {
    val hasFilters: Boolean
        get() = city != null || minPrice > PRICE_FLOOR || maxPrice < PRICE_CEILING ||
            minScore > 0 || categories.isNotEmpty() || eventType != null

    val activeFilterCount: Int
        get() {
            var n = 0
            if (city != null) n++
            if (minPrice > PRICE_FLOOR || maxPrice < PRICE_CEILING) n++
            if (minScore > 0) n++
            if (categories.isNotEmpty()) n++
            if (eventType != null) n++
            return n
        }

    /** Something to search? false = the pure-browse empty state (facet chips). */
    val hasActiveQuery: Boolean get() = query.isNotBlank() || hasFilters

    val allCities: List<String> get() = facets.cities.map { it.label }
    val allCategories: List<String> get() = facets.categories.map { it.label }

    /** Identity that re-fires the (debounced) search — inputs only, never outputs. */
    val queryKey: QueryKey
        get() = QueryKey(query.trim(), city, categories.sorted(), minPrice, maxPrice, minScore, eventType, sort)

    data class QueryKey(
        val query: String,
        val city: String?,
        val categories: List<String>,
        val minPrice: Int,
        val maxPrice: Int,
        val minScore: Int,
        val eventType: String?,
        val sort: SearchSort,
    )

    companion object {
        const val PRICE_FLOOR = 10_000
        const val PRICE_CEILING = 80_000
    }
}

/**
 * Drives the Search tab (port of iOS `SearchStore`). SERVER-BACKED: it pages
 * [SearchRepository] (the `search_artists` / `search_facets` RPCs) so search
 * scales to thousands of artists with ranking + pagination.
 *
 * Debounce + supersede: an init collector maps the input-only [SearchUiState.queryKey],
 * de-dupes it, and `collectLatest`-runs the first-page search. The ~280ms debounce
 * lives INSIDE [runSearch] (a cancellable `delay` after `isLoading` is raised), so a
 * new key cancels the in-flight search — including its debounce window — exactly like
 * iOS's `.task(id:)`; a fast typist only pays for the last query. A monotonic
 * [generation] additionally guards pagination: a page that arrives after a filter
 * change is dropped rather than appended to a different result set.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val recentsStore: SearchRecents,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var nextCursor: SearchCursor? = null
    // Bumped on every first-page search; a stale page's generation != this is dropped.
    private var generation = 0

    init {
        // Self-cancelling first-page search on any input change. `collectLatest`
        // cancels an in-flight `runSearch` — including its debounce `delay` — the
        // instant the key changes, so mid-flight keystrokes never race a stale
        // result to the screen (iOS `.task(id:)` parity). The debounce itself lives
        // inside `runSearch`, after `isLoading` is raised, so the skeleton (not a
        // "No results" flash) covers the quiet window.
        viewModelScope.launch {
            _state.map { it.queryKey }.distinctUntilChanged().collectLatest {
                runSearch()
            }
        }
        loadFacets()
        loadRecents()
    }

    // MARK: - Input setters

    fun onQueryChange(text: String) = _state.update { it.copy(query = text) }
    fun setCity(city: String?) = _state.update { it.copy(city = city) }
    fun setMinPrice(v: Int) = _state.update { it.copy(minPrice = v) }
    fun setMaxPrice(v: Int) = _state.update { it.copy(maxPrice = v) }
    fun setMinScore(v: Int) = _state.update { it.copy(minScore = v) }
    fun setEventType(e: String?) = _state.update { it.copy(eventType = e) }
    fun setSort(sort: SearchSort) = _state.update { it.copy(sort = sort) }

    fun toggleCategory(cat: String) = _state.update {
        it.copy(categories = if (cat in it.categories) it.categories - cat else it.categories + cat)
    }

    fun selectOnlyCategory(cat: String) = _state.update { it.copy(categories = setOf(cat)) }

    fun clearFilters() = _state.update {
        it.copy(
            city = null,
            minPrice = SearchUiState.PRICE_FLOOR,
            maxPrice = SearchUiState.PRICE_CEILING,
            minScore = 0,
            categories = emptySet(),
            eventType = null,
        )
    }

    // MARK: - Loading

    private fun currentFilters(): SearchFilters {
        val s = _state.value
        return SearchFilters(
            text = s.query,
            city = s.city,
            categories = s.categories.sorted(),
            minPrice = if (s.minPrice > SearchUiState.PRICE_FLOOR) s.minPrice else null,
            maxPrice = if (s.maxPrice < SearchUiState.PRICE_CEILING) s.maxPrice else null,
            minScore = if (s.minScore > 0) s.minScore else null,
            eventType = s.eventType,
            sort = s.sort,
        )
    }

    /**
     * First-page search for the current inputs. Raises `isLoading` SYNCHRONOUSLY —
     * before the debounce `delay` — so the skeleton (not a "No results" flash) covers
     * the whole quiet window: `NoResults` renders only after a search has actually
     * completed with zero rows. Called from the init collector (superseded via
     * `collectLatest` cancellation on a new key) and from retry.
     */
    suspend fun runSearch() {
        if (!_state.value.hasActiveQuery) {
            // Pure browse — clear results; the view shows the facet rails.
            nextCursor = null
            _state.update { it.copy(results = emptyList(), canLoadMore = false, loadError = null, isLoading = false) }
            return
        }
        _state.update { it.copy(isLoading = true, loadError = null) }
        // Debounce lives here (not in the flow) so it's raised AFTER isLoading and is
        // cancelled by collectLatest when the key changes — CancellationException
        // propagates out, aborting cleanly without touching state.
        delay(280)
        generation += 1
        val gen = generation
        try {
            val page = repository.search(currentFilters(), SearchCursor.Start)
            if (gen != generation) return  // superseded
            nextCursor = page.nextCursor
            _state.update {
                it.copy(
                    results = page.artists,
                    canLoadMore = page.nextCursor != SearchCursor.End,
                    isLoading = false,
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t  // never swallow cancellation
            if (gen != generation) return
            nextCursor = SearchCursor.End
            _state.update {
                it.copy(results = emptyList(), canLoadMore = false, isLoading = false, loadError = message(t))
            }
        }
    }

    /** Append the next page (infinite scroll). No-op when loading or at the end. */
    fun loadMore() {
        val cursor = nextCursor
        val s = _state.value
        if (cursor == null || cursor == SearchCursor.End || s.isLoadingMore || s.isLoading) return
        val gen = generation
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val page = repository.search(currentFilters(), cursor)
                // Drop the page if a filter change bumped the generation mid-flight.
                if (gen == generation) {
                    nextCursor = page.nextCursor
                    _state.update {
                        val known = it.results.mapTo(HashSet()) { a -> a.id }
                        it.copy(
                            results = it.results + page.artists.filter { a -> a.id !in known },
                            canLoadMore = page.nextCursor != SearchCursor.End,
                            isLoadingMore = false,
                        )
                    }
                } else {
                    _state.update { it.copy(isLoadingMore = false) }
                }
            } catch (_: Throwable) {
                // Keep current results; canLoadMore stays true so scrolling retries.
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun retry() { viewModelScope.launch { runSearch() } }

    private fun loadFacets() {
        viewModelScope.launch {
            runCatching { repository.facets() }.onSuccess { f -> _state.update { it.copy(facets = f) } }
        }
    }

    // MARK: - Recents (persisted in DataStore)

    fun recordRecent() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        val updated = (listOf(q) + _state.value.recents.filter { !it.equals(q, ignoreCase = true) }).take(6)
        _state.update { it.copy(recents = updated) }
        viewModelScope.launch { runCatching { recentsStore.save(updated) } }
    }

    private fun loadRecents() {
        viewModelScope.launch {
            val list = runCatching { recentsStore.load() }.getOrDefault(emptyList())
            if (list.isNotEmpty()) _state.update { it.copy(recents = list) }
        }
    }

    private fun message(error: Throwable): String {
        val m = (error.message ?: "").lowercase()
        return if (m.contains("could not find the function") || m.contains("search_artists") ||
            m.contains("42883") || m.contains("does not exist")
        ) {
            "Search isn't available right now. Please try again in a moment."
        } else {
            "Couldn't load results. Check your connection and try again."
        }
    }
}
