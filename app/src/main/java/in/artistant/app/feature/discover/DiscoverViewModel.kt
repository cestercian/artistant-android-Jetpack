package `in`.artistant.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.state.SavedStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Discover home rails + load status (iOS `DiscoverFeedStore` @Published surface). */
data class DiscoverUiState(
    val hero: List<Artist> = emptyList(),
    val featured: List<Artist> = emptyList(),
    val topBangalore: List<Artist> = emptyList(),
    val topIndia: List<Artist> = emptyList(),
    val newOnArtistant: List<Artist> = emptyList(),
    val comedy: List<Artist> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
) {
    /** True once at least the hero rail has content (drives the empty vs rails branch). */
    val hasContent: Boolean get() = hero.isNotEmpty()
}

/**
 * Loads the Discover rails from the server instead of slicing an in-memory roster
 * (port of iOS `DiscoverFeedStore`). Each rail is a BOUNDED, indexed
 * `search_artists` query (limit 20, keep the top N), and the four distinct
 * queries fire CONCURRENTLY via `async` so the whole fold is one round-trip's
 * latency, not four. Rail → query mapping mirrors iOS exactly:
 *   • top-by-score (bookability) → hero(5) / featured(8) / Top India(10)
 *   • city-filtered bookability   → Top 10 in Bangalore
 *   • sort=new                    → New on Artistant
 *   • category=Stand-up           → Comedy
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val search: SearchRepository,
    private val artists: ArtistsRepository,
    val saved: SavedStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    /** Launch-market city for the "Top 10 in Bangalore" rail (matches the live facet label). */
    private val topCity = "Bangalore"
    private val comedyCategories = listOf("Stand-up")

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, loadError = null)
            try {
                // Fire the four rails concurrently — each is a bounded index-only query.
                val (top, city, fresh, com) = coroutineScope {
                    val topD = async {
                        search.search(SearchFilters(sort = SearchSort.Bookability), SearchCursor.Start)
                    }
                    val cityD = async {
                        search.search(SearchFilters(city = topCity, sort = SearchSort.Bookability), SearchCursor.Start)
                    }
                    val freshD = async {
                        search.search(SearchFilters(sort = SearchSort.New), SearchCursor.Start)
                    }
                    val comedyD = async {
                        search.search(SearchFilters(categories = comedyCategories, sort = SearchSort.Bookability), SearchCursor.Start)
                    }
                    listOf(topD.await(), cityD.await(), freshD.await(), comedyD.await())
                }

                // Cache every fetched artist so a tapped tile resolves via ensureFull
                // (cache() never downgrades a fully-hydrated profile).
                artists.cache(top.artists + city.artists + fresh.artists + com.artists)

                _state.value = DiscoverUiState(
                    hero = top.artists.take(5),
                    featured = top.artists.take(8),
                    topIndia = top.artists.take(10),
                    topBangalore = city.artists.take(10),
                    newOnArtistant = fresh.artists.take(10),
                    comedy = com.artists.take(10),
                    isLoading = false,
                    loadError = null,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(isLoading = false, loadError = message(t))
            }
        }
    }

    fun toggleSaved(id: String) = saved.toggle(id)

    /** Degrade the "RPC not deployed" case (Release hitting prod before 0044/0045) to clear copy. */
    private fun message(error: Throwable): String {
        val m = (error.message ?: "").lowercase()
        return if (m.contains("could not find the function") || m.contains("search_artists") ||
            m.contains("42883") || m.contains("does not exist")
        ) {
            "We couldn't load the roster right now. Pull to refresh in a moment."
        } else {
            "Something went wrong loading the roster."
        }
    }
}
