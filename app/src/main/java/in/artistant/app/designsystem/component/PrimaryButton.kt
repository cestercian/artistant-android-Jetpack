package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Filled (accent), Ghost (hairline outline) or Subtle (`surface2`).
 *
 * One accent per screen, so at most one Filled button is on screen at a time —
 * that is the rule the whole palette hangs off. A second action next to it is a
 * [SecondaryButton], not a second lime.
 */
enum class ButtonVariant { Filled, Ghost, Subtle }

/**
 * The app's primary action: 54dp tall, radius 16, accent fill, `onAccent` label
 * at 16.5/700 (REDESIGN_2026-09 §2, "Geometry").
 *
 * **Disabled is a real state here, not an opacity.** The design draws it as a
 * `hairline` fill with `ink3` copy — a filled button that has visibly gone
 * quiet — and pairs it with an inline reason (screen 118: "Tick this to
 * continue"). Fading the enabled button to 40% instead would produce pale lime
 * on off-white, which is both illegible and reads as a rendering glitch rather
 * than as a deliberate state. The reason line is the caller's job; this
 * component's job is to stop looking pressable.
 *
 * Press feedback comes from the shared [pressScale] modifier so it matches every
 * other pressable in the app and honours reduce-motion.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Filled,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }

    val (fill, label) = when {
        !enabled -> colors.hairline to colors.ink3
        variant == ButtonVariant.Filled -> colors.accent to colors.onAccent
        variant == ButtonVariant.Ghost -> Color.Transparent to colors.ink
        else -> colors.surface2 to colors.ink
    }
    val shape = RoundedCornerShape(dimens.radii.buttonLg)

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .pressScale(interaction)
            .clip(shape)
            .background(fill)
            .then(
                if (variant == ButtonVariant.Ghost && enabled) {
                    Modifier.border(dimens.size.hairline, colors.hairline, shape)
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = dimens.component.cta)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(color = label),
                onClick = onClick,
            )
            .padding(horizontal = dimens.space.xl, vertical = dimens.space.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = AppTheme.type.cta,
                color = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The action that is NOT the one signal: same footprint, `surface2` fill, ink
 * label. Screen 57 stacks one under a Filled ("Notify me when one joins" /
 * "Clear filters"), which is the shape this exists for.
 *
 * A touch shorter than [PrimaryButton] — 50 against 54 — because the design
 * draws it that way, and the 4dp is what makes the pair read as a primary with
 * an alternative rather than as two equal choices.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val label = if (enabled) colors.ink else colors.ink4
    val shape = RoundedCornerShape(dimens.radii.buttonLg)

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .pressScale(interaction)
            .clip(shape)
            .background(colors.surface2)
            .defaultMinSize(minHeight = dimens.component.control)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(color = label),
                onClick = onClick,
            )
            .padding(horizontal = dimens.space.xl, vertical = dimens.space.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = AppTheme.type.rowTitle,
                color = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun ButtonsPreview() {
    ArtistantTheme {
        Box(Modifier.padding(AppTheme.dimens.component.gutter)) {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
            ) {
                PrimaryButton("Notify me when one joins", {}, fullWidth = true)
                SecondaryButton("Clear filters", {}, fullWidth = true)
                PrimaryButton("Get started", {}, fullWidth = true, enabled = false)
                PrimaryButton("Continue with Apple", {}, variant = ButtonVariant.Ghost, fullWidth = true)
            }
        }
    }
}
