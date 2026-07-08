package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.platform.media.PendingAudioRef

/** Step 9 — up to 6 audio samples (optional). Files stage into the wizard cache on pick; the
 *  queue drains them on Publish (iOS `ArtistSamplesStep`). */
@Composable
fun WizardSamplesStep(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val pickAudio = rememberAudioPicker { uri, name -> vm.addSample(uri, name) }

    WizardScaffold(
        step = WizardStep.Samples,
        title = "Drop in a few clips",
        subtitle = "Up to 6 short audio samples. Anything 30s–2 min works — give clients a real sense of your sound.",
        ctaText = if (state.samples.isEmpty()) "Skip for now →" else "Continue →",
        ctaEnabled = state.samplesValid,
        onBack = vm::back,
        onCta = vm::advance,
    ) {
        state.samples.forEach { ref ->
            SampleRow(
                ref = ref,
                onRename = { vm.renameSample(ref.id, it) },
                onRemove = { vm.removeSample(ref.id) },
            )
            Spacer(Modifier.height(space.md))
        }
        if (state.samples.size < 6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
                    .clickable(onClick = pickAudio)
                    .padding(space.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = colors.brand, modifier = Modifier.size(AppTheme.dimens.size.iconLg))
                Text("Add sample", style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink, modifier = Modifier.weight(1f))
                Text("${state.samples.size} / 6", style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.ink3)
            }
        }
        state.publishError?.let {
            Spacer(Modifier.height(space.md))
            Text(it, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.hot)
        }
    }
}

@Composable
private fun SampleRow(ref: PendingAudioRef, onRename: (String) -> Unit, onRemove: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.bgCard).padding(space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = colors.brand, modifier = Modifier.size(AppTheme.dimens.size.iconXl))
        Column(Modifier.weight(1f)) {
            TextField(
                value = ref.title,
                onValueChange = onRename,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = colors.ink, fontWeight = FontWeight.SemiBold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = colors.brand,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Text(ref.durationLabel, style = AppTheme.type.monoSmall, color = colors.ink3)
        }
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Remove ${ref.title}",
            tint = colors.ink3,
            modifier = Modifier.size(AppTheme.dimens.size.iconLg).clickable(onClick = onRemove),
        )
    }
}
