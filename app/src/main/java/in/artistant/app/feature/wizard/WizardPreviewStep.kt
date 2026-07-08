package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Step 10 — preview the EPK as a client sees it, then Publish. The CTA drives [WizardViewModel.publish]
 * (save row + go live + enqueue media); its text/enabled reflect the in-flight publish. Publish
 * failures surface here, on this screen (iOS `ArtistPreviewStep`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardPreviewStep(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    WizardScaffold(
        step = WizardStep.Preview,
        title = "Looking good",
        subtitle = "This is what clients will see on your profile.",
        ctaText = if (state.isPublishing) "Publishing…" else "Looks good, publish →",
        ctaEnabled = !state.isPublishing,
        onBack = vm::back,
        onCta = vm::publish,
    ) {
        WizardCoverPreview(state, vm)
        Spacer(Modifier.height(space.lg))

        // Stats strip.
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.bgCard).padding(vertical = space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat("@${state.handle}", "HANDLE", Modifier.weight(1f))
            StatDivider()
            Stat(WizardConstants.allDays.filter { it in state.daysAvailable }.joinToString(" "), "DAYS", Modifier.weight(1f))
            StatDivider()
            Stat("${state.packages.size}", "PACKAGES", Modifier.weight(1f))
        }

        Spacer(Modifier.height(space.lg))
        WizardSectionLabel("PACKAGES")
        Spacer(Modifier.height(space.sm))
        state.packages.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        Text(p.name, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
                        if (p.popular) Pill(text = "Popular", tone = PillTone.Brand)
                    }
                    Text(p.duration, style = AppTheme.type.caption, color = colors.ink3)
                }
                Text(formatInr(p.price), style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Black), color = colors.ink)
            }
            Spacer(Modifier.height(space.sm))
        }

        if (state.tech.isNotEmpty()) {
            Spacer(Modifier.height(space.sm))
            WizardSectionLabel("TECH RIDER")
            Spacer(Modifier.height(space.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tech.forEach { t -> Pill(text = t, tone = PillTone.Neutral) }
            }
        }

        state.publishError?.let {
            Spacer(Modifier.height(space.md))
            Text(it, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.hot)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier = modifier.padding(horizontal = AppTheme.dimens.space.lg)) {
        Text(value, style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.ink, maxLines = 1)
        Text(label, style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black), color = colors.ink3)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(AppTheme.colors.lineSoft))
}
