package `in`.artistant.app.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.feature.booking.BookingDraftStore
import `in`.artistant.app.feature.saved.SavedStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistProfileUiState(
    val artist: Artist? = null,
    val reviews: List<Review> = emptyList(),
    val reviewsFailed: Boolean = false,
    val scoreBreakdown: ScoreBreakdown? = null,
    val showScoreSheet: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val selectedPackageIndex: Int = 0,
    val isSaved: Boolean = false,
)

@HiltViewModel
class ArtistProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistsRepository: ArtistsRepository,
    private val reviewsRepository: ReviewsRepository,
    private val scoreRepository: ScoreRepository,
    private val savedStore: SavedStore,
    private val draftStore: BookingDraftStore,
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _state = MutableStateFlow(ArtistProfileUiState())
    val state: StateFlow<ArtistProfileUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            savedStore.ids.collect { ids ->
                _state.update { it.copy(isSaved = artistId.lowercase() in ids) }
            }
        }
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
            var reviewsFailed = false
            val reviews = runCatching { reviewsRepository.listForArtist(artistId) }
                .onFailure { reviewsFailed = true }
                .getOrDefault(emptyList())
            val breakdown = runCatching { scoreRepository.breakdown(artistId) }.getOrNull()
            _state.update {
                it.copy(
                    artist = full,
                    reviews = reviews,
                    reviewsFailed = reviewsFailed,
                    scoreBreakdown = breakdown,
                    selectedPackageIndex = popularIdx,
                    isLoading = false,
                    loadError = null,
                    isSaved = savedStore.contains(artistId),
                )
            }
        }
    }

    fun openScoreSheet() = _state.update { it.copy(showScoreSheet = true) }
    fun dismissScoreSheet() = _state.update { it.copy(showScoreSheet = false) }

    fun selectPackage(index: Int) {
        _state.update { it.copy(selectedPackageIndex = index) }
    }

    /**
     * Hand the tapped tier to the booking screen, called as the client leaves via
     * "Check availability". The booking route carries only the artist id (same as
     * iOS `Route.booking(artist.id)`), so the selection travels through the shared
     * [BookingDraftStore] instead — iOS seeds its booking store in a
     * `simultaneousGesture` on the same CTA for exactly this reason. Seeding
     * unconditionally is safe: [ArtistProfileUiState.selectedPackageIndex] is
     * initialised with the same popular-first rule the booking screen falls back
     * to, so an untouched profile hands over the value booking would have picked.
     */
    fun startBooking() {
        draftStore.seedPackageIndex(artistId, _state.value.selectedPackageIndex)
    }

    fun toggleSaved() {
        savedStore.toggle(artistId)
    }
}
