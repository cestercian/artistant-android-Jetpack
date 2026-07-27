package `in`.artistant.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.data.model.SearchCatalog
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFacets
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.model.SearchTuning
import `in`.artistant.app.data.repository.SearchRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search tab — port of iOS `SearchStore`. Debounced text + accordion filters drive
 * `search_artists` (incl. 0073 dims); facets power empty-state rails; histogram
 * feeds the Budget section.
 */
data class SearchUiState(
    val query: String = "",
    val city: String? = null,
    val categories: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.Bookability,
    val minPrice: Int = SearchTuning.PRICE_FLOOR,
    val maxPrice: Int = SearchTuning.PRICE_CEILING,
    val priceDataMin: Int = SearchTuning.PRICE_FLOOR,
    val priceDataMax: Int = SearchTuning.PRICE_CEILING,
    val minScore: Int = 0,
    val eventType: String? = null,
    val services: Set<String> = emptySet(),
    val dateIso: String? = null,
    val flexDays: Int = 0,
    val histogram: List<PriceBucket> = emptyList(),
    val results: List<Artist> = emptyList(),
    val facets: SearchFacets = SearchFacets.Empty,
    val recents: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadError: String? = null,
) {
    val hasActiveQuery: Boolean
        get() = query.isNotBlank() || activeFilterCount > 0 || categories.isNotEmpty()

    /** Badge count for the filter button — excludes category rails (iOS sheet count). */
    val activeFilterCount: Int
        get() {
            var n = 0
            if (city != null) n++
            if (dateIso != null) n++
            if (eventType != null) n++
            if (services.isNotEmpty()) n++
            if (minPrice > priceDataMin || maxPrice < priceDataMax) n++
            if (minScore > 0) n++
            return n
        }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val searchRecents: SearchRecents,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var nextCursor: SearchCursor? = null
    private var searchJob: Job? = null
    private var generation = 0
    private val histogramCache = mutableMapOf<String, List<PriceBucket>>()

    init {
        viewModelScope.launch {
            runCatching { searchRepository.facets() }
                .onSuccess { facets -> _state.update { it.copy(facets = facets) } }
        }
        viewModelScope.launch {
            runCatching { searchRecents.load() }
                .onSuccess { terms -> _state.update { it.copy(recents = terms) } }
        }
        viewModelScope.launch {
            queryFlow
                .debounce(280)
                .distinctUntilChanged()
                .collect { q ->
                    _state.update { it.copy(query = q) }
                    runSearch(reset = true)
                }
        }
        loadHistogram(null)
    }

    fun applyRecent(term: String) {
        onQueryChange(term)
    }

    fun onQueryChange(text: String) {
        queryFlow.value = text
        _state.update { it.copy(query = text) }
    }

    fun clearQuery() {
        queryFlow.value = ""
        _state.update { it.copy(query = "") }
        runSearch(reset = true)
    }

    fun selectCity(city: String?) {
        _state.update { it.copy(city = city) }
        loadHistogram(city)
        runSearch(reset = true)
    }

    fun toggleCategory(category: String) {
        _state.update { s ->
            val next = s.categories.toMutableSet()
            if (!next.add(category)) next.remove(category)
            s.copy(categories = next)
        }
        runSearch(reset = true)
    }

    fun setSort(sort: SearchSort) {
        _state.update { it.copy(sort = sort) }
        runSearch(reset = true)
    }

    fun setPriceRange(min: Int, max: Int) {
        _state.update { it.copy(minPrice = min.coerceAtMost(max), maxPrice = max.coerceAtLeast(min)) }
    }

    fun setMinScore(score: Int) {
        _state.update { it.copy(minScore = score.coerceIn(0, 100)) }
    }

    fun setEventType(type: String?) {
        _state.update { it.copy(eventType = type) }
    }

    fun toggleService(slug: String) {
        _state.update { s ->
            val next = s.services.toMutableSet()
            if (!next.add(slug)) next.remove(slug)
            s.copy(services = next)
        }
    }

    fun setDate(iso: String?) {
        _state.update { it.copy(dateIso = iso, flexDays = if (iso == null) 0 else it.flexDays) }
    }

    fun setFlexDays(days: Int) {
        _state.update { it.copy(flexDays = days) }
    }

    /** Apply sheet edits and re-search (sheet mutates live; Apply just closes + refreshes). */
    fun applyFilters() = runSearch(reset = true)

    fun clearFilters() {
        _state.update {
            it.copy(
                city = null,
                minPrice = it.priceDataMin,
                maxPrice = it.priceDataMax,
                minScore = 0,
                eventType = null,
                services = emptySet(),
                dateIso = null,
                flexDays = 0,
            )
        }
        loadHistogram(null)
        runSearch(reset = true)
    }

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.canLoadMore) return
        runSearch(reset = false)
    }

    fun retry() = runSearch(reset = true)

    private fun loadHistogram(city: String?) {
        val key = city.orEmpty()
        histogramCache[key]?.let { cached ->
            applyHistogram(cached)
            return
        }
        viewModelScope.launch {
            val buckets = searchRepository.priceHistogram(city)
            if (buckets.isNotEmpty()) histogramCache[key] = buckets
            applyHistogram(buckets)
        }
    }

    private fun applyHistogram(buckets: List<PriceBucket>) {
        if (buckets.isEmpty()) {
            _state.update { it.copy(histogram = emptyList()) }
            return
        }
        val dataMin = buckets.minOf { it.bucketMin }
        val dataMax = buckets.maxOf { it.bucketMax }
        _state.update {
            // Widen span; keep current selection if still inside, else reset to full span.
            val min = it.minPrice.coerceIn(dataMin, dataMax)
            val max = it.maxPrice.coerceIn(dataMin, dataMax)
            it.copy(
                histogram = buckets,
                priceDataMin = dataMin,
                priceDataMax = dataMax,
                minPrice = if (min >= max) dataMin else min,
                maxPrice = if (min >= max) dataMax else max,
            )
        }
    }

    private fun runSearch(reset: Boolean) {
        val snapshot = _state.value
        if (!snapshot.hasActiveQuery && snapshot.query.isBlank()) {
            if (snapshot.activeFilterCount == 0 && snapshot.categories.isEmpty()) {
                _state.update {
                    it.copy(results = emptyList(), canLoadMore = false, loadError = null, isLoading = false)
                }
                nextCursor = null
                return
            }
        }

        searchJob?.cancel()
        val gen = ++generation
        searchJob = viewModelScope.launch {
            if (reset) {
                _state.update { it.copy(isLoading = true, loadError = null) }
                nextCursor = SearchCursor.Start
            } else {
                _state.update { it.copy(isLoadingMore = true) }
            }
            val cursor = if (reset) SearchCursor.Start else (nextCursor ?: SearchCursor.End)
            if (cursor is SearchCursor.End) {
                _state.update { it.copy(isLoading = false, isLoadingMore = false, canLoadMore = false) }
                return@launch
            }
            try {
                val live = _state.value
                val narrowedMin = live.minPrice.takeIf { it > live.priceDataMin }
                val narrowedMax = live.maxPrice.takeIf { it < live.priceDataMax }
                val filters = SearchFilters(
                    text = live.query,
                    city = live.city,
                    categories = live.categories.toList(),
                    minPrice = narrowedMin,
                    maxPrice = narrowedMax,
                    minScore = live.minScore.takeIf { it > 0 },
                    eventType = live.eventType,
                    sort = live.sort,
                )
                val page = searchRepository.search(
                    filters = filters,
                    cursor = cursor,
                    services = live.services.takeIf { it.isNotEmpty() }?.toList(),
                    date = live.dateIso,
                    flexDays = live.flexDays.takeIf { live.dateIso != null },
                )
                if (gen != generation) return@launch
                nextCursor = page.nextCursor
                _state.update {
                    it.copy(
                        results = if (reset) page.artists else it.results + page.artists,
                        isLoading = false,
                        isLoadingMore = false,
                        canLoadMore = page.nextCursor !is SearchCursor.End,
                        loadError = null,
                    )
                }
                // Persist successful text queries as recents (iOS SearchStore).
                // Isolated from the search catch so a prefs write failure never
                // masquerades as a failed search.
                val q = live.query.trim()
                if (reset && q.isNotEmpty()) {
                    val next = (listOf(q) + live.recents.filter { !it.equals(q, ignoreCase = true) })
                        .take(8)
                    runCatching { searchRecents.save(next) }
                        .onSuccess { _state.update { it.copy(recents = next) } }
                }
            } catch (t: Throwable) {
                if (gen != generation) return@launch
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        loadError = t.message ?: "Search failed.",
                    )
                }
            }
        }
    }

    companion object {
        /** Exposed for sheet labels — keeps UI from importing catalog directly. */
        val eventTypes get() = SearchCatalog.eventTypes
        val services get() = SearchCatalog.services
        val flexOptions get() = SearchCatalog.flexOptions
    }
}
