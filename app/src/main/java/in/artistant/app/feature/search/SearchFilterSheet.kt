package `in`.artistant.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

private enum class FilterSection { City, Date, What, Budget, Score }

/**
 * Accordion filter sheet — port of iOS `SearchFilterSheet`.
 * City / Date / What (event+services) / Budget (histogram+range) / Bookability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterSheet(
    state: SearchUiState,
    cityOptions: List<String>,
    onDismiss: () -> Unit,
    onSelectCity: (String?) -> Unit,
    onSetDate: (String?) -> Unit,
    onSetFlex: (Int) -> Unit,
    onSetEventType: (String?) -> Unit,
    onToggleService: (String) -> Unit,
    onSetPrice: (Int, Int) -> Unit,
    onSetScore: (Int) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var open by remember { mutableStateOf(FilterSection.City) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.lg)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filters", style = AppTheme.type.headline, color = colors.ink)
                Text(
                    "Clear",
                    style = AppTheme.type.callout,
                    color = colors.ink2,
                    modifier = Modifier.clickable(onClick = onClear).padding(space.sm),
                )
            }
            Spacer(Modifier.height(space.md))

            AccordionHeader("City", open == FilterSection.City, state.city ?: "Any") { open = FilterSection.City }
            if (open == FilterSection.City) {
                ChipRow(
                    options = listOf(null to "Any") + cityOptions.map { it to it },
                    selected = state.city,
                    onSelect = { onSelectCity(it) },
                )
            }

            AccordionHeader("Date", open == FilterSection.Date, state.dateIso ?: "Any date") { open = FilterSection.Date }
            if (open == FilterSection.Date) {
                Text(
                    "ISO date (yyyy-MM-dd) — tap Clear to unset",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                )
                Spacer(Modifier.height(space.sm))
                // Lightweight date entry: preset chips for today-ish + clear.
                // Full Calendar picker can replace this without changing the VM contract.
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    listOf(
                        null to "Any day",
                        java.time.LocalDate.now().toString() to "Today",
                        java.time.LocalDate.now().plusDays(7).toString() to "+7d",
                    ).forEach { (iso, label) ->
                        FilterChip(
                            label = label,
                            selected = state.dateIso == iso,
                            onClick = { onSetDate(iso) },
                        )
                    }
                }
                if (state.dateIso != null) {
                    Spacer(Modifier.height(space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        SearchViewModel.flexOptions.forEach { (days, label) ->
                            FilterChip(
                                label = label,
                                selected = state.flexDays == days,
                                onClick = { onSetFlex(days) },
                            )
                        }
                    }
                }
            }

            AccordionHeader(
                "What",
                open == FilterSection.What,
                // Count, not the slugs: the set holds server slugs, and a
                // summary that printed one of them would show a different
                // string than the chip the user actually tapped.
                if (state.services.isEmpty()) "Anything" else "${state.services.size} selected",
            ) { open = FilterSection.What }
            if (open == FilterSection.What) {
                Text("Event type", style = AppTheme.type.caption, color = colors.ink3)
                Spacer(Modifier.height(space.xs))
                ChipRow(
                    options = listOf(null to "Any") + SearchViewModel.eventTypes.map { it to it },
                    selected = state.eventType,
                    onSelect = onSetEventType,
                )
                Spacer(Modifier.height(space.sm))
                Text("Services", style = AppTheme.type.caption, color = colors.ink3)
                Spacer(Modifier.height(space.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    // Wrap via Column of rows would be nicer; FlowRow needs foundation-layout.
                    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                        SearchViewModel.services.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                                row.forEach { (slug, label) ->
                                    FilterChip(
                                        label = label,
                                        selected = slug in state.services,
                                        onClick = { onToggleService(slug) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AccordionHeader(
                "Budget",
                open == FilterSection.Budget,
                searchBudgetSummary(state),
            ) { open = FilterSection.Budget }
            if (open == FilterSection.Budget) {
                if (state.histogram.isNotEmpty()) {
                    PriceHistogram(state.histogram, state.minPrice, state.maxPrice)
                    Spacer(Modifier.height(space.sm))
                }
                Text(
                    "${formatInr(state.minPrice)} – ${formatInr(state.maxPrice)}",
                    style = AppTheme.type.monoSmall,
                    color = colors.ink2,
                )
                RangeSlider(
                    value = state.minPrice.toFloat()..state.maxPrice.toFloat(),
                    onValueChange = { onSetPrice(it.start.toInt(), it.endInclusive.toInt()) },
                    valueRange = state.priceDataMin.toFloat()..state.priceDataMax.toFloat(),
                )
            }

            AccordionHeader(
                "Bookability",
                open == FilterSection.Score,
                if (state.minScore <= 0) "Any" else "${state.minScore}+",
            ) { open = FilterSection.Score }
            if (open == FilterSection.Score) {
                Text("Min score ${state.minScore}", style = AppTheme.type.caption, color = colors.ink2)
                Slider(
                    value = state.minScore.toFloat(),
                    onValueChange = { onSetScore(it.toInt()) },
                    valueRange = 0f..100f,
                )
            }

            Spacer(Modifier.height(space.xl))
            PrimaryButton(
                text = searchApplyLabel(
                    resultCount = state.results.size,
                    hasActiveQuery = state.hasActiveQuery,
                    isLoading = state.isLoading,
                    canLoadMore = state.canLoadMore,
                ),
                onClick = onApply,
                fullWidth = true,
            )
            Spacer(Modifier.height(space.xxl))
        }
    }
}

/**
 * The Budget row's collapsed summary.
 *
 * Reads [SearchUiState.isPriceNarrowed] — the same predicate the filter badge and
 * the `search_artists` price arguments read — instead of comparing the selection
 * against the `SearchTuning` ₹10k/₹80k constants. Those constants describe no
 * roster: once `price_histogram` lands, an untouched selection SNAPS onto the
 * learned span, so on any roster that isn't exactly ₹10k–₹80k the old comparison
 * failed and a cold sheet announced a price range nobody had set, sitting under a
 * badge that correctly read 0. Third source of truth for the one thing
 * [SearchUiState.activePriceFloor] is documented as owning.
 *
 * Internal so it can be tested, same as [searchApplyLabel]. Takes the whole state
 * rather than loose numbers on purpose — the bug WAS an inconsistent triple, and
 * a signature that can express one proves nothing.
 */
internal fun searchBudgetSummary(state: SearchUiState): String =
    if (!state.isPriceNarrowed) {
        "Any"
    } else {
        "${formatInr(state.minPrice)}–${formatInr(state.maxPrice)}"
    }

/**
 * What the sheet's primary CTA claims it will show — a straight port of iOS
 * `SearchFilterSheet.applyLabel`.
 *
 * Internal rather than private so it can be tested, same as the results header's
 * [searchResultCountLabel]: the branching is small and every branch is one a
 * user sees. It replaces a `coerceAtLeast(1)` that had been added to dodge
 * "Show 0 artists" and instead turned a zero into a claim — a cold Search tab
 * (nothing typed, no filter, so [hasActiveQuery] false and the "No matches"
 * branch skipped) opened the sheet on "Show 1 artist".
 *
 * Nothing set at all is its own answer: closing the sheet then goes back to the
 * browse rails, so the honest label is the unnumbered one.
 *
 * The trailing "+" carries the same caveat as the header's count — the list
 * pages, so the number is a floor rather than a total.
 */
internal fun searchApplyLabel(
    resultCount: Int,
    hasActiveQuery: Boolean,
    isLoading: Boolean,
    canLoadMore: Boolean,
): String = when {
    isLoading -> "Searching…"
    !hasActiveQuery -> "Show artists"
    resultCount == 0 -> "No matches"
    canLoadMore -> "Show $resultCount+ artists"
    resultCount == 1 -> "Show 1 artist"
    else -> "Show $resultCount artists"
}

/**
 * One filter section's header row.
 *
 * A collapsed section states what it is currently set to, on the trailing edge.
 * Without that the sheet could tell you nothing about four of its five filters
 * without opening each one in turn — and since only one section is open at a
 * time, "what am I filtering by" took four taps to answer. The reference puts
 * the same summary in the same place, and it is the reason its rows are taller
 * than ours were (56 against 48.8).
 *
 * The disclosure marker moved from a text glyph on the LEADING edge to a real
 * chevron on the trailing one: a leading marker pushed the section titles off
 * the sheet's own gutter, so they no longer lined up with the chips underneath
 * them.
 */
@Composable
private fun AccordionHeader(
    title: String,
    open: Boolean,
    summary: String? = null,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // `lg`, not `md`: with the summary and the rule in place the row
            // measured 48.8 against the reference's 56, and the ramp step up
            // lands it at 56.2. A filter row is a setting you read, not a
            // heading you skim past.
            .padding(vertical = space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppTheme.type.headline, color = colors.ink, modifier = Modifier.weight(1f))
        if (!open && summary != null) {
            Text(summary, style = AppTheme.type.callout, color = colors.ink3)
            Spacer(Modifier.width(space.sm))
        }
        Icon(
            imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(AppTheme.dimens.size.iconSm),
        )
    }
    // A rule under every section, so the sheet reads as a list of settings rather
    // than as five headings floating on the page.
    HRule()
}

@Composable
private fun ChipRow(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                row.forEach { (value, label) ->
                    FilterChip(label, selected == value) { onSelect(value) }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Text(
        label,
        style = AppTheme.type.caption,
        color = if (selected) colors.brandInk else colors.ink2,
        modifier = Modifier
            .background(
                if (selected) colors.brand else colors.bgElev,
                RoundedCornerShape(AppTheme.dimens.radii.xl),
            )
            .border(
                width = if (selected) 0.dp else AppTheme.dimens.size.hairline,
                color = colors.line,
                shape = RoundedCornerShape(AppTheme.dimens.radii.xl),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = space.md, vertical = space.sm),
    )
}

@Composable
private fun PriceHistogram(buckets: List<PriceBucket>, min: Int, max: Int) {
    val colors = AppTheme.colors
    val dashboard = AppTheme.dimens.dashboard
    val peak = buckets.maxOf { it.count }.coerceAtLeast(1)
    // One source for the drawing box. Written twice — once as the row's height,
    // once as a bare Float in the bar math — the two drift apart the moment
    // either is retuned, and the tallest bar stops reaching its own ceiling.
    val barBox = 48.dp
    Row(
        Modifier
            .fillMaxWidth()
            .height(barBox),
        horizontalArrangement = Arrangement.spacedBy(dashboard.barGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { b ->
            val inRange = b.bucketMax >= min && b.bucketMin <= max
            Box(
                Modifier
                    .weight(1f)
                    .height((barBox * b.count / peak).coerceAtLeast(dashboard.barGap))
                    .background(if (inRange) colors.brand else colors.ink4),
            )
        }
    }
}
