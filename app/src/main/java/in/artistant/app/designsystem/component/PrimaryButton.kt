package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/** Filled (brand), ghost (hairline), or subtle (bgCard) — one signal per screen. */
enum class ButtonVariant { Filled, Ghost, Subtle }

/**
 * The PrimaryButton port. Press-scale to 0.98 (iOS press feedback), min-height
 * from the design tokens, brand fill by default. Ghost = a hairline border, no
 * card chrome. Disabled via [enabled].
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Filled,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val radii = AppTheme.dimens.radii
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "pressScale")

    val (bg, fg) = when (variant) {
        ButtonVariant.Filled -> colors.brand to colors.brandInk
        ButtonVariant.Ghost -> Color.Transparent to colors.ink
        ButtonVariant.Subtle -> colors.bgCard to colors.ink
    }
    val shape = RoundedCornerShape(radii.md)

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(scale)
            .clip(shape)
            .background(if (enabled) bg else colors.bgSoft)
            .then(
                if (variant == ButtonVariant.Ghost)
                    Modifier.border(1.dp, colors.line, shape)
                else Modifier,
            )
            .defaultMinSize(minHeight = AppTheme.dimens.size.controlMin)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(color = fg),
                onClick = onClick,
            )
            .padding(horizontal = space.xl, vertical = space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AppTheme.type.headline, color = if (enabled) fg else colors.ink3)
    }
}
