package `in`.artistant.app.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.state.SavedStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The artist-profile screen states (iOS `ArtistRouteLoader` + `ArtistView`).
 * [Loaded] carries the full artist plus the two async-loaded sections (reviews,
 * score breakdown), each with its own honest failure flag so a failed reviews
 * fetch renders "couldn't load", never "none yet".
 */
sealed interface ArtistProfileUiState {
    data object Loading : ArtistProfileUiState
    data object NotFound : ArtistProfileUiState
    data class Error(val message: String) : ArtistProfileUiState
    data class Loaded(
        val artist: Artist,
        val reviews: List<Review> = emptyList(),
        val reviewsFailed: Boolean = false,
        val breakdown: ScoreBreakdown? = null,
    ) : ArtistProfileUiState
}

/**
 * Resolves the FULL artist by id (fetch-on-miss via [ArtistsRepository.fetchArtist])
 * then fires the two profile section loads in parallel (iOS `ArtistView`'s
 * `.task` fan-out). Media + samples already ride on the stitched [Artist]
 * (M2a), so only reviews + score need a round-trip here. Errors are per-section
 * so one failure never blanks the whole page.
 */
@HiltViewModel
class ArtistProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artists: ArtistsRepository,
    private val reviewsRepo: ReviewsRepository,
    private val scoreRepo: ScoreRepository,
    val saved: SavedStore,
) : ViewModel() {

    // Type-safe Navigation stores `ClientRoute.ArtistProfile(artistId)`'s arg
    // under its property name, so reading the handle key directly resolves it in
    // production AND keeps the VM unit-testable (no nav-runtime decode needed).
    private val artistId: String = savedStateHandle.get<String>("artistId").orEmpty()

    private val _state = MutableStateFlow<ArtistProfileUiState>(ArtistProfileUiState.Loading)
    val state: StateFlow<ArtistProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = ArtistProfileUiState.Loading
            // fetchArtist (not ensureFull) so a transport error surfaces as Error;
            // a null (unpublished / RLS-hidden / stale link) surfaces as NotFound.
            val artist: Artist? = try {
                artists.fetchArtist(artistId)
            } catch (t: Throwable) {
                _state.value = ArtistProfileUiState.Error(t.message ?: "Couldn't load this artist.")
                return@launch
            }
            if (artist == null) {
                _state.value = ArtistProfileUiState.NotFound
                return@launch
            }
            _state.value = ArtistProfileUiState.Loaded(artist)
            loadSections(artist)
        }
    }

    private suspend fun loadSections(artist: Artist) = coroutineScope {
        val reviewsD = async { runCatching { reviewsRepo.listForArtist(artist.id) } }
        val scoreD = async { runCatching { scoreRepo.breakdown(artist.id) } }

        reviewsD.await().fold(
            onSuccess = { rows -> updateLoaded { it.copy(reviews = rows, reviewsFailed = false) } },
            onFailure = { updateLoaded { it.copy(reviews = emptyList(), reviewsFailed = true) } },
        )
        scoreD.await().onSuccess { b -> updateLoaded { it.copy(breakdown = b) } }
    }

    /** Copy into the current Loaded state, ignoring the update if we've navigated off it. */
    private inline fun updateLoaded(block: (ArtistProfileUiState.Loaded) -> ArtistProfileUiState.Loaded) {
        _state.update { s -> if (s is ArtistProfileUiState.Loaded) block(s) else s }
    }

    fun toggleSaved() = saved.toggle(artistId)
}
