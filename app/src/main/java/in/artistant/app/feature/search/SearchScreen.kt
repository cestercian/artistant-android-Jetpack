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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.SearchSort
import androidx.compose.ui.draw.clip
import `in`.artistant.app.designsystem.component.ArtistTile
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.ScreenTitleBar
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

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
        // The screen never said its own name. Every other tab root either declares a
        // title band or carries a masthead that names it; Search opened straight onto
        // its query field, so it read as a sheet someone had already pushed you into.
        // Measured against the reference: its field sits ~77 units below the top inset,
        // ours sat ~36; the missing 44 is exactly this band.
        ScreenTitleBar("Search")

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
                // The results are already live off the debounce; the Search key is
                // here to say "this one was a search", which is the only thing that
                // records a recent. Without it the rail filled with the prefixes
                // typed on the way to a word.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submitQuery() }),
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
            // The badge is silent on its own: the button merges its descendants and
            // the icon's own description wins, so the count inside it is never read.
            // It rides on the button instead — the same thing iOS speaks as the
            // control's value.
            val filterCount = state.activeFilterCount
            IconButton(
                onClick = { showFilters = true },
                modifier = Modifier.semantics {
                    contentDescription =
                        if (filterCount > 0) "Filters, $filterCount active" else "Filters"
                },
            ) {
                Box {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = colors.ink2)
                    if (filterCount > 0) {
                        Text(
                            filterCount.toString(),
                            style = AppTheme.type.monoSmall,
                            color = colors.brandInk,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(colors.brand, RoundedCornerShape(AppTheme.dimens.radii.xl))
                                .padding(horizontal = space.xs),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.xl)
                .height(AppTheme.dimens.size.hairline)
                .background(colors.line),
        )

        when {
            !state.hasActiveQuery -> {
                // Sort lives with the results, not here: ordering an empty
                // page is a control with nothing to act on, and the results
                // list already carries its own row.
                FacetBrowse(
                    state = state,
                    onCity = viewModel::selectCity,
                    onCategory = viewModel::toggleCategory,
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
                            ResultsHeader(
                                state = state,
                                onSort = viewModel::setSort,
                                onClearFilters = viewModel::clearFilters,
                            )
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
                                        // No `width`: the weighted slot hands the
                                        // tile fixed width constraints, so anything
                                        // passed here was only ever coerced away.
                                        modifier = Modifier.weight(1f),
                                        height = AppTheme.dimens.hero.gridTileHeight,
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
        // Closing the sheet IS applying it, however it closes.
        //
        // The sheet mutates the ViewModel live but none of those setters
        // re-search, so `onDismiss` — which ModalBottomSheet also routes the
        // swipe-down, the scrim tap and the back gesture to — used to leave the
        // badge and the RPC arguments changed while the grid still showed the
        // pre-edit page. The next `loadMore()` then fetched page 2 under the new
        // bounds and appended it to page 1 of the old ones: one list, two filter
        // sets. iOS has no such split — every filter mutation re-runs the search
        // off `SearchStore.queryKey`, so there "dismissed" and "applied" are the
        // same event too, and its Apply button is a bare `dismiss()`.
        val closeAndApply = {
            viewModel.applyFilters()
            showFilters = false
        }
        SearchFilterSheet(
            state = state,
            cityOptions = state.facets.cities.map { it.label },
            onDismiss = closeAndApply,
            onSelectCity = viewModel::selectCity,
            onSetDate = viewModel::setDate,
            onSetFlex = viewModel::setFlexDays,
            onSetEventType = viewModel::setEventType,
            onToggleService = viewModel::toggleService,
            onSetPrice = viewModel::setPriceRange,
            onSetScore = viewModel::setMinScore,
            onClear = viewModel::clearFilters,
            onApply = closeAndApply,
        )
    }
}

@Composable
private fun FacetBrowse(
    state: SearchUiState,
    onCity: (String?) -> Unit,
    onCategory: (String) -> Unit,
    onRecent: (String) -> Unit,
) {
    val space = AppTheme.dimens.space
    LazyColumn(
        // Wider gutter and far more air between rails than a list would take:
        // this is a browse surface of three short rows, and at list spacing the
        // three labels stacked into one grey block.
        contentPadding = PaddingValues(space.xl),
        verticalArrangement = Arrangement.spacedBy(space.xxl),
    ) {
        if (state.recents.isNotEmpty()) {
            item {
                BrowseRail("Recent") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        items(state.recents, key = { it }) { term ->
                            Chip(
                                label = term,
                                selected = false,
                                filled = false,
                                onClick = { onRecent(term) },
                            )
                        }
                    }
                }
            }
        }
        // Categories lead. The rails are ordered by how people actually shop for
        // a performer — what kind of act first, then where — and the filled/
        // hollow split below reinforces it: the categories rail is the one that
        // reads as a set of destinations.
        if (state.facets.categories.isNotEmpty()) {
            item {
                BrowseRail("Browse by category") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        items(state.facets.categories, key = { it.label }) { facet ->
                            Chip(
                                label = "${facet.label} · ${facet.count}",
                                selected = facet.label in state.categories,
                                filled = true,
                                onClick = { onCategory(facet.label) },
                            )
                        }
                    }
                }
            }
        }
        if (state.facets.cities.isNotEmpty()) {
            item {
                BrowseRail("Browse by city") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        items(state.facets.cities, key = { it.label }) { facet ->
                            Chip(
                                label = "${facet.label} · ${facet.count}",
                                selected = state.city == facet.label,
                                filled = false,
                                onClick = {
                                    onCity(if (state.city == facet.label) null else facet.label)
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            EmptyState(
                title = "Search artists",
                body = "Type a name, or pick a city / category above.",
            )
        }
    }
}

/**
 * One browse rail: a wide-tracked small-caps label over its row of chips.
 *
 * The label is the same idiom the funnel's section headers use — uppercase, ink3
 * — but tracked wider, because these three sit alone on an otherwise empty
 * screen and the extra letter-spacing is what stops them reading as body copy.
 */
@Composable
private fun BrowseRail(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md)) {
        Text(
            label.uppercase(Locale.US),
            style = AppTheme.type.railLabel,
            color = AppTheme.colors.ink3,
        )
        content()
    }
}

/**
 * How many results the header should claim it is showing.
 *
 * Internal rather than private so it can be tested: the branching is small but
 * every branch is one a user sees, and the singular case is the kind of thing
 * that ships as "1 results".
 *
 * The trailing "+" is doing real work — the list pages, so the number on screen
 * is a floor rather than a total, and a bare "12 results" above a list that
 * keeps growing as you scroll is simply wrong.
 */
internal fun searchResultCountLabel(
    count: Int,
    isLoading: Boolean,
    canLoadMore: Boolean,
): String {
    if (isLoading && count == 0) return "Searching…"
    val base = if (count == 1) "1 result" else "$count results"
    return if (canLoadMore) "$base+" else base
}

/**
 * The strip above the results grid.
 *
 * Was three sort chips and nothing else. Three problems with that: the chips
 * spent the accent on a control rather than on a result, they said nothing
 * about how many results there are, and there was no way back out of a filter
 * set from the results themselves — you had to reopen the sheet to clear it.
 *
 * Now: a count, the sort as a MENU (one line instead of a rail, and it scales
 * past three options without becoming a scrolling row of pills), a clear-filters
 * escape, then the all-inclusive-pricing line and a rule. The pricing line is
 * the one piece of copy on this screen that pre-empts the question every quote
 * raises, which is why it sits above the results rather than inside each card.
 */
@Composable
private fun ResultsHeader(
    state: SearchUiState,
    onSort: (SearchSort) -> Unit,
    onClearFilters: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = searchResultCountLabel(
                    count = state.results.size,
                    isLoading = state.isLoading,
                    canLoadMore = state.canLoadMore,
                ),
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink3,
            )
            Spacer(Modifier.weight(1f))
            // Hidden while a text query is live: the server ranks those by
            // relevance, so offering a sort would promise an ordering the
            // results are not actually in.
            if (state.query.isBlank()) {
                SortMenu(sort = state.sort, onSort = onSort)
            }
            // Gated on `activeFilterCount`, which is exactly the set
            // `clearFilters()` clears — categories included, since a category
            // picked from the rail above is precisely what this list replaced,
            // so this is the only escape from it once the rail is off-screen.
            if (state.activeFilterCount > 0) {
                Text(
                    text = "Clear filters",
                    style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.brand,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClearFilters)
                        .padding(horizontal = space.xs, vertical = space.xs),
                )
            }
        }
        // Only over actual results — on an empty search it would be reassurance
        // about nothing.
        if (state.results.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Verified,
                    contentDescription = null,
                    tint = colors.ink3,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
                Text(
                    text = "Quotes are all-inclusive — no hidden fees.",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(dimens.size.hairline)
                    .background(colors.lineSoft),
            )
        }
    }
}

/**
 * Sort as a label that opens a menu, mirroring the reference.
 *
 * A menu rather than the old chip rail because sort is a single choice out of a
 * set, and a rail of pills says "these are filters you can combine". It also
 * keeps the accent off the strip: the selected sort is named in the label, so
 * nothing here has to be lime to show which one is on.
 */
@Composable
private fun SortMenu(sort: SearchSort, onSort: (SearchSort) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { open = true }
                .padding(horizontal = space.xs, vertical = space.xs)
                .semantics { contentDescription = "Sort, ${sort.label}" },
            horizontalArrangement = Arrangement.spacedBy(space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(dimens.size.iconMd),
            )
            Text(
                text = sort.label,
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink2,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.bgCard,
        ) {
            SearchSort.entries.forEach { option ->
                val selected = option == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            style = AppTheme.type.callout,
                            color = if (selected) colors.ink else colors.ink2,
                        )
                    },
                    // A tick on the live option rather than a highlight: the menu
                    // is the only place all three are visible at once, so it is
                    // the only place that has to say which one is on.
                    leadingIcon = {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.brand,
                                modifier = Modifier.size(dimens.size.iconMd),
                            )
                        }
                    },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * A browse / sort chip.
 *
 * [filled] is the rail's own signal, separate from [selected]: a filled chip
 * sits on the card fill with no stroke, a hollow one is a hairline outline on
 * the page. It is the only thing distinguishing the categories rail from the
 * cities rail, which otherwise render the same short words at the same size.
 *
 * A capsule, not a rounded rect — every other chip in the app is one, and at
 * this size an 8-unit radius reads as a small button rather than a tag. Note the
 * `clip` before the fill: with a shaped background alone the clickable's ripple
 * still spills into the corners.
 */
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val shape = CircleShape
    val background = when {
        selected -> colors.brand
        filled -> colors.bgCard
        else -> colors.bg
    }
    Text(
        text = label,
        style = AppTheme.type.caption,
        color = when {
            selected -> colors.brandInk
            filled -> colors.ink
            else -> colors.ink2
        },
        modifier = Modifier
            .clip(shape)
            .background(background)
            .then(
                // A filled chip carries no outline: the fill already separates it
                // from the page, and a stroke on top of it draws the eye to the
                // container instead of the word.
                if (filled && !selected) {
                    Modifier
                } else {
                    Modifier.border(
                        dimens.size.hairline,
                        if (selected) colors.brand else colors.line,
                        shape,
                    )
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = space.md, vertical = space.sm),
    )
}
