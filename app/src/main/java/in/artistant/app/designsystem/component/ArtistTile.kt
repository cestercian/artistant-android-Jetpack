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
import `in`.artistant.app.common.util.formatInrShort
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
    val colors = AppTheme.colors
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
            // Same unconstrained-SpaceBetween shape the price row below had, and it
            // failed the same way at rail width: Row measures in order, so a long
            // category ("Indie Band") took what it wanted and the score capsule was
            // measured against the remainder — a few dp short of its own content,
            // which rendered the badge as a sliced "8|2".
            //
            // The capsule is the load-bearing half here (it IS the Bookability
            // score, the hero metric), so it keeps its intrinsic width and the
            // category yields, mirroring the iOS HStack where the pill truncates
            // and the capsule stays whole. Only the rail tile is tight enough to
            // spend the ellipsis; the wider search-grid tile still shows the
            // category in full.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(
                    text = artist.category,
                    tone = PillTone.Neutral,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(space.sm))
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
                    color = colors.inkOnMedia,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val gap = Modifier.height(space.sm)
            Spacer(gap)
            // Both children used to be unconstrained under SpaceBetween, which is
            // fine until the price is long: on a narrow rail tile a lakh-grouped
            // figure (₹2,10,000) and a duration have no room to sit apart, so they
            // ran into each other. The price is the load-bearing half — it takes
            // the flexible width and truncates; the duration keeps its intrinsic
            // size so it never gets squeezed to an unreadable stub.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Compact form (₹2.1L), not the full grouping. A rail tile is ~150dp
                // wide and a lakh-scale price plus a duration simply do not both
                // fit: the full form either collided with the duration (before) or
                // truncated to "₹2,10…" (after constraining it), and a price cut
                // mid-number is no more use than one that overlaps. The hero strip
                // already quotes the short form, so this also makes the two agree.
                Text(
                    text = formatInrShort(artist.price),
                    style = AppTheme.type.monoSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (artist.duration.isNotBlank()) {
                    Spacer(Modifier.width(space.sm))
                    Text(
                        text = artist.duration,
                        style = AppTheme.type.caption,
                        color = colors.inkOnMediaSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
 * The fill is a dark scrim rather than an opaque swatch: it sits on artwork, and
 * a solid chip reads as a sticker stuck onto the photo where a scrim reads as
 * part of it. It used to be the PAGE background at 70%, which was the same
 * near-black by coincidence — and became a 70% off-white disc the moment the
 * page turned light, taking its own tier dot's contrast with it.
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
            .background(colors.glassScrim)
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
        // softWrap = false / maxLines = 1 keep the number on one line, but they are
        // only a guard: they cannot MAKE room, and on their own they turned the old
        // two-line "8/2" break into a mid-glyph clip instead. What actually keeps
        // the digits whole is the caller giving this capsule its intrinsic width.
        Text(
            text = label,
            style = AppTheme.type.monoSmall,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
        )
    }
}
