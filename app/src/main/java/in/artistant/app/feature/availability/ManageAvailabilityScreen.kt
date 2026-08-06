package `in`.artistant.app.feature.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.feature.wizard.WizardWeekdays

/**
 * Post-onboarding availability editor — port of iOS ManageAvailabilityView.
 * Seeds from / writes to artists.days_available + default_time_slots.
 * Save blocked when seed failed so an empty working copy can't wipe real data.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageAvailabilityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageAvailabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
            }
            Spacer(Modifier.weight(1f))
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
            else -> {
                // The weight moves OUT to the wrapper: it is the wrapper that is
                // now the Column's child, so it has to be the thing that claims
                // the remaining height. The content then fills the slot it was
                // handed.
                RevealOnAppear(Modifier.weight(1f)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = space.xl),
                    ) {
                        Text("Availability", style = AppTheme.type.displaySub, color = colors.ink)
                        Spacer(Modifier.height(space.sm))
                        Text(
                            "When you're open to play. Clients see this on your profile and in search.",
                            style = AppTheme.type.footnote,
                            color = colors.ink2,
                        )
                        Spacer(Modifier.height(space.lg))

                        Text("HOW CLIENTS SEE YOU", style = AppTheme.type.caption, color = colors.ink3)
                        Spacer(Modifier.height(space.sm))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
                                .background(colors.bgSoft)
                                .border(1.dp, colors.lineSoft, RoundedCornerShape(AppTheme.dimens.radii.md))
                                .padding(space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(space.md),
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.brandSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("You", style = AppTheme.type.caption, color = colors.brandInk)
                            }
                            Column {
                                Text("Your profile", style = AppTheme.type.footnote, color = colors.ink)
                                val label = state.previewLabel
                                if (label != null) {
                                    Text(label, style = AppTheme.type.monoSmall, color = colors.ink)
                                } else {
                                    Text(
                                        "No badge yet — pick a day you play",
                                        style = AppTheme.type.caption,
                                        color = colors.ink3,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(space.lg))
                        Text("DAYS YOU PLAY", style = AppTheme.type.caption, color = colors.ink3)
                        Spacer(Modifier.height(space.sm))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                            WizardWeekdays.forEach { day ->
                                val on = day in state.days
                                if (on) {
                                    Pill(
                                        text = day,
                                        tone = PillTone.Brand,
                                        modifier = Modifier.clickable { viewModel.toggleDay(day) },
                                    )
                                } else {
                                    Text(
                                        day,
                                        style = AppTheme.type.caption,
                                        color = colors.ink2,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.bgSoft)
                                            .clickable { viewModel.toggleDay(day) }
                                            .padding(horizontal = space.md, vertical = space.xs),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(space.lg))
                        Text("PREFERRED START TIMES", style = AppTheme.type.caption, color = colors.ink3)
                        Spacer(Modifier.height(space.sm))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                            DefaultTimeSlots.forEach { slot ->
                                val on = slot in state.times
                                if (on) {
                                    Pill(
                                        text = slot,
                                        tone = PillTone.Brand,
                                        modifier = Modifier.clickable { viewModel.toggleTime(slot) },
                                    )
                                } else {
                                    Text(
                                        slot,
                                        style = AppTheme.type.monoSmall,
                                        color = colors.ink2,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.bgSoft)
                                            .clickable { viewModel.toggleTime(slot) }
                                            .padding(horizontal = space.md, vertical = space.xs),
                                    )
                                }
                            }
                        }

                        state.error?.let {
                            Spacer(Modifier.height(space.md))
                            Text(it, style = AppTheme.type.footnote, color = colors.hot)
                        }
                        if (state.saved) {
                            Spacer(Modifier.height(space.md))
                            Text("Saved.", style = AppTheme.type.footnote, color = colors.brand)
                        }
                        Spacer(Modifier.height(120.dp))
                    }
                }

                Column {
                    HRule()
                    Box(Modifier.padding(space.xl)) {
                        if (state.seedFailed) {
                            PrimaryButton(text = "Retry", onClick = viewModel::seed, fullWidth = true)
                        } else {
                            PrimaryButton(
                                text = if (state.isSaving) "Saving…" else "Save changes",
                                onClick = viewModel::save,
                                enabled = !state.isSaving,
                                fullWidth = true,
                            )
                        }
                    }
                }
            }
        }
    }
}
