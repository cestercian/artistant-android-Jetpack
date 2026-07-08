package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/** Step 8 — free-text bio (<=200 chars, optional). Counter warms as it fills (iOS `ArtistBioStep`). */
@Composable
fun WizardBioStep(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val remaining = 200 - state.bio.length
    val counterColor = when {
        remaining <= 0 -> colors.hot
        remaining <= 20 -> colors.warm
        else -> colors.ink3
    }
    WizardScaffold(
        step = WizardStep.Bio,
        title = "Your story, briefly",
        subtitle = "A line or two about your sound, your live energy, or what makes your gigs different.",
        ctaText = if (state.bio.isBlank()) "Skip for now →" else "Continue →",
        ctaEnabled = state.bioValid,
        onBack = vm::back,
        onCta = vm::advance,
    ) {
        TextField(
            value = state.bio,
            onValueChange = vm::setBio,
            placeholder = {
                Text(
                    "e.g. Indie-folk trio out of Bangalore. Mellow weddings, smoky pub sets, the occasional NH7 stage.",
                    color = colors.ink4,
                )
            },
            textStyle = LocalTextStyle.current.copy(color = colors.ink),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.bgCard,
                unfocusedContainerColor = colors.bgCard,
                cursorColor = colors.brand,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.lineSoft, RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("${state.bio.length} / 200", style = AppTheme.type.caption, color = counterColor)
        }
    }
}
