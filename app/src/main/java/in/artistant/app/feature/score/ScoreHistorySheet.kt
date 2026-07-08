package `in`.artistant.app.feature.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.ScoreHistoryPoint
import `in`.artistant.app.designsystem.component.Sparkline
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** History-sheet states. [Error] is kept distinct from an empty history (no rows). */
sealed interface ScoreHistoryUiState {
    data object Loading : ScoreHistoryUiState
    data class Error(val message: String) : ScoreHistoryUiState
    data class Loaded(val points: List<ScoreHistoryPoint>) : ScoreHistoryUiState
}

/** Loads the signed-in artist's score trajectory for `ScoreHistorySheet`. */
@HiltViewModel
class ScoreHistoryViewModel @Inject constructor(
    private val scoreRepo: `in`.artistant.app.data.repository.ScoreRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScoreHistoryUiState>(ScoreHistoryUiState.Loading)
    val state: StateFlow<ScoreHistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                ScoreHistoryUiState.Loaded(scoreRepo.historyForSelf())
            } catch (t: Throwable) {
                ScoreHistoryUiState.Error(t.message ?: "Couldn't load history.")
            }
        }
    }

    companion object {
        /** Last-30-days slice, oldest→newest (the repo returns up to 12 months). */
        fun last30Days(points: List<ScoreHistoryPoint>, now: Instant = Instant.now()): List<ScoreHistoryPoint> {
            val cutoff = now.minus(Duration.ofDays(30))
            return points.filter { !it.computedAt.isBefore(cutoff) }
        }
    }
}

/**
 * Score-trajectory sheet (port of iOS `ScoreHistorySheet`): today's score + a delta
 * vs the oldest point in the last 30 days, over a [Sparkline]. Distinct empty / error
 * states so a fetch failure never reads as "no history yet".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreHistorySheet(
    onDismiss: () -> Unit,
    viewModel: ScoreHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.bgElev) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = space.xl).padding(bottom = space.xl),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            Text("Score history", style = AppTheme.type.displaySmall, color = colors.ink)
            when (val s = state) {
                ScoreHistoryUiState.Loading ->
                    Text("Loading…", style = AppTheme.type.footnote, color = colors.ink3)
                is ScoreHistoryUiState.Error ->
                    Empty("Couldn't load history", s.message)
                is ScoreHistoryUiState.Loaded -> {
                    val window = ScoreHistoryViewModel.last30Days(s.points)
                    if (window.isEmpty()) {
                        Empty(
                            "No score history yet",
                            "Once you finish a few gigs and receive reviews, the trajectory fills in here.",
                        )
                    } else {
                        Trajectory(window)
                    }
                }
            }
        }
    }
}

@Composable
private fun Trajectory(window: List<ScoreHistoryPoint>) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val latest = window.last().score
    val earliest = window.first().score
    val delta = latest - earliest

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
        Text(
            "$latest",
            style = AppTheme.type.monoLarge.copy(fontWeight = FontWeight.Bold, fontSize = 48.sp),
            color = colors.ink,
        )
        DeltaBadge(delta)
    }
    Text("LAST 30 DAYS", style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
    Sparkline(
        data = window.map { it.score.toDouble() },
        modifier = Modifier.fillMaxWidth().height(88.dp),
    )
    Text(
        "Your score recomputes on every new review, completed booking, or cancellation. New artists with fewer than 5 completed gigs show 'New' until they cross that threshold.",
        style = AppTheme.type.footnote,
        color = colors.ink2,
    )
}

@Composable
private fun DeltaBadge(delta: Int) {
    val colors = AppTheme.colors
    val up = delta > 0
    val down = delta < 0
    val color = if (up) colors.good else if (down) colors.hot else colors.ink3
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = AppTheme.dimens.space.sm, vertical = AppTheme.dimens.space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(if (up) "▲" else if (down) "▼" else "–", style = AppTheme.type.caption, color = color)
        Text("${kotlin.math.abs(delta)}", style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun Empty(title: String, sub: String) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm)) {
        Text(title, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
        Text(sub, style = AppTheme.type.footnote, color = colors.ink3)
        Spacer(Modifier.height(AppTheme.dimens.space.md))
    }
}
