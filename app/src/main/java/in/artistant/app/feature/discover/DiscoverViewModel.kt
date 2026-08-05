package `in`.artistant.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SearchFilters
import `in`.artistant.app.data.model.SearchCursor
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.feature.saved.SavedStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Discover home rails — port of iOS `DiscoverFeedStore`.
 *
 * Each rail is a bounded `search_artists` page fired concurrently (top-by-score
 * feeds hero/featured/topIndia; city / new / comedy are separate queries).
 */
data class DiscoverUiState(
    val hero: List<Artist> = emptyList(),
    val featured: List<Artist> = emptyList(),
    val topBangalore: List<Artist> = emptyList(),
    val topIndia: List<Artist> = emptyList(),
    val newOnArtistant: List<Artist> = emptyList(),
    val comedy: List<Artist> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
    /**
     * The user's city, driving the masthead headline. Loaded separately from the
     * rails and never allowed to fail the screen — a missing city downgrades the
     * headline to the national fallback, it does not blank Discover.
     */
    val city: String? = null,
    /** The user's name, for the masthead avatar monogram. */
    val displayName: String? = null,
) {
    /** "Tonight in <X>." — see [DiscoverHeroLogic.mastheadPlace]. */
    val mastheadPlace: String get() = DiscoverHeroLogic.mastheadPlace(city)

    /** Masthead avatar monogram; empty when the user has no name yet. */
    val avatarInitial: String get() = DiscoverHeroLogic.avatarInitial(displayName)
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val artistsRepository: ArtistsRepository,
    private val usersRepository: UsersRepository,
    private val savedStore: SavedStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    /** Saved-heart ids, straight off the shared optimistic store. */
    val savedIds: StateFlow<Set<String>> = savedStore.ids

    init {
        refresh()
        loadIdentity()
    }

    /** Optimistic save/unsave for the hero's heart. */
    fun toggleSaved(artistId: String) = savedStore.toggle(artistId)

    /**
     * Masthead personalisation. Deliberately a separate coroutine from
     * [refresh]: the headline is cosmetic, so a failed profile read must not
     * surface as a roster error or retry the rails. On failure we simply keep
     * the national fallback.
     */
    private fun loadIdentity() {
        viewModelScope.launch {
            val profile = runCatching { usersRepository.fetchSelfProfile() }.getOrNull()
                ?: return@launch
            _state.update { it.copy(city = profile.city, displayName = profile.fullName) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                loadRails()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(isLoading = false, loadError = messageFor(t))
                }
            }
        }
    }

    private suspend fun loadRails() = coroutineScope {
        val topDeferred = async {
            searchRepository.search(SearchFilters(sort = SearchSort.Bookability), SearchCursor.Start)
        }
        val cityDeferred = async {
            searchRepository.search(
                SearchFilters(city = TOP_CITY, sort = SearchSort.Bookability),
                SearchCursor.Start,
            )
        }
        val newDeferred = async {
            searchRepository.search(SearchFilters(sort = SearchSort.New), SearchCursor.Start)
        }
        val comedyDeferred = async {
            searchRepository.search(
                SearchFilters(categories = COMEDY_CATEGORIES, sort = SearchSort.Bookability),
                SearchCursor.Start,
            )
        }
        val top = topDeferred.await()
        val city = cityDeferred.await()
        val fresh = newDeferred.await()
        val comedy = comedyDeferred.await()

        // Cache every fetched artist so profile taps resolve via ensureFull.
        artistsRepository.cache(
            top.artists + city.artists + fresh.artists + comedy.artists,
        )

        _state.update {
            it.copy(
                hero = top.artists.take(5),
                featured = top.artists.take(8),
                topIndia = top.artists.take(10),
                topBangalore = city.artists.take(10),
                newOnArtistant = fresh.artists.take(10),
                comedy = comedy.artists.take(10),
                isLoading = false,
                loadError = null,
            )
        }
    }

    companion object {
        private const val TOP_CITY = "Bangalore"
        private val COMEDY_CATEGORIES = listOf("Stand-up")

        fun messageFor(error: Throwable): String {
            val m = error.message.orEmpty().lowercase()
            if (m.contains("could not find the function") ||
                m.contains("search_artists") ||
                m.contains("42883") ||
                m.contains("does not exist")
            ) {
                return "We couldn't load the roster right now. Pull to refresh in a moment."
            }
            return "Something went wrong loading the roster."
        }
    }
}
