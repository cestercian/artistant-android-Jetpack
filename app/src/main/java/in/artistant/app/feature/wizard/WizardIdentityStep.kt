package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppColors
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.signup.HandleStatus
import `in`.artistant.app.feature.signup.SignupInputRow

/** Step 1 — stage identity: stage name, live-checked @handle, category grid, genre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardIdentityStep(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    val colors = AppTheme.colors
    WizardScaffold(
        step = WizardStep.Identity,
        title = "Your stage identity",
        subtitle = "How clients will discover and book you.",
        canBack = false,
        ctaText = "Continue →",
        ctaEnabled = state.identityValid,
        onBack = {},
        onCta = vm::advance,
    ) {
        SignupInputRow(
            label = "Stage name",
            value = state.stageName,
            onValueChange = vm::setStageName,
            placeholder = "Kaavya & The Tape",
        )
        Spacer(Modifier.height(space.xl))

        SignupInputRow(
            label = "Handle",
            value = state.handle,
            onValueChange = vm::setHandle,
            placeholder = "yourname",
            prefix = "@",
            keyboardType = KeyboardType.Ascii,
            underline = handleUnderline(state.handleStatus, colors),
            trailing = { HandleIndicator(state.handleStatus) },
        )
        Spacer(Modifier.height(space.xl))

        WizardSectionLabel("CATEGORY")
        Spacer(Modifier.height(space.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WizardConstants.categories.forEach { c ->
                WizardChip(label = c, selected = state.category == c, onClick = { vm.chooseCategory(c) })
            }
        }
        Spacer(Modifier.height(space.xl))

        SignupInputRow(
            label = "Genre",
            value = state.genre,
            onValueChange = vm::setGenre,
            placeholder = "Indie / Folk-rock",
        )
    }
}

/** Right-side live availability indicator (mirrors the signup ProfileScreen's HandleIndicator). */
@Composable
private fun HandleIndicator(status: HandleStatus) {
    val colors = AppTheme.colors
    when (status) {
        HandleStatus.Empty -> Unit
        HandleStatus.Invalid -> Text("3–24 · a–z 0–9 _", style = AppTheme.type.caption, color = colors.warm)
        HandleStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CircularProgressIndicator(color = colors.ink3, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            Text("Checking…", style = AppTheme.type.monoSmall, color = colors.ink3)
        }
        HandleStatus.Available -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(AppTheme.dimens.size.iconSm))
            Text("free", style = AppTheme.type.monoSmall, color = colors.brand)
        }
        HandleStatus.Taken -> Text("Taken", style = AppTheme.type.caption, color = colors.hot)
        HandleStatus.Error -> Text("Couldn't check", style = AppTheme.type.caption, color = colors.warm)
    }
}

private fun handleUnderline(status: HandleStatus, colors: AppColors): Color? = when (status) {
    HandleStatus.Available -> colors.brand
    HandleStatus.Taken, HandleStatus.Invalid -> colors.hot.copy(alpha = 0.5f)
    HandleStatus.Checking, HandleStatus.Error -> colors.brand.copy(alpha = 0.4f)
    HandleStatus.Empty -> null
}
