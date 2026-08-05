package `in`.artistant.app.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.feature.score.ScoreBreakdownSheet

/**
 * Artist profile — the Android read of iOS `ArtistView`: hero (back / message /
 * save), score chip, bio, packages, the request-a-quote line, reviews, and a
 * compact bottom [ActionDock].
 *
 * Action placement mirrors iOS rather than stacking every CTA in the bar: the
 * dock carries the "from" price plus the one primary action, Message rides in the
 * hero cluster, and Request a quote sits with the pricing it negotiates.
 */
@Composable
fun ArtistProfileScreen(
    onBack: () -> Unit,
    onBook: (artistId: String) -> Unit = {},
    onRequestQuote: (artistId: String) -> Unit = {},
    onMessage: (artistId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArtistProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val space = AppTheme.dimens.space
    val colors = AppTheme.colors

    when {
        state.isLoading && state.artist == null -> {
            Box(modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
        }
        state.artist == null -> {
            Column(modifier.fillMaxSize().background(colors.bg)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                }
                EmptyState(
                    title = "Artist not found",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }
        }
        else -> {
            val artist = state.artist!!
            Column(modifier.fillMaxSize().background(colors.bg)) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Hero(
                        artist = artist,
                        onBack = onBack,
                        isSaved = state.isSaved,
                        onToggleSaved = viewModel::toggleSaved,
                        onMessage = { onMessage(artist.id) },
                    )
                    Column(Modifier.padding(space.lg)) {
                        Text(artist.name, style = AppTheme.type.displaySub, color = colors.ink)
                        Spacer(Modifier.height(space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                            Pill(artist.category, tone = PillTone.Neutral)
                            Pill(artist.city, tone = PillTone.Neutral)
                            ScoreChip(
                                score = artist.score,
                                gigs = artist.gigs,
                                onClick = viewModel::openScoreSheet,
                            )
                        }
                        if (artist.bio.isNotBlank()) {
                            Spacer(Modifier.height(space.lg))
                            Text("About", style = AppTheme.type.headline, color = colors.ink)
                            Spacer(Modifier.height(space.sm))
                            Text(artist.bio, style = AppTheme.type.body, color = colors.ink2)
                        }
                        if (artist.packages.isNotEmpty()) {
                            Spacer(Modifier.height(space.xl))
                            Text("Packages", style = AppTheme.type.headline, color = colors.ink)
                            Spacer(Modifier.height(space.md))
                            // Asked once per set, not per row: existing server rows
                            // carry `popular = true` on every package, and a badge
                            // every row shares distinguishes nothing.
                            val badgesMeanSomething =
                                PackagePricing.popularBadgeIsMeaningful(artist.packages)
                            artist.packages.forEachIndexed { index, pkg ->
                                PackageRow(
                                    pkg = pkg,
                                    selected = index == state.selectedPackageIndex,
                                    showPopularBadge = badgesMeanSomething && pkg.popular,
                                    onClick = { viewModel.selectPackage(index) },
                                )
                                Spacer(Modifier.height(space.sm))
                            }
                        }
                        // The negotiation entry sits HERE, with the pricing
                        // context, rather than in the dock — collapsing the bar
                        // to one row is the point of this layout (iOS
                        // ArtistView.swift puts Request-a-quote in the body for
                        // exactly this reason).
                        Spacer(Modifier.height(space.md))
                        RequestQuoteRow(onClick = { onRequestQuote(artist.id) })
                        Spacer(Modifier.height(space.xl))
                        ReviewsBlock(reviews = state.reviews)
                        Spacer(Modifier.height(space.xxl))
                    }
                }
                // "from" means MINIMUM, so it is computed over the packages we
                // actually loaded — not the selected row (which made the page
                // advertise the dearest tier) and not `artist.price`, the
                // server's denormalized `artists.min_price`, which is confirmed
                // stale on dev (a row reading ₹51,000 while a ₹22,000 package
                // exists). `artist.price` survives only as the empty-set
                // fallback. iOS does the same thing via `cheapestPackage`.
                val fromPrice = PackagePricing.fromPrice(artist.packages, fallback = artist.price)
                ActionDock(fromPrice = fromPrice, onBook = { onBook(artist.id) })
            }
            if (state.showScoreSheet) {
                ScoreBreakdownSheet(
                    artist = artist,
                    breakdown = state.scoreBreakdown,
                    reviews = state.reviews,
                    reviewsFailed = state.reviewsFailed,
                    onDismiss = viewModel::dismissScoreSheet,
                )
            }
        }
    }
}

@Composable
private fun Hero(
    artist: Artist,
    onBack: () -> Unit,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    onMessage: () -> Unit,
) {
    val size = AppTheme.dimens.size
    val space = AppTheme.dimens.space
    Box(
        Modifier
            .fillMaxWidth()
            .height(size.heroMed),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(artist.gradient)),
        )
        if (!artist.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = artist.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(space.sm),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        // Message + Save share the hero's trailing cluster. Message lives here
        // (not in the dock) so the bottom bar stays one row and the primary CTA
        // never competes for width with a secondary — same call iOS made when it
        // collapsed its dock to price + one button.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMessage) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "Message ${artist.name}",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onToggleSaved) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isSaved) "Unsave" else "Save",
                    tint = if (isSaved) AppTheme.colors.brand else Color.White,
                )
            }
        }
    }
}

/**
 * Compact bottom action bar — the iOS `ArtistView.bookingDock` anatomy.
 *
 * One row: the "from" figure hard-left (informational, deliberately outside the
 * tap target), the filled primary CTA hard-right at its intrinsic width, and the
 * gap between them left as air. It replaces a ~4x taller slab that stacked the
 * price over three full-width buttons and ate roughly a fifth of the screen; the
 * two secondaries moved to where their context is (Request a quote sits with the
 * packages, Message sits in the hero cluster).
 *
 * Nothing is hidden behind it: the dock is a sibling of the scrolling column in
 * the parent `Column`, and that column takes `weight(1f)`, so the scroll
 * viewport is measured as (screen − dock) and the content is inset rather than
 * occluded — Compose's equivalent of the `safeAreaInset(edge: .bottom)` iOS uses
 * for the same bar. The page also carries `space.xxl` of trailing air so the last
 * row never sits flush against the lid.
 */
@Composable
private fun ActionDock(fromPrice: Int, onBook: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxWidth().background(colors.bg)) {
        // Hairline lid — honest about where the bar begins, instead of a slab of
        // elevated fill doing the same job with 10x the ink.
        HRule()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.lg, vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    // Read as one phrase; the two stacked lines are typography,
                    // not two separate facts for a screen reader to announce.
                    .semantics(mergeDescendants = true) {
                        contentDescription = "From ${formatInr(fromPrice)}"
                    },
            ) {
                Text(formatInr(fromPrice), style = AppTheme.type.monoMedium, color = colors.ink)
                Text("FROM", style = AppTheme.type.caption, color = colors.ink3)
            }
            // Intrinsic width (no fullWidth) so the weighted price column above
            // supplies the gap — a stretched CTA would eat it.
            PrimaryButton(text = "Check availability", onClick = onBook)
        }
    }
}

/**
 * The negotiation entry, in the body with the pricing context. Muted question +
 * lime answer: lime is the signal (this is the actionable half), the grey lead-in
 * is the framing — the same two-tone footnote iOS uses under its ticket row.
 */
@Composable
private fun RequestQuoteRow(onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = space.sm),
        horizontalArrangement = Arrangement.spacedBy(space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Custom date or budget?", style = AppTheme.type.footnote, color = colors.ink3)
        Text("Request a quote", style = AppTheme.type.footnote, color = colors.brand)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(AppTheme.dimens.size.iconMd),
        )
    }
}

@Composable
private fun ScoreChip(score: Int, gigs: Int, onClick: () -> Unit = {}) {
    val tier = ScoreBands.tier(score, gigs)
    val label = if (tier == ScoreTier.New) "New" else "Score $score"
    Pill(text = label, tone = PillTone.Brand, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun PackageRow(
    pkg: ArtistPackage,
    selected: Boolean,
    showPopularBadge: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Hairline border — selected uses brand ink as the single accent signal.
    Column(
        Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) colors.brand else colors.line,
                shape = RoundedCornerShape(AppTheme.dimens.radii.md),
            )
            .clickable(onClick = onClick)
            .padding(space.lg),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pkg.name, style = AppTheme.type.callout, color = colors.ink)
            Text(formatInr(pkg.price), style = AppTheme.type.monoSmall, color = colors.ink)
        }
        Text(pkg.duration, style = AppTheme.type.caption, color = colors.ink3)
        if (showPopularBadge) {
            Spacer(Modifier.height(space.xs))
            Pill("Popular", tone = PillTone.Brand)
        }
    }
}

@Composable
private fun ReviewsBlock(reviews: List<Review>) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Text("Reviews", style = AppTheme.type.headline, color = colors.ink)
    Spacer(Modifier.height(space.md))
    if (reviews.isEmpty()) {
        Text("No reviews yet.", style = AppTheme.type.body, color = colors.ink3)
        return
    }
    reviews.take(5).forEach { review ->
        Column(Modifier.padding(bottom = space.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Text(review.name, style = AppTheme.type.callout, color = colors.ink)
                Text("★".repeat(review.rating.coerceIn(0, 5)), style = AppTheme.type.caption, color = colors.warm)
            }
            if (review.org.isNotBlank()) {
                Text(review.org, style = AppTheme.type.caption, color = colors.ink3)
            }
            Text(
                review.body,
                style = AppTheme.type.body,
                color = colors.ink2,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
