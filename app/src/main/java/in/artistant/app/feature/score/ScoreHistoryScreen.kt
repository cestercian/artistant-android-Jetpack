package `in`.artistant.app.feature.score

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScoreHistoryUiState(
    val history: List<ScoreHistoryPoint> = emptyList(),
    val failed: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ScoreHistoryViewModel @Inject constructor(
    private val scores: ScoreRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ScoreHistoryUiState())
    val state: StateFlow<ScoreHistoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * The in-flight load, and the stamp that decides whether it may commit.
     *
     * Retry is a button, so two reads can be alive at once and can return in
     * either order — an older, slower one finishing last would overwrite the
     * fresher ledger it was supposed to replace, and on this screen that means
     * showing yesterday's deltas as today's. Cancelling is most of the fix; the
     * stamp closes the rest of the window, because a coroutine cancelled after
     * its last suspension point can still reach the `update`.
     */
    private var loadJob: Job? = null
    private var loadGeneration = 0

    fun refresh() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val read = runCatching { scores.historyForSelf() }
            if (generation != loadGeneration) return@launch
            _state.update {
                it.copy(
                    history = read.getOrDefault(emptyList()),
                    // Empty and unreadable are the same list and the opposite claim.
                    failed = read.isFailure,
                    isLoading = false,
                )
            }
        }
    }
}

/**
 * Screen 51 — **the ledger, not the number**.
 *
 * `score_history` stores `(score, computed_at)` and nothing else, so what this
 * screen can honestly show is a per-*recomputation* delta: the score moved from
 * X to Y on this date, by this much. The design's per-*event* rows ("Review from
 * Rhea Menon · +3") would need a reason column the table does not have, and
 * inventing one would be the marketplace attributing a penalty to a gig that may
 * not be the cause. The rule that produced each row is stated at the bottom
 * instead — the decay window and the New-tier floor are real and knowable.
 *
 * The chart is bars, not a line: each point is a discrete recomputation, and a
 * line between them implies a value on the days in between that the table never
 * recorded.
 */
@Composable
fun ScoreHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScoreHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val points = state.history
    val scores = points.map { it.score }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            title = "Score history",
            subtitle = if (points.isEmpty()) null else "Last ${points.size} recomputations",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter)
                .padding(top = space.lg),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            when {
                state.isLoading && points.isEmpty() -> SkeletonBlock(
                    Modifier.fillMaxWidth().height(dimens.component.skeletonTile),
                    radius = dimens.radii.lg,
                )

                state.failed -> Banner(
                    title = "Couldn't load your history",
                    detail = "Your score itself is unaffected — this is the ledger " +
                        "behind it, and we couldn't read it just now.",
                    tone = BannerTone.Failure,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )

                points.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Timeline,
                    title = "No recomputations yet",
                    body = "Your score is recalculated after a review, a completed " +
                        "booking or a cancellation. The first one will appear here.",
                )

                else -> {
                    HistoryHeadline(scores = scores)
                    ScoreBars(values = scores)
                    EyebrowLabel("Every recomputation")
                    Column {
                        // Newest first: the reader is auditing what just happened,
                        // and the oldest point has no predecessor to differ from.
                        points.indices.reversed().forEach { index ->
                            val point = points[index]
                            val delta = if (index == 0) {
                                null
                            } else {
                                point.score - points[index - 1].score
                            }
                            HistoryRow(
                                delta = delta,
                                score = point.score,
                                date = point.computedAtIso.take(ISO_DATE_LENGTH),
                                showHairline = index > 0,
                            )
                        }
                    }
                }
            }
            Text(
                "Recomputed on every review, completed booking or cancellation. " +
                    "This view keeps the last 12 months. Under " +
                    "${ScoreBands.MIN_GIGS_FOR_RANK} completed gigs stays on the New " +
                    "tier whatever the number says.",
                style = AppTheme.type.caption,
                color = colors.ink4,
            )
            Spacer(Modifier.navigationBarsPadding().height(space.lg))
        }
    }
}

/** Today's score and how far it has moved across the whole window. */
@Composable
private fun HistoryHeadline(scores: List<Int>) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val latest = scores.lastOrNull() ?: return
    val delta = if (scores.size >= 2) latest - scores.first() else null
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                latest.toString(),
                style = AppTheme.type.displayHero,
                color = colors.ink,
            )
            Text("today", style = AppTheme.type.caption, color = colors.ink4)
        }
        if (delta != null) {
            DeltaPill(delta, "over ${scores.size} recomputations")
        }
    }
}

/**
 * A signed delta as a pill. Up is the accent, down is `danger`, flat is quiet —
 * and each carries its own arrow, so the sign survives for a reader who cannot
 * separate the two fills.
 */
@Composable
private fun DeltaPill(delta: Int, qualifier: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val icon = when {
        delta > 0 -> Icons.AutoMirrored.Filled.TrendingUp
        delta < 0 -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    val tint = when {
        delta > 0 -> colors.accentDeep
        delta < 0 -> colors.danger
        else -> colors.ink3
    }
    val fill = when {
        delta > 0 -> colors.accent.copy(alpha = DELTA_PILL_ALPHA)
        delta < 0 -> colors.dangerSoft
        else -> colors.surface3
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(fill)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.xs),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(dimens.size.iconSm),
        )
        Text(
            "${signed(delta)} $qualifier",
            style = AppTheme.type.badge,
            color = tint,
            maxLines = 1,
        )
    }
}

private const val DELTA_PILL_ALPHA = 0.4f

/**
 * The window as bars, the most recent [RECENT_BARS] in the accent.
 *
 * Bars are scaled against the window's own range rather than against 0–100: a
 * career that has moved between 78 and 86 is a flat wall of near-identical bars
 * on an absolute scale, which says nothing. The floor is the window's minimum
 * minus one step so the lowest bar is still visible.
 */
@Composable
private fun ScoreBars(values: List<Int>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shown = values.takeLast(MAX_BARS)
    val top = shown.max()
    val floor = (shown.min() - 1).coerceAtLeast(0)
    val span = (top - floor).coerceAtLeast(1)
    val chartHeight = dimens.dashboard.chartHeight
    val recentFrom = (shown.size - RECENT_BARS).coerceAtLeast(0)

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.lg))
            .background(colors.surface3)
            .padding(dimens.space.md)
            .height(chartHeight),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs / 2),
        verticalAlignment = Alignment.Bottom,
    ) {
        shown.forEachIndexed { index, value ->
            val ratio = ((value - floor).toFloat() / span).coerceIn(MIN_BAR, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height(chartHeight * ratio)
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .background(if (index >= recentFrom) colors.accent else colors.lineStrong),
            )
        }
    }
}

private const val MAX_BARS = 20
private const val RECENT_BARS = 3
private const val MIN_BAR = 0.08f
private const val ISO_DATE_LENGTH = 10

/** One recomputation: the delta, what it landed on, and when. */
@Composable
private fun HistoryRow(delta: Int?, score: Int, date: String, showHairline: Boolean) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (showHairline) Modifier.hairlineBottom() else Modifier)
            .padding(vertical = dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = delta?.let { signed(it) } ?: "·",
            style = AppTheme.type.monoPill.copy(fontWeight = FontWeight.Bold),
            color = when {
                delta == null -> colors.ink4
                delta > 0 -> colors.accentDeep
                delta < 0 -> colors.danger
                else -> colors.ink3
            },
            modifier = Modifier.width(dimens.size.listThumbW / 2),
        )
        Column(Modifier.weight(1f)) {
            Text(
                // The first point has nothing before it, so it states the value
                // rather than a change it cannot compute.
                if (delta == null) "First recorded score: $score" else "Score moved to $score",
                style = AppTheme.type.rowTitle,
                color = colors.ink,
            )
            Text(date, style = AppTheme.type.caption, color = colors.ink4)
        }
    }
}

private fun signed(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "−${-delta}"
    else -> "±0"
}
