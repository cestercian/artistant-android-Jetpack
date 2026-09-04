package `in`.artistant.app.feature.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Meter
import `in`.artistant.app.designsystem.component.SectionHeader
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookabilityUiState(
    val artist: Artist? = null,
    val breakdown: ScoreBreakdown? = null,
    /** The breakdown read threw. The page still shows the number it already has. */
    val breakdownFailed: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * The client-facing audit of one artist's Bookability Score.
 *
 * Reads the artist row (for the number, the gig count and the name) and the five
 * weighted metrics separately, because they fail separately — and the screen's
 * whole claim is that the number is auditable, so it must be able to say which
 * half of the audit is missing.
 */
@HiltViewModel
class BookabilityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artists: ArtistsRepository,
    private val scores: ScoreRepository,
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _state = MutableStateFlow(BookabilityUiState())
    val state: StateFlow<BookabilityUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * The in-flight load, and the stamp that decides whether it may commit.
     *
     * Retry is a button, so two loads can be alive at once and can return in
     * either order — the older, slower one finishing last would overwrite the
     * fresher data it was supposed to replace. Cancelling is most of the fix;
     * the stamp closes the rest of the window, because a coroutine cancelled
     * after its last suspension point can still reach the `update`.
     */
    private var loadJob: Job? = null
    private var loadGeneration = 0

    fun refresh() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // The cached tile carries the score and the gig count, which is
            // everything the headline needs — so the page paints before the
            // round-trip and never flashes an empty card.
            artists.find(artistId)?.let { cached -> _state.update { it.copy(artist = cached) } }
            val full = artists.ensureFull(artistId)
            val read = runCatching { scores.breakdown(artistId) }
            if (generation != loadGeneration) return@launch
            _state.update {
                it.copy(
                    artist = full ?: it.artist,
                    breakdown = read.getOrNull(),
                    breakdownFailed = read.isFailure,
                    isLoading = false,
                )
            }
        }
    }
}

/**
 * Screen 16 — "show the arithmetic".
 *
 * The accent card states the number and where it came from; the meters below
 * itemise it. Every input is an event both sides witnessed — a gig that
 * happened, a reply that was sent, a review a host left — which is the whole
 * reason a marketplace score is worth trusting, and the note at the bottom says
 * the other half of it: nothing here can be bought.
 *
 * The itemisation is derived from the published weights and is deliberately not
 * presented as summing to the total (see [ScoreFactors]).
 */
@Composable
fun BookabilityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val artist = state.artist
    val breakdown = state.breakdown
    // The headline's two halves come from ONE source, whichever answered.
    //
    // They used to be picked independently — the tier from the breakdown, the
    // number from the artist row — so a profile read that returned nothing while
    // the metrics read succeeded rendered the tier of a real score above a
    // headline that said "New / no score yet". Two rows disagreeing about the
    // same artist, on the screen whose whole claim is that the number is
    // auditable. `numericScore` is null on the New tier, which is what keeps a
    // <5-gig artist from being shown their `score` column's 0.
    val score: Int? = when {
        breakdown != null -> breakdown.numericScore
        artist != null ->
            artist.score.takeIf { ScoreBands.tier(it, artist.gigs) != ScoreTier.New }
        else -> null
    }
    val tier = breakdown?.tier
        ?: artist?.let { ScoreBands.tier(it.score, it.gigs) }
        ?: ScoreTier.New
    // Neither read answered. There is no artist, no score and no breakdown —
    // rendering the "New" headline here would invent a tier for a page that
    // knows nothing at all.
    val nothingLoaded = artist == null && breakdown == null && !state.isLoading

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            title = "Bookability score",
            subtitle = artist?.name,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter)
                .padding(top = space.lg),
            verticalArrangement = Arrangement.spacedBy(space.xl),
        ) {
            if (artist == null && breakdown == null && state.isLoading) {
                SkeletonBlock(
                    Modifier.fillMaxWidth().height(dimens.component.skeletonTile),
                    radius = dimens.radii.xl,
                )
                return@Column
            }
            if (nothingLoaded) {
                EmptyState(
                    icon = Icons.Filled.Insights,
                    title = "Couldn't load this score",
                    body = "We couldn't reach this artist's record or their " +
                        "breakdown. Nothing about their score has changed.",
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
                return@Column
            }

            ScoreHeadline(score = score, tier = tier)

            if (state.breakdownFailed) {
                Banner(
                    title = "Couldn't load the breakdown",
                    detail = "The score above is the one on this artist's record. " +
                        "We just couldn't fetch what it is made of.",
                    tone = BannerTone.Failure,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }

            SectionHeader("What moves it")
            Column(verticalArrangement = Arrangement.spacedBy(space.lg)) {
                if (breakdown != null) {
                    ScoreFactors.of(breakdown).forEach { factor ->
                        Meter(
                            label = factor.label,
                            fraction = factor.fraction,
                            value = factor.display,
                        )
                    }
                } else {
                    // Every factor, every one of them unavailable — the same
                    // shape the loaded page has, so the reader can see what is
                    // missing rather than a shorter page that looks complete.
                    ScoreFactors.labels.forEach { label ->
                        Meter(label = label, fraction = null)
                    }
                }
            }

            AccentNote(
                text = "Cancelling a confirmed booking is the only thing that pulls " +
                    "this down, and nothing on this screen can be bought.",
            )

            // Gigs from the same source that answered for the headline — the
            // breakdown carries `total_gigs` too, so a page drawn from the
            // metrics alone still has the count.
            val gigs = breakdown?.totalGigs ?: artist?.gigs
            if (gigs != null) {
                HRule()
                Row(
                    Modifier.fillMaxWidth().padding(top = space.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (gigs == 1) "1 show on Artistant" else "$gigs shows on Artistant",
                        style = AppTheme.type.subtitle,
                        color = colors.ink4,
                    )
                    Text(
                        "Recalculated after every set",
                        style = AppTheme.type.subtitle,
                        color = colors.ink4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(space.xl))
        }
    }
}

/**
 * The accent card: the number, what it is out of, and one sentence of where it
 * came from.
 *
 * A New-tier artist gets the word instead of the figure — the `score` column is
 * usually 0 for them and printing that is the misreading screen 79 exists to
 * prevent.
 */
@Composable
private fun ScoreHeadline(score: Int?, tier: ScoreTier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.xl))
            .background(colors.accent)
            .padding(dimens.space.lg),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = score?.toString() ?: "New",
                style = AppTheme.type.displayHero,
                color = colors.onAccent,
            )
            Text(
                text = if (score != null) "/ 100 · ${tier.label}" else "no score yet",
                style = AppTheme.type.subtitle,
                color = colors.onAccent.copy(alpha = ON_ACCENT_SOFT),
                modifier = Modifier.padding(bottom = dimens.space.xs),
            )
        }
        Text(
            text = if (score != null) {
                "Built from what actually happened on past bookings on Artistant."
            } else {
                "Five completed gigs and this becomes a number. Until then it is " +
                    "the New tier — not a low score, no score."
            },
            style = AppTheme.type.caption,
            color = colors.onAccent.copy(alpha = ON_ACCENT_SOFT),
        )
    }
}

private const val ON_ACCENT_SOFT = 0.7f
