package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * An empty slot that is asking to be filled — the dashed box on screens 23 / 87
 * / 76 / 135.
 *
 * The pair with [MediaSlot] is the whole idea, and the difference between them is
 * load-bearing rather than decorative. A [MediaSlot] is a solid `placeholder`
 * rectangle: it means "there is media here and you are waiting for it". This one
 * is an outline with nothing inside: it means "there is nothing here yet, and you
 * can put something here". Drawing both as the same grey box is how a gallery
 * with two photos and one free slot reads as a gallery with three photos still
 * loading.
 *
 * The dash is drawn rather than bordered because Compose's `border` takes a solid
 * brush; the on/off pair comes off the spacing scale (`space.sm` / `space.xs`)
 * so it stays a token and scales with the rest of the system.
 */
@Composable
fun DashedSlot(
    modifier: Modifier = Modifier,
    height: Dp? = null,
    radius: Dp = AppTheme.dimens.radii.card,
    label: String? = null,
    icon: ImageVector = Icons.Filled.Add,
    contentDescription: String? = label,
    onClick: (() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(radius)
    val stroke = dimens.size.strokeEmphasis
    val on = dimens.space.sm
    val off = dimens.space.xs
    val line = colors.lineStrong

    Box(
        modifier = modifier
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(shape)
            .drawBehind {
                val w = stroke.toPx()
                drawRoundRect(
                    color = line,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
                    style = Stroke(
                        width = w,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(on.toPx(), off.toPx()),
                        ),
                    ),
                )
            }
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
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            content != null -> content()
            label != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
                modifier = Modifier.padding(dimens.space.md),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.ink4,
                    modifier = Modifier.size(dimens.size.iconLg),
                )
                Text(label, style = AppTheme.type.rowTitle, color = colors.ink2)
            }
            else -> Icon(
                icon,
                contentDescription = null,
                tint = colors.ink4,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun DashedSlotPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            DashedSlot(
                Modifier.padding(top = AppTheme.dimens.space.sm),
                height = AppTheme.dimens.size.heroShort / 2,
                label = "Add a cover photo or video",
                onClick = {},
            )
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
            ) {
                MediaSlot(
                    Modifier
                        .weight(1f)
                        .height(AppTheme.dimens.component.row),
                    radius = AppTheme.dimens.radii.md,
                ) { Box(Modifier.size(AppTheme.dimens.space.xs)) { } }
                DashedSlot(
                    Modifier
                        .weight(1f)
                        .height(AppTheme.dimens.component.row),
                    radius = AppTheme.dimens.radii.md,
                    contentDescription = "Add a photo",
                    onClick = {},
                )
            }
        }
    }
}
