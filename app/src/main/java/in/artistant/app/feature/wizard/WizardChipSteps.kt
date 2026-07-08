package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The three chip-group wizard steps — Location, Tech rider, Availability. All share the same
 * FlowRow-of-[WizardChip] layout, so they live in one file (ports of iOS `ArtistLocationStep` /
 * `ArtistTechStep` / `ArtistAvailabilityStep`).
 */

/** Step 2 — where you play: base city (single-select) + event types (optional multi-select). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardLocationStep(state: WizardUiState, vm: WizardViewModel) {
    WizardScaffold(
        step = WizardStep.Location,
        title = "Where you play",
        subtitle = "Helps us route the right gigs your way.",
        ctaEnabled = state.locationValid,
        onBack = vm::back,
        onCta = vm::advance,
    ) {
        WizardSectionLabel("BASE CITY")
        Spacer(Modifier.height(AppTheme.dimens.space.sm))
        ChipFlow {
            WizardConstants.cities.forEach { c ->
                WizardChip(label = c, selected = state.baseCity == c, onClick = { vm.setBaseCity(c) })
            }
        }
        Spacer(Modifier.height(AppTheme.dimens.space.xl))
        WizardSectionLabel("EVENT TYPES YOU'RE UP FOR (OPTIONAL)")
        Spacer(Modifier.height(AppTheme.dimens.space.sm))
        ChipFlow {
            WizardConstants.allEventTypes.forEach { e ->
                WizardChip(label = e, selected = e in state.eventTypes, onClick = { vm.toggleEventType(e) })
            }
        }
    }
}

/** Step 4 — tech rider: optional multi-select of preset requirements. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardTechStep(state: WizardUiState, vm: WizardViewModel) {
    WizardScaffold(
        step = WizardStep.Tech,
        title = "Tech rider",
        subtitle = "What you'll need from the venue. Pick all that apply.",
        ctaText = if (state.tech.isEmpty()) "Skip for now →" else "Continue →",
        ctaEnabled = true, // optional
        onBack = vm::back,
        onCta = vm::advance,
    ) {
        ChipFlow {
            WizardConstants.techPresets.forEach { item ->
                WizardTechChip(label = item, selected = item in state.tech, onClick = { vm.toggleTech(item) })
            }
        }
    }
}

/**
 * Tech-rider chip (iOS `ArtistTechStep`): distinct from the plain ink chips — a
 * brand(lime)-fill + leading checkmark when selected, hairline + plus when not.
 * The lime is the signal that these are the artist's ACTIVE requirements.
 */
@Composable
private fun WizardTechChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val shape = CircleShape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.xs),
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(colors.brand, shape)
                else Modifier.border(1.dp, colors.line, shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = space.md, vertical = space.sm),
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = null,
            tint = if (selected) colors.brandInk else colors.ink3,
            modifier = Modifier.size(AppTheme.dimens.size.iconSm),
        )
        Text(
            label,
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.brandInk else colors.ink2,
        )
    }
}

/** Step 5 — availability: days (required, >=1) + preferred start times. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardAvailabilityStep(state: WizardUiState, vm: WizardViewModel) {
    WizardScaffold(
        step = WizardStep.Availability,
        title = "When you play",
        subtitle = "Pick the days and start times you take gigs. Clients see this on your profile and in search.",
        ctaEnabled = state.availabilityValid,
        onBack = vm::back,
        onCta = vm::advance,
    ) {
        WizardSectionLabel("DAYS YOU'RE OPEN TO PLAY")
        Spacer(Modifier.height(AppTheme.dimens.space.sm))
        ChipFlow {
            WizardConstants.allDays.forEach { d ->
                WizardChip(label = d, selected = d in state.daysAvailable, onClick = { vm.toggleDay(d) })
            }
        }
        Spacer(Modifier.height(AppTheme.dimens.space.xl))
        WizardSectionLabel("PREFERRED START TIMES")
        Spacer(Modifier.height(AppTheme.dimens.space.sm))
        ChipFlow {
            WizardConstants.allTimeSlots.forEach { slot ->
                WizardChip(label = slot, selected = slot in state.timeSlots, onClick = { vm.toggleTimeSlot(slot) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
