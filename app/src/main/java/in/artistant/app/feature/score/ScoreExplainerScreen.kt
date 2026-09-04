package `in`.artistant.app.feature.score

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.Meter
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.SegmentedControl
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.feature.messages.ViewerIdentity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The three views of one's own score (screens 50 / 79 / 80). */
enum class ScoreTab(val label: String) {
    Score("Score"),
    Stats("Stats"),
    Opportunities("Opportunities"),
}

data class ScoreExplainerUiState(
    val breakdown: ScoreBreakdown = ScoreBreakdown.NewArtist,
    val history: List<ScoreHistoryPoint> = emptyList(),
    /**
     * The history read THREW, as opposed to returning nothing.
     *
     * Both arrive as an empty list and they mean opposite things: "no gigs have
     * landed yet" against "we couldn't ask". Flattening them hid the History
     * section entirely on a transport/RLS failure and made "No history yet" a
     * statement we hadn't earned.
     */
    val historyFailed: Boolean = false,
    /**
     * The artist's own record, for the Opportunities tab.
     *
     * Nullable and subordinate: the score is the screen, and a failed profile
     * read must not blank it. What it costs is the profile-completeness half of
     * the advice, which simply is not offered rather than being guessed at.
     */
    val artist: Artist? = null,
    val isLoading: Boolean = true,
    /** The BREAKDOWN read failed — screen 80. Not "an error occurred". */
    val failed: Boolean = false,
)

@HiltViewModel
class ScoreExplainerViewModel @Inject constructor(
    private val scores: ScoreRepository,
    private val artists: ArtistsRepository,
    private val viewer: ViewerIdentity,
) : ViewModel() {
    private val _state = MutableStateFlow(ScoreExplainerUiState())
    val state: StateFlow<ScoreExplainerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * The in-flight load, and the stamp that decides whether it may commit.
     *
     * Retry is a button and this screen fires three reads per load, so two loads
     * can easily be alive at once and finish out of order — the older one
     * landing last would overwrite the fresher score with a stale one. Cancelling
     * is most of the fix; the stamp closes the rest of the window, because a
     * coroutine cancelled after its last suspension point can still reach the
     * `update`.
     */
    private var loadJob: Job? = null
    private var loadGeneration = 0

    fun refresh() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, failed = false) }
            val breakdown = runCatching { scores.breakdownForSelf() }
            // Both subordinate reads happen either way: the history sparkline and
            // the "finish your profile" rows are useful even when the metrics
            // fetch failed, and re-running them behind a second Retry would be a
            // second round-trip for data this one already has.
            val history = runCatching { scores.historyForSelf() }
            val artist = viewer.currentUserId()?.let { id -> artists.ensureFull(id) }
            if (generation != loadGeneration) return@launch
            _state.update {
                it.copy(
                    breakdown = breakdown.getOrDefault(ScoreBreakdown.NewArtist),
                    failed = breakdown.isFailure,
                    history = history.getOrDefault(emptyList()),
                    historyFailed = history.isFailure,
                    artist = artist ?: it.artist,
                    isLoading = false,
                )
            }
        }
    }
}

/**
 * The artist's own Bookability Score — design screens 50 / 79 / 80.
 *
 * Three tabs over one subject. **Score** is the number and what it means;
 * **Stats** is the itemisation; **Opportunities** is what to do next, and every
 * row of it opens the editor it is about, because advice that dead-ends in a
 * lecture is the failure mode screen 50 is written against.
 *
 * Two states change the whole page rather than a section of it:
 *
 *  - **New** (79): under five completed gigs there is no ranked score. The page
 *    says so outright — "not a low score, no score" — and shows the gig counter
 *    instead, because an artist who reads "0" leaves.
 *  - **Failed** (80): the headline is "This isn't your real score". A dash on
 *    its own is read as a penalty, so the copy has to out-rank the glyph.
 */
@Composable
fun ScoreExplainerScreen(
    onBack: () -> Unit,
    onOpenEditor: (ScoreEditor) -> Unit = {},
    onSeeHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ScoreExplainerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    var tab by rememberSaveable { mutableStateOf(ScoreTab.Score) }
    val tier = ScoreBands.tier(state.breakdown.score, state.breakdown.totalGigs)
    val isNew = tier == ScoreTier.New

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            title = "Bookability Score",
            subtitle = ScoreTab.entries.joinToString(" · ") { it.label },
            onBack = onBack,
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
        )
        SegmentedControl(
            options = ScoreTab.entries,
            selected = tab,
            onSelect = { tab = it },
            label = { it.label },
            modifier = Modifier
                .padding(horizontal = dimens.component.gutter)
                .padding(top = space.md),
        )
        RevealOnAppear(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.component.gutter)
                    .padding(top = space.xl),
                verticalArrangement = Arrangement.spacedBy(space.lg),
            ) {
                if (state.isLoading && state.breakdown == ScoreBreakdown.NewArtist) {
                    SkeletonBlock(
                        Modifier.fillMaxWidth().height(dimens.component.skeletonTile),
                        radius = dimens.radii.xl,
                    )
                    return@Column
                }
                when (tab) {
                    ScoreTab.Score -> ScoreTabContent(
                        state = state,
                        isNew = isNew,
                        tier = tier,
                        onRetry = viewModel::refresh,
                    )
                    ScoreTab.Stats -> StatsTabContent(state = state)
                    ScoreTab.Opportunities -> OpportunitiesTabContent(
                        state = state,
                        onOpenEditor = onOpenEditor,
                    )
                }
                Spacer(Modifier.height(space.xl))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = dimens.component.gutter)
                .padding(bottom = space.lg, top = space.sm),
        ) {
            when {
                state.failed -> PrimaryButton("Retry", viewModel::refresh, fullWidth = true)
                tab == ScoreTab.Score -> PrimaryButton(
                    text = "See what counts",
                    onClick = { tab = ScoreTab.Stats },
                    fullWidth = true,
                )
                else -> PrimaryButton(
                    text = "See score history",
                    onClick = onSeeHistory,
                    fullWidth = true,
                )
            }
        }
    }
}

// ── Score (79 / 80) ─────────────────────────────────────────────────────────

@Composable
private fun ScoreTabContent(
    state: ScoreExplainerUiState,
    isNew: Boolean,
    tier: ScoreTier,
    onRetry: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val b = state.breakdown

    if (state.failed) {
        Banner(
            title = "Couldn't load your score",
            detail = "We hit a problem fetching your breakdown.",
            tone = BannerTone.Failure,
            actionLabel = "Retry",
            onAction = onRetry,
        )
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ScoreDonut(
            value = if (isNew || state.failed) null else b.score,
            caption = when {
                state.failed -> "unavailable"
                isNew -> "no score yet"
                else -> "of 100 · ${tier.label}"
            },
            unavailable = state.failed,
        )
    }
    if (state.failed) {
        Text(
            "This isn't your real score",
            style = AppTheme.type.sectionTitle,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Nothing has changed on your account. We just couldn't fetch it right now.",
            style = AppTheme.type.body,
            color = colors.ink4,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(space.sm))
        // The same five rows the loaded page has, all unavailable — a shorter
        // page would look complete.
        ScoreFactors.labels.forEach { label -> Meter(label = label, fraction = null) }
        return
    }

    if (isNew) {
        Text(
            "${ScoreBands.MIN_GIGS_FOR_RANK} completed gigs and you get a number. " +
                "Until then hosts see the New tier — it isn't a low score, it's no score.",
            style = AppTheme.type.body,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(space.xs))
        GigProgress(done = b.totalGigs, target = ScoreBands.MIN_GIGS_FOR_RANK)
        AccentNote(
            text = "Reply speed is already being counted — it starts from your very " +
                "first request, not from gig ${ScoreBands.MIN_GIGS_FOR_RANK}.",
        )
    } else {
        Text(
            "Recomputed on every review, completed booking or cancellation. Nothing " +
                "on this screen can be bought.",
            style = AppTheme.type.body,
            color = colors.ink2,
        )
        AccentNote(
            text = "Hosts see this number on your profile, and the same breakdown " +
                "you see under Stats.",
        )
    }
}

/**
 * "GIGS COMPLETED — n of 5", as five segments.
 *
 * Segments rather than a single bar because the target is five discrete events,
 * and a continuous bar invites the reader to wonder what 40% of a gig is.
 */
@Composable
private fun GigProgress(done: Int, target: Int) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val filled = done.coerceIn(0, target)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EyebrowLabel("Gigs completed")
            Text(
                "$filled of $target",
                style = AppTheme.type.monoPill,
                color = colors.ink,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            repeat(target) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(dimens.space.sm)
                        .clip(RoundedCornerShape(dimens.radii.sm))
                        .background(if (index < filled) colors.accent else colors.hairline),
                )
            }
        }
    }
}

// ── Stats ───────────────────────────────────────────────────────────────────

@Composable
private fun StatsTabContent(state: ScoreExplainerUiState) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    EyebrowLabel("What goes into it")
    Column(verticalArrangement = Arrangement.spacedBy(space.lg)) {
        ScoreFactors.of(state.breakdown).forEach { factor ->
            Meter(
                label = factor.label,
                fraction = if (state.failed) null else factor.fraction,
                value = if (state.failed) null else factor.display,
            )
        }
    }
    Spacer(Modifier.height(space.sm))
    replyDurationLabel(state.breakdown.replySpeed)?.let { average ->
        Text(
            "You answer in $average on average.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
    Text(
        "Weights are fixed at ${ScoreFactors.SHOW_UP_WEIGHT} / " +
            "${ScoreFactors.REVIEWS_WEIGHT} / ${ScoreFactors.REPLY_WEIGHT} / " +
            "${ScoreFactors.RELIABILITY_WEIGHT} / ${ScoreFactors.SOCIAL_WEIGHT}. " +
            "The total is the server's own number, recomputed after every set.",
        style = AppTheme.type.caption,
        color = colors.ink4,
    )
}

// ── Opportunities (50) ──────────────────────────────────────────────────────

@Composable
private fun OpportunitiesTabContent(
    state: ScoreExplainerUiState,
    onOpenEditor: (ScoreEditor) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val wins = ScoreOpportunities.of(state.breakdown, state.artist)

    Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
        Text(
            "Small wins",
            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
        )
        Text(
            "Optional touches that help clients say yes. Each one opens the thing " +
                "it edits.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
    Spacer(Modifier.height(space.xs))
    if (wins.isEmpty()) {
        Text(
            if (state.artist == null) {
                "We couldn't read your profile just now, so there's nothing to " +
                    "suggest. Your score is unaffected."
            } else {
                "Nothing outstanding — your profile is complete and every factor " +
                    "is at full marks."
            },
            style = AppTheme.type.body,
            color = colors.ink3,
        )
        return
    }
    wins.forEach { win -> OpportunityRow(win, onOpenEditor) }
}

/**
 * One win. Every row opens something — that is [ScoreEditor]'s contract, and it
 * is why the whole card is the tap target and the chevron is unconditional. The
 * "+N" pill is the part that varies: it appears only where the number is real,
 * i.e. points still unearned on a published factor.
 */
@Composable
private fun OpportunityRow(win: ScoreOpportunity, onOpenEditor: (ScoreEditor) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.surface3)
            .clickable { onOpenEditor(win.editor) }
            .padding(dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        win.points?.let { points ->
            Text(
                "+$points",
                style = AppTheme.type.monoPill.copy(fontWeight = FontWeight.Bold),
                color = colors.onAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .background(colors.accent)
                    .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs),
            )
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
        ) {
            Text(
                win.title,
                style = AppTheme.type.rowTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(win.detail, style = AppTheme.type.caption, color = colors.ink4)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.lineStrong,
            modifier = Modifier.size(dimens.size.iconMd),
        )
    }
}
