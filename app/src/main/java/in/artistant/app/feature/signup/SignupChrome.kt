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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colors.bgCard)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(AppTheme.dimens.size.iconLg),
        )
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
    val space = AppTheme.dimens.space
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
        // The typed line uses a transparent-container TextField so only our 1dp rule shows —
        // Material's default filled/outlined chrome would break the hairline aesthetic.
        val ruleColor = underline ?: if (value.isEmpty()) colors.line else colors.brand.copy(alpha = 0.4f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefix != null) {
                Text(prefix, style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.ink3)
                Spacer(Modifier.width(6.dp))
            }
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = colors.ink4) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = colors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                ),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = colors.brand,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ruleColor))
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
            .then(if (checked) Modifier.background(colors.brand) else Modifier.border(1.5.dp, colors.line, shape)),
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
