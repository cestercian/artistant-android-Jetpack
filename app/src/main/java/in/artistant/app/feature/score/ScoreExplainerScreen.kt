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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreTier
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
            val h = runCatching { scores.historyForSelf() }.getOrDefault(emptyList())
            b to h
        }.onSuccess { (b, h) ->
            _state.update { it.copy(breakdown = b, history = h, isLoading = false) }
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
    val b = state.breakdown

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
                CircularProgressIndicator(color = colors.brand)
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
            else -> Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = space.xl),
            ) {
                Text("Bookability Score", style = AppTheme.type.displaySub, color = colors.ink)
                Spacer(Modifier.height(space.lg))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        b.numericScore?.toString() ?: "NEW",
                        style = AppTheme.type.displayTitle,
                        color = colors.brand,
                    )
                }
                Text(
                    b.tier.label,
                    style = AppTheme.type.headline,
                    color = colors.ink2,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(space.xl))
                HRule()
                Spacer(Modifier.height(space.lg))
                Text("Tiers", style = AppTheme.type.caption, color = colors.ink3)
                Spacer(Modifier.height(space.sm))
                listOf(
                    "New" to "0–60",
                    "Rising" to "60–75",
                    "Trusted" to "75–90",
                    "Elite" to "90+",
                ).forEach { (label, range) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = space.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, style = AppTheme.type.callout, color = colors.ink)
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
                if (state.history.isNotEmpty()) {
                    Spacer(Modifier.height(space.xl))
                    HRule()
                    Spacer(Modifier.height(space.lg))
                    Text("History", style = AppTheme.type.caption, color = colors.ink3)
                    Spacer(Modifier.height(space.sm))
                    state.history.takeLast(12).forEach { point ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = space.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(point.computedAtIso.take(10), style = AppTheme.type.footnote, color = colors.ink3)
                            Text(point.score.toString(), style = AppTheme.type.monoMedium, color = colors.ink)
                        }
                    }
                }
                Spacer(Modifier.height(space.xxl))
            }
        }
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
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = colors.brand,
            trackColor = colors.bgSoft,
        )
    }
}

private fun clamp(v: Int) = min(100, max(0, v))
