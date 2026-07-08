package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.signup.SignupInputRow

/**
 * Step 7 — paste-based socials (iOS `ArtistSocialsStep`). Three loose text inputs; the artist
 * pastes whatever shape they have (URL / @handle), and Publish normalizes empties → NULL. No
 * OAuth (that's a multi-week external-review dependency tracked separately).
 */
@Composable
fun WizardSocialsStep(state: WizardUiState, vm: WizardViewModel) {
    val space = AppTheme.dimens.space
    val colors = AppTheme.colors
    val anyEntered = listOf(state.spotify, state.instagram, state.youtube).any { it.isNotBlank() }

    WizardScaffold(
        step = WizardStep.Socials,
        title = "Plug in your sound",
        subtitle = "Paste any of these — they show up on your profile and feed the Bookability Score later.",
        ctaText = if (anyEntered) "Continue →" else "Skip for now →",
        ctaEnabled = true, // optional
        onBack = vm::back,
        onCta = vm::advance,
    ) {
        SocialRow(
            label = "Spotify",
            value = state.spotify,
            onChange = vm::setSpotify,
            placeholder = "open.spotify.com/artist/…",
            helper = "From Spotify for Artists → Profile → Share.",
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(space.xl))
        SocialRow(
            label = "Instagram",
            value = state.instagram,
            onChange = vm::setInstagram,
            placeholder = "@yourhandle",
            helper = "We deep-link clients straight into the Instagram app.",
            keyboardType = KeyboardType.Text,
        )
        Spacer(Modifier.height(space.xl))
        SocialRow(
            label = "YouTube",
            value = state.youtube,
            onChange = vm::setYoutube,
            placeholder = "youtube.com/@yourchannel",
            helper = "Channel URL — handle URLs (with @) work too.",
            keyboardType = KeyboardType.Uri,
        )
    }
}

@Composable
private fun SocialRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    helper: String,
    keyboardType: KeyboardType,
) {
    val colors = AppTheme.colors
    SignupInputRow(
        label = label,
        value = value,
        onValueChange = onChange,
        placeholder = placeholder,
        keyboardType = keyboardType,
    )
    Spacer(Modifier.height(AppTheme.dimens.space.sm))
    androidx.compose.material3.Text(helper, style = AppTheme.type.caption, color = colors.ink3)
}
