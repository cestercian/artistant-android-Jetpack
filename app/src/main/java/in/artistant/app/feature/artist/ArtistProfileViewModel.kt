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
    /**
     * True once a full stitch has landed, i.e. once an empty
     * [Artist.packages] is a FACT about the artist rather than a mid-load
     * state.
     *
     * [artist] goes non-null the moment the cached Discover/Search tile is
     * found — before the profile round-trip returns — and that projection
     * carries no packages beside a real `min_price`. Without this flag the dock
     * asked `PackagePricing.dockPrice` with `packagesLoaded = true` against
     * that tile and quoted "On request / PRICING" for an artist with tiers,
     * then snapped to a price; on a failed stitch it stayed wrong for good.
     *
     * Never reset by a later failed refresh: the packages already on screen
     * came from a stitch, and a dropped packet does not un-load them.
     */
    val packagesLoaded: Boolean = false,
    val reviews: List<Review> = emptyList(),
    val reviewsFailed: Boolean = false,
    val scoreBreakdown: ScoreBreakdown? = null,
    val showScoreSheet: Boolean = false,
    val isLoading: Boolean = true,
    /**
     * Non-null means **what is on screen is not a loaded profile** — either
     * nothing at all, or the Discover/Search tile projection that arrived in the
     * cache. Never null just because [artist] is non-null: see
     * [artistProfileLoadError].
     */
    val loadError: String? = null,
    val selectedPackageIndex: Int = 0,
    val isSaved: Boolean = false,
)

/** The read failed in transport / RLS — we do not know what this artist offers. */
internal const val PROFILE_LOAD_FAILED = "Couldn't load this profile."

/** The server answered, and there is no such artist to show. */
internal const val ARTIST_NOT_FOUND = "Artist not found."

/**
 * What the profile says when a hydration attempt did **not** produce a full artist.
 *
 * Called only on that path, so it never returns null: a page drawn from anything
 * less than a hydrated row has something to admit. That is the fix. `ensureFull`
 * folds every transport failure into the same null the server returns for "no
 * such row", and the screen used to read that null as "nothing to report" as soon
 * as a cached artist existed — which it does for every client arriving from
 * Discover or Search, because both `cache()` the tile projection first. A dropped
 * packet therefore rendered the whole profile from a tile that by construction
 * carries no packages, bio, samples or reviews, and every section stated its own
 * emptiness as fact: "Pricing on request", a blank About, "No reviews yet." On a
 * booking marketplace that is misquoting an artist after a network blip.
 *
 * [hasCachedArtist] takes [PROFILE_LOAD_FAILED] whatever the cause, because
 * "Artist not found." next to a rendered artist name reads as a contradiction
 * rather than as a warning — and from this client's seat a row it cannot read is
 * a row it cannot show, whether the network dropped it or RLS did.
 */
internal fun artistProfileLoadError(fetchFailed: Boolean, hasCachedArtist: Boolean): String =
    if (fetchFailed || hasCachedArtist) PROFILE_LOAD_FAILED else ARTIST_NOT_FOUND

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
            // `fetchArtist`, not `ensureFull`: the convenience wrapper swallows the
            // throw, and "the read failed" has to stay distinguishable from "there
            // is no such artist" — the two say opposite things about the tile this
            // page may already be drawn from. See [artistProfileLoadError].
            val fetched = runCatching { artistsRepository.fetchArtist(artistId) }
            val full = fetched.getOrNull()
            if (full == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        loadError = artistProfileLoadError(
                            fetchFailed = fetched.isFailure,
                            hasCachedArtist = it.artist != null,
                        ),
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
                    packagesLoaded = true,
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
