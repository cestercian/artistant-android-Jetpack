package `in`.artistant.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.component.TrustedTick
import `in`.artistant.app.designsystem.component.isTrusted
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier

/**
 * Saved artists — and, on the same screen, Bookings and Completed (screens 32
 * and 112).
 *
 * The design's note is that these are one screen, not three: "the stat you
 * tapped picks the rows". So the kind chips are permanent navigation rather than
 * a decoration — a reader who arrived from the Saved stat can reach Bookings
 * without going back to the profile to tap a different number — and switching
 * one re-enters this destination with the other kind's argument.
 *
 * The second rail (screen 32's "All / Bands / DJ / Comedy") filters within the
 * list, and every chip in it comes from a category some row actually has: a chip
 * for an act type nobody in your list plays can only empty the screen. The
 * design's "Free 12 Oct" chip is absent — availability on a given night is a
 * server-side predicate on a search, and nothing here has asked one.
 */
@Composable
fun ArtistListScreen(
    onBack: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    onBookingClick: (bookingId: String) -> Unit,
    onSelectKind: (ArtistListKind) -> Unit,
    onBrowseDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter
    val kind = state.kind
    val rows = state.visibleRows

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page)
            .semantics { testTag = "screen.artistList.${kind.raw}" },
    ) {
        BackHeader(
            title = kind.title,
            onBack = onBack,
            subtitle = when {
                state.isLoading && state.rows.isEmpty() -> null
                state.error != null && state.rows.isEmpty() -> "Couldn't load"
                state.rows.isEmpty() -> kind.emptyTitle
                else -> kind.countLabel(rows.size)
            },
            modifier = Modifier.padding(horizontal = dimens.space.sm),
        )

        // The kind switcher. Always drawn, on every branch — it is the screen's
        // navigation, and hiding it on the empty and failed states would strand a
        // reader on whichever list happened to have nothing in it.
        LazyRow(
            modifier = Modifier.padding(top = dimens.space.md),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            contentPadding = PaddingValues(horizontal = gutter),
        ) {
            items(ArtistListKind.entries, key = { it.raw }) { entry ->
                Chip(
                    label = entry.chipLabel,
                    selected = entry == kind,
                    onClick = { if (entry != kind) onSelectKind(entry) },
                )
            }
        }

        if (state.categories.size > 1) {
            LazyRow(
                modifier = Modifier.padding(top = dimens.space.sm),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                contentPadding = PaddingValues(horizontal = gutter),
            ) {
                item(key = "all") {
                    Chip(
                        label = "All",
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                    )
                }
                items(state.categories, key = { it }) { category ->
                    Chip(
                        label = category,
                        selected = state.selectedCategory == category,
                        onClick = {
                            viewModel.selectCategory(
                                if (state.selectedCategory == category) null else category,
                            )
                        },
                    )
                }
            }
        }

        when {
            state.isLoading && state.rows.isEmpty() -> ListSkeleton()
            // Failure is not emptiness. A dropped read and a list you have not
            // filled are the same empty set and the opposite meaning, and the
            // empty state's only action ("Browse Discover") is exactly the wrong
            // advice for a connection problem.
            state.error != null && state.rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = kind.failedTitle,
                        body = state.error,
                        icon = Icons.Filled.FavoriteBorder,
                        actionLabel = "Try again",
                        onAction = { viewModel.refresh() },
                        modifier = Modifier.semantics { testTag = "artistList.failed" },
                    )
                }
            }
            rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = state.selectedCategory?.let { "No $it in this list" }
                            ?: kind.emptyTitle,
                        body = state.selectedCategory?.let { null } ?: kind.emptyBody,
                        icon = Icons.Filled.FavoriteBorder,
                        actionLabel = state.selectedCategory?.let { "Show all" }
                            ?: kind.emptyAction,
                        onAction = {
                            if (state.selectedCategory != null) {
                                viewModel.selectCategory(null)
                            } else {
                                onBrowseDiscover()
                            }
                        },
                        modifier = Modifier.semantics { testTag = "artistList.empty" },
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = gutter,
                        end = gutter,
                        top = dimens.space.md,
                        bottom = dimens.chrome.contentTailroom + dimens.size.listTailroom,
                    ),
                ) {
                    items(rows, key = { it.id }) { row ->
                        ArtistListRowUi(
                            row = row,
                            onClick = {
                                when {
                                    row.bookingId != null -> onBookingClick(row.bookingId)
                                    row.artistId != null -> onArtistClick(row.artistId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One row: a 56dp cover, the name with its tick, the act line, then the score
 * pill and the price (screen 32).
 *
 * The score pill is drawn only for a RANKED artist. Under five completed gigs
 * `ScoreBands` says there is nothing to rank, and a "0" in an accent pill beside
 * a name is a claim about that artist rather than an absence of one — the same
 * rule the hero badge and the profile ring already follow.
 */
@Composable
private fun ArtistListRowUi(row: ArtistListRow, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val artist = row.artist
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = dimens.space.md)
                .semantics { testTag = "artistList.row.${row.id}" },
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(dimens.size.avatarLg)
                    .clip(RoundedCornerShape(dimens.radii.md))
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
                        text = artist?.name ?: row.fallbackTitle,
                        style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (artist != null && isTrusted(artist.score, artist.gigs)) {
                        TrustedTick(size = dimens.size.iconSm)
                    }
                }
                artist?.let { act ->
                    actLine(act).takeIf { it.isNotEmpty() }?.let { line ->
                        Text(
                            text = line,
                            style = AppTheme.type.caption,
                            color = colors.ink4,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = dimens.space.xs / 2),
                        )
                    }
                    ScoreAndPrice(act)
                }
                if (row.pills.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = dimens.space.sm),
                        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                    ) {
                        row.pills.forEach { (text, tone) -> Pill(text, tone = tone) }
                    }
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

/** The accent score chip and the "from ₹26,000" beside it. */
@Composable
private fun ScoreAndPrice(artist: Artist) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val ranked = ScoreBands.tier(artist.score, artist.gigs) != ScoreTier.New
    val price = PackagePricing.fromPrice(artist.packages, artist.price)
    if (!ranked && price <= 0) return
    Row(
        modifier = Modifier.padding(top = dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        if (ranked) {
            Text(
                text = artist.score.toString(),
                style = AppTheme.type.monoPill,
                color = colors.accentDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .background(colors.brandSoft)
                    .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs / 2),
            )
        }
        if (price > 0) {
            Text(
                text = "from ${formatInr(price)}",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                maxLines = 1,
            )
        }
    }
}

/** The loading list — five rows at the real row's geometry. */
@Composable
private fun ListSkeleton() {
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.component.gutter)
            .padding(top = dimens.space.lg),
        verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
    ) {
        repeat(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.md)) {
                SkeletonBlock(
                    Modifier.size(dimens.size.avatarLg),
                    radius = dimens.radii.md,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
                ) {
                    SkeletonBlock(
                        Modifier
                            .fillMaxWidth(TITLE_BAR_FRACTION)
                            .height(dimens.component.skeletonLineHeight),
                    )
                    SkeletonBlock(
                        Modifier
                            .fillMaxWidth(META_BAR_FRACTION)
                            .height(dimens.component.skeletonLineHeight),
                    )
                }
            }
        }
    }
}

/** How wide a skeleton row's two bars run — a name, then a shorter meta line. */
private const val TITLE_BAR_FRACTION = 0.62f
private const val META_BAR_FRACTION = 0.4f

/** "Indie folk · 5 pc" — whichever of those the projection carries. */
private fun actLine(artist: Artist): String =
    listOf(artist.genre, artist.category)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" · ")

/**
 * The never-empty cover: brand gradient first, real photo over it when there is
 * one. The gradient is the floor the photo lands on, not a loading state.
 */
@Composable
private fun ArtistThumb(artist: Artist?) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    artist?.gradient?.takeIf { it.isNotEmpty() }
                        ?: listOf(colors.surface3, colors.surface2),
                ),
            ),
    ) {
        artist?.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
