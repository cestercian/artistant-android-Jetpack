package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Shared signup chrome — the iOS `SignupFlowView` bottom-of-file components (progress dots,
 * back button, underline input). The primary/ghost CTAs reuse the M1a [PrimaryButton] (already
 * has press-scale + Filled/Ghost variants), so there's no duplicate button here.
 */

/** 5-segment progress bar (iOS `SignupProgressDots`). Segments up to [index] are brand, the
 *  rest are dim. Welcome + Done pass null and hide it. */
@Composable
fun SignupProgressDots(bar: ProgressBar?, modifier: Modifier = Modifier) {
    if (bar == null) return
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl, vertical = space.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(bar.total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= bar.index) colors.brand else colors.bgSoft),
            )
        }
    }
}

/** Circular hairline back chevron (iOS `SignupBackButton`). */
@Composable
fun SignupBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        // The 36dp disc sits inside a `controlMin` tap target rather than being grown to it —
        // the touch floor is a hit area, not a size. Sizing the disc itself to 48dp would make
        // the back affordance the heaviest thing on a signup step.
        modifier = modifier
            .size(dimens.size.controlMin)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(colors.bgCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
    }
}

/**
 * Borderless underline input (iOS `SignupInputRow`): small-caps mono-ish label + an 18sp typed
 * line over a 1dp bottom rule that tints brand when non-empty (or on an explicit [underline]
 * override the caller passes for handle-status coloring). No card chrome.
 */
@Composable
fun SignupInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    underline: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                style = AppTheme.type.caption,
                color = colors.ink3,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        val ruleColor = underline ?: if (value.isEmpty()) colors.line else colors.brand.copy(alpha = 0.4f)
        val typed = AppTheme.type.headline.copy(color = colors.ink)
        // `BasicTextField`, not Material's `TextField`, for the same reason
        // `EpkField` gives: Material ships a container fill, an indicator line
        // AND a 56dp minimum height. The first two can be recoloured to
        // transparent — the minimum cannot, and it is invisible, so a field
        // whose ink is one 18sp line still reserved 56dp of column. Across four
        // rows that was the whole difference between this step and the
        // reference's: ~28dp of dead air per field, none of it drawn.
        //
        // The rule is an overlay on the typed line rather than a third child of
        // the spaced Column, which is what it used to be — as a sibling it
        // collected the Column's own gap and put 8dp between a field and its
        // own underline.
        Box(contentAlignment = Alignment.BottomStart) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prefix != null) {
                    Text(prefix, style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.ink3)
                    Spacer(Modifier.width(space.sm))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = typed,
                    cursorBrush = SolidColor(colors.brand),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) Text(placeholder, style = typed, color = colors.ink4)
                            inner()
                        }
                    },
                )
            }
            Box(Modifier.fillMaxWidth().height(dimens.size.hairline).background(ruleColor))
        }
    }
}

/** Custom terms checkbox (iOS welcome `termsRow`): rounded square that fills brand + shows a
 *  check when on. No Material Checkbox — the hairline/brand treatment is the design signal. */
@Composable
fun TermsCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = modifier
            .size(AppTheme.dimens.size.iconLg)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(colors.brand)
                } else {
                    Modifier.border(AppTheme.dimens.size.strokeEmphasis, colors.line, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.brandInk,
                modifier = Modifier.size(AppTheme.dimens.size.iconSm),
            )
        }
    }
}
