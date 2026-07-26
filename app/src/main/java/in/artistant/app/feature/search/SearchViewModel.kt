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
 * Search tab — port of iOS `SearchStore`. Debounced text + filters drive
 * `search_artists`; facets power the empty-state browse rails.
 */
data class SearchUiState(
    val query: String = "",
    val city: String? = null,
    val categories: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.Bookability,
    val results: List<Artist> = emptyList(),
    val facets: SearchFacets = SearchFacets.Empty,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadError: String? = null,
) {
    val hasActiveQuery: Boolean
        get() = query.isNotBlank() || city != null || categories.isNotEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var nextCursor: SearchCursor? = null
    private var searchJob: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            runCatching { searchRepository.facets() }
                .onSuccess { facets -> _state.update { it.copy(facets = facets) } }
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
    }

    fun onQueryChange(text: String) {
        queryFlow.value = text
        // Reflect typed text immediately in the field; search waits for debounce.
        _state.update { it.copy(query = text) }
    }

    fun clearQuery() {
        queryFlow.value = ""
        _state.update { it.copy(query = "") }
        runSearch(reset = true)
    }

    fun selectCity(city: String?) {
        _state.update { it.copy(city = city) }
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

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.canLoadMore) return
        runSearch(reset = false)
    }

    fun retry() = runSearch(reset = true)

    private fun runSearch(reset: Boolean) {
        val snapshot = _state.value
        if (!snapshot.hasActiveQuery && snapshot.query.isBlank()) {
            // Empty browse: still show top results so the tab isn't blank.
            // Matching iOS empty-state rails when query+filters are empty — we
            // leave results empty and show facets; only search when active.
            if (snapshot.query.isBlank() && snapshot.city == null && snapshot.categories.isEmpty()) {
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
                val filters = SearchFilters(
                    text = snapshot.query,
                    city = snapshot.city,
                    categories = snapshot.categories.toList(),
                    sort = snapshot.sort,
                )
                // Re-read query from flow in case debounce already advanced state.
                val live = _state.value
                val page = searchRepository.search(
                    filters.copy(
                        text = live.query,
                        city = live.city,
                        categories = live.categories.toList(),
                        sort = live.sort,
                    ),
                    cursor,
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
}
