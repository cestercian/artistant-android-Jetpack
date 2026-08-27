package `in`.artistant.app.feature.discover

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.availabilityKicker
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.component.ArtistTile
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.InlineBanner
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.heroGlass
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.MotionSpecs
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.domain.score.tierColor
import kotlinx.coroutines.delay

/**
 * Discover — the client home, built as an immersive marquee rather than a search
 * surface.
 *
 * The screen is one full-bleed hero (~3/4 of the viewport) over a stack of
 * horizontal rails. Two structural decisions carry the whole design:
 *
 * - **The hero is the page's first screenful, not a banner.** It bleeds under
 *   the status bar and behind the floating tab bar, so the first thing the app
 *   shows is a performer at full size. That is why this screen — alone among the
 *   client destinations — opts out of the scaffold insets (see `TabPane`) and
 *   reserves its own trailing tailroom instead.
 * - **The masthead does not swipe.** It sits in a fixed layer over the pager, so
 *   "Tonight in <City>." and the profile chip stay put while slides move beneath
 *   them. Putting them inside a page would make the app's identity flicker on
 *   every rotation.
 *
 * Photo containment: every cover is drawn as a *background layer* inside a
 * fixed-size Box, never as a `ContentScale.Crop` child that could stretch its
 * parent. A cropped image left in a sizing position grows the container to the
 * source image's aspect, which silently breaks any fixed-height frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val hero = AppTheme.dimens.hero

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.hero.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .semantics { testTag = "screen.discover" },
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
                // The copy used to read "Pull to refresh once the roster is
                // live." — an instruction for the one gesture this branch cannot
                // receive. PullToRefreshBox listens on a nested-scroll
                // connection, and `EmptyState` is a plain Column with nothing
                // scrollable in it, so no delta ever reaches the connection.
                // Discover is also the NavHost start destination, so its
                // ViewModel is never re-created and a tab switch won't refetch
                // either: without a button of its own, an empty roster was a dead
                // end. Same escape hatch the inbox's empty state carries.
                EmptyState(
                    title = "No artists yet",
                    body = "We're onboarding the first artists right now — check back in a moment.",
                    actionLabel = "Refresh",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> RevealOnAppear {
                // No content padding: the hero owns the status-bar area and
                // the tailroom item below clears the floating tab bar.
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.hero.isNotEmpty()) {
                        item(key = "hero") {
                            HeroCarousel(
                                artists = state.hero,
                                place = state.mastheadPlace,
                                avatarInitial = state.avatarInitial,
                                savedIds = savedIds,
                                onArtistClick = onArtistClick,
                                onToggleSave = viewModel::toggleSaved,
                            )
                        }
                    } else {
                        // The hero is the only thing that absorbs the status-bar
                        // inset. With no hero (a city/comedy-only roster) the
                        // first rail would otherwise start under the clock.
                        item(key = "statusInset") { Spacer(Modifier.statusBarsPadding()) }
                    }
                    // A refresh that fails over a roster already on screen used
                    // to be completely silent: the error branch above requires an
                    // empty hero, so all a failed pull did was retract the
                    // indicator — indistinguishable from a successful one, and
                    // the user walks away believing the rails are current. It
                    // sits here rather than above the hero because the hero owns
                    // the status-bar inset and the first screenful; this is the
                    // first thing in the rail stack. `refresh()` clears
                    // `loadError`, so it self-hides on the next good load.
                    if (state.loadError != null) {
                        item(key = "refreshError") {
                            InlineBanner(
                                title = "Couldn't refresh the roster",
                                detail = state.loadError,
                                tone = BannerTone.Failure,
                                actionLabel = "Retry",
                                onAction = viewModel::refresh,
                                modifier = Modifier
                                    .padding(horizontal = AppTheme.dimens.space.xl)
                                    .padding(top = AppTheme.dimens.space.md)
                                    .semantics { testTag = "discover.refreshErrorBanner" },
                            )
                        }
                    }
                    if (state.featured.isNotEmpty()) {
                        item(key = "featured") {
                            FeaturedRail(state.featured, onArtistClick)
                        }
                    }
                    tileSection("Top 10 in Bangalore", "topBangalore", state.topBangalore, onArtistClick)
                    tileSection("Top 10 in India", "topIndia", state.topIndia, onArtistClick)
                    tileSection("New on Artistant", "new", state.newOnArtistant, onArtistClick)
                    tileSection("Comedy", "comedy", state.comedy, onArtistClick)
                    item(key = "tailroom") {
                        // Clears the floating tab bar so the last rail is fully
                        // reachable — this screen takes no scaffold bottom inset.
                        Spacer(Modifier.height(hero.scrollTailroom))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroCarousel(
    artists: List<Artist>,
    place: String,
    avatarInitial: String,
    savedIds: Set<String>,
    onArtistClick: (String) -> Unit,
    onToggleSave: (String) -> Unit,
) {
    val hero = AppTheme.dimens.hero
    val space = AppTheme.dimens.space
    val pagerState = rememberPagerState(pageCount = { artists.size })

    // Height is a fraction of the *device* height, not of a parent constraint:
    // inside a LazyColumn the vertical constraint is infinite, so there is
    // nothing to take a fraction of. Configuration reports the window's height,
    // which under edge-to-edge is the full screen — exactly what a full-bleed
    // hero wants to measure against.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = screenHeight * hero.heightFraction

    // Auto-advance is motion the user did not ask for, so it is the first thing
    // reduce-motion takes away. Resolved from the theme now rather than re-read
    // here — the setting is read once at the theme root for the whole app.
    val animationsEnabled = !AppTheme.reduceMotion

    if (DiscoverHeroLogic.shouldAutoAdvance(artists.size, animationsEnabled)) {
        // Keyed on the settled page so a manual swipe restarts the dwell timer
        // rather than yanking the carousel forward a moment later.
        LaunchedEffect(pagerState.settledPage, artists.size) {
            delay(hero.autoAdvanceMillis)
            pagerState.animateScrollToPage(
                DiscoverHeroLogic.nextPage(pagerState.currentPage, artists.size),
            )
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            HeroSlide(
                artist = artists[page],
                isSaved = savedIds.contains(artists[page].id.lowercase()),
                onClick = { onArtistClick(artists[page].id) },
                onToggleSave = { onToggleSave(artists[page].id) },
            )
        }

        // Fixed layers over the pager — see the class doc for why these don't swipe.
        HeroMasthead(
            place = place,
            avatarInitial = avatarInitial,
            modifier = Modifier.align(Alignment.TopStart),
        )
        if (artists.size > 1) {
            HeroDots(
                count = artists.size,
                selected = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = space.lg),
            )
        }
    }
}

@Composable
private fun HeroSlide(
    artist: Artist,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val hero = AppTheme.dimens.hero

    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .semantics { testTag = "discover.featured"; contentDescription = artist.name },
    ) {
        ArtistCoverLayer(artist)

        // Two scrims, each doing one job. The top one buys the masthead legible
        // contrast over an arbitrary photo. The bottom one darkens the caption
        // area AND lands on the page background at the very bottom edge, so the
        // hero dissolves into the rails instead of ending on a visible seam.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.55f),
                            0.24f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.44f to Color.Transparent,
                            0.66f to Color.Black.copy(alpha = 0.42f),
                            1.0f to colors.bg,
                        ),
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = space.xl),
        ) {
            // Rendered only when the artist actually published weekday
            // availability. A badge that says "available" on everyone says
            // nothing — no pill beats a pill that might be lying.
            availabilityKicker(artist.daysAvailable)?.let { kicker ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(hero.labelGap),
                    modifier = Modifier
                        .heroGlass(CircleShape)
                        .height(hero.statusPillHeight)
                        .padding(horizontal = space.md),
                ) {
                    Box(
                        Modifier
                            .size(hero.signalDot)
                            .clip(CircleShape)
                            .background(colors.good),
                    )
                    Text(kicker, style = AppTheme.type.heroStatus, color = Color.White)
                }
                Spacer(Modifier.height(space.md))
            }

            Text(
                text = artist.name,
                style = AppTheme.type.heroName.copy(
                    // The name sits directly on a photo, so it gets a soft drop
                    // shadow rather than relying on the scrim alone — a light
                    // shirt behind a white serif is otherwise unreadable.
                    //
                    // `Shadow` measures in PIXELS, so the offset and the blur are
                    // converted from dp rather than written raw: raw, the 16 that
                    // matches iOS's 16pt would land as 5dp of blur on a 3x phone
                    // and 16dp on a 1x one — legibility that depends on density.
                    shadow = with(LocalDensity.current) {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.45f),
                            offset = Offset(0f, 3.dp.toPx()),
                            blurRadius = 16.dp.toPx(),
                        )
                    },
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(space.md))
            HeroMetaLine(artist)
            Spacer(Modifier.height(space.lg))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // "Book" is a visual, not a button: the tap falls through to the
                // slide, which opens the artist. The booking flow starts there,
                // so a nested button would only add a second route to the same
                // place — and steal the larger tap target from the card.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(hero.labelGap),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brand)
                        .height(hero.ctaHeight)
                        .padding(horizontal = space.xl),
                ) {
                    Text(
                        "Book",
                        style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                        color = colors.brandInk,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colors.brandInk,
                        modifier = Modifier.size(AppTheme.dimens.size.iconMd),
                    )
                }
                Spacer(Modifier.width(space.md))
                Box(
                    Modifier
                        .size(hero.saveSize)
                        .heroGlass(CircleShape)
                        // Intercepts the slide's tap so the heart saves instead
                        // of navigating.
                        .clickable(onClick = onToggleSave)
                        .semantics {
                            testTag = "discover.featured.save"
                            contentDescription = if (isSaved) "Unsave artist" else "Save artist"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isSaved) colors.brand else Color.White,
                        modifier = Modifier.size(AppTheme.dimens.size.iconLg),
                    )
                }
            }
            // Clearance for the page dots pinned below.
            Spacer(Modifier.height(hero.ctaBottomGap))
        }
    }
}

/** `INDIE BAND · BANGALORE · ● 82 TRUSTED · FROM ₹75K` */
@Composable
private fun HeroMetaLine(artist: Artist) {
    val colors = AppTheme.colors
    val hero = AppTheme.dimens.hero
    val space = AppTheme.dimens.space
    val tier = ScoreBands.tier(artist.score, artist.gigs)
    val tint = tierColor(tier, colors)
    val ink = colors.inkOnMedia
    // The strip must survive a long category or city on a narrow screen. Rather
    // than shrink the whole line (which would desync it from every other mono
    // run on the page), the two free-text fields are the ones allowed to
    // ellipsize; the score and the price — the two facts a client is scanning
    // for — always render in full.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        MetaText(artist.category.uppercase(), ink, Modifier.weight(1f, fill = false))
        MetaDot()
        MetaText(artist.city.uppercase(), ink, Modifier.weight(1f, fill = false))
        MetaDot()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(hero.scoreGap),
        ) {
            Box(
                Modifier
                    .size(hero.signalDot)
                    .clip(CircleShape)
                    .background(tint),
            )
            // A New-tier artist's number is not a score, it is an absence — under
            // five gigs there is nothing to rank. Printing it gives a fresh
            // profile "● 0 NEW", which is the one thing the tile capsule and the
            // ring both go out of their way to avoid.
            Text(
                if (tier == ScoreTier.New) {
                    tier.label.uppercase()
                } else {
                    "${artist.score} ${tier.label.uppercase()}"
                },
                style = AppTheme.type.heroMeta.copy(fontWeight = FontWeight.Bold),
                color = tint,
                maxLines = 1,
            )
        }
        MetaDot()
        MetaText(DiscoverHeroLogic.fromPriceLabel(artist.packages, artist.price), ink)
    }
}

@Composable
private fun MetaText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.heroMeta,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The 3dp "·" between metadata fields — a dot, not a glyph, so it never wraps. */
@Composable
private fun MetaDot() {
    Box(
        Modifier
            .size(AppTheme.dimens.hero.separatorDot)
            .clip(CircleShape)
            .background(AppTheme.colors.inkOnMediaSoft),
    )
}

/**
 * "Tonight in *<City>*." — the app's signature editorial move: a serif headline
 * where exactly ONE word is set in italic brand lime. The accent is the screen's
 * single signal, which is why nothing else up here is tinted.
 */
@Composable
private fun HeroMasthead(
    place: String,
    avatarInitial: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val hero = AppTheme.dimens.hero
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = space.xl)
            .padding(top = space.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = buildAnnotatedString {
                append("Tonight in\n")
                withStyle(
                    SpanStyle(color = colors.brand, fontStyle = FontStyle.Italic),
                ) { append(place) }
                append(".")
            },
            style = AppTheme.type.masthead,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        MastheadAvatar(initial = avatarInitial, size = hero.avatarSize)
    }
}

/**
 * The profile chip: an accent→brand gradient disc with a ringed edge.
 *
 * Distinct from the shared `Avatar` on purpose — that one derives a per-name hue
 * so strangers are visually distinguishable, which is exactly wrong here. This
 * chip always represents *you*.
 *
 * The violet stop is not a slip and is deliberately NOT swapped for a brand-only
 * ramp: iOS fills the same circle with `[.accent, .brand]` (DiscoverView, the
 * masthead button), and this screen is client-only, so the disc runs from the
 * fixed accent into the session's own lime. What ties it to the role is the rim
 * and the initial, both of which are brand.
 */
@Composable
private fun MastheadAvatar(initial: String, size: Dp) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.accent, colors.brand)))
            .border(AppTheme.dimens.size.stroke, colors.brand, CircleShape)
            .semantics { testTag = "discover.avatar"; contentDescription = "Your profile" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold),
            color = colors.brandInk,
        )
    }
}

/**
 * Page dots — the active one stretches into a capsule rather than growing.
 *
 * The stretch is animated, and that is the point: watching one dot elongate
 * while its neighbour contracts reads as *the same indicator moving*, which
 * matches what the pager underneath actually did. Swapping two static widths
 * instead makes the row flicker and tells the eye nothing about direction.
 *
 * Both the width and the fill animate on the same curve so the shape and the
 * colour arrive together — staggering them makes a lime stub appear before it
 * has finished growing.
 */
@Composable
private fun HeroDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val hero = AppTheme.dimens.hero
    val colors = AppTheme.colors
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val duration = MotionSpecs.durationMillis(motion.indicator, reduceMotion)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(hero.pageDotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == selected
            val width by animateDpAsState(
                targetValue = if (active) hero.pageDotActiveWidth else hero.pageDot,
                animationSpec = tween(duration, easing = motion.emphasized),
                label = "dotWidth",
            )
            val fill by animateColorAsState(
                targetValue = if (active) colors.brand else Color.White.copy(alpha = INACTIVE_DOT_ALPHA),
                animationSpec = tween(duration, easing = motion.standard),
                label = "dotFill",
            )
            Box(
                Modifier
                    .width(width)
                    .height(hero.pageDot)
                    .clip(CircleShape)
                    .background(fill),
            )
        }
    }
}

/** How far an inactive page dot recedes against the hero photo behind it. */
private const val INACTIVE_DOT_ALPHA = 0.3f

// ─────────────────────────────────────────────────────────────────────────────
// Rails
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeaturedRail(artists: List<Artist>, onArtistClick: (String) -> Unit) {
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = space.xl),
        verticalArrangement = Arrangement.spacedBy(space.md),
    ) {
        SectionHeader("Featured this week")
        LazyRow(
            contentPadding = PaddingValues(horizontal = space.xl),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            items(artists, key = { it.id }) { artist ->
                FeatureFrame(artist) { onArtistClick(artist.id) }
            }
        }
    }
}

/**
 * A large editorial frame — the same anatomy as the hero, shrunk. Deliberately
 * NOT the standard `ArtistTile`: this rail's job is to show a handful of artists
 * *big*, so it gets the serif name and the full metadata strip that the compact
 * tile can't fit.
 */
@Composable
private fun FeatureFrame(artist: Artist, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val hero = AppTheme.dimens.hero
    val radii = AppTheme.dimens.radii
    val tier = ScoreBands.tier(artist.score, artist.gigs)
    val tint = tierColor(tier, colors)
    val shape = RoundedCornerShape(radii.xl)

    Box(
        Modifier
            .size(width = hero.frameWidth, height = hero.frameHeight)
            .shadow(elevation = hero.frameShadow, shape = shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .semantics {
                testTag = "discover.feature.${artist.id}"
                contentDescription = artist.name
            },
    ) {
        ArtistCoverLayer(artist)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.10f),
                            0.40f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.55f),
                            1.00f to Color.Black.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(space.lg),
        ) {
            Text(
                text = artist.name,
                style = AppTheme.type.frameName,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(space.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(hero.frameMetaGap),
            ) {
                Text(
                    artist.category.uppercase(),
                    style = AppTheme.type.frameMeta,
                    color = colors.inkOnMedia,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                MetaDot()
                // Same rule as the hero: no number for an unranked artist.
                Text(
                    if (tier == ScoreTier.New) {
                        tier.label.uppercase()
                    } else {
                        "${artist.score} ${tier.label.uppercase()}"
                    },
                    style = AppTheme.type.frameMeta.copy(fontWeight = FontWeight.Bold),
                    color = tint,
                    maxLines = 1,
                )
                MetaDot()
                Text(
                    DiscoverHeroLogic.fromPriceLabel(artist.packages, artist.price),
                    style = AppTheme.type.frameMeta,
                    color = colors.inkOnMedia,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * Bold-sans section title with a trailing chevron.
 *
 * [slug] namespaces the per-tile test tag by section because the SAME artist
 * legitimately appears in more than one rail (a top Bangalore act is also a top
 * India act), and a bare `discover.tile.<id>` would produce duplicate ids on one
 * screen — ambiguous for a screen reader and for any test lookup.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.tileSection(
    title: String,
    slug: String,
    artists: List<Artist>,
    onArtistClick: (String) -> Unit,
) {
    if (artists.isEmpty()) return
    item(key = "section-$slug") {
        val space = AppTheme.dimens.space
        val hero = AppTheme.dimens.hero
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = space.xl),
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            SectionHeader(title)
            LazyRow(
                contentPadding = PaddingValues(horizontal = space.xl),
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                items(artists, key = { it.id }) { artist ->
                    ArtistTile(
                        artist = artist,
                        onClick = { onArtistClick(artist.id) },
                        width = hero.tileWidth,
                        height = hero.tileHeight,
                        modifier = Modifier.semantics {
                            testTag = "discover.tile.$slug.${artist.id}"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val hero = AppTheme.dimens.hero
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl)
            .padding(bottom = space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(hero.headerChevronGap),
    ) {
        Text(
            title,
            style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(AppTheme.dimens.size.iconLg),
        )
    }
}

/**
 * The never-empty cover: brand gradient first, real photo over it when there is
 * one. Ordering matters — the gradient is not a "loading" state you swap out,
 * it's the floor the photo lands on, so an artist with no media still gets a
 * composed-looking frame and a slow network never shows a blank rectangle.
 */
@Composable
private fun ArtistCoverLayer(artist: Artist) {
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
