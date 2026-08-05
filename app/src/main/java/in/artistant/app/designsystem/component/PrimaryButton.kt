package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import `in`.artistant.app.designsystem.theme.AppTheme

/** Filled (brand), ghost (hairline), or subtle (bgCard) — one signal per screen. */
enum class ButtonVariant { Filled, Ghost, Subtle }

/**
 * The PrimaryButton port. Press feedback via the shared [pressScale] modifier
 * (so it matches every other pressable in the app, and honours reduce-motion),
 * min-height from the design tokens, brand fill by default. Ghost = a hairline
 * border, no card chrome. Disabled via [enabled].
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

    val (bg, fg) = when (variant) {
        ButtonVariant.Filled -> colors.brand to colors.brandInk
        ButtonVariant.Ghost -> Color.Transparent to colors.ink
        ButtonVariant.Subtle -> colors.bgCard to colors.ink
    }
    val shape = RoundedCornerShape(radii.md)

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .pressScale(interaction)
            .clip(shape)
            .background(if (enabled) bg else colors.bgSoft)
            .then(
                if (variant == ButtonVariant.Ghost)
                    Modifier.border(AppTheme.dimens.size.hairline, colors.line, shape)
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
