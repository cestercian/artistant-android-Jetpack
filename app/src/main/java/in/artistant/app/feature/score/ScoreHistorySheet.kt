package `in`.artistant.app.feature.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Sparkline
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * 30-day Bookability Score history — port of iOS `ScoreHistorySheet`.
 * Sparkline + delta vs oldest point in the window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreHistorySheet(
    history: List<ScoreHistoryPoint>,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scores = history.map { it.score }
    val delta = if (scores.size >= 2) scores.last() - scores.first() else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElev,
    ) {
        Column(Modifier.padding(horizontal = space.lg, vertical = space.md)) {
            Text("Score history", style = AppTheme.type.displaySmall, color = colors.ink)
            Spacer(Modifier.height(space.xs))
            Text(
                "Last ${history.size.coerceAtLeast(0)} computations",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
            Spacer(Modifier.height(space.lg))
            if (history.isEmpty()) {
                Text("No history yet — score updates after gigs land.", style = AppTheme.type.body, color = colors.ink3)
            } else {
                Sparkline(values = scores)
                Spacer(Modifier.height(space.md))
                delta?.let { d ->
                    val label = when {
                        d > 0 -> "+$d"
                        d < 0 -> "$d"
                        else -> "±0"
                    }
                    val tint = when {
                        d > 0 -> colors.good
                        d < 0 -> colors.hot
                        else -> colors.ink3
                    }
                    Text("Delta $label", style = AppTheme.type.monoMedium, color = tint)
                    Spacer(Modifier.height(space.md))
                }
                HRule()
                Spacer(Modifier.height(space.sm))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    items(history.asReversed(), key = { it.computedAtIso }) { point ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                point.computedAtIso.take(10),
                                style = AppTheme.type.footnote,
                                color = colors.ink3,
                            )
                            Text(
                                point.score.toString(),
                                style = AppTheme.type.monoMedium,
                                color = colors.ink,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(space.xxl))
        }
    }
}
