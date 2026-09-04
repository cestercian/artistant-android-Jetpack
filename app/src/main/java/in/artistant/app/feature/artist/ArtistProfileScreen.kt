package `in`.artistant.app.feature.artist

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.ArtistPrompt
import `in`.artistant.app.data.model.GalleryPhoto
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.ListRow
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.SampleRow
import `in`.artistant.app.designsystem.component.SectionHeader
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.component.SkeletonCircle
import `in`.artistant.app.designsystem.component.ToastHost
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.domain.artist.ServiceTags
import `in`.artistant.app.domain.artist.spotifyEmbedUrl
import `in`.artistant.app.feature.score.ScoreBreakdownSheet
import `in`.artistant.app.platform.media.rememberSamplePlayer

/**
 * The artist profile — design screens 04 / 54 / 55 / 101 / 103.
 *
 * A **listing, not a feed**. The redesign retired the full-bleed hero that used
 * to open this page: the identity is a 96dp round portrait beside the name, and
 * the space that photo was spending goes to the things a host actually decides
 * on — three stats they can check, the packages, and the clips.
 *
 * **Packages replaced the rate card, and the price rides the CTA.** A rate card
 * states a number the host then has to convert into "what do I get"; a package
 * is the unit they buy. The dock therefore quotes the *minimum* over the loaded
 * packages ("Check availability · ₹26,000"), because "from" means minimum — not
 * the selected tier, and not `artists.min_price`, which is denormalized and
 * confirmed stale on dev.
 *
 * Three branches, and they are three different screens on purpose (§2): a
 * skeleton with **no navigation bar** while the cached tile is stitched into a
 * full record (54), a named cause with a route out when there is no such artist
 * (55), and the page itself. Everything below the page's fold degrades on its
 * own — reviews can fail without taking the profile down (100), the score can
 * fail to itemise without taking the number down (99), and an artist with no
 * audio gets a section that names the signal to use instead (101).
 */
@Composable
fun ArtistProfileScreen(
    onBack: () -> Unit,
    onBook: (artistId: String) -> Unit = {},
    onRequestQuote: (artistId: String) -> Unit = {},
    onMessage: (artistId: String) -> Unit = {},
    onBrowse: () -> Unit = onBack,
    onSeeReviews: (artistId: String) -> Unit = {},
    onSeeBookability: (artistId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArtistProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val context = LocalContext.current

    Box(modifier.fillMaxSize().background(colors.surface)) {
        when {
            state.isLoading && state.artist == null -> ArtistProfileSkeleton()

            state.artist == null -> ArtistNotFound(
                // ARTIST_NOT_FOUND is the server saying there is no such row; any
                // other message is a read that failed, and the two get different
                // copy because they license different actions. Retry is pointless
                // for the first and the only useful control for the second.
                notFound = state.loadError == ARTIST_NOT_FOUND,
                onBack = onBack,
                onBrowse = onBrowse,
                onRetry = viewModel::refresh,
            )

            else -> {
                val artist = state.artist!!
                LoadedProfile(
                    state = state,
                    artist = artist,
                    onBack = onBack,
                    onOverflow = viewModel::openActionSheet,
                    onScore = viewModel::openScoreSheet,
                    onSelectPackage = viewModel::selectPackage,
                    onRequestQuote = { onRequestQuote(artist.id) },
                    onSeeReviews = { onSeeReviews(artist.id) },
                    onRetryReviews = viewModel::refresh,
                    onRetryReport = viewModel::retryReport,
                    onDismissReportFailure = viewModel::dismissReportFailure,
                    onMessage = { onMessage(artist.id) },
                    onBook = {
                        // Hand the tapped tier over BEFORE navigating — the route
                        // carries only the artist id, so the booking screen reads
                        // the selection from the shared draft store on the way in.
                        viewModel.startBooking()
                        onBook(artist.id)
                    },
                )

                if (state.showActionSheet) {
                    ProfileActionSheet(
                        artistName = artist.name,
                        isSaved = state.isSaved,
                        isSelf = state.isSelf,
                        onToggleSaved = viewModel::toggleSaved,
                        onShare = { shareArtist(context, artist) },
                        onReport = viewModel::openReportSheet,
                        onDismiss = viewModel::dismissActionSheet,
                    )
                }
                if (state.showReportSheet) {
                    ReportArtistSheet(
                        artistName = artist.name,
                        onSubmit = viewModel::submitReport,
                        onDismiss = viewModel::dismissReportSheet,
                    )
                }
                if (state.showScoreSheet) {
                    ScoreBreakdownSheet(
                        artist = artist,
                        breakdown = state.scoreBreakdown,
                        breakdownFailed = state.scoreFailed,
                        reviews = state.reviews,
                        reviewsFailed = state.reviewsFailed,
                        onRetry = viewModel::refresh,
                        onSeeBookability = {
                            viewModel.dismissScoreSheet()
                            onSeeBookability(artist.id)
                        },
                        onDismiss = viewModel::dismissScoreSheet,
                    )
                }
            }
        }
        // "Queued", never "received" — the insert soft-fails into a local log and
        // the reader is owed the difference (screen 56's note).
        ToastHost(
            message = ArtistProfileFacts.reportToast(state.reportOutcome),
            onDismiss = viewModel::dismissReportToast,
            icon = Icons.Filled.Flag,
        )
    }
}

// ── Branch: loading (screen 54) ──────────────────────────────────────────────

/**
 * Screen 54. The page's own rhythm in grey, and **no navigation bar**.
 *
 * The bar is what would flash: this screen is what a push lands on while the
 * cached Discover/Search tile is being stitched into a full record, and a
 * centred title over an empty page reads as a screen that arrived broken. The
 * blocks below are the real layout's — portrait, name, stat strip, About,
 * packages — so the content does not jump when it lands.
 */
@Composable
private fun ArtistProfileSkeleton(modifier: Modifier = Modifier) {
    val dimens = AppTheme.dimens
    val space = dimens.space
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = dimens.component.gutter)
            .padding(top = space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.xl),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCircle(dimens.size.avatarXl)
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                SkeletonBlock(
                    Modifier
                        .width(dimens.component.skeletonSectionWidth)
                        .height(dimens.component.skeletonTitleHeight),
                )
                SkeletonBlock(
                    Modifier
                        .width(dimens.component.skeletonSubtitleWidth)
                        .height(dimens.component.skeletonLineHeight),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            repeat(STAT_CELLS) {
                SkeletonBlock(
                    Modifier
                        .weight(1f)
                        .height(dimens.size.dateCellH),
                    radius = dimens.radii.lg,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
            repeat(SKELETON_BODY_LINES) { line ->
                SkeletonBlock(
                    Modifier
                        .fillMaxWidth(SKELETON_LINE_WIDTHS[line])
                        .height(dimens.component.skeletonLineHeight),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
            SkeletonBlock(
                Modifier
                    .width(dimens.component.skeletonTitleWidth)
                    .height(dimens.component.skeletonTitleHeight),
            )
            SkeletonBlock(
                Modifier
                    .fillMaxWidth()
                    .height(dimens.component.skeletonTile),
                radius = dimens.radii.lg,
            )
        }
    }
}

private const val STAT_CELLS = 3
private const val SKELETON_BODY_LINES = 3
private val SKELETON_LINE_WIDTHS = listOf(1f, 0.94f, 0.62f)

// ── Branch: nothing to show (screen 55) ─────────────────────────────────────

/**
 * Screen 55. Names the likely cause and offers the route out.
 *
 * The one thing this screen must not do is go blank: it is where a stale share
 * link lands, and "nothing here" with a back arrow gives the reader no account
 * of what happened. A read that FAILED gets Retry instead — retrying a row the
 * server says does not exist is a button that cannot work.
 */
@Composable
private fun ArtistNotFound(
    notFound: Boolean,
    onBack: () -> Unit,
    onBrowse: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = dimens.component.gutter),
    ) {
        IconCircle(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.padding(top = dimens.space.sm),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (notFound) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "We can't find this artist",
                    body = "This artist may have been unpublished. Head back to " +
                        "Discover to see who's available.",
                    actionLabel = "Back to Discover",
                    onAction = onBrowse,
                )
            } else {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Couldn't load this profile",
                    body = "The profile is there — we couldn't reach it. Check your " +
                        "connection and try again.",
                    actionLabel = "Retry",
                    onAction = onRetry,
                    secondaryLabel = "Back to Discover",
                    onSecondary = onBrowse,
                )
            }
        }
    }
}

// ── Branch: the page (screens 04 / 101 / 103) ────────────────────────────────

@Composable
private fun LoadedProfile(
    state: ArtistProfileUiState,
    artist: Artist,
    onBack: () -> Unit,
    onOverflow: () -> Unit,
    onScore: () -> Unit,
    onSelectPackage: (Int) -> Unit,
    onRequestQuote: () -> Unit,
    onSeeReviews: () -> Unit,
    onRetryReviews: () -> Unit,
    onRetryReport: () -> Unit,
    onDismissReportFailure: () -> Unit,
    onMessage: () -> Unit,
    onBook: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    RevealOnAppear {
        Column(Modifier.fillMaxSize().background(colors.surface)) {
            ProfileHeader(
                isSelf = state.isSelf,
                onBack = onBack,
                onOverflow = onOverflow,
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.component.gutter),
                verticalArrangement = Arrangement.spacedBy(space.xl),
            ) {
                if (state.isSelf) {
                    // Screen 103. Stated at the top rather than implied by the
                    // missing Book button, because "why can't I book" is the
                    // question a missing control raises and never answers.
                    AccentNote(
                        lead = "This is how clients see your profile.",
                        text = "Booking controls are off while you're looking at " +
                            "your own act.",
                        modifier = Modifier.padding(top = space.md),
                    )
                }
                // A profile can be drawn from the Discover/Search TILE when
                // hydration failed, and the tile carries no packages, bio,
                // samples or reviews — so every block below would state an
                // absence it cannot vouch for. The failure has to be visible and
                // retryable here.
                state.loadError?.let { message ->
                    Banner(
                        title = message,
                        detail = "Pricing and packages may be missing.",
                        tone = BannerTone.Failure,
                        actionLabel = "Retry",
                        onAction = onRetryReviews,
                        modifier = Modifier.padding(top = space.md),
                    )
                }
                // A report the server refused AND the device failed to log. This
                // is a banner rather than the toast its two siblings get,
                // because a toast fades and this one has to survive until the
                // report is either filed or explicitly abandoned — the reader
                // was told nothing is holding it, and the app owes them the way
                // to try again without retyping.
                if (state.failedReport != null) {
                    Column(
                        Modifier.padding(top = space.md),
                        verticalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        Banner(
                            title = "Your report wasn't filed",
                            detail = "It didn't reach Artistant, and we couldn't " +
                                "save it on this device either. Nothing is holding " +
                                "it right now.",
                            tone = BannerTone.Failure,
                            actionLabel = "Try again",
                            onAction = onRetryReport,
                        )
                        Text(
                            "Discard this report",
                            style = AppTheme.type.subtitle.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.ink4,
                            modifier = Modifier
                                .clip(RoundedCornerShape(dimens.radii.sm))
                                .clickable(onClick = onDismissReportFailure)
                                .padding(
                                    horizontal = space.sm,
                                    vertical = space.xs,
                                ),
                        )
                    }
                }

                IdentityBlock(
                    artist = artist,
                    ratingLabel = ArtistProfileFacts.ratingLabel(state.reviews),
                    modifier = Modifier.padding(top = space.lg),
                )
                StatStrip(artist = artist, onScore = onScore)

                if (artist.bio.isNotBlank()) {
                    AboutBlock(bio = artist.bio)
                }
                PackagesBlock(
                    packages = artist.packages,
                    packagesLoaded = state.packagesLoaded,
                    selectedIndex = state.selectedPackageIndex,
                    onSelect = onSelectPackage,
                    weekendPremiumPct = artist.weekendPremiumPct,
                    newArtistDiscountPct = artist.newArtistDiscountPct,
                    onRequestQuote = onRequestQuote.takeUnless { state.isSelf },
                )
                if (artist.gallery.isNotEmpty()) {
                    GalleryBlock(photos = artist.gallery)
                }
                ListenBlock(
                    samples = artist.samples,
                    spotifyEmbed = spotifyEmbedUrl(artist.spotifyArtistUrl),
                )
                if (artist.prompts.isNotEmpty()) {
                    PromptsBlock(prompts = artist.prompts)
                }
                if (artist.serviceTags.isNotEmpty()) {
                    ServicesBlock(tags = artist.serviceTags)
                }
                ReviewsBlock(
                    reviews = state.reviews,
                    failed = state.reviewsFailed,
                    artist = artist,
                    onRetry = onRetryReviews,
                    onSeeAll = onSeeReviews,
                )
                Spacer(Modifier.height(space.md))
            }
            ActionDock(
                isSelf = state.isSelf,
                price = PackagePricing.dockPrice(
                    artist.packages,
                    fallback = artist.price,
                    packagesLoaded = state.packagesLoaded,
                ),
                onMessage = onMessage,
                onBook = onBook,
            )
        }
    }
}

/**
 * The pushed-screen bar: a back circle, a centred title, and the "···".
 *
 * The overflow is a sheet rather than a dropdown — the design has no dropdown in
 * it, and the three things behind this control (save, share, report) are the
 * same three a sheet already carries elsewhere in the app.
 */
@Composable
private fun ProfileHeader(isSelf: Boolean, onBack: () -> Unit, onOverflow: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = dimens.component.gutter)
            .padding(top = dimens.space.sm, bottom = dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
        )
        Column(
            Modifier.weight(1f).padding(horizontal = dimens.space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isSelf) "Your profile" else "Artist profile",
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSelf) {
                Text(
                    "How clients see you",
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                )
            }
        }
        IconCircle(
            icon = Icons.Filled.MoreHoriz,
            contentDescription = "More actions",
            onClick = onOverflow,
        )
    }
}

/**
 * Portrait, name, one line of who-and-where, and the rating pill.
 *
 * The portrait falls back to the artist's own cover gradient rather than to a
 * grey disc: every artist row carries a `cover_gradient_index`, so there is
 * always something of theirs to draw and never a hole in the identity.
 */
@Composable
private fun IdentityBlock(
    artist: Artist,
    ratingLabel: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.avatarXl)
                .clip(CircleShape)
                .background(Brush.verticalGradient(artist.gradient)),
        ) {
            if (!artist.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
        ) {
            Text(
                artist.name,
                style = AppTheme.type.displaySub,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = ArtistProfileFacts.subtitle(artist)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = AppTheme.type.subtitle,
                    color = colors.ink4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ratingLabel != null) {
                RatingPill(ratingLabel, Modifier.padding(top = dimens.space.xs))
            }
        }
    }
}

/**
 * "★ 4.92 (128)" on a wash of the accent.
 *
 * Rendered only when reviews were loaded and there is at least one — a failed
 * read arrives as the same empty list, and a pill reading "0.00 (0)" beside an
 * artist's name is a claim the marketplace has not earned.
 */
@Composable
private fun RatingPill(label: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = RATING_PILL_ALPHA))
            .padding(horizontal = dimens.space.md, vertical = dimens.space.xs)
            .semantics(mergeDescendants = true) { contentDescription = "Rated $label" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = colors.accentDeep,
            modifier = Modifier.size(dimens.size.iconSm),
        )
        Text(
            label,
            style = AppTheme.type.badge,
            color = colors.accentDeep,
            maxLines = 1,
        )
    }
}

private const val RATING_PILL_ALPHA = 0.3f

/**
 * Shows · Bookability · Replies in, ruled above and below.
 *
 * Always three cells, always rendered: this is the strip a host scans before
 * anything else, and dropping it for an artist with no gigs would hide the one
 * fact that most wants stating — that they are new. Each cell knows how to say
 * "we don't know" ([ArtistProfileFacts]); none of them invents a figure.
 *
 * The middle cell is the only tappable one. It opens the breakdown, because the
 * number it shows is the one the reader is most likely to want an account of.
 */
@Composable
private fun StatStrip(artist: Artist, onScore: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column {
        HRule()
        // IntrinsicSize.Min so the hairline dividers can measure themselves
        // against the tallest cell instead of collapsing to zero height.
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell(
                modifier = Modifier.weight(1f),
                value = ArtistProfileFacts.showsCell(artist),
                label = "Shows",
            )
            StatDivider()
            StatCell(
                modifier = Modifier.weight(1f),
                value = ArtistProfileFacts.scoreCell(artist),
                label = "Bookability",
                onClick = onScore,
            )
            StatDivider()
            StatCell(
                modifier = Modifier.weight(1f),
                value = ArtistProfileFacts.replyCell(artist),
                label = "Replies in",
            )
        }
        HRule()
        Spacer(Modifier.height(space.xs))
    }
}

@Composable
private fun StatDivider() {
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = dimens.space.md)
            .width(dimens.size.hairline)
            .background(AppTheme.colors.hairline),
    )
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = dimens.space.lg)
            .semantics(mergeDescendants = true) {
                contentDescription = if (onClick != null) {
                    "$label: $value. Tap for the breakdown."
                } else {
                    "$label: $value"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Text(
            value,
            style = AppTheme.type.displaySmall,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = AppTheme.type.caption, color = colors.ink4, maxLines = 1)
    }
}

/**
 * The bio, clamped to four lines with a "More" that expands it.
 *
 * The affordance appears only when the text actually overflows — measured, not
 * guessed from a character count, because the same bio wraps differently at
 * every font scale and a "More" that expands nothing is worse than no control.
 */
@Composable
private fun AboutBlock(bio: String) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        SectionHeader("About")
        Text(
            bio,
            style = AppTheme.type.body,
            color = colors.ink2,
            maxLines = if (expanded) Int.MAX_VALUE else ABOUT_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (result.hasVisualOverflow) overflows = true },
        )
        if (overflows) {
            Text(
                if (expanded) "Less" else "More",
                style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accentInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .clickable { expanded = !expanded }
                    .padding(vertical = space.xs),
            )
        }
    }
}

private const val ABOUT_COLLAPSED_LINES = 4

/**
 * The tiers, and under them the way out of the tiers.
 *
 * The heading and rows are conditional; **the quote row is not**. Every artist
 * has no packages until they finish the wizard, and guarding the whole block on
 * a non-empty list took the negotiation entry down with it — leaving the client
 * with the least to go on the fewest ways to start a conversation.
 *
 * The two pricing modifiers render identically because a client is owed the same
 * clarity about the one that raises the price as about the one that lowers it.
 */
@Composable
private fun PackagesBlock(
    packages: List<ArtistPackage>,
    packagesLoaded: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    weekendPremiumPct: Int,
    newArtistDiscountPct: Int,
    onRequestQuote: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Picking a tier here is browsing, not committing — iOS buzzes on the
    // profile and stays silent in the booking screen's own package list.
    val haptics = rememberHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionHeader("Packages")
        if (packages.isEmpty()) {
            Text(
                // Only once the stitch has landed is an empty set a FACT about
                // the artist. Before then it is "we don't know yet", and the
                // page says nothing rather than announcing an artist with three
                // tiers has no prices.
                if (packagesLoaded) {
                    "This artist quotes on request rather than publishing tiers."
                } else {
                    "Loading packages…"
                },
                style = AppTheme.type.body,
                color = colors.ink3,
            )
        } else {
            // Asked once per set, not per row: existing server rows carry
            // `popular = true` on every package, and a badge every row shares
            // distinguishes nothing.
            val badgesMeanSomething = PackagePricing.popularBadgeIsMeaningful(packages)
            packages.forEachIndexed { index, pkg ->
                PackageRow(
                    pkg = pkg,
                    selected = index == selectedIndex,
                    showPopular = badgesMeanSomething && pkg.popular,
                    onClick = {
                        haptics.tap()
                        onSelect(index)
                    },
                )
            }
        }
        if (newArtistDiscountPct > 0) {
            Text(
                "New-artist offer: $newArtistDiscountPct% off your booking",
                style = AppTheme.type.caption,
                color = colors.ink3,
            )
        }
        if (weekendPremiumPct > 0) {
            Text(
                "Fri–Sun: +$weekendPremiumPct% on the quoted price",
                style = AppTheme.type.caption,
                color = colors.ink3,
            )
        }
        if (onRequestQuote != null) {
            ListRow(
                title = "Custom date or budget?",
                subtitle = "Ask for a quote instead",
                onClick = onRequestQuote,
                showHairline = false,
            )
        }
    }
}

/**
 * One tier: name and what is in it on the left, the price on the right.
 *
 * Selection is a real state — the tap seeds the booking draft — so it is drawn,
 * quietly: an accent hairline and a wash, rather than a filled card. A filled
 * row would out-shout the CTA, which is the screen's one accent.
 */
@Composable
private fun PackageRow(
    pkg: ArtistPackage,
    selected: Boolean,
    showPopular: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    val detail = (listOf(pkg.duration) + pkg.includes)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = SELECTED_ROW_ALPHA) else colors.surface3)
            .border(
                dimens.size.hairline,
                if (selected) colors.accent else colors.hairline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(pkg.name)
                    if (detail.isNotEmpty()) append(". $detail")
                    append(". ${formatInr(pkg.price)}")
                    if (selected) append(". Selected")
                }
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.space.xs)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pkg.name,
                    style = AppTheme.type.rowTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showPopular) {
                    Text(
                        "POPULAR",
                        style = AppTheme.type.monoLabel,
                        color = colors.accentInk,
                    )
                }
            }
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            formatInr(pkg.price),
            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            maxLines = 1,
        )
    }
}

private const val SELECTED_ROW_ALPHA = 0.22f

/**
 * The artist's other photos, as a strip under the packages.
 *
 * No "View all": the full-screen pager is iOS PROF-10 and is not built, and a
 * disclosure that dead-ends is worse than none.
 */
@Composable
private fun GalleryBlock(photos: List<GalleryPhoto>) {
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionHeader("Gallery")
        GalleryStrip(photos)
    }
}

/**
 * What the artist sounds like — or, on screen 101, what to listen to instead.
 *
 * The empty state is the point of that screen: an act with no audio is not an
 * apology, it is a redirect to the signal a host should use (the clips and the
 * reviews, both of which are on this page). One player for the whole block, so
 * starting a second clip replaces the first rather than layering two.
 */
@Composable
private fun ListenBlock(samples: List<Sample>, spotifyEmbed: String?) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    var spotifyExpanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionHeader("Listen")
        if (samples.isEmpty() && spotifyEmbed == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.xl))
                    .background(colors.surface3)
                    .padding(horizontal = dimens.space.lg, vertical = dimens.space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Box(
                    Modifier
                        .size(dimens.component.emptyGlyphCircle)
                        .clip(CircleShape)
                        .background(colors.hairline),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = colors.ink3,
                        modifier = Modifier.size(dimens.component.emptyGlyph),
                    )
                }
                Text(
                    "No tracks listed yet",
                    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "This act hasn't linked audio. Their gallery and reviews are the " +
                        "best signal here.",
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        Column {
            HRule()
            // Behind an `if`, so a Spotify-only artist does not cost an
            // ExoPlayer, an audio-focus request and a lifecycle observer for a
            // list with nothing in it.
            if (samples.isNotEmpty()) {
                SampleRows(samples)
            }
            if (spotifyEmbed != null) {
                SpotifyDisclosure(
                    embedUrl = spotifyEmbed,
                    expanded = spotifyExpanded,
                    onToggle = { spotifyExpanded = !spotifyExpanded },
                )
                HRule()
            }
        }
    }
}

/**
 * The playable rows, and the one player they share. Split out so the player's
 * whole lifetime is the lifetime of a non-empty sample list.
 */
@Composable
private fun SampleRows(samples: List<Sample>) {
    val player = rememberSamplePlayer(samples)
    val playback by player.playback
    samples.forEach { sample ->
        SampleRow(
            sample = sample,
            playback = playback,
            onTap = { player.onTap(sample) },
        )
        HRule()
    }
}

/**
 * The artist in their own words — screen 101's "Most clients ask about".
 *
 * Question above, answer below, on a quiet fill. The question is the smaller
 * half because the answer is what the client came to read.
 */
@Composable
private fun PromptsBlock(prompts: List<ArtistPrompt>) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionHeader("Most clients ask about")
        prompts.forEach { prompt ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.buttonLg))
                    .background(colors.surface3)
                    .padding(dimens.space.md),
                verticalArrangement = Arrangement.spacedBy(space.xs),
            ) {
                Text(
                    prompt.question,
                    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
                Text(prompt.answer, style = AppTheme.type.caption, color = colors.ink2)
            }
        }
    }
}

/**
 * What this artist plays, in the client's own filter vocabulary — the same nine
 * labels the search sheet offers, so a client who filtered for "Full band" sees
 * the words they ticked on the profile they landed on.
 *
 * Hairline outline, never filled: a filled chip is the *selected* state in this
 * app's chip language, and nothing on a read-only profile is selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServicesBlock(tags: List<String>) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionHeader("What they play")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            ServiceTags.labels(tags).forEach { label ->
                Text(
                    label,
                    style = AppTheme.type.chip,
                    color = colors.ink2,
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(dimens.size.hairline, colors.hairline, CircleShape)
                        .padding(
                            horizontal = dimens.component.chipPadH,
                            vertical = dimens.component.chipPadV,
                        ),
                )
            }
        }
    }
}

/**
 * The artist's track record — or, on screen 100, an honest account of why it is
 * not here and a reminder that the rest of the page still is.
 *
 * A failed read and a genuinely unreviewed artist arrive as the same empty list
 * and say opposite things, so [failed] is threaded in rather than inferred: "No
 * reviews yet." for a dropped request is a false claim about the artist, made by
 * the marketplace, on the page a client decides from.
 *
 * The failure is SCOPED. Screen 100's whole argument is that the artist stays
 * bookable while reviews are unreachable, so the block states what else is on
 * the page rather than letting the reader assume the profile is broken.
 */
@Composable
private fun ReviewsBlock(
    reviews: List<Review>,
    failed: Boolean,
    artist: Artist,
    onRetry: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionHeader(
            title = "Reviews",
            actionLabel = if (reviews.size > REVIEW_PREVIEW) "See all" else null,
            onAction = onSeeAll.takeIf { reviews.size > REVIEW_PREVIEW },
        )
        when {
            failed -> {
                Banner(
                    title = "Couldn't load reviews",
                    detail = "This artist may have reviews — we just couldn't load " +
                        "them. Don't read this as \"no reviews\".",
                    tone = BannerTone.Failure,
                    actionLabel = "Retry",
                    onAction = onRetry,
                )
                Spacer(Modifier.height(space.xs))
                EyebrowLabel("The rest of the profile is fine")
                Column {
                    ListRow(
                        title = "Packages",
                        value = packagesSummary(artist),
                        showHairline = true,
                    )
                    ListRow(
                        title = "Listen",
                        value = listenSummary(artist),
                        showHairline = true,
                    )
                    ListRow(
                        title = "Gallery",
                        value = if (artist.gallery.isEmpty()) {
                            "No photos"
                        } else {
                            "${artist.gallery.size} photos"
                        },
                        showHairline = false,
                    )
                }
            }

            reviews.isEmpty() -> Text(
                "No reviews yet. This artist hasn't been reviewed on Artistant.",
                style = AppTheme.type.body,
                color = colors.ink3,
            )

            else -> reviews.take(REVIEW_PREVIEW).forEach { review ->
                ReviewCard(review)
            }
        }
    }
}

private const val REVIEW_PREVIEW = 3

private fun packagesSummary(artist: Artist): String = when {
    artist.packages.isEmpty() -> "On request"
    else -> "${artist.packages.size} tiers · from " +
        formatInr(artist.packages.minOf { it.price })
}

private fun listenSummary(artist: Artist): String = when {
    artist.samples.isNotEmpty() -> "${artist.samples.size} tracks"
    !artist.spotifyArtistUrl.isNullOrBlank() -> "Spotify"
    else -> "No tracks"
}

/** One review: who, how many stars, what they said. Shared with screen 102. */
@Composable
internal fun ReviewCard(review: Review, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.surface3)
            .padding(dimens.space.md),
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                review.name,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${review.rating} out of 5"
                },
            ) {
                repeat(review.rating.coerceIn(0, MAX_STARS)) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = colors.accentInk,
                        modifier = Modifier.size(dimens.size.iconSm),
                    )
                }
            }
        }
        if (review.org.isNotBlank()) {
            Text(review.org, style = AppTheme.type.caption, color = colors.ink4, maxLines = 1)
        }
        if (review.body.isNotBlank()) {
            Text(
                review.body,
                style = AppTheme.type.body,
                color = colors.ink2,
                maxLines = REVIEW_BODY_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val MAX_STARS = 5
private const val REVIEW_BODY_LINES = 6

/**
 * The bottom bar: Message on the left, and the price riding the CTA.
 *
 * **The price is on the button.** That is the redesign's decision, and it is the
 * reason there is no separate price column here any more: a host reads the
 * commitment and its cost in one glance instead of pairing a figure on the left
 * with a verb on the right.
 *
 * On the self view (103) both controls come off — the server's self-booking
 * guard would reject the request anyway, and offering a control that cannot work
 * is worse than not offering it. The note at the top of the page has already
 * said why.
 */
@Composable
private fun ActionDock(
    isSelf: Boolean,
    price: Int?,
    onMessage: () -> Unit,
    onBook: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HRule()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = dimens.component.gutter, vertical = dimens.space.md),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelf) {
                Text(
                    "You're viewing your own profile.",
                    style = AppTheme.type.subtitle,
                    color = colors.ink4,
                )
                return@Row
            }
            IconCircle(
                icon = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Message this artist",
                onClick = onMessage,
                background = colors.surface,
                outlined = true,
                size = dimens.chrome.actionSize,
            )
            PrimaryButton(
                text = if (price != null) {
                    "Check availability · ${formatInr(price)}"
                } else {
                    "Check availability"
                },
                onClick = onBook,
                fullWidth = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Hand the artist's public link to the system share sheet.
 *
 * `runCatching` because a device with no app able to handle `ACTION_SEND` throws
 * on `startActivity`, and a share button is not worth crashing a profile over.
 */
private fun shareArtist(context: Context, artist: Artist) {
    val handle = artist.handle.trim()
    val url = if (handle.isEmpty()) ARTIST_SHARE_ORIGIN else "$ARTIST_SHARE_ORIGIN/$handle"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, artist.name)
        putExtra(Intent.EXTRA_TEXT, "Book ${artist.name} on Artistant\n$url")
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

private const val ARTIST_SHARE_ORIGIN = "https://artistant.in"

/** Icons the action sheet needs, resolved here so the sheet file stays copy. */
internal object ProfileActionIcons {
    val Saved = Icons.Filled.Bookmark
    val Save = Icons.Filled.BookmarkBorder
    val Share = Icons.Outlined.IosShare
    val Report = Icons.Filled.Flag
}
