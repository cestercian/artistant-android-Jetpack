package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A rail tile: an 18-radius image band with a name and a meta line under it
 * (screen 02, "Available Sat night").
 *
 * The text sits BELOW the image, not on it. That is the difference between this
 * and [HeroCard], and it is a decision about how many of them are on screen: a
 * rail shows several tiles at once, and text over media forces a scrim on each
 * one, which turns a bright rail into a row of grey rectangles.
 *
 * [media] is a slot rather than a URL so the tile does not depend on the image
 * loader. It is drawn into a `Box` already clipped and filled with `placeholder`,
 * so a caller that has nothing to draw yet gets the empty slot for free.
 */
@Composable
fun Tile(
    name: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    imageHeight: Dp = AppTheme.dimens.component.tileImage,
    onClick: (() -> Unit)? = null,
    overlay: @Composable (BoxScope.() -> Unit)? = null,
    media: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(dimens.radii.lg))
                        .clickable(
                            interactionSource = interaction,
                            indication = ripple(),
                            role = Role.Button,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(name, meta).joinToString(". ")
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .clip(RoundedCornerShape(dimens.radii.lg))
                .background(colors.placeholder),
        ) {
            media?.invoke(this)
            overlay?.invoke(this)
        }
        Text(
            text = name,
            style = AppTheme.type.rowTitle,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = dimens.space.sm),
        )
        if (!meta.isNullOrBlank()) {
            Text(
                text = meta,
                style = AppTheme.type.caption,
                color = colors.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
        }
    }
}

/**
 * A square media slot with the tile's fill and radius, for a gallery strip
 * (screen 04, "Live clips") where there is no name under the picture.
 */
@Composable
fun MediaSlot(
    modifier: Modifier = Modifier,
    radius: Dp = AppTheme.dimens.radii.md,
    onClick: (() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(colors.placeholder)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content?.invoke(this)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun TilePreview() {
    ArtistantTheme {
        Row(
            Modifier.padding(AppTheme.dimens.component.gutter),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            Tile("Kabir Sen", meta = "Techno DJ · ₹28,000", onClick = {}, modifier = Modifier.weight(1f))
            Tile("Ananya Rao", meta = "Stand-up · ₹35,000", onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun MediaSlotPreview() {
    ArtistantTheme {
        Row(
            Modifier.padding(AppTheme.dimens.component.gutter),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        ) {
            repeat(3) {
                MediaSlot(
                    Modifier
                        .weight(1f)
                        .height(AppTheme.dimens.component.row),
                ) { Box(Modifier.fillMaxSize()) }
            }
        }
    }
}
