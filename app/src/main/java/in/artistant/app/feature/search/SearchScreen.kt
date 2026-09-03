package `in`.artistant.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.InlineBanner
import `in`.artistant.app.designsystem.component.SearchBar
import `in`.artistant.app.designsystem.component.SectionHeader
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.component.TrustedTick
import `in`.artistant.app.designsystem.component.isTrusted
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.PackagePricing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Search — four screens in one destination (14 browse, 03 results, 57 empty,
 * 58 unavailable), plus the two sheets it opens (15/104 Filters, 53 Compare by
 * service).
 *
 * **Which one shows is a two-axis decision, and the second axis is local.**
 * `hasActiveQuery` says whether there is a search to show at all; `editing` says
 * whether the user is currently in the field. That second flag is what makes
 * screen 14 reachable with text typed: the design draws suggestions UNDER a field
 * containing "sufi", which cannot happen if a non-empty query always swaps the
 * page for its results. Editing is UI, not domain — the ViewModel has no opinion
 * about where the cursor is — so it lives here.
 *
 * The back chevron the design puts left of the field is deliberately absent.
 * Search is a tab root in this app; a back control there would either pop nothing
 * or leave the tab, and a dead affordance is worse than a missing one.
 */
@Composable
fun SearchScreen(
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter
    var showFilters by remember { mutableStateOf(false) }
    var showServices by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // A seed from Discover arrives as a query the user did not type. Leaving
    // `editing` true would open the suggestion list over results they explicitly
    // asked to see.
    LaunchedEffect(state.city, state.dateIso) { editing = false }

    val submit: () -> Unit = {
        viewModel.submitQuery()
        editing = false
        keyboard?.hide()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .semantics { testTag = "screen.search" },
    ) {
        SearchTopBar(
            state = state,
            editing = editing,
            focusRequester = focusRequester,
            onQueryChange = viewModel::onQueryChange,
            onFocused = { editing = true },
            onSubmit = submit,
            onClear = {
                viewModel.clearQuery()
                editing = true
            },
            onFilters = { showFilters = true },
            modifier = Modifier
                .padding(horizontal = gutter)
                .padding(top = dimens.space.md),
        )

        when {
            editing || !state.hasActiveQuery -> {
                BrowseSurface(
                    state = state,
                    onSuggestion = { suggestion ->
                        when (suggestion) {
                            is SearchSuggestion.Term -> {
                                viewModel.applyRecent(suggestion.text)
                                editing = false
                                keyboard?.hide()
                            }
                            is SearchSuggestion.Act -> onArtistClick(suggestion.artist.id)
                        }
                    },
                    onRecent = { term ->
                        viewModel.applyRecent(term)
                        editing = false
                        keyboard?.hide()
                    },
                    onOccasion = { occasion ->
                        viewModel.setEventType(occasion)
                        viewModel.applyFilters()
                        editing = false
                        keyboard?.hide()
                    },
                )
            }
            state.isLoading && state.results.isEmpty() -> ResultsSkeleton()
            // Screen 58. A failed reach is NOT an empty roster, and the design
            // states the distinction twice on purpose — once in the banner, once
            // in the headline — because conflating them is how a user concludes
            // there are no artists in their city.
            state.loadError != null && state.results.isEmpty() -> {
                Column(Modifier.fillMaxSize()) {
                    InlineBanner(
                        title = "We couldn't reach search.",
                        detail = "This is a connection problem — it is not that no artists match.",
                        tone = BannerTone.Attention,
                        modifier = Modifier
                            .padding(horizontal = gutter)
                            .padding(top = dimens.space.md)
                            .semantics { testTag = "search.unavailableBanner" },
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = "Search unavailable",
                            body = "Pull back and try again in a moment.",
                            icon = Icons.Filled.Refresh,
                            actionLabel = "Try again",
                            onAction = viewModel::retry,
                        )
                    }
                }
            }
            // Screen 57. The design's first action is "Notify me when one
            // joins"; the shared schema has nowhere to put that alert — no
            // search-alerts table, and `waitlist_signups` denies every client
            // read and write — so it is omitted rather than shipped as a promise
            // nothing can keep. See [searchNoResultsActions].
            state.results.isEmpty() -> {
                val actions = searchNoResultsActions(state.query, state.activeFilterCount)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "No artists for this yet",
                        body = searchNoResultsBody(state.query, state.activeFilterCount),
                        icon = Icons.Filled.SearchOff,
                        actionLabel = actions.primary,
                        onAction = {
                            if (state.activeFilterCount > 0) {
                                viewModel.clearFilters()
                            } else {
                                viewModel.clearQuery()
                            }
                            editing = false
                        },
                        secondaryLabel = actions.secondary,
                        onSecondary = viewModel::clearQuery,
                        modifier = Modifier.semantics { testTag = "search.noResults" },
                    )
                }
            }
            else -> ResultsList(
                state = state,
                onArtistClick = onArtistClick,
                onLoadMore = viewModel::loadMore,
                onDropFilter = viewModel::dropFilter,
            )
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
        // sets.
        val closeAndApply = {
            viewModel.applyFilters()
            showFilters = false
        }
        SearchFilterSheet(
            state = state,
            cityOptions = state.facets.cities.map { it.label },
            categoryOptions = state.facets.categories.map { it.label },
            onDismiss = closeAndApply,
            onSelectCity = viewModel::selectCity,
            onToggleCategory = viewModel::toggleCategory,
            onSetDate = viewModel::setDate,
            onSetFlex = viewModel::setFlexDays,
            onSetEventType = viewModel::setEventType,
            onSetPrice = viewModel::setPriceRange,
            onSetScore = viewModel::setMinScore,
            onDropFilter = viewModel::dropFilter,
            onCompareServices = {
                showFilters = false
                showServices = true
            },
            onClear = viewModel::clearFilters,
            onApply = closeAndApply,
        )
    }

    if (showServices) {
        val closeServices = {
            viewModel.applyFilters()
            showServices = false
        }
        CompareByServiceSheet(
            state = state,
            onDismiss = closeServices,
            onSelect = viewModel::selectService,
            onApply = closeServices,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The field, and the filter circle beside it.
 *
 * The circle carries the active count as an accent badge (screen 57). It rides on
 * the BUTTON's own semantics rather than as a sibling Text, because the button
 * merges its descendants and the icon's description would otherwise win — the
 * count inside it was never spoken.
 */
@Composable
private fun SearchTopBar(
    state: SearchUiState,
    editing: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onFocused: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val count = state.activeFilterCount
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        SearchBar(
            value = state.query,
            onValueChange = onQueryChange,
            hint = "Search artists, genres, cities",
            onSearch = onSubmit,
            onClear = onClear,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .then(
                    // The focused field takes an ink rim (screen 14). It is the
                    // only stroke on the page, so it says "the cursor is here"
                    // without a second colour.
                    if (editing) {
                        Modifier.border(
                            width = dimens.component.focusStroke,
                            color = colors.ink,
                            shape = RoundedCornerShape(dimens.radii.control),
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Box {
            IconCircle(
                icon = Icons.Filled.Tune,
                contentDescription = if (count > 0) "Filters, $count active" else "Filters",
                onClick = onFilters,
                size = dimens.component.iconCircleSm,
                modifier = Modifier.semantics { testTag = "search.filters" },
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = AppTheme.type.badge,
                    color = colors.onAccent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = dimens.space.xs),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Browse (screen 14)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BrowseSurface(
    state: SearchUiState,
    onSuggestion: (SearchSuggestion) -> Unit,
    onRecent: (String) -> Unit,
    onOccasion: (String) -> Unit,
) {
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter
    val suggestions = searchSuggestions(state.query, state.facets, state.results)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = dimens.space.lg,
            bottom = dimens.chrome.contentTailroom + dimens.size.listTailroom,
        ),
    ) {
        if (suggestions.isNotEmpty()) {
            item(key = "suggestions-header") { SectionHeader("Suggestions") }
            items(suggestions, key = { it.key }) { suggestion ->
                SuggestionRow(suggestion) { onSuggestion(suggestion) }
            }
        }
        if (state.recents.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeader("Recent", modifier = Modifier.padding(top = dimens.space.xl))
            }
            item(key = "recent") {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.space.md),
                    horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                    verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
                ) {
                    state.recents.forEach { term ->
                        Chip(label = term, selected = false, onClick = { onRecent(term) })
                    }
                }
            }
        }
        item(key = "occasion-header") {
            SectionHeader("Browse by occasion", modifier = Modifier.padding(top = dimens.space.xl))
        }
        // The design prints an act count under every occasion. `search_facets`
        // publishes counts for categories and cities and for nothing else, so
        // these tiles carry the name alone rather than a number we would have to
        // make up.
        items(SearchViewModel.eventTypes.chunked(2), key = { it.first() }) { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.space.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            ) {
                pair.forEach { occasion ->
                    OccasionCard(
                        label = occasion,
                        selected = state.eventType == occasion,
                        onClick = { onOccasion(occasion) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A suggestion row: 36dp glyph or cover, title, real detail, chevron. */
@Composable
private fun SuggestionRow(suggestion: SearchSuggestion, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Box(
                Modifier
                    .size(dimens.size.avatarSm)
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .background(colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                when (suggestion) {
                    is SearchSuggestion.Term -> Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.ink4,
                        modifier = Modifier.size(dimens.size.iconMd),
                    )
                    is SearchSuggestion.Act -> ArtistThumb(suggestion.artist)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
                ) {
                    Text(
                        text = when (suggestion) {
                            is SearchSuggestion.Term -> suggestion.text
                            is SearchSuggestion.Act -> suggestion.artist.name
                        },
                        style = AppTheme.type.rowTitle,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (suggestion is SearchSuggestion.Act &&
                        isTrusted(suggestion.artist.score, suggestion.artist.gigs)
                    ) {
                        TrustedTick(size = dimens.size.iconSm)
                    }
                }
                val detail = when (suggestion) {
                    is SearchSuggestion.Term -> suggestion.detail
                    is SearchSuggestion.Act -> artistLine(suggestion.artist)
                }
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = AppTheme.type.caption,
                        color = colors.ink4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = dimens.space.xs / 2),
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.lineStrong,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.size.hairline)
                .background(colors.hairline),
        )
    }
}

@Composable
private fun OccasionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radii.lg))
            .background(if (selected) colors.accent else colors.surface3)
            .clickable(onClick = onClick)
            .padding(dimens.space.lg),
    ) {
        Text(
            text = label,
            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = if (selected) colors.onAccent else colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Results (screen 03)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultsList(
    state: SearchUiState,
    onArtistClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDropFilter: (SearchFilterKind) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter
    val listState = rememberLazyListState()
    val chips = searchFilterChips(state)

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = dimens.space.md,
            bottom = dimens.chrome.contentTailroom + dimens.size.listTailroom,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = gutter)) {
                Text(
                    text = searchResultsTitle(state),
                    style = AppTheme.type.sectionTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = searchResultsSubtitle(state),
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        // The design draws four canned quick-filters here ("Under ₹50k",
        // "Verified", "4.8+", "Own PA"). Three of those four have no filter
        // behind them in this backend, so the row carries the filters that ARE
        // on instead — the same chips as screen 104, and each one still its own
        // undo.
        if (chips.isNotEmpty()) {
            item(key = "chips") {
                FilterChipRail(
                    chips = chips,
                    onDrop = onDropFilter,
                    contentPadding = PaddingValues(horizontal = gutter),
                )
            }
        }
        items(state.results, key = { it.id }) { artist ->
            ResultCard(
                artist = artist,
                onClick = { onArtistClick(artist.id) },
                modifier = Modifier
                    .padding(horizontal = gutter)
                    .semantics { testTag = "search.result.${artist.id}" },
            )
        }
        if (state.isLoadingMore) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentInk)
                }
            }
        }
        // A page that fails AFTER results are up is otherwise silent: the
        // unavailable branch requires an empty list, so a failed `loadMore` just
        // stopped paging with no explanation.
        state.loadError?.let { message ->
            item(key = "pageError") {
                InlineBanner(
                    title = "Couldn't load more",
                    detail = message,
                    tone = BannerTone.Failure,
                    modifier = Modifier.padding(horizontal = gutter),
                )
            }
        }
    }
}

/**
 * One result (screen 03): a 92dp cover, the name with its tick, the act line, the
 * rating, and the price.
 *
 * **The price does not say "all-in".** The design's card does, and the design's
 * note is that travel and crew are already inside the number. In this backend the
 * figure is `PackagePricing.fromPrice` — the artist's cheapest package — and the
 * app's own money math adds 5% platform plus 18% GST at checkout, so "all-in" on
 * this row would be contradicted two screens later by the app itself. "from" is
 * what the number actually is.
 */
@Composable
private fun ResultCard(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val price = PackagePricing.fromPrice(artist.packages, artist.price)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .clickable(onClick = onClick)
            .padding(dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(
            Modifier
                .size(dimens.size.avatarXl)
                .clip(RoundedCornerShape(dimens.radii.control))
                .background(colors.placeholder),
        ) {
            ArtistThumb(artist)
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
            ) {
                Text(
                    text = artist.name,
                    style = AppTheme.type.cardTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isTrusted(artist.score, artist.gigs)) TrustedTick(size = dimens.size.iconSm)
            }
            artistLine(artist).takeIf { it.isNotEmpty() }?.let { line ->
                Text(
                    text = line,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
            if (artist.rating > 0.0) {
                Row(
                    modifier = Modifier.padding(top = dimens.space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = colors.accentInk,
                        modifier = Modifier.size(dimens.size.iconSm),
                    )
                    Text(
                        text = ratingLine(artist),
                        style = AppTheme.type.caption,
                        color = colors.ink2,
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = dimens.space.sm),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
            ) {
                if (price > 0) {
                    Text(
                        text = formatInr(price),
                        style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                    )
                    Text("from", style = AppTheme.type.caption, color = colors.ink4)
                } else {
                    Text(
                        text = "Pricing on request",
                        style = AppTheme.type.caption,
                        color = colors.ink4,
                    )
                }
            }
        }
    }
}

/** The results skeleton — three cards at the real card's geometry. */
@Composable
private fun ResultsSkeleton() {
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.component.gutter)
            .padding(top = dimens.space.lg)
            .semantics(mergeDescendants = true) { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        SkeletonBlock(
            Modifier
                .width(dimens.component.skeletonSectionWidth)
                .height(dimens.component.skeletonLineHeight),
        )
        repeat(3) {
            SkeletonBlock(
                Modifier
                    .fillMaxWidth()
                    .height(dimens.size.avatarXl + dimens.space.xl),
                radius = dimens.radii.card,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared bits
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The active-filter rail — each chip its own undo (screen 104's note).
 *
 * Shared by the results header and the filter sheet, which is the reason it takes
 * a `contentPadding`: one draws it inside a gutter-padded sheet, the other
 * full-bleed under a page gutter.
 */
@Composable
internal fun FilterChipRail(
    chips: List<SearchFilterChip>,
    onDrop: (SearchFilterKind) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val haptics = rememberHaptics()
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        chips.forEach { chip ->
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable {
                        haptics.tap()
                        onDrop(chip.kind)
                    }
                    .padding(
                        horizontal = dimens.component.chipPadH,
                        vertical = dimens.component.chipPadV,
                    )
                    .semantics { contentDescription = "Remove filter ${chip.label}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                Text(
                    text = chip.label,
                    style = AppTheme.type.chip.copy(fontWeight = FontWeight.Bold),
                    color = colors.onAccent,
                    maxLines = 1,
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
            }
        }
    }
}

/** "Indie folk · 5 pc · Bengaluru" — whichever of those the projection carries. */
private fun artistLine(artist: Artist): String =
    listOf(artist.genre, artist.category, artist.city)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" · ")

/** "4.92 · 128 shows" — the count is what the average is over. */
private fun ratingLine(artist: Artist): String {
    val stars = String.format(java.util.Locale.US, "%.2f", artist.rating)
    return if (artist.gigs > 0) "$stars · ${artist.gigs} shows" else stars
}

/**
 * The never-empty cover: brand gradient first, real photo over it when there is
 * one. The gradient is not a "loading" state you swap out, it's the floor the
 * photo lands on.
 */
@Composable
private fun ArtistThumb(artist: Artist) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(artist.gradient)),
    ) {
        artist.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
