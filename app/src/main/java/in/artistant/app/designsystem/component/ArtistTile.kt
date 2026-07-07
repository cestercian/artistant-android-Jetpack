package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Photo-backed artist card (iOS `ArtistTile`) — the tappable unit across the
 * Discover rails and the Search grid. The cover photo lives BEHIND everything as
 * a Coil [AsyncImage] over the brand-gradient floor (never as a size-driving
 * child), so the tile can't overflow its cell (HANDOFF §9). Overlays: a top-left
 * category pill, a top-right score badge (tier dot + mono number), and the
 * bottom name / genre·city / price strip over a 4-stop darken scrim.
 *
 * Pass [fullWidth] = true for the 2-col Search grid (the cell decides the width);
 * otherwise a fixed [width] × [height] frame for the horizontally-scrolled rails.
 */
@Composable
fun ArtistTile(
    artist: Artist,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    height: Dp = 200.dp,
    fullWidth: Boolean = false,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val dot = tierColor(artist.score, artist.gigs)

    val sizedModifier = if (fullWidth) {
        // Grid cell owns the width; keep the card's aspect stable so rows align.
        modifier.fillMaxWidth().aspectRatio(width.value / height.value)
    } else {
        modifier.width(width).height(height)
    }

    Box(
        sizedModifier
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(Brush.linearGradient(artist.gradient)),
    ) {
        // Cover photo — transparent while loading / on error, so the gradient
        // shows through. Skipped entirely when the artist has no uploaded cover.
        if (!artist.coverUrl.isNullOrEmpty()) {
            AsyncImage(
                model = artist.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Bottom-darken scrim for headline legibility.
        BottomDarkenScrim()

        // Top row — category pill (left) + score badge (right).
        Row(
            Modifier.fillMaxWidth().padding(space.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Pill(text = artist.category)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.bg.copy(alpha = 0.7f))
                    .padding(horizontal = space.sm, vertical = space.xs),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                Text(
                    "${artist.score}",
                    style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }

        // Bottom identity strip.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = space.lg)
                .padding(bottom = space.md),
        ) {
            Text(
                artist.name,
                style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
            )
            Text(
                "${artist.genre} · ${artist.city}",
                style = AppTheme.type.footnote,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
            )
            Spacer(Modifier.height(space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatInr(artist.price),
                    style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.width(space.xs))
                if (artist.duration.isNotBlank()) {
                    Text(
                        artist.duration,
                        style = AppTheme.type.caption,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Tile-shaped shimmer used by the Discover / Search loading states. */
@Composable
fun ArtistTileSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    height: Dp = 200.dp,
    fullWidth: Boolean = false,
) {
    val sized = if (fullWidth) {
        modifier.fillMaxWidth().aspectRatio(width.value / height.value)
    } else {
        modifier.width(width).height(height)
    }
    Skeleton(modifier = sized, cornerRadius = AppTheme.dimens.radii.md)
}
