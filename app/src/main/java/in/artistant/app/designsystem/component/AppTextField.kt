package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The app's text input: a `surface2` well at radius 15, an optional label above
 * it and an optional error under it (screen 12).
 *
 * `BasicTextField` rather than an M3 `TextField`. The Material one brings an
 * indicator line, a floating label that animates into the border, and its own
 * container colour — three pieces of Material chrome the design does not draw,
 * and all three would have to be neutralised before the token styling could be
 * applied on top. What is wanted from Material here is the text behaviour, and
 * `BasicTextField` is that behaviour with nothing painted.
 *
 * **Focus is a border, not a fill.** The design (screen 119) darkens the stroke
 * and lifts the fill to `surface` on the focused box. On a light page a focus
 * ring drawn in the accent would be the lime-on-off-white contrast problem
 * again; ink at 1.5dp is unambiguous and costs the screen nothing.
 *
 * **An error replaces nothing.** The message appears BELOW the field rather
 * than inside it, so the value the user typed is still visible while they read
 * what is wrong with it. The stroke turns `danger` at the same time, because a
 * message below a field that looks fine is a message people miss.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    /**
     * Ceiling on how tall a multi-line field is allowed to grow.
     *
     * Ignored while [singleLine] is true (Compose forbids setting both). It
     * exists for the fields that live inside fixed chrome — the chat composer
     * pinned above the keyboard, the report sheet's note box — where an
     * unbounded field would eat the transcript above it as someone types. The
     * text still scrolls inside the field once the cap is reached, so nothing
     * is truncated; only the box stops growing.
     */
    maxLines: Int = Int.MAX_VALUE,
    minHeight: Dp = AppTheme.dimens.component.control,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val stroke = when {
        error != null -> colors.danger
        focused -> colors.ink
        else -> colors.hairline
    }
    val fill = when {
        !enabled -> colors.surface3
        focused -> colors.surface
        else -> colors.surface2
    }
    val shape = RoundedCornerShape(dimens.radii.control)

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = dimens.space.sm),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(fill)
                .border(
                    width = if (focused || error != null) {
                        dimens.component.focusStroke
                    } else {
                        dimens.size.hairline
                    },
                    color = stroke,
                    shape = shape,
                )
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = dimens.space.lg, vertical = dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            leading?.invoke()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    // `maxLines` and `singleLine` are mutually exclusive in
                    // BasicTextField — passing a bounded maxLines alongside
                    // singleLine throws. One line already IS the cap, so the
                    // single-line case forces the default the platform expects.
                    maxLines = if (singleLine) Int.MAX_VALUE else maxLines,
                    textStyle = LocalTextStyle.current.merge(
                        AppTheme.type.body.copy(
                            color = if (enabled) colors.ink else colors.ink4,
                            fontWeight = FontWeight.Medium,
                        ),
                    ),
                    cursorBrush = SolidColor(colors.accentInk),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isEmpty() && !hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        style = AppTheme.type.body,
                        color = colors.hint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = AppTheme.type.caption,
                color = colors.danger,
                modifier = Modifier.padding(top = dimens.space.sm),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun AppTextFieldPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.lg),
        ) {
            AppTextField(
                value = "+91 98450 12345",
                onValueChange = {},
                label = "Mobile number",
                trailing = {
                    Text(
                        "IN +91",
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = AppTheme.colors.ink4,
                    )
                },
            )
            AppTextField(
                value = "",
                onValueChange = {},
                label = "Or use email",
                hint = "you@studio.in",
            )
            AppTextField(
                value = "not-an-email",
                onValueChange = {},
                label = "Email",
                error = "That address doesn't look right.",
            )
        }
    }
}
