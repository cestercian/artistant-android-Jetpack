package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A 42dp `surface2` disc with a glyph in it — the light design's one header
 * action shape (notifications, share, close, back).
 *
 * The optional [dot] is the accent pip screen 02 puts on the bell: it says
 * "something is waiting" without a count, and it is the only accent on a header
 * that is otherwise all ink. Drawn INSIDE the circle's bounds rather than
 * overhanging it, so a row of these keeps a straight baseline.
 *
 * 42dp is under the 48dp tap-target floor, so a clickable one grows its touch
 * area with padding rather than its disc — the visible circle stays 42 and the
 * node measures 48. Callers that want a bigger visible disc pass [size].
 */
@Composable
fun IconCircle(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = AppTheme.dimens.component.iconCircle,
    dot: Boolean = false,
    background: androidx.compose.ui.graphics.Color = AppTheme.colors.surface2,
    tint: androidx.compose.ui.graphics.Color = AppTheme.colors.ink,
    outlined: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    // Grow the NODE to the tap-target floor without growing the
                    // disc: the padding is outside `clip`, so it is hit area only.
                    Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = ripple(bounded = false),
                            role = Role.Button,
                            onClick = onClick,
                        )
                        .padding(
                            ((dimens.size.controlMin - size) / 2).coerceAtLeast(dimens.space.xs),
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (outlined) {
                        Modifier.border(dimens.size.hairline, colors.hairline, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(dimens.size.iconLg),
            )
            if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        // Pulled in off the corner: on a circle the true corner is
                        // outside the fill, so an un-inset dot floats beside the
                        // disc instead of sitting on it.
                        .offset(x = -dimens.space.xs, y = dimens.space.sm)
                        .size(dimens.component.iconCircleDot)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun IconCirclePreview() {
    ArtistantTheme {
        Row(
            Modifier.padding(AppTheme.dimens.component.gutter),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            IconCircle(Icons.Filled.Notifications, "Notifications", onClick = {}, dot = true)
            IconCircle(Icons.Filled.Share, "Share", onClick = {})
            IconCircle(Icons.Filled.Share, "Share", onClick = {}, outlined = true, background = AppTheme.colors.surface)
        }
    }
}
