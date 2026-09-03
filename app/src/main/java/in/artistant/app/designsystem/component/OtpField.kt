package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The six-box one-time code (screen 119).
 *
 * **One real text field behind six drawn boxes**, not six fields. Six separate
 * inputs is the obvious construction and it is wrong on every count that
 * matters here: SMS autofill hands the platform a whole code and needs one
 * field to put it in; backspace at the start of box 4 has to move to box 3,
 * which means writing focus plumbing that the platform already has; and a
 * screen reader announces six unlabelled edit fields instead of one code. So
 * the input is a single invisible `BasicTextField` carrying the whole string,
 * and the boxes are a decoration drawn from it.
 *
 * The field is transparent rather than `size(0)` — a zero-sized node cannot
 * take focus reliably, and the caret has to live somewhere real for the IME to
 * anchor to.
 *
 * Digits only, and never more than [length]: the filter is on the way in, so a
 * paste of "your code is 472913" cannot put letters in the boxes.
 */
@Composable
fun OtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = DEFAULT_LENGTH,
    enabled: Boolean = true,
    isError: Boolean = false,
    onFilled: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).take(length)
                val changed = digits != value
                if (changed) onValueChange(digits)
                // Only on the transition INTO a full code: a keystroke that leaves
                // the value unchanged (typing past the end) must not re-submit.
                if (changed && digits.length == length) onFilled?.invoke()
            },
            enabled = enabled,
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            // Done on an incomplete code is a no-op; the boxes show what is missing.
            keyboardActions = KeyboardActions(onDone = { if (value.length == length) onFilled?.invoke() }),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.component.otpBox)
                // Invisible but real: it holds focus, anchors the IME and
                // receives the autofilled code. `alpha(0f)` keeps the node
                // laid out; `size(0)` would not.
                .alpha(0f)
                .semantics { contentDescription = "One-time code" },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            repeat(length) { index ->
                OtpBox(
                    digit = value.getOrNull(index)?.toString(),
                    active = focused && index == value.length.coerceAtMost(length - 1),
                    isError = isError,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OtpBox(
    digit: String?,
    active: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.control)
    val stroke = when {
        isError -> colors.danger
        active -> colors.ink
        else -> colors.hairline
    }
    Box(
        modifier = modifier
            .height(dimens.component.otpBox)
            .clip(shape)
            .background(if (active) colors.surface else colors.surface3)
            .border(dimens.component.focusStroke, stroke, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (digit != null) {
            Text(
                text = digit,
                style = AppTheme.type.monoLarge,
                color = colors.ink,
            )
        } else if (active) {
            // The caret. Drawn rather than borrowed from the hidden field,
            // whose own cursor is transparent — a blinking bar in a box the
            // user cannot type into directly would be pointing at the wrong
            // thing.
            Box(
                Modifier
                    .padding(vertical = dimens.space.lg)
                    .width(dimens.size.hairline * CARET_WIDTH)
                    .fillMaxHeight()
                    .background(colors.accentInk),
            )
        }
    }
}

private const val DEFAULT_LENGTH = 6

/** The caret is two hairlines wide — one is invisible at this size. */
private const val CARET_WIDTH = 2

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun OtpFieldPreview() {
    ArtistantTheme {
        Box(Modifier.padding(AppTheme.dimens.component.gutter)) {
            OtpField(value = "4729", onValueChange = {})
        }
    }
}
