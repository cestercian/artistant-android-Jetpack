package `in`.artistant.app.feature.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.common.util.formatInrShort
import `in`.artistant.app.data.model.PriceBucket
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.theme.AppTheme
import java.time.LocalDate

/**
 * How much of the screen a filter sheet takes.
 *
 * The design's sheet starts 132 units down an 844-unit frame, which is 84%. A
 * fraction rather than a height because the thing being reproduced is the
 * PROPORTION — a fixed height would swallow the scrim strip on a short phone and
 * float on a tall one — and it has to be bounded rather than `fillMaxHeight()`
 * because the pinned CTA below the scroll needs a `weight(1f)` to push against.
 */
private const val SHEET_FRACTION = 0.84f

/** The design's `rgba(20,21,15,.36)` scrim, expressed on the ink token. */
private const val SCRIM_ALPHA = 0.36f

/**
 * Filters (screens 15 and 104).
 *
 * The two screens are one sheet in two states: 104 is 15 with filters on. The
 * active-filter chip row at the top is what makes that work — the design's note
 * is that it "makes six accordions legible at a glance and each chip is its own
 * undo", so the sheet answers "what am I filtering by" without opening anything.
 *
 * **What is not here, and why.** The design's "Must have" block (own PA and
 * lights / verified ID / travels outside city) has no counterpart in
 * `search_artists`: there is no PA column, no verification flag, and no travel
 * radius on `artists`. Three switches that changed nothing would be worse than
 * three that are absent, so the block is omitted and the sheet carries only
 * filters that reach the RPC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterSheet(
    state: SearchUiState,
    cityOptions: List<String>,
    categoryOptions: List<String>,
    onDismiss: () -> Unit,
    onSelectCity: (String?) -> Unit,
    onToggleCategory: (String) -> Unit,
    onSetDate: (String?) -> Unit,
    onSetFlex: (Int) -> Unit,
    onSetEventType: (String?) -> Unit,
    onSetPrice: (Int, Int) -> Unit,
    onSetScore: (Int) -> Unit,
    onDropFilter: (SearchFilterKind) -> Unit,
    onCompareServices: () -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var open by remember { mutableStateOf<FilterSection?>(null) }
    val chips = searchFilterChips(state)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // SheetScaffold draws the container — fill, radius, gutter, nav inset —
        // so Material must draw none of it, or the two stack into a double card
        // with an M3 surface tint underneath.
        containerColor = Color.Transparent,
        contentColor = colors.ink,
        scrimColor = colors.ink.copy(alpha = SCRIM_ALPHA),
        dragHandle = null,
    ) {
        SheetScaffold(modifier = Modifier.fillMaxHeight(SHEET_FRACTION)) {
            SheetHeaderRow(
                leadingLabel = if (chips.isEmpty()) "Reset" else "Clear",
                leadingEnabled = chips.isNotEmpty(),
                title = "Filters",
                onLeading = onClear,
                onClose = onDismiss,
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (chips.isNotEmpty()) {
                    FilterChipRail(
                        chips = chips,
                        onDrop = onDropFilter,
                        modifier = Modifier.padding(top = dimens.space.lg),
                    )
                    Text(
                        text = searchFilterSummaryLine(chips.size),
                        style = AppTheme.type.caption,
                        color = colors.ink3,
                        modifier = Modifier.padding(top = dimens.space.md),
                    )
                    SheetRule()
                }

                DisclosureRow(
                    title = "City",
                    value = state.city ?: "Any",
                    expanded = open == FilterSection.City,
                    onClick = { open = open.toggle(FilterSection.City) },
                )
                if (open == FilterSection.City) {
                    OptionChips(
                        options = listOf(null to "Any") + cityOptions.map { it to it },
                        selected = state.city,
                        onSelect = onSelectCity,
                    )
                }

                DisclosureRow(
                    title = "Date",
                    value = state.dateIso?.let { searchDateLabel(it) } ?: "Any date",
                    expanded = open == FilterSection.Date,
                    onClick = { open = open.toggle(FilterSection.Date) },
                )
                if (open == FilterSection.Date) {
                    DateSection(state = state, onSetDate = onSetDate, onSetFlex = onSetFlex)
                }

                DisclosureRow(
                    title = "Occasion",
                    value = state.eventType ?: "Any",
                    expanded = open == FilterSection.Occasion,
                    onClick = { open = open.toggle(FilterSection.Occasion) },
                )
                if (open == FilterSection.Occasion) {
                    OptionChips(
                        options = listOf(null to "Any") +
                            SearchViewModel.eventTypes.map { it to it },
                        selected = state.eventType,
                        onSelect = onSetEventType,
                    )
                }

                // Service is a PUSH, not an expansion: it is screen 53, a radio
                // list with its own count on its own button, and comparing means
                // one lens at a time rather than a chip grid you can multi-select.
                NavRow(
                    title = "Service",
                    value = state.services.firstOrNull()?.let(::serviceLabel) ?: "Any",
                    onClick = onCompareServices,
                )

                SectionTitle("Act type", top = dimens.space.xl)
                if (categoryOptions.isEmpty()) {
                    Text(
                        text = "Act types load with the roster.",
                        style = AppTheme.type.caption,
                        color = colors.ink4,
                        modifier = Modifier.padding(top = dimens.space.sm),
                    )
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimens.space.md),
                        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
                    ) {
                        categoryOptions.forEach { category ->
                            Chip(
                                label = category,
                                selected = category in state.categories,
                                onClick = { onToggleCategory(category) },
                            )
                        }
                    }
                }

                SectionTitle("Budget, all-in", top = dimens.space.xl)
                BudgetSection(state = state, onSetPrice = onSetPrice)

                SectionTitle("Bookability", top = dimens.space.xl)
                ScoreSection(state = state, onSetScore = onSetScore)

                Spacer(Modifier.height(dimens.space.xl))
            }

            SheetRule()
            PrimaryButton(
                text = searchApplyLabel(
                    resultCount = state.results.size,
                    hasActiveQuery = state.hasActiveQuery,
                    isLoading = state.isLoading,
                    canLoadMore = state.canLoadMore,
                ),
                onClick = onApply,
                fullWidth = true,
                modifier = Modifier.padding(top = dimens.space.lg),
            )
        }
    }
}

/** Which section is expanded. One at a time — the sheet is a list, not a form. */
private enum class FilterSection { City, Date, Occasion }

private fun FilterSection?.toggle(section: FilterSection): FilterSection? =
    if (this == section) null else section

// ─────────────────────────────────────────────────────────────────────────────
// Sections
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.DateSection(
    state: SearchUiState,
    onSetDate: (String?) -> Unit,
    onSetFlex: (Int) -> Unit,
) {
    val dimens = AppTheme.dimens
    val today = remember { LocalDate.now() }
    // Presets rather than a picker: `p_date` takes one day, the useful days are
    // all within the next fortnight, and a full calendar sheet on top of a sheet
    // is a second modal for a one-field decision. A picker can replace this
    // without touching the ViewModel contract.
    val presets = remember(today) {
        listOf(
            null to "Any day",
            today.toString() to "Today",
            today.plusDays(1).toString() to "Tomorrow",
            today.with(java.time.DayOfWeek.SATURDAY).toString() to "This Sat",
            today.plusWeeks(1).with(java.time.DayOfWeek.SATURDAY).toString() to "Next Sat",
        )
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        presets.forEach { (iso, label) ->
            Chip(label = label, selected = state.dateIso == iso, onClick = { onSetDate(iso) })
        }
    }
    if (state.dateIso != null) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.space.sm),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            SearchViewModel.flexOptions.forEach { (days, label) ->
                Chip(
                    label = label,
                    selected = state.flexDays == days,
                    onClick = { onSetFlex(days) },
                )
            }
        }
    }
}

/**
 * Budget — the histogram, the range, and the slider (screens 15 and 104).
 *
 * The histogram is `price_histogram`'s own 16 buckets, so the caption under it is
 * a fact rather than flavour. When the facet did not load there is nothing
 * truthful to draw and the section says so: a slider whose ends are the
 * `SearchTuning` placeholders would let a user "narrow" against a span that
 * describes no roster, which is the bug `priceBoundsLoaded` exists to stop.
 */
@Composable
private fun ColumnScope.BudgetSection(state: SearchUiState, onSetPrice: (Int, Int) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    if (!state.priceBoundsLoaded) {
        Text(
            text = "Prices for this search haven't loaded yet.",
            style = AppTheme.type.caption,
            color = colors.ink4,
            modifier = Modifier.padding(top = dimens.space.sm),
        )
        return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatInrShort(state.priceDataMin),
            style = AppTheme.type.subtitle,
            color = colors.ink4,
        )
        Text(
            text = "${formatInr(state.minPrice)} – ${formatInr(state.maxPrice)}",
            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatInrShort(state.priceDataMax),
            style = AppTheme.type.subtitle,
            color = colors.ink4,
        )
    }

    val hasBars = state.histogram.isNotEmpty()
    if (hasBars) {
        PriceHistogram(state.histogram, state.minPrice, state.maxPrice)
    }
    RangeSlider(
        value = state.minPrice.toFloat()..state.maxPrice.toFloat(),
        onValueChange = { onSetPrice(it.start.toInt(), it.endInclusive.toInt()) },
        valueRange = state.priceDataMin.toFloat()..state.priceDataMax.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = colors.surface,
            // The accent is spent on the BARS when there are bars (screen 104),
            // so the track drops to ink — one signal per screen, and the lit
            // bars are the more informative half of the pair. With no histogram
            // (screen 15) the track is the only thing to look at, so it takes it.
            activeTrackColor = if (hasBars) colors.ink else colors.accent,
            inactiveTrackColor = colors.hairline,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
    Text(
        text = if (hasBars) {
            "Bars show real market prices for this search — the lit range is what you'd see."
        } else {
            "Quotes are the artist's own; the platform fee and GST are added at checkout."
        },
        style = AppTheme.type.caption,
        color = colors.ink4,
    )
}

@Composable
private fun ColumnScope.ScoreSection(state: SearchUiState, onSetScore: (Int) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Text(
        text = if (state.minScore <= 0) {
            "Any Bookability Score"
        } else {
            "Score ${state.minScore} and above"
        },
        style = AppTheme.type.subtitle,
        color = colors.ink2,
        modifier = Modifier.padding(top = dimens.space.sm),
    )
    Slider(
        value = state.minScore.toFloat(),
        onValueChange = { onSetScore(it.toInt()) },
        valueRange = 0f..SCORE_MAX,
        colors = SliderDefaults.colors(
            thumbColor = colors.surface,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.hairline,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/** The score's own ceiling, matching `search_artists`' `p_min_score` domain. */
private const val SCORE_MAX = 100f

/**
 * The price facet, drawn as bars.
 *
 * A bucket is lit when it overlaps the selection, so the lit run IS the range the
 * slider posts — the caption under it says exactly that, and a bar that lit on a
 * different rule would make the sentence false.
 */
@Composable
private fun ColumnScope.PriceHistogram(buckets: List<PriceBucket>, min: Int, max: Int) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val peak = buckets.maxOf { it.count }.coerceAtLeast(1)
    // One source for the drawing box. Written twice — once as the row's height,
    // once as a bare number in the bar math — the two drift apart the moment
    // either is retuned, and the tallest bar stops reaching its own ceiling.
    val barBox = dimens.dashboard.chartHeight
    Row(
        Modifier
            .fillMaxWidth()
            .height(barBox)
            .padding(top = dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.dashboard.barGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { bucket ->
            val lit = bucket.bucketMax >= min && bucket.bucketMin <= max
            Box(
                Modifier
                    .weight(1f)
                    .height((barBox * bucket.count / peak).coerceAtLeast(dimens.dashboard.barGap))
                    .clip(RoundedCornerShape(topStart = dimens.radii.sm, topEnd = dimens.radii.sm))
                    .background(if (lit) colors.accent else colors.hairline),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rows and chrome shared with the Compare-by-service sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A sheet's top row: a text action, a centred title, a close circle.
 *
 * The leading label and the close circle reserve the same width so the title is
 * centred against the BAR rather than against the space left between them —
 * asymmetric reservation reads as almost-centred, which is worse than either
 * extreme.
 */
@Composable
internal fun SheetHeaderRow(
    leadingLabel: String,
    leadingEnabled: Boolean,
    title: String,
    onLeading: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = leadingLabel,
            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
            color = if (leadingEnabled) colors.ink2 else colors.ink4,
            maxLines = 1,
            modifier = Modifier
                .width(dimens.component.iconCircleSm)
                .then(if (leadingEnabled) Modifier.clickable(onClick = onLeading) else Modifier),
        )
        Text(
            text = title,
            style = AppTheme.type.sectionTitle,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimens.space.sm),
        )
        IconCircle(
            icon = Icons.Filled.Close,
            contentDescription = "Close",
            onClick = onClose,
            size = dimens.component.iconCircleSm,
        )
    }
}

/** A hairline the width of the sheet's content. */
@Composable
internal fun SheetRule() {
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.space.lg)
            .height(dimens.size.hairline)
            .background(AppTheme.colors.hairline),
    )
}

@Composable
private fun SectionTitle(text: String, top: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = AppTheme.type.sectionTitle,
        color = AppTheme.colors.ink,
        modifier = Modifier.padding(top = top),
    )
}

/**
 * A filter row that expands in place.
 *
 * Its collapsed state states what it is currently set to, on the trailing edge.
 * Without that the sheet could tell you nothing about four of its filters without
 * opening each one in turn — and since only one section is open at a time, "what
 * am I filtering by" took four taps to answer.
 */
@Composable
private fun DisclosureRow(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else CHEVRON_COLLAPSED_DEGREES,
        label = "disclosure",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = AppTheme.type.subtitle,
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier
                .padding(start = dimens.space.sm)
                .size(dimens.size.iconMd)
                .rotate(rotation),
        )
    }
}

/** How far the disclosure chevron turns when its section is shut. */
private const val CHEVRON_COLLAPSED_DEGREES = -90f

/** A row that opens another sheet — chevron, not a disclosure triangle. */
@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = AppTheme.type.subtitle,
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier
                .padding(start = dimens.space.sm)
                .size(dimens.size.iconMd),
        )
    }
}

/** A wrapped grid of single-choice chips, with "Any" modelled as null. */
@Composable
private fun ColumnScope.OptionChips(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val dimens = AppTheme.dimens
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        options.forEach { (value, label) ->
            Chip(label = label, selected = selected == value, onClick = { onSelect(value) })
        }
    }
}
