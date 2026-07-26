package `in`.artistant.app.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.component.ArtistTile
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Discover home — port of iOS `DiscoverView` rails (hero + featured + city/
 * India / new / comedy). Immersive hero carousel polish lands later; M2 ships
 * the data-backed rails that replace the Placeholder tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val space = AppTheme.dimens.space

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.hero.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg),
    ) {
        when {
            state.isLoading && state.hero.isEmpty() && state.loadError == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppTheme.colors.brand)
                }
            }
            state.loadError != null && state.hero.isEmpty() -> {
                EmptyState(
                    title = "Couldn't load artists",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            !state.isLoading &&
                state.hero.isEmpty() &&
                state.featured.isEmpty() &&
                state.topBangalore.isEmpty() &&
                state.topIndia.isEmpty() &&
                state.newOnArtistant.isEmpty() &&
                state.comedy.isEmpty() -> {
                EmptyState(
                    title = "No artists yet",
                    body = "Pull to refresh once the roster is live.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = space.xxl),
                ) {
                    item {
                        Text(
                            text = "Tonight in India.",
                            style = AppTheme.type.displaySub,
                            color = AppTheme.colors.ink,
                            modifier = Modifier.padding(
                                horizontal = space.lg,
                                vertical = space.lg,
                            ),
                        )
                    }
                    if (state.hero.isNotEmpty()) {
                        item {
                            DiscoverRail(
                                title = "For you",
                                artists = state.hero,
                                onArtistClick = onArtistClick,
                                large = true,
                            )
                        }
                    }
                    if (state.featured.isNotEmpty()) {
                        item {
                            DiscoverRail(
                                title = "Featured this week",
                                artists = state.featured,
                                onArtistClick = onArtistClick,
                                large = true,
                            )
                        }
                    }
                    if (state.topBangalore.isNotEmpty()) {
                        item {
                            DiscoverRail(
                                title = "Top in Bangalore",
                                artists = state.topBangalore,
                                onArtistClick = onArtistClick,
                            )
                        }
                    }
                    if (state.topIndia.isNotEmpty()) {
                        item {
                            DiscoverRail(
                                title = "Top in India",
                                artists = state.topIndia,
                                onArtistClick = onArtistClick,
                            )
                        }
                    }
                    if (state.newOnArtistant.isNotEmpty()) {
                        item {
                            DiscoverRail(
                                title = "New on Artistant",
                                artists = state.newOnArtistant,
                                onArtistClick = onArtistClick,
                            )
                        }
                    }
                    if (state.comedy.isNotEmpty()) {
                        item {
                            DiscoverRail(
                                title = "Comedy",
                                artists = state.comedy,
                                onArtistClick = onArtistClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverRail(
    title: String,
    artists: List<Artist>,
    onArtistClick: (String) -> Unit,
    large: Boolean = false,
) {
    val space = AppTheme.dimens.space
    val size = AppTheme.dimens.size
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = AppTheme.type.headline,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(horizontal = space.lg, vertical = space.sm),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = space.lg),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            items(artists, key = { it.id }) { artist ->
                ArtistTile(
                    artist = artist,
                    onClick = { onArtistClick(artist.id) },
                    width = if (large) size.heroMed * 0.55f else size.avatarXl * 2,
                    height = if (large) size.heroMed else size.heroShort * 0.9f,
                )
            }
        }
        val bottom = Modifier.height(space.lg)
        Spacer(bottom)
    }
}
