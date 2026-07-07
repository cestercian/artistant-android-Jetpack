package `in`.artistant.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.tierColor
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands

/**
 * The Search filter sheet (iOS `SearchFilterSheet`) — city chips, a budget dual
 * slider, a tier-colored min-score slider, multi-select categories, and a
 * single-select occasion. Every change applies IMMEDIATELY to [viewModel] (which
 * re-runs the debounced search behind the sheet), so "Apply" just dismisses.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    state: SearchUiState,
    viewModel: SearchViewModel,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.bgElev) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.xl)
                .padding(bottom = space.xxl),
            verticalArrangement = Arrangement.spacedBy(space.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = AppTheme.type.displaySmall, color = colors.ink)
                Spacer(Modifier.weight(1f))
                Text("Clear", style = AppTheme.type.footnote, color = colors.ink2, modifier = Modifier.clickable { viewModel.clearFilters() })
                Spacer(Modifier.width(space.lg))
                Text(
                    "Apply",
                    style = AppTheme.type.callout.copy(fontWeight = FontWeight.Bold),
                    color = colors.brandInk,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brand)
                        .clickable { onDismiss() }
                        .padding(horizontal = space.md, vertical = space.sm),
                )
            }

            // City
            Section("CITY") {
                Chip("Any", selected = state.city == null) { viewModel.setCity(null) }
                state.allCities.forEach { c -> Chip(c, selected = state.city == c) { viewModel.setCity(c) } }
            }

            // Budget dual slider
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionTitle("BUDGET (₹)")
                Text(
                    "${formatInr(state.minPrice)} – ${formatInr(state.maxPrice)}",
                    style = AppTheme.type.monoMedium,
                    color = colors.ink,
                )
                RangeSlider(
                    value = state.minPrice.toFloat()..state.maxPrice.toFloat(),
                    onValueChange = { r ->
                        viewModel.setMinPrice(r.start.toInt())
                        viewModel.setMaxPrice(r.endInclusive.toInt())
                    },
                    valueRange = SearchUiState.PRICE_FLOOR.toFloat()..SearchUiState.PRICE_CEILING.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.brand,
                        activeTrackColor = colors.brand,
                        inactiveTrackColor = colors.line,
                    ),
                )
            }

            // Min Bookability
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                SectionTitle("MIN BOOKABILITY SCORE")
                val tierAt = ScoreBands.tier(maxOf(state.minScore, 1))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.minScore}+",
                        style = AppTheme.type.monoLarge,
                        color = tierColor(maxOf(state.minScore, 1)),
                    )
                    Spacer(Modifier.width(space.sm))
                    Text(tierAt.label, style = AppTheme.type.footnote, color = colors.ink2)
                }
                Slider(
                    value = state.minScore.toFloat(),
                    onValueChange = { viewModel.setMinScore((it / 5f).toInt() * 5) },
                    valueRange = 0f..95f,
                    steps = 18, // 0..95 in steps of 5
                    colors = SliderDefaults.colors(
                        thumbColor = colors.brand,
                        activeTrackColor = colors.brand,
                        inactiveTrackColor = colors.line,
                    ),
                )
            }

            // Categories (multi)
            Section("CATEGORY") {
                state.allCategories.forEach { c ->
                    Chip(c, selected = c in state.categories) { viewModel.toggleCategory(c) }
                }
            }

            // Occasion (single). ponytail: canonical list hardcoded — the wizard
            // (which owns the source enum on iOS) is a later Android milestone.
            Section("OCCASION") {
                Chip("Any", selected = state.eventType == null) { viewModel.setEventType(null) }
                OCCASIONS.forEach { e -> Chip(e, selected = state.eventType == e) { viewModel.setEventType(e) } }
            }
        }
    }
}

private val OCCASIONS = listOf("Wedding", "Corporate", "Club", "Private party", "Festival", "Birthday")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md)) {
        SectionTitle(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        ) { content() }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = AppTheme.type.caption, color = AppTheme.colors.ink3)
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Text(
        label,
        style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) colors.brandInk else colors.ink2,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.brand else colors.bgCard)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.lineSoft, CircleShape))
            .clickable { onClick() }
            .padding(horizontal = AppTheme.dimens.space.md, vertical = AppTheme.dimens.space.sm),
    )
}
