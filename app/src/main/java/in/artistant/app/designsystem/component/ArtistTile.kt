package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.domain.score.tierColor

/**
 * Photo-backed artist card — port of iOS `ArtistTile`.
 *
 * Cover lives in a background Box (Coil / gradient), never as a layout-driving
 * child that can expand the tile beyond [width]×[height] (iOS HANDOFF §9).
 */
@Composable
fun ArtistTile(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 192.dp,
    height: Dp = 252.dp,
) {
    val radii = AppTheme.dimens.radii
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            // Scale BEFORE the clip so the rounded corners scale with the tile
            // rather than the tile shrinking inside a fixed-size mask.
            .pressScale(interaction)
            .clip(RoundedCornerShape(radii.md))
            .clickable(
                interactionSource = interaction,
                // The photo already dims under the press scale; a ripple on top
                // of a full-bleed image reads as a smudge.
                indication = null,
                onClick = onClick,
            ),
    ) {
        ArtistCoverBackground(artist = artist)
        // Was a verbatim inline copy of BottomDarkenScrim's four stops. Same
        // pixels, one definition — and `matchParentSize` (which the shared one
        // uses) is the stricter fit here: unlike `fillMaxSize` it takes no part in
        // measuring the Box, which is the invariant this tile's own doc comment
        // is about.
        BottomDarkenScrim()
        Column(
            Modifier
                .fillMaxSize()
                .padding(space.md),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(text = artist.category, tone = PillTone.Neutral)
                ScoreCapsule(score = artist.score, gigs = artist.gigs)
            }
            // Flex spacer so the name strip sits at the bottom.
            val flex = Modifier.weight(1f)
            Spacer(flex)
            Text(
                text = artist.name,
                style = AppTheme.type.headline,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOf(artist.genre, artist.city)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = AppTheme.type.footnote,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val gap = Modifier.height(space.sm)
            Spacer(gap)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatInr(artist.price),
                    style = AppTheme.type.monoSmall,
                    color = Color.White,
                )
                if (artist.duration.isNotBlank()) {
                    Text(
                        text = artist.duration,
                        style = AppTheme.type.caption,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistCoverBackground(artist: Artist) {
    val gradient = Brush.verticalGradient(artist.gradient)
    Box(
        Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        val url = artist.coverUrl
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The score badge that rides on the tile's cover photo.
 *
 * A dark translucent capsule with a TIER-COLOURED DOT, not a solid brand fill.
 * Two reasons, and both are why the reference draws it this way:
 *
 *  - Accent is one signal per screen. A grid of twelve tiles each carrying a
 *    solid lime chip spends the accent twelve times over and it stops meaning
 *    anything — least of all "this artist scores well", since a 61 and a 98 got
 *    the same lime.
 *  - The dot is where the tier actually lives. Filling the whole capsule with
 *    the accent throws away the one channel that separates Elite from Rising,
 *    so the badge could only ever say "here is a number".
 *
 * The fill is the page background at 70% rather than an opaque swatch: it sits
 * on artwork, and a solid chip reads as a sticker stuck onto the photo where a
 * scrim reads as part of it.
 */
@Composable
private fun ScoreCapsule(score: Int, gigs: Int) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val tier = ScoreBands.tier(score, gigs)
    val label = if (tier == ScoreTier.New) "New" else score.toString()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.bg.copy(alpha = 0.7f))
            .border(dimens.size.hairline, colors.glassLine, CircleShape)
            .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.dot)
                .clip(CircleShape)
                .background(tierColor(tier, colors)),
        )
        Text(text = label, style = AppTheme.type.monoSmall, color = Color.White)
    }
}
