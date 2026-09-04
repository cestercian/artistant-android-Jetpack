package `in`.artistant.app.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SearchBar
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ArtistReviewsUiState(
    val artistName: String = "",
    /** The whole corpus. Every count on the screen is taken from THIS list. */
    val reviews: List<Review> = emptyList(),
    val failed: Boolean = false,
    val isLoading: Boolean = true,
    val query: String = "",
    val lens: ReviewLens = ReviewLens.All,
)

@HiltViewModel
class ArtistReviewsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artists: ArtistsRepository,
    private val reviews: ReviewsRepository,
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _state = MutableStateFlow(ArtistReviewsUiState())
    val state: StateFlow<ArtistReviewsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * The in-flight load, and the stamp that decides whether it may commit.
     *
     * Retry is a button, so two reads can be alive at once and can return in
     * either order — the older one landing last would replace a loaded corpus
     * with a stale one, and on this screen the corpus size is quoted in the
     * copy. Cancelling is most of the fix; the stamp closes the rest of the
     * window, because a coroutine cancelled after its last suspension point can
     * still reach the `update`.
     */
    private var loadJob: Job? = null
    private var loadGeneration = 0

    fun refresh() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val name = artists.find(artistId)?.name ?: artists.ensureFull(artistId)?.name
            val read = runCatching { reviews.listForArtist(artistId) }
            if (generation != loadGeneration) return@launch
            _state.update {
                it.copy(
                    artistName = name ?: it.artistName,
                    reviews = read.getOrDefault(emptyList()),
                    // A failed read and an unreviewed artist are the same empty
                    // list and say opposite things about the artist (screen 100).
                    failed = read.isFailure,
                    isLoading = false,
                )
            }
        }
    }

    fun search(query: String) = _state.update { it.copy(query = query) }
    fun clearSearch() = _state.update { it.copy(query = "") }
    fun select(lens: ReviewLens) = _state.update { it.copy(lens = lens) }
}

/**
 * Every review for one artist — design screen 102.
 *
 * **Search-within-empty is a different screen from empty.** When a query returns
 * nothing, the page states the corpus size ("nothing in the 121 reviews matches
 * …") and offers to clear the search, because the reader's real question is
 * whether this artist has reviews at all — and the answer is yes. An artist who
 * genuinely has none gets the other copy, and a read that FAILED gets neither:
 * it gets screen 100's banner and a retry, since claiming "no reviews" for a
 * dropped request is a false claim about the artist on the page a client decides
 * from.
 *
 * The corpus size quoted anywhere on this screen is always the unfiltered list's
 * size. Quoting the filtered one would make the sentence circular.
 */
@Composable
fun ArtistReviewsScreen(
    onBack: () -> Unit,
    onRequestQuote: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistReviewsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val today = remember { LocalDate.now() }
    val shown = remember(state.reviews, state.query, state.lens, today) {
        ReviewSearch.apply(state.reviews, state.query, state.lens, today)
    }
    val total = state.reviews.size

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            title = "Reviews",
            subtitle = when {
                state.failed -> "count unavailable"
                total == 0 -> null
                else -> "$total for ${state.artistName}".trim()
            },
            onBack = onBack,
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
        )
        if (!state.failed && total > 0) {
            SearchBar(
                value = state.query,
                onValueChange = viewModel::search,
                hint = "Search these reviews",
                onClear = viewModel::clearSearch,
                modifier = Modifier
                    .padding(horizontal = dimens.component.gutter)
                    .padding(top = space.md),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = dimens.component.gutter)
                    .padding(top = space.md),
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                ReviewLens.entries.forEach { lens ->
                    Chip(
                        label = ReviewSearch.chipLabel(lens, total),
                        selected = lens == state.lens,
                        onClick = { viewModel.select(lens) },
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                state.isLoading && state.reviews.isEmpty() -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimens.component.gutter)
                        .padding(top = space.lg),
                    verticalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    repeat(SKELETON_CARDS) {
                        SkeletonBlock(
                            Modifier.fillMaxWidth().height(dimens.component.skeletonTile),
                            radius = dimens.radii.buttonLg,
                        )
                    }
                }

                state.failed -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimens.component.gutter)
                        .padding(top = space.lg),
                ) {
                    Banner(
                        title = "Couldn't load reviews",
                        detail = "This artist may have reviews — we just couldn't " +
                            "load them. Don't read this as \"no reviews\".",
                        tone = BannerTone.Failure,
                        actionLabel = "Retry",
                        onAction = viewModel::refresh,
                    )
                }

                total == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Filled.RateReview,
                        title = "No reviews yet",
                        body = "Only a host who actually booked this artist can " +
                            "leave one, so the first review lands after the first show.",
                    )
                }

                shown.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Two different empties, and the reader must be able to tell
                    // which rule emptied the list. A query gets the corpus-size
                    // sentence (screen 102's note); a lens with no query gets
                    // that LENS's own explanation — the copy used to describe the
                    // Recent window whatever was selected, which is a false
                    // statement about the corpus when the lens is "5 star".
                    val lensCopy = ReviewSearch.lensEmpty(state.lens, total)
                    val searching = state.query.isNotBlank()
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = if (searching) "No reviews mention that" else lensCopy.title,
                        body = if (searching) {
                            "Nothing in the $total reviews matches " +
                                "\"${state.query.trim()}\". Clear the search to see " +
                                "them all."
                        } else {
                            lensCopy.body
                        },
                        actionLabel = if (searching) "Clear search" else "Show all",
                        onAction = {
                            viewModel.clearSearch()
                            viewModel.select(ReviewLens.All)
                        },
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = dimens.component.gutter,
                        end = dimens.component.gutter,
                        top = space.lg,
                        bottom = space.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    items(shown, key = { it.id }) { review -> ReviewCard(review) }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = dimens.component.gutter)
                .padding(bottom = space.lg, top = space.sm),
        ) {
            PrimaryButton(
                text = "Request a quote",
                onClick = onRequestQuote,
                fullWidth = true,
            )
        }
    }
}

private const val SKELETON_CARDS = 4
