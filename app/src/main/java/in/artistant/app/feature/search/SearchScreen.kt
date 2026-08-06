package `in`.artistant.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.SearchSort
import androidx.compose.ui.draw.clip
import `in`.artistant.app.designsystem.component.ArtistTile
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Search tab — port of iOS `SearchView` (debounced query, facet rails, filter sheet,
 * paged results).
 */
@Composable
fun SearchScreen(
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val space = AppTheme.dimens.space
    val colors = AppTheme.colors
    var showFilters by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        // Search bar — hairline, no card chrome.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.xl, vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.ink3)
            Spacer(Modifier.width(space.md))
            BasicTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                textStyle = AppTheme.type.callout.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (state.query.isEmpty()) {
                            Text(
                                "Artists, genre, city…",
                                style = AppTheme.type.callout,
                                color = colors.ink3,
                            )
                        }
                        inner()
                    }
                },
            )
            if (state.query.isNotEmpty()) {
                IconButton(onClick = viewModel::clearQuery) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = colors.ink3)
                }
            }
            IconButton(onClick = { showFilters = true }) {
                Box {
                    Icon(Icons.Filled.Tune, contentDescription = "Filters", tint = colors.ink2)
                    if (state.activeFilterCount > 0) {
                        Text(
                            state.activeFilterCount.toString(),
                            style = AppTheme.type.monoSmall,
                            color = colors.brandInk,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(colors.brand, RoundedCornerShape(AppTheme.dimens.radii.xl))
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.xl)
                .height(1.dp)
                .background(colors.line),
        )

        when {
            !state.hasActiveQuery -> {
                FacetBrowse(
                    state = state,
                    onCity = viewModel::selectCity,
                    onCategory = viewModel::toggleCategory,
                    onSort = viewModel::setSort,
                    onRecent = viewModel::applyRecent,
                )
            }
            state.isLoading && state.results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            }
            state.loadError != null && state.results.isEmpty() -> {
                EmptyState(
                    title = "Search failed",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::retry,
                )
            }
            state.results.isEmpty() -> {
                EmptyState(
                    title = "No matches",
                    body = "Try another city, category, or spelling.",
                )
            }
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= info.totalItemsCount - 3
                    }
                        .distinctUntilChanged()
                        .collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
                }
                RevealOnAppear {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(space.lg),
                        verticalArrangement = Arrangement.spacedBy(space.md),
                    ) {
                        item {
                            SortRow(sort = state.sort, onSort = viewModel::setSort)
                        }
                        items(state.results.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(space.md),
                            ) {
                                row.forEach { artist ->
                                    ArtistTile(
                                        artist = artist,
                                        onClick = { onArtistClick(artist.id) },
                                        modifier = Modifier.weight(1f),
                                        width = 160.dp,
                                        height = 220.dp,
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = colors.brand)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        SearchFilterSheet(
            state = state,
            cityOptions = state.facets.cities.map { it.label },
            onDismiss = { showFilters = false },
            onSelectCity = viewModel::selectCity,
            onSetDate = viewModel::setDate,
            onSetFlex = viewModel::setFlexDays,
            onSetEventType = viewModel::setEventType,
            onToggleService = viewModel::toggleService,
            onSetPrice = viewModel::setPriceRange,
            onSetScore = viewModel::setMinScore,
            onClear = viewModel::clearFilters,
            onApply = {
                viewModel.applyFilters()
                showFilters = false
            },
        )
    }
}

@Composable
private fun FacetBrowse(
    state: SearchUiState,
    onCity: (String?) -> Unit,
    onCategory: (String) -> Unit,
    onSort: (SearchSort) -> Unit,
    onRecent: (String) -> Unit,
) {
    val space = AppTheme.dimens.space
    LazyColumn(
        contentPadding = PaddingValues(space.lg),
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        item {
            Text("Browse", style = AppTheme.type.headline, color = AppTheme.colors.ink)
        }
        if (state.recents.isNotEmpty()) {
            item {
                Text("Recent", style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
                val gap = Modifier.height(space.sm)
                Spacer(gap)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    items(state.recents, key = { it }) { term ->
                        Chip(label = term, selected = false, onClick = { onRecent(term) })
                    }
                }
            }
        }
        if (state.facets.cities.isNotEmpty()) {
            item {
                Text("Cities", style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
                val gap = Modifier.height(space.sm)
                Spacer(gap)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    items(state.facets.cities, key = { it.label }) { facet ->
                        Chip(
                            label = "${facet.label} · ${facet.count}",
                            selected = state.city == facet.label,
                            onClick = {
                                onCity(if (state.city == facet.label) null else facet.label)
                            },
                        )
                    }
                }
            }
        }
        if (state.facets.categories.isNotEmpty()) {
            item {
                Text("Categories", style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
                val gap = Modifier.height(space.sm)
                Spacer(gap)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    items(state.facets.categories, key = { it.label }) { facet ->
                        Chip(
                            label = "${facet.label} · ${facet.count}",
                            selected = facet.label in state.categories,
                            onClick = { onCategory(facet.label) },
                        )
                    }
                }
            }
        }
        item { SortRow(sort = state.sort, onSort = onSort) }
        item {
            EmptyState(
                title = "Search artists",
                body = "Type a name, or pick a city / category above.",
            )
        }
    }
}

@Composable
private fun SortRow(sort: SearchSort, onSort: (SearchSort) -> Unit) {
    val space = AppTheme.dimens.space
    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
        SearchSort.entries.forEach { option ->
            Chip(
                label = option.label,
                selected = sort == option,
                onClick = { onSort(option) },
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    // Capsule, matching every other chip in the app. Note the `clip` before the
    // fill: previously the border was rounded but the background was not, so a
    // selected chip painted square lime corners OUTSIDE its own rounded stroke.
    // A shaped `background` alone would not have been enough either — the
    // clickable's ripple would still have spilled into the corners.
    val shape = RoundedCornerShape(dimens.radii.sm)
    Text(
        text = label,
        style = AppTheme.type.caption,
        color = if (selected) colors.brandInk else colors.ink2,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.brand else colors.bg)
            .border(dimens.size.hairline, if (selected) colors.brand else colors.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = space.md, vertical = space.sm),
    )
}
