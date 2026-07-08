package `in`.artistant.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.SearchSort
import `in`.artistant.app.designsystem.component.ArtistTile
import `in`.artistant.app.designsystem.component.ArtistTileSkeleton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.ui.rememberHaptics

/**
 * Search — hairline search bar over facet-chip browse (empty) or a 2-col
 * `ArtistTile` grid with infinite scroll (results). Port of iOS `SearchView`.
 * Filter icon opens [SearchFilterSheet]; the sort menu only shows in browse
 * (a text query ranks by relevance server-side, so sort would be a no-op).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    var showFilters by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(colors.bg)) {
        SearchBar(
            query = state.query,
            activeFilterCount = state.activeFilterCount,
            onQueryChange = viewModel::onQueryChange,
            onClear = { viewModel.onQueryChange("") },
            onSubmit = viewModel::recordRecent,
            onFilters = { showFilters = true },
        )

        if (state.query.isEmpty() && !state.hasFilters) {
            BrowseChips(state, viewModel)
        } else {
            Results(state, viewModel, onArtist)
        }
    }

    if (showFilters) {
        SearchFilterSheet(state = state, viewModel = viewModel, onDismiss = { showFilters = false })
    }
}

// MARK: - Search bar

@Composable
private fun SearchBar(
    query: String,
    activeFilterCount: Int,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onFilters: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Focus the query field when the Search tab opens so the keyboard is already up
    // (iOS focuses the field on appear).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.xl, vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.ink3)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Artists, genre, city…", style = AppTheme.type.callout, color = colors.ink3)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = AppTheme.type.callout.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.brand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Cancel,
                    contentDescription = "Clear",
                    tint = colors.ink3,
                    modifier = Modifier.clickable { onClear() },
                )
            }
            Box {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Filters",
                    tint = colors.ink2,
                    modifier = Modifier.clickable { onFilters() },
                )
                if (activeFilterCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(colors.brand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$activeFilterCount", style = AppTheme.type.monoSmall.copy(fontSize = 9.sp), color = colors.brandInk)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = space.xl).background(colors.line))
    }
}

// MARK: - Browse (empty) chips

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseChips(state: SearchUiState, viewModel: SearchViewModel) {
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(space.xl),
        verticalArrangement = Arrangement.spacedBy(space.xxl),
    ) {
        if (state.recents.isNotEmpty()) {
            ChipSection("RECENT") {
                state.recents.forEach { term -> HollowChip(term) { viewModel.onQueryChange(term) } }
            }
        }
        if (state.allCategories.isNotEmpty()) {
            ChipSection("BROWSE BY CATEGORY") {
                state.allCategories.forEach { cat -> FilledChip(cat) { viewModel.selectOnlyCategory(cat) } }
            }
        }
        if (state.allCities.isNotEmpty()) {
            ChipSection("BROWSE BY CITY") {
                state.allCities.forEach { city -> HollowChip(city) { viewModel.setCity(city) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(title: String, content: @Composable () -> Unit) {
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text(title, style = AppTheme.type.caption, color = AppTheme.colors.ink3)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) { content() }
    }
}

@Composable
private fun HollowChip(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics() // iOS-parity: light tick on a discrete chip pick
    Text(
        label,
        style = AppTheme.type.footnote,
        color = colors.ink2,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, colors.line, CircleShape)
            .clickable { haptics.selection(); onClick() }
            .padding(horizontal = AppTheme.dimens.space.md, vertical = 7.dp),
    )
}

@Composable
private fun FilledChip(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Text(
        label,
        style = AppTheme.type.footnote,
        color = colors.ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.bgCard)
            .clickable { haptics.selection(); onClick() }
            .padding(horizontal = AppTheme.dimens.space.md, vertical = AppTheme.dimens.space.sm),
    )
}

// MARK: - Results grid

@Composable
private fun Results(state: SearchUiState, viewModel: SearchViewModel, onArtist: (String) -> Unit) {
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxSize()) {
        ResultsHeader(state, viewModel)
        when {
            state.isLoading && state.results.isEmpty() -> SkeletonGrid()
            state.loadError != null && state.results.isEmpty() -> ErrorState(state.loadError, viewModel::retry)
            state.results.isEmpty() -> NoResults(state, viewModel)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(space.lg),
                horizontalArrangement = Arrangement.spacedBy(space.md),
                verticalArrangement = Arrangement.spacedBy(space.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.results, key = { _, a -> a.id }) { index, a ->
                    ArtistTile(artist = a, fullWidth = true, modifier = Modifier.clickable { onArtist(a.id) })
                    // Infinite scroll: the last tile composing pages the next set.
                    if (index == state.results.lastIndex && state.canLoadMore) {
                        LaunchedEffect(state.results.size) { viewModel.loadMore() }
                    }
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(space.lg), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppTheme.colors.ink3)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsHeader(state: SearchUiState, viewModel: SearchViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val count = state.results.size
    val label = if (state.isLoading && count == 0) "Searching…"
    else "$count result${if (count == 1) "" else "s"}${if (state.canLoadMore) "+" else ""}"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = space.lg, vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppTheme.type.footnote, color = colors.ink3)
        Spacer(Modifier.weight(1f))
        if (state.query.isEmpty()) SortMenu(state.sort, viewModel::setSort)
        if (state.hasFilters) {
            Spacer(Modifier.width(space.md))
            Text(
                "Clear filters",
                style = AppTheme.type.footnote,
                color = colors.brand,
                modifier = Modifier.clickable { viewModel.clearFilters() },
            )
        }
    }
}

@Composable
private fun SortMenu(current: SearchSort, onSelect: (SearchSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    Box {
        Text("↕ ${current.label}", style = AppTheme.type.footnote, color = colors.ink2, modifier = Modifier.clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SearchSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    onClick = { onSelect(s); open = false },
                    trailingIcon = if (s == current) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brand) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun SkeletonGrid() {
    val space = AppTheme.dimens.space
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(space.lg),
        horizontalArrangement = Arrangement.spacedBy(space.md),
        verticalArrangement = Arrangement.spacedBy(space.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(6) { ArtistTileSkeleton(fullWidth = true) }
    }
}

@Composable
private fun NoResults(state: SearchUiState, viewModel: SearchViewModel) {
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().padding(space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No artists match", style = AppTheme.type.displaySmall, color = AppTheme.colors.ink)
        Spacer(Modifier.height(space.sm))
        Text("Try widening your budget or removing a filter.", style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
        if (state.hasFilters) {
            Spacer(Modifier.height(space.lg))
            Text(
                "Clear filters",
                style = AppTheme.type.footnote,
                color = AppTheme.colors.brand,
                modifier = Modifier.clickable { viewModel.clearFilters() },
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().padding(space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Search unavailable", style = AppTheme.type.displaySmall, color = AppTheme.colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(message, style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
        Spacer(Modifier.height(space.lg))
        Text(
            "Try again",
            style = AppTheme.type.footnote,
            color = AppTheme.colors.brand,
            modifier = Modifier.clip(RoundedCornerShape(AppTheme.dimens.radii.sm)).clickable { onRetry() }.padding(space.sm),
        )
    }
}
