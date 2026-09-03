package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The featured act: a 262dp, 24-radius media card with a badge top-left, a save
 * circle top-right, and a title / meta / price stack over a bottom scrim
 * (screen 02).
 *
 * **The scrim is the only dark thing on a light page, and it has to be.** The
 * card's content is a photograph nobody controls, so the text over it cannot
 * take a colour from the light palette — `ink` on an unknown cover is a coin
 * flip. Ramping to `mediaScrim` (95% of `#0b0b0c`) at the bottom edge makes the
 * backdrop predictable, so white can be chosen once and hold over any image.
 * This is the same reasoning the over-media chrome tokens carry, and it is why
 * §4 leaves that whole block unchanged by the redesign.
 *
 * The badge is the screen's one accent; the save circle is deliberately NOT
 * accent — it is a translucent black disc with a white glyph, because a lime
 * circle beside a lime badge would give the card two signals and neither would
 * mean anything.
 */
@Composable
fun HeroCard(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    badge: String? = null,
    price: String? = null,
    priceSuffix: String? = null,
    rating: String? = null,
    height: Dp = AppTheme.dimens.component.heroCard,
    saved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleTrailing: @Composable (() -> Unit)? = null,
    media: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(RoundedCornerShape(dimens.radii.xl))
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
            )
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(badge, title, meta, price).joinToString(". ")
            },
    ) {
        media?.invoke(this)

        // The scrim. `align(BottomCenter)` + a fractional height rather than
        // `matchParentSize`, so it darkens only the band the text occupies —
        // a full-card gradient washes out the subject of the photo, which is
        // the one thing the card exists to show.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height * SCRIM_FRACTION)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colors.mediaScrim),
                    ),
                ),
        )

        badge?.let {
            Text(
                text = it,
                style = AppTheme.type.badge,
                color = colors.onAccent,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimens.space.md)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.xs),
            )
        }

        onToggleSave?.let { toggle ->
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimens.space.md)
                    .size(dimens.component.heroSave)
                    .clip(CircleShape)
                    .background(colors.glassScrim)
                    .clickable(role = Role.Button, onClick = toggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (saved) "Saved" else "Save",
                    tint = colors.onDark,
                    modifier = Modifier.size(dimens.size.iconMd),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(dimens.space.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
            ) {
                Text(
                    text = title,
                    style = AppTheme.type.cardTitle,
                    color = colors.onDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                titleTrailing?.invoke()
            }
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = AppTheme.type.subtitle,
                    color = colors.onDarkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs),
                )
            }
            if (rating != null || price != null) {
                Row(
                    modifier = Modifier.padding(top = dimens.space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
                ) {
                    rating?.let {
                        Text(
                            text = it,
                            style = AppTheme.type.subtitle,
                            color = colors.onDark,
                            maxLines = 1,
                        )
                    }
                    if (rating != null && price != null) {
                        Box(
                            Modifier
                                .width(AppTheme.dimens.size.hairline)
                                .height(AppTheme.dimens.size.iconSm)
                                .background(colors.lineStrong),
                        )
                    }
                    price?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = it,
                                style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.Bold),
                                color = colors.onDark,
                                maxLines = 1,
                            )
                            priceSuffix?.let { suffix ->
                                Text(
                                    text = " $suffix",
                                    style = AppTheme.type.subtitle,
                                    color = colors.onDarkSoft,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The share of the card's height the bottom scrim covers.
 *
 * Not `matchParentSize`: a full-card gradient washes out the subject of the
 * photograph, which is the one thing the card exists to show. 45% is where the
 * ramp reaches transparent above the title block on a 262dp card.
 */
private const val SCRIM_FRACTION = 0.45f

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun HeroCardPreview() {
    ArtistantTheme {
        Box(Modifier.padding(AppTheme.dimens.component.gutter)) {
            HeroCard(
                title = "The Tilt Collective",
                meta = "Indie folk band · 5 pc · Bengaluru",
                badge = "Top rated",
                rating = "4.92 (128)",
                price = "₹42,000",
                priceSuffix = "/ 90 min set",
                onToggleSave = {},
                onClick = {},
            )
        }
    }
}
