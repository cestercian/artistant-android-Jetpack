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
import kotlinx.coroutines.flow.updateAndGet
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
    // Placeholders until the `price_histogram` load lands — they describe the
    // tuning constants, NOT the roster. See [priceBoundsLoaded].
    val priceDataMin: Int = SearchTuning.PRICE_FLOOR,
    val priceDataMax: Int = SearchTuning.PRICE_CEILING,
    /**
     * True once `price_histogram` returned buckets and [priceDataMin]/
     * [priceDataMax] describe the real roster span. Everything about the price
     * filter is meaningless before this: comparing the selection against the
     * ₹10k/₹80k placeholders can't distinguish "user narrowed it" from "nobody
     * has touched it yet", which is what put a phantom "1" on the filter badge
     * of a cold Search tab.
     */
    val priceBoundsLoaded: Boolean = false,
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

    /**
     * The `p_min_price` / `p_max_price` actually worth posting, or null when the
     * end sits on the data bound (or the bounds aren't known yet). Single source
     * of truth for BOTH the badge and the RPC arguments — when these two drifted
     * apart, the badge could read 0 while `runSearch` still posted a ceiling that
     * dropped every artist above it.
     */
    val activePriceFloor: Int? get() = minPrice.takeIf { priceBoundsLoaded && it > priceDataMin }
    val activePriceCeiling: Int? get() = maxPrice.takeIf { priceBoundsLoaded && it < priceDataMax }

    /** The user has moved an end of the price range off the loaded data span. */
    val isPriceNarrowed: Boolean get() = activePriceFloor != null || activePriceCeiling != null

    /** Badge count for the filter button — excludes category rails (iOS sheet count). */
    val activeFilterCount: Int
        get() {
            var n = 0
            if (city != null) n++
            if (dateIso != null) n++
            if (eventType != null) n++
            if (services.isNotEmpty()) n++
            if (isPriceNarrowed) n++
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

    /**
     * Load the price facet for [city] (null = all cities).
     *
     * The RPC is wrapped rather than trusted: the real repository swallows its own
     * errors into an empty list today, but this talks to the interface, and an
     * uncaught throw here would take `viewModelScope` down with it. A failed load
     * and a city with no priced artists are the same thing to this screen — we
     * don't know the span — so both arrive as "no buckets".
     */
    private fun loadHistogram(city: String?) {
        val key = city.orEmpty()
        histogramCache[key]?.let { cached ->
            applyHistogram(cached, city)
            return
        }
        viewModelScope.launch {
            val buckets = runCatching { searchRepository.priceHistogram(city) }.getOrDefault(emptyList())
            if (buckets.isNotEmpty()) histogramCache[key] = buckets
            applyHistogram(buckets, city)
        }
    }

    /**
     * Adopt [buckets] as the price facet for [forCity].
     *
     * [forCity] is carried through the async hop so a slow response can't land on
     * a scope it doesn't describe: two quick city taps race, and without this the
     * loser's response would overwrite the winner's bounds.
     */
    private fun applyHistogram(buckets: List<PriceBucket>, forCity: String?) {
        val before = _state.value
        val after = _state.updateAndGet { s ->
            // A response for a city we've already navigated away from describes
            // someone else's roster. Drop it.
            if (s.city != forCity) return@updateAndGet s

            if (buckets.isEmpty()) {
                // No facet data for THIS scope — a city with no priced artists, a
                // fresh project, or a failed load. Revert to exactly the cold-start
                // shape rather than keeping the previous city's numbers: bounds are
                // a claim about the roster being searched, and the honest state for
                // "we don't know this city's span" is the same one we hold before
                // looking at all. Keeping them stale left a narrowing from the last
                // city still posted as p_min/p_max here, cutting results under a
                // badge whose "1" pointed at a budget section with no bars.
                //
                // The selection reverts with the span because the sheet feeds the
                // RangeSlider `value = minPrice..maxPrice` inside
                // `valueRange = priceDataMin..priceDataMax` — reverting one without
                // the other puts the thumbs outside their own track.
                return@updateAndGet s.copy(
                    histogram = emptyList(),
                    priceBoundsLoaded = false,
                    priceDataMin = SearchTuning.PRICE_FLOOR,
                    priceDataMax = SearchTuning.PRICE_CEILING,
                    minPrice = SearchTuning.PRICE_FLOOR,
                    maxPrice = SearchTuning.PRICE_CEILING,
                )
            }

            val dataMin = buckets.minOf { it.bucketMin }
            val dataMax = buckets.maxOf { it.bucketMax }
            // An untouched selection SNAPS to the newly-learned span. It used to be
            // merely coerced into it, which left the pre-load ₹10k/₹80k placeholder
            // selection in place — so any roster reaching outside that window (an
            // artist under ₹10k or over ₹80k, both ordinary) looked permanently
            // "narrowed": phantom filter badge, and a real price predicate posted
            // to `search_artists` that silently cut results nobody asked to cut.
            //
            // A selection made BEFORE the bounds landed also counts as untouched:
            // the slider's range was the placeholders, so those numbers described
            // nothing, and the badge correctly reported them as no filter at all.
            val narrowed = s.isPriceNarrowed
            val min = s.minPrice.coerceIn(dataMin, dataMax)
            val max = s.maxPrice.coerceIn(dataMin, dataMax)
            s.copy(
                histogram = buckets,
                priceDataMin = dataMin,
                priceDataMax = dataMax,
                priceBoundsLoaded = true,
                // Degenerate coercions (span moved entirely past the old selection)
                // collapse min onto max — fall back to the full span there too.
                minPrice = if (!narrowed || min >= max) dataMin else min,
                maxPrice = if (!narrowed || min >= max) dataMax else max,
            )
        }

        // The facet load is async, so `selectCity`'s own search has usually already
        // gone out with the OLD price predicate by the time this lands. If applying
        // the facet changed what we'd post, the results on screen were fetched with
        // arguments the UI no longer claims — refetch. `runSearch` cancels the
        // in-flight job and bumps `generation`, so a redundant call is cheap.
        if (after.activePriceFloor != before.activePriceFloor ||
            after.activePriceCeiling != before.activePriceCeiling
        ) {
            runSearch(reset = true)
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
                val filters = SearchFilters(
                    text = live.query,
                    city = live.city,
                    categories = live.categories.toList(),
                    // Same predicate the badge counts — see `activePriceFloor`.
                    minPrice = live.activePriceFloor,
                    maxPrice = live.activePriceCeiling,
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
