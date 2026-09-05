package `in`.artistant.app.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HeroCard
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.ScreenHeader
import `in`.artistant.app.designsystem.component.SearchBarButton
import `in`.artistant.app.designsystem.component.SectionHeader
import `in`.artistant.app.designsystem.component.SkeletonPage
import `in`.artistant.app.designsystem.component.Tile
import `in`.artistant.app.designsystem.component.TrustedTick
import `in`.artistant.app.designsystem.component.isTrusted
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.search.SearchSeedRequest

/**
 * Discover — the client home (screens 02 and 59).
 *
 * The Sep-2026 light design replaced the dark full-bleed marquee with an ordinary
 * scrolling page: a named header carrying the city and the date, a search bar
 * that navigates rather than types, a category rail, one 262dp hero card, then
 * titled rails of tiles.
 *
 * Two things that were structural in the old screen and are gone:
 *
 * - **No status-bar bleed.** The hero is a card on the page now, not the page's
 *   first screenful, so this destination takes the ordinary scaffold insets like
 *   every other tab root instead of opting out of them.
 * - **No auto-advancing pager.** There is one hero act. A carousel of five was a
 *   dark-design affordance for a hero that filled the viewport; at 262dp the
 *   rails do that job, and a self-rotating card would move content under a reader
 *   for nothing.
 *
 * Photo containment is unchanged: every cover is drawn as a *background layer*
 * inside a fixed-size slot, never as a `ContentScale.Crop` child that could
 * stretch its parent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onArtistClick: (artistId: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val hasUnread by viewModel.hasUnreadActivity.collectAsStateWithLifecycle()
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter

    // "See all" is two steps — hand the rail's filters over, then switch tabs.
    // Both live here rather than in the ViewModel because only the screen knows
    // the navigation half, and splitting them would let a seed be posted with no
    // tab switch to consume it.
    val seeAll: (SearchSeedRequest) -> Unit = { seed ->
        viewModel.seedSearch(seed)
        onOpenSearch()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(AppTheme.colors.page)
            .semantics { testTag = "screen.discover" },
    ) {
        // Above the state branch, not inside the loaded one. The bell is this
        // graph's only way to Activity from home, and it used to be composed
        // only once the roster had landed — so a client whose feed was still
        // loading, empty or failed had no notifications at all, and a failed
        // feed is exactly when a missed alert matters. Nothing in the header
        // waits on the roster: the title is a constant, the city and date come
        // off the profile, and the dot comes off the device's own push log.
        ScreenHeader(
            title = "Discover",
            subtitle = state.headerSubtitle,
            modifier = Modifier
                .padding(horizontal = gutter)
                .padding(top = dimens.space.sm),
            trailing = {
                // The bell the design draws (02), now that it has somewhere to
                // go: section SH shipped screen 123, and the dot counts the
                // pushes THIS DEVICE received — the log Activity itself reads.
                // Saved used to borrow this slot; it keeps its own row on
                // Profile (26), which is where the design puts it.
                IconCircle(
                    icon = Icons.Outlined.Notifications,
                    contentDescription = if (hasUnread) {
                        "Activity, unread notifications"
                    } else {
                        "Activity"
                    },
                    onClick = onOpenActivity,
                    dot = hasUnread,
                )
            },
        )
        PullToRefreshBox(
            isRefreshing = state.isLoading && !state.isEmpty,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                // Screen 59. Skeletons only while there is nothing at all to show;
                // a refresh over a live feed gets the pull indicator instead, so the
                // page the user is reading is never replaced by grey blocks.
                state.isLoading && state.isEmpty && state.loadError == null -> {
                    SkeletonPage(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = gutter, vertical = dimens.space.md),
                        // The real header is already drawn above this branch; a
                        // skeleton bar under a live title reads as a second,
                        // broken header rather than as loading.
                        header = false,
                    )
                }
                state.loadError != null && state.isEmpty -> {
                    EmptyState(
                        title = "Couldn't load Discover",
                        body = state.loadError,
                        icon = Icons.Filled.FavoriteBorder,
                        actionLabel = "Try again",
                        onAction = viewModel::refresh,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                !state.isLoading && state.isEmpty -> {
                    // The copy used to read "Pull to refresh once the roster is
                    // live." — an instruction for the one gesture this branch cannot
                    // receive. PullToRefreshBox listens on a nested-scroll
                    // connection, and `EmptyState` is a plain Column with nothing
                    // scrollable in it, so no delta ever reaches the connection.
                    // Discover is also the NavHost start destination, so its
                    // ViewModel is never re-created and a tab switch won't refetch
                    // either: without a button of its own, an empty roster was a dead
                    // end.
                    EmptyState(
                        title = if (state.selectedCategory != null) {
                            "No ${state.selectedCategory} yet"
                        } else {
                            "No artists yet"
                        },
                        body = "We're onboarding the first artists right now — check back in a moment.",
                        icon = Icons.Filled.FavoriteBorder,
                        actionLabel = "Refresh",
                        onAction = viewModel::refresh,
                        secondaryLabel = state.selectedCategory?.let { "Show everything" },
                        onSecondary = { viewModel.selectCategory(null) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = dimens.space.sm,
                            bottom = dimens.size.listTailroom,
                        ),
                    ) {
                        item(key = "search") {
                            SearchBarButton(
                                hint = "Search artists, genres, cities",
                                onClick = onOpenSearch,
                                modifier = Modifier
                                    .padding(horizontal = gutter)
                                    .padding(top = dimens.space.lg),
                            )
                        }
                        if (state.categories.isNotEmpty()) {
                            item(key = "categories") {
                                CategoryRail(
                                    categories = state.categories,
                                    selected = state.selectedCategory,
                                    onSelect = viewModel::selectCategory,
                                    modifier = Modifier.padding(top = dimens.space.md),
                                )
                            }
                        }
                        // A refresh that fails over a roster already on screen used
                        // to be completely silent: the error branch above requires an
                        // empty feed, so all a failed pull did was retract the
                        // indicator — indistinguishable from a successful one, and
                        // the user walks away believing the rails are current.
                        // `refresh()` clears `loadError`, so it self-hides on the next
                        // good load.
                        state.loadError?.let { message ->
                            item(key = "refreshError") {
                                Banner(
                                    title = "Couldn't refresh the roster",
                                    detail = message,
                                    tone = BannerTone.Failure,
                                    actionLabel = "Retry",
                                    onAction = viewModel::refresh,
                                    modifier = Modifier
                                        .padding(horizontal = gutter)
                                        .padding(top = dimens.space.md)
                                        .semantics { testTag = "discover.refreshErrorBanner" },
                                )
                            }
                        }
                        state.hero?.let { hero ->
                            item(key = "hero") {
                                DiscoverHero(
                                    artist = hero,
                                    saved = hero.id.lowercase() in savedIds,
                                    onToggleSave = { viewModel.toggleSaved(hero.id) },
                                    onClick = { onArtistClick(hero.id) },
                                    modifier = Modifier
                                        .padding(horizontal = gutter)
                                        .padding(top = dimens.space.md),
                                )
                            }
                        }
                        state.rails.forEach { rail ->
                            railSection(rail, onArtistClick, seeAll)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The category chips.
 *
 * "For you" is a chip like any other rather than a separate control, because it
 * is the same choice — which slice of the roster the feed shows — and the design
 * draws it selected in the first slot. It is modelled as `null`, so the rail is a
 * list of nullable labels and the selection can never be "a category that does
 * not exist".
 */
@Composable
private fun CategoryRail(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    val haptics = rememberHaptics()
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        contentPadding = PaddingValues(horizontal = dimens.component.gutter),
    ) {
        item(key = "for-you") {
            Chip(
                label = "For you",
                selected = selected == null,
                onClick = {
                    haptics.tap()
                    onSelect(null)
                },
            )
        }
        items(categories, key = { it }) { category ->
            Chip(
                label = category,
                selected = selected == category,
                onClick = {
                    haptics.tap()
                    onSelect(if (selected == category) null else category)
                },
            )
        }
    }
}

/** The featured act. Everything visible is [HeroCard]; this resolves the copy. */
@Composable
private fun DiscoverHero(
    artist: Artist,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    HeroCard(
        title = artist.name,
        meta = DiscoverHeroLogic.heroMeta(artist),
        badge = DiscoverHeroLogic.heroBadge(artist.score, artist.gigs),
        price = DiscoverHeroLogic.heroPrice(artist),
        priceSuffix = artist.duration.trim().takeIf { it.isNotEmpty() }?.let { "/ $it" },
        rating = DiscoverHeroLogic.heroRating(artist.rating, artist.gigs),
        saved = saved,
        onToggleSave = {
            // Light tap: saving is a preference, not an outcome — the same weight
            // iOS gives it.
            haptics.tap()
            onToggleSave()
        },
        onClick = onClick,
        titleTrailing = { if (isTrusted(artist.score, artist.gigs)) TrustedTick() },
        media = { ArtistCover(artist) },
        modifier = modifier.semantics { testTag = "discover.hero" },
    )
}

/**
 * One titled rail: a section header with its "See all", then tiles.
 *
 * The tile width is computed from the viewport so exactly TWO land in the row,
 * matching the design and — more to the point — matching the loading skeleton's
 * two 47%-wide blocks. A rail whose tiles are a different width from the
 * skeleton's reflows the page at the moment the content arrives, which is the one
 * thing screen 59's note exists to prevent.
 */
private fun LazyListScope.railSection(
    rail: DiscoverRail,
    onArtistClick: (String) -> Unit,
    onSeeAll: (SearchSeedRequest) -> Unit,
) {
    item(key = "rail-${rail.id}") {
        val dimens = AppTheme.dimens
        val gutter = dimens.component.gutter
        val tileWidth = railTileWidth(gutter, dimens.space.md)
        Column(Modifier.padding(top = dimens.space.lg)) {
            SectionHeader(
                title = rail.title,
                actionLabel = "See all",
                onAction = { onSeeAll(rail.seed) },
                modifier = Modifier.padding(horizontal = gutter),
            )
            LazyRow(
                modifier = Modifier.padding(top = dimens.space.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
                contentPadding = PaddingValues(horizontal = gutter),
            ) {
                items(rail.artists, key = { it.id }) { artist ->
                    Tile(
                        name = artist.name,
                        meta = DiscoverHeroLogic.tileMeta(artist),
                        onClick = { onArtistClick(artist.id) },
                        media = { ArtistCover(artist) },
                        modifier = Modifier
                            .width(tileWidth)
                            .semantics { testTag = "discover.tile.${rail.id}.${artist.id}" },
                    )
                }
            }
        }
    }
}

/**
 * Half the page's content width — two tiles and the gap between them.
 *
 * Read from `LocalConfiguration` rather than from a parent constraint because the
 * tiles sit inside a `LazyRow`, whose horizontal constraint is infinite: there is
 * nothing there to take a half of. Configuration reports the window width, which
 * is what the page gutters are measured against.
 */
@Composable
private fun railTileWidth(gutter: Dp, gap: Dp): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return ((screenWidth - gutter * 2 - gap) / 2).coerceAtLeast(AppTheme.dimens.hero.tileWidth)
}

/**
 * The never-empty cover: brand gradient first, real photo over it when there is
 * one. Ordering matters — the gradient is not a "loading" state you swap out,
 * it's the floor the photo lands on, so an artist with no media still gets a
 * composed-looking frame and a slow network never shows a blank rectangle.
 */
@Composable
private fun ArtistCover(artist: Artist) {
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
