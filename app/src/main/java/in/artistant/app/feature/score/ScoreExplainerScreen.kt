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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.component.Sparkline
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.domain.score.tierColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

data class ScoreExplainerUiState(
    val breakdown: ScoreBreakdown = ScoreBreakdown.NewArtist,
    val history: List<ScoreHistoryPoint> = emptyList(),
    /**
     * The history read THREW, as opposed to returning nothing.
     *
     * Both arrive as an empty list and they mean opposite things: "no gigs have
     * landed yet" against "we couldn't ask". Flattening them hid the History
     * section entirely on a transport/RLS failure and, once the sheet was open,
     * made ScoreHistorySheet's "No history yet" a statement we hadn't earned.
     * iOS carries the same flag (`ScoreHistorySheet.fetchError`, and
     * `breakdownError` for the sibling read).
     */
    val historyFailed: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ScoreExplainerViewModel @Inject constructor(
    private val scores: ScoreRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ScoreExplainerUiState())
    val state: StateFlow<ScoreExplainerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching {
            val b = scores.breakdownForSelf()
            // The history read stays subordinate — the ring is the screen, and a
            // dead sparkline must not blank it. But the failure is CARRIED, not
            // discarded: see [ScoreExplainerUiState.historyFailed].
            val h = runCatching { scores.historyForSelf() }
            Triple(b, h.getOrDefault(emptyList()), h.isFailure)
        }.onSuccess { (b, h, historyFailed) ->
            _state.update {
                it.copy(
                    breakdown = b,
                    history = h,
                    historyFailed = historyFailed,
                    isLoading = false,
                )
            }
        }.onFailure { e ->
            _state.update {
                it.copy(isLoading = false, error = e.message ?: "Couldn't load score.")
            }
        }
    }
}

/**
 * Self-facing Bookability Score explainer — port of iOS ScoreExplainerView
 * (ring + tiers + weighted metric bars). History listed compactly when present.
 */
@Composable
fun ScoreExplainerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScoreExplainerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val size = AppTheme.dimens.size
    val b = state.breakdown
    var showHistory by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = space.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
            }
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentInk)
            }
            state.error != null -> Column(
                Modifier.fillMaxSize().padding(space.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error!!, style = AppTheme.type.body, color = colors.hot)
                Spacer(Modifier.height(space.md))
                PrimaryButton(text = "Retry", onClick = viewModel::refresh)
            }
            // The weight moves OUT to the wrapper: it is the wrapper that is now
            // the Column's child, so it has to be the thing that claims the
            // remaining height. The content then fills the slot it was handed.
            else -> RevealOnAppear(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = space.xl),
                ) {
                    Text("Bookability Score", style = AppTheme.type.displaySub, color = colors.ink)
                    Spacer(Modifier.height(space.lg))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ScoreRing(
                            value = b.numericScore,
                            size = size.ringXl,
                            stroke = size.ringXlStroke,
                            showLabel = true,
                        )
                    }
                    Spacer(Modifier.height(space.xl))
                    HRule()
                    Spacer(Modifier.height(space.lg))
                    Text("Tiers", style = AppTheme.type.caption, color = colors.ink3)
                    Spacer(Modifier.height(space.sm))
                    listOf(
                        ScoreTier.New to "0–60",
                        ScoreTier.Rising to "60–75",
                        ScoreTier.Trusted to "75–90",
                        ScoreTier.Elite to "90+",
                    ).forEach { (tier, range) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = space.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The dot carries the tier colour, same as the ring
                            // above and every tile's score capsule. Without it the
                            // legend explains a ring it shares nothing with.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(space.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(size.dot)
                                        .clip(CircleShape)
                                        .background(tierColor(tier, colors)),
                                )
                                Text(tier.label, style = AppTheme.type.callout, color = colors.ink)
                            }
                            Text(range, style = AppTheme.type.monoSmall, color = colors.ink3)
                        }
                    }
                    Spacer(Modifier.height(space.lg))
                    HRule()
                    Spacer(Modifier.height(space.lg))
                    Text("What goes into it", style = AppTheme.type.caption, color = colors.ink3)
                    Spacer(Modifier.height(space.md))
                    MetricBar("Show-up rate", 30, clamp(b.showUpRate))
                    MetricBar("Reviews", 25, clamp(b.reviewScore))
                    MetricBar("Reply speed", 20, clamp(b.replySpeed))
                    MetricBar("Cancellations", 15, clamp(100 - b.cancellationRate))
                    MetricBar("Social proof", 10, clamp(b.socialProof))
                    // A failed history read keeps the section — hiding it made the
                    // failure look like an artist who has no history, and left the
                    // only retry behind a "Full history" button that never
                    // rendered. Same distinction iOS draws (`fetchError`).
                    if (state.history.isNotEmpty() || state.historyFailed) {
                        Spacer(Modifier.height(space.xl))
                        HRule()
                        Spacer(Modifier.height(space.lg))
                        Text("History", style = AppTheme.type.caption, color = colors.ink3)
                        Spacer(Modifier.height(space.sm))
                        if (state.historyFailed) {
                            Text(
                                "Couldn't load history.",
                                style = AppTheme.type.body,
                                color = colors.ink3,
                            )
                            Spacer(Modifier.height(space.md))
                            PrimaryButton(
                                text = "Retry",
                                onClick = viewModel::refresh,
                                fullWidth = true,
                            )
                        } else {
                            Sparkline(values = state.history.map { it.score })
                            Spacer(Modifier.height(space.md))
                            PrimaryButton(
                                text = "Full history",
                                onClick = { showHistory = true },
                                fullWidth = true,
                            )
                        }
                    }
                    Spacer(Modifier.height(space.xxl))
                }
            }
        }
    }
    if (showHistory) {
        ScoreHistorySheet(history = state.history, onDismiss = { showHistory = false })
    }
}

@Composable
private fun MetricBar(name: String, weight: Int, value: Int) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.padding(bottom = space.md)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$name · ${weight}%", style = AppTheme.type.footnote, color = colors.ink2)
            Text("$value", style = AppTheme.type.monoSmall, color = colors.ink)
        }
        Spacer(Modifier.height(space.xs))
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(AppTheme.dimens.dashboard.meterHeight)
                .clip(CircleShape),
            color = colors.accentInk,
            trackColor = colors.bgSoft,
        )
    }
}

private fun clamp(v: Int) = min(100, max(0, v))
