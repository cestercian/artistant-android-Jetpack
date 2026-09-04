package `in`.artistant.app.feature.epk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import java.util.Locale

/**
 * The editor's small parts.
 *
 * They live here rather than in `designsystem/component` because none of them
 * is shared yet: a hairline inline field and a caps section rule are the EPK's
 * house style, and promoting a component before a second caller exists is how a
 * design system fills up with near-duplicates. If the artist home or the wizard
 * grows the same need, this is the file to lift from.
 */

/**
 * A section rule: caps label on the left, one optional text action on the right.
 *
 * A text action, not a button. The editor has eight sections and each one can
 * add something; eight filled buttons down a page would read as eight equally
 * urgent CTAs, which is exactly the "card chrome" the design language rejects.
 */
@Composable
fun EpkSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    trailingNote: String? = null,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(Locale.US), style = AppTheme.type.caption, color = colors.ink3)
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (trailingNote != null) {
                Text(trailingNote, style = AppTheme.type.caption, color = colors.ink3)
            }
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    style = AppTheme.type.footnote,
                    // Dimmed rather than hidden when disabled: a control that
                    // vanishes reads as a bug, one that dims reads as "not yet".
                    color = if (actionEnabled) colors.brand else colors.ink4,
                    modifier = Modifier
                        .heightIn(min = AppTheme.dimens.size.rowMin)
                        .clickable(enabled = actionEnabled, onClick = onAction)
                        .padding(vertical = space.md),
                )
            }
        }
    }
}

/**
 * Inline text field: no Material container, one hairline under the text.
 *
 * `BasicTextField` rather than `TextField` because Material's version ships an
 * indicator line, a container fill and a 56dp minimum that all have to be
 * fought back to transparent — cheaper to draw the one rule we actually want.
 *
 * The rule lights up once the field holds something, which is the only state
 * feedback the editor gives per-field; correctness feedback belongs to the save
 * banner, not to a red underline the artist is still typing into.
 */
@Composable
fun EpkField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = AppTheme.type.body,
    keyboardType: KeyboardType = KeyboardType.Text,
    textAlign: TextAlign = TextAlign.Start,
    enabled: Boolean = true,
    contentDescription: String? = null,
    /**
     * Multi-line fields keep the Return key instead of spending it on "next
     * field". Only prose wants that — a name, a duration or a price is one line
     * by definition, and letting those wrap turns a stray paste with a newline
     * in it into a row that silently changes height.
     */
    singleLine: Boolean = true,
    /**
     * Opening height for a multi-line field, in lines. A prose field that starts
     * one line tall reads as a name field and gets a name typed into it, so the
     * box has to look like it wants a paragraph before anyone has typed one.
     */
    minLines: Int = 1,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val resolved = textStyle.copy(
        color = if (enabled) colors.ink else colors.ink3,
        textAlign = textAlign,
    )
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = resolved,
            cursorBrush = SolidColor(colors.brand),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimens.size.rowMin)
                        .padding(vertical = dimens.space.sm),
                    // A multi-line box grows downward, so its placeholder and its
                    // text have to start at the top — centring them would walk the
                    // first line down the box as the artist types.
                    contentAlignment = when {
                        !singleLine -> Alignment.TopStart
                        textAlign == TextAlign.End -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    },
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = resolved, color = colors.ink4)
                    }
                    inner()
                }
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.size.hairline)
                .background(if (value.isBlank()) colors.line else colors.brand.copy(alpha = 0.4f)),
        )
    }
}

/**
 * Capsule toggle — the tech rider and any future chip-select.
 *
 * Selected is a FILLED capsule in the role accent, unselected is a hairline
 * outline. Two states that differ in fill rather than in tint, because a tinted
 * outline and an untinted outline are indistinguishable at chip size on a phone.
 */
@Composable
fun EpkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .clip(CircleShape)
            .background(if (selected) colors.brand else Color.Transparent)
            .border(
                dimens.size.hairline,
                if (selected) Color.Transparent else colors.line,
                CircleShape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            )
            // The fill is the whole state signal, and a screen reader cannot see a
            // fill: without this the chip reads out identically ticked or not.
            .semantics { this.selected = selected }
            .pressScale(interaction)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppTheme.type.footnote,
            color = when {
                !enabled -> colors.ink4
                selected -> colors.brandInk
                else -> colors.ink2
            },
        )
    }
}

/**
 * A destructive row action, rendered as a word.
 *
 * Word rather than a trash glyph: at this size a glyph needs a label for
 * accessibility anyway, and "Remove" is unambiguous where a 16dp icon next to
 * three other 16dp icons is not.
 */
@Composable
fun EpkRowAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Text(
        label,
        style = AppTheme.type.footnote,
        color = if (enabled) (tone ?: colors.ink3) else colors.ink4,
        modifier = modifier
            .heightIn(min = dimens.size.rowMin)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = dimens.space.md, horizontal = dimens.space.xs),
    )
}
