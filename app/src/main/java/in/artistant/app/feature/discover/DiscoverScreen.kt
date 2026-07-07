package `in`.artistant.app.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.availabilityKicker
import `in`.artistant.app.common.util.formatInrShort
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.component.ArtistTile
import `in`.artistant.app.designsystem.component.ArtistTileSkeleton
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.Skeleton
import `in`.artistant.app.designsystem.component.tierColor
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import kotlinx.coroutines.delay

/**
 * Discover — the immersive marquee home (iOS `DiscoverView`). A full-bleed
 * auto-scrolling hero carousel (~74% height) over horizontal `ArtistTile` rails.
 * Pull-to-refresh reloads all rails; the carousel auto-advance is reduce-motion
 * aware (it freezes, but stays manually swipeable). Tapping any artist → [onArtist].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onArtist: (String) -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedIds by viewModel.saved.ids.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val reduceMotion = rememberReduceMotion()

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.hasContent,
        onRefresh = viewModel::load,
        modifier = modifier.fillMaxSize().background(colors.bg),
    ) {
        when {
            state.hasContent -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    HeroCarousel(
                        hero = state.hero,
                        savedIds = savedIds,
                        reduceMotion = reduceMotion,
                        onArtist = onArtist,
                        onSave = viewModel::toggleSaved,
                        onProfile = onProfile,
                    )
                }
                item { FeaturedRail(state.featured, onArtist) }
                item { TileRail("Top 10 in Bangalore", state.topBangalore, onArtist) }
                item { TileRail("Top 10 in India", state.topIndia, onArtist) }
                item { TileRail("New on Artistant", state.newOnArtistant, onArtist) }
                item { TileRail("Comedy", state.comedy, onArtist) }
                item { Spacer(Modifier.height(AppTheme.dimens.space.xxl)) }
            }

            state.isLoading -> LoadingState()
            state.loadError != null -> ErrorState(state.loadError!!, viewModel::load)
            else -> EmptyState()
        }
    }
}

// MARK: - Hero carousel

@Composable
private fun HeroCarousel(
    hero: List<Artist>,
    savedIds: Set<String>,
    reduceMotion: Boolean,
    onArtist: (String) -> Unit,
    onSave: (String) -> Unit,
    onProfile: () -> Unit,
) {
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.74f).dp
    val pagerState = rememberPagerState(pageCount = { hero.size })

    // Auto-advance every 6s (iOS heroTimer). Frozen under reduce-motion; the
    // pager stays manually swipeable. Keyed on size so it restarts after refresh.
    LaunchedEffect(hero.size, reduceMotion) {
        if (!reduceMotion && hero.size > 1) {
            while (true) {
                delay(6000)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % hero.size)
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            HeroSlide(hero[page], isSaved = savedIds.contains(hero[page].id), onArtist = onArtist, onSave = onSave)
        }
        // Fixed masthead — sits ABOVE the pager and does NOT swipe with the slides
        // (iOS heroMasthead): the editorial headline + a profile chip stay put.
        HeroMasthead(onProfile = onProfile, modifier = Modifier.align(Alignment.TopStart))
        if (hero.size > 1) {
            HeroDots(
                count = hero.size,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = AppTheme.dimens.space.lg),
            )
        }
    }
}

/**
 * The fixed hero overlay (iOS `heroMasthead`): an editorial serif "Tonight in {City}."
 * headline on the left and a circular profile chip on the right that switches to the
 * Profile tab. Rendered on top of the swiping carousel, so it never moves.
 */
@Composable
private fun HeroMasthead(onProfile: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // TODO: bind to the signed-in user's city — it isn't plumbed to Discover yet.
    // The catalogue is national, so "India" is the honest country-wide fallback
    // (matches iOS's OnboardingStore.city → "India" default).
    val place = "India"
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = space.xl)
            .padding(top = space.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "Tonight in\n$place.",
            style = AppTheme.type.displayMedium,
            color = Color.White,
        )
        // Profile chip → Profile tab (onProfile switches the bottom-nav selection).
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.accent, colors.brand)))
                .border(2.dp, colors.brand, CircleShape)
                .clickable { onProfile() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Your profile", tint = colors.brandInk)
        }
    }
}

@Composable
private fun HeroSlide(
    artist: Artist,
    isSaved: Boolean,
    onArtist: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val tier = ScoreBands.tier(artist.score, artist.gigs)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(artist.gradient))
            .clickable { onArtist(artist.id) },
    ) {
        if (!artist.coverUrl.isNullOrEmpty()) {
            AsyncImage(
                model = artist.coverUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Bottom-darken scrim that fades into the page ground.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.44f to Color.Transparent,
                    0.66f to Color.Black.copy(alpha = 0.42f),
                    1.0f to colors.bg,
                ),
            ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = space.xl)
                .padding(bottom = 46.dp),
        ) {
            // Per-artist availability pill — soonest open weekday from the wizard's
            // days_available (iOS heroSlideContent). Neutral translucent capsule
            // (lime stays reserved for the Book CTA); the green dot is the only
            // colour. Hidden when the artist set no availability (no false pill).
            availabilityKicker(artist.daysAvailable)?.let { kicker ->
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = space.md)
                        .height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(colors.good))
                    Text(
                        kicker,
                        style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(space.md))
            }
            Text(
                artist.name,
                style = AppTheme.type.displayHero,
                color = Color.White,
                maxLines = 2,
            )
            Spacer(Modifier.height(space.md))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonoMeta(artist.category.uppercase())
                Dot()
                MonoMeta(artist.city.uppercase())
                Dot()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(tierColor(tier)))
                    Text(
                        "${artist.score} ${tier.label.uppercase()}",
                        style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
                        color = tierColor(tier),
                    )
                }
                Dot()
                MonoMeta("FROM ${formatInrShort(artist.packages.firstOrNull()?.price ?: artist.price)}")
            }
            Spacer(Modifier.height(space.lg))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
                // "Book" — the one lime CTA. Visual only; the tap falls through
                // to the slide's click → ArtistProfile where booking begins.
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(colors.brand)
                        .padding(horizontal = space.xl)
                        .height(50.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Book →", style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.brandInk)
                }
                // Save — intercepts its own tap.
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onSave(artist.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isSaved) "Unsave artist" else "Save artist",
                        tint = if (isSaved) colors.brand else Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == current) 22.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i == current) AppTheme.colors.brand else Color.White.copy(alpha = 0.3f)),
            )
        }
    }
}

// MARK: - Rails

@Composable
private fun FeaturedRail(artists: List<Artist>, onArtist: (String) -> Unit) {
    if (artists.isEmpty()) return
    val space = AppTheme.dimens.space
    Column(Modifier.padding(top = space.xl)) {
        SectionHeader("Featured this week")
        LazyRow(
            contentPadding = PaddingValues(horizontal = space.xl),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            items(artists, key = { it.id }) { a ->
                // Larger "feature frame" — reuse ArtistTile at a taller size.
                ArtistTile(
                    artist = a,
                    width = 300.dp,
                    height = 440.dp,
                    modifier = Modifier.clickable { onArtist(a.id) },
                )
            }
        }
    }
}

@Composable
private fun TileRail(title: String, artists: List<Artist>, onArtist: (String) -> Unit) {
    if (artists.isEmpty()) return
    val space = AppTheme.dimens.space
    Column(Modifier.padding(top = space.xl)) {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = space.xl),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            items(artists, key = { it.id }) { a ->
                ArtistTile(artist = a, modifier = Modifier.clickable { onArtist(a.id) })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold),
        color = AppTheme.colors.ink,
        modifier = Modifier.padding(horizontal = AppTheme.dimens.space.xl, vertical = AppTheme.dimens.space.md),
    )
}

// MARK: - State surfaces

@Composable
private fun LoadingState() {
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxSize().padding(top = space.xxl)) {
        Skeleton(
            Modifier.fillMaxWidth().height(420.dp).padding(horizontal = space.xl),
            cornerRadius = AppTheme.dimens.radii.lg,
        )
        Spacer(Modifier.height(space.xl))
        LazyRow(
            contentPadding = PaddingValues(horizontal = space.xl),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            items(3) { ArtistTileSkeleton() }
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
        Text("Couldn't load artists", style = AppTheme.type.displaySmall, color = AppTheme.colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(message, style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
        Spacer(Modifier.height(space.lg))
        PrimaryButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun EmptyState() {
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().padding(space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No artists yet", style = AppTheme.type.displaySmall, color = AppTheme.colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(
            "We're onboarding the first artists right now. Pull to refresh in a moment.",
            style = AppTheme.type.footnote,
            color = AppTheme.colors.ink3,
        )
    }
}

// MARK: - Small helpers

@Composable
private fun MonoMeta(text: String) {
    Text(
        text,
        style = AppTheme.type.monoSmall,
        color = Color.White.copy(alpha = 0.82f),
        maxLines = 1,
    )
}

@Composable
private fun Dot() {
    Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
}

/** Reads the system "remove animations" a11y setting (iOS reduce-motion parity). */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scale = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}
