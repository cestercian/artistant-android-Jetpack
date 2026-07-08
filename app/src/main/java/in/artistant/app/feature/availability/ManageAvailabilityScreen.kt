package `in`.artistant.app.feature.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.availabilityKicker
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.wizard.WizardConstants

/**
 * Post-onboarding availability editor (port of iOS `ManageAvailabilityView`). Two
 * chip grids (days / start times) under a live "HOW CLIENTS SEE YOU" preview pill,
 * with a pinned save bar. On a successful save it dismisses via [onDone].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageAvailabilityScreen(
    onDone: () -> Unit,
    viewModel: ManageAvailabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    // Dismiss once the save lands (the VM flips `saved`).
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = space.xl).padding(top = space.lg),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            // Header — editorial serif masthead.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Availability", style = AppTheme.type.displayMedium, color = colors.ink)
                Text(
                    "When you're open to play. Clients see this on your profile and in search.",
                    style = AppTheme.type.footnote,
                    color = colors.ink2,
                )
            }

            LivePreview(state.days)

            ChipSection("DAYS YOU PLAY") {
                WizardConstants.allDays.forEach { d ->
                    Chip(d, on = d in state.days) { viewModel.toggleDay(d) }
                }
            }
            ChipSection("PREFERRED START TIMES") {
                WizardConstants.allTimeSlots.forEach { s ->
                    Chip(s, on = s in state.times, mono = true) { viewModel.toggleTime(s) }
                }
            }

            state.error?.let { Text(it, style = AppTheme.type.footnote, color = colors.hot) }
        }

        SaveBar(saving = state.saving, onSave = viewModel::save)
    }
}

@Composable
private fun LivePreview(days: Set<String>) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Soonest open day, shared with Discover's pill so the preview never disagrees.
    val label = availabilityKicker(days)
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Text("HOW CLIENTS SEE YOU", style = AppTheme.type.caption, color = colors.ink3)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppTheme.dimens.radii.md.let { androidx.compose.foundation.shape.RoundedCornerShape(it) })
                .background(colors.bgSoft)
                .border(1.dp, colors.lineSoft, androidx.compose.foundation.shape.RoundedCornerShape(AppTheme.dimens.radii.md))
                .padding(space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(34.dp).clip(CircleShape).background(colors.brandSoft),
                contentAlignment = Alignment.Center,
            ) { Text("♪", color = colors.brand) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your profile", style = AppTheme.type.footnote, color = colors.ink)
                if (label != null) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(colors.bg)
                            .border(1.dp, colors.line, CircleShape)
                            .padding(horizontal = space.sm, vertical = space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.size(6.dp).clip(CircleShape).background(colors.good))
                        Text(label, style = AppTheme.type.monoSmall, color = colors.ink)
                    }
                } else {
                    Text("No badge yet — pick a day you play", style = AppTheme.type.caption, color = colors.ink3)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(title: String, content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm)) {
        Text(title, style = AppTheme.type.caption, color = colors.ink3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun Chip(label: String, on: Boolean, mono: Boolean = false, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Text(
        label,
        style = (if (mono) AppTheme.type.monoSmall else AppTheme.type.footnote).copy(fontWeight = FontWeight.SemiBold),
        color = if (on) colors.bg else colors.ink2,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) colors.ink else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (on) androidx.compose.ui.graphics.Color.Transparent else colors.line, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = space.md, vertical = space.sm),
    )
}

@Composable
private fun SaveBar(saving: Boolean, onSave: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.background(colors.bg)) {
        HRule()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.xl, vertical = space.md)
                .clip(CircleShape)
                .background(colors.brand)
                .clickable(enabled = !saving) { onSave() }
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.brandInk)
                Spacer(Modifier.size(8.dp))
            }
            Text(
                if (saving) "Saving…" else "Save changes",
                style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = colors.brandInk,
            )
        }
    }
}
