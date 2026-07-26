package `in`.artistant.app.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistProfileUiState(
    val artist: Artist? = null,
    val reviews: List<Review> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val selectedPackageIndex: Int = 0,
)

@HiltViewModel
class ArtistProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistsRepository: ArtistsRepository,
    private val reviewsRepository: ReviewsRepository,
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _state = MutableStateFlow(ArtistProfileUiState())
    val state: StateFlow<ArtistProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            val cached = artistsRepository.find(artistId)
            if (cached != null) {
                _state.update { it.copy(artist = cached) }
            }
            val full = artistsRepository.ensureFull(artistId)
            if (full == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        loadError = if (it.artist == null) "Artist not found." else null,
                    )
                }
                return@launch
            }
            val popularIdx = full.packages.indexOfFirst { it.popular }.takeIf { it >= 0 } ?: 0
            val reviews = runCatching { reviewsRepository.listForArtist(artistId) }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    artist = full,
                    reviews = reviews,
                    selectedPackageIndex = popularIdx,
                    isLoading = false,
                    loadError = null,
                )
            }
        }
    }

    fun selectPackage(index: Int) {
        _state.update { it.copy(selectedPackageIndex = index) }
    }
}
