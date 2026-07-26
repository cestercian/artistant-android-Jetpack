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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier

/**
 * Artist profile — M2 slice of iOS `ArtistView`: hero, score chip, bio,
 * packages, reviews, dock CTAs (Book/Message stubbed until M3/M4).
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
                    Hero(artist = artist, onBack = onBack, isSaved = state.isSaved, onToggleSaved = viewModel::toggleSaved)
                    Column(Modifier.padding(space.lg)) {
                        Text(artist.name, style = AppTheme.type.displaySub, color = colors.ink)
                        Spacer(Modifier.height(space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                            Pill(artist.category, tone = PillTone.Neutral)
                            Pill(artist.city, tone = PillTone.Neutral)
                            ScoreChip(score = artist.score, gigs = artist.gigs)
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
                            artist.packages.forEachIndexed { index, pkg ->
                                PackageRow(
                                    pkg = pkg,
                                    selected = index == state.selectedPackageIndex,
                                    onClick = { viewModel.selectPackage(index) },
                                )
                                Spacer(Modifier.height(space.sm))
                            }
                        }
                        Spacer(Modifier.height(space.xl))
                        ReviewsBlock(reviews = state.reviews)
                        Spacer(Modifier.height(space.xxl))
                    }
                }
                val selected = artist.packages.getOrNull(state.selectedPackageIndex)
                val price = selected?.price ?: artist.price
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.bgElev)
                        .padding(space.lg),
                ) {
                    Text(
                        text = "from ${formatInr(price)}",
                        style = AppTheme.type.monoMedium,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(space.md))
                    PrimaryButton(
                        text = "Check availability",
                        onClick = { onBook(artist.id) },
                    )
                    Spacer(Modifier.height(space.sm))
                    PrimaryButton(
                        text = "Request a quote",
                        onClick = { onRequestQuote(artist.id) },
                        variant = ButtonVariant.Ghost,
                    )
                    Spacer(Modifier.height(space.sm))
                    PrimaryButton(
                        text = "Message",
                        onClick = { onMessage(artist.id) },
                        variant = ButtonVariant.Ghost,
                    )
                }
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
        IconButton(
            onClick = onToggleSaved,
            modifier = Modifier.align(Alignment.TopEnd).padding(space.sm),
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isSaved) "Unsave" else "Save",
                tint = if (isSaved) AppTheme.colors.brand else Color.White,
            )
        }
    }
}

@Composable
private fun ScoreChip(score: Int, gigs: Int) {
    val tier = ScoreBands.tier(score, gigs)
    val label = if (tier == ScoreTier.New) "New" else "Score $score"
    Pill(text = label, tone = PillTone.Brand)
}

@Composable
private fun PackageRow(pkg: ArtistPackage, selected: Boolean, onClick: () -> Unit) {
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
        if (pkg.popular) {
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
