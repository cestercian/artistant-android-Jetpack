package `in`.artistant.app.feature.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.feature.epk.EpkChip
import `in`.artistant.app.feature.epk.EpkField
import `in`.artistant.app.feature.epk.EpkRowAction
import `in`.artistant.app.feature.epk.EpkSectionHeader
import `in`.artistant.app.feature.epk.PackageRow
import `in`.artistant.app.feature.epk.TECH_PRESETS
import `in`.artistant.app.feature.epk.packageRowBlocker
import `in`.artistant.app.feature.epk.packageRowIsPartiallyFilled
import `in`.artistant.app.feature.epk.popularBadgeWouldMeanSomething
import `in`.artistant.app.feature.epk.previewPackages
import `in`.artistant.app.feature.signup.SignupInputRow

/**
 * The five required steps: who you are, where you play, what you charge, what
 * you need from the venue, and when you're free.
 *
 * They are the ones a client's search result and booking sheet read from, which
 * is why all five gate the Continue button while the optional steps do not.
 */

// ── Identity ─────────────────────────────────────────────────────────────────

fun LazyListScope.identityStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "identity.stageName") {
        SignupInputRow(
            label = "Stage name",
            value = state.stageName,
            onValueChange = vm::onStageNameChanged,
            placeholder = "Kaavya & The Tape",
            modifier = Modifier.semantics { testTag = "wizard.identity.stageName" },
        )
    }
    item(key = "identity.handle") { HandleRow(state, vm) }
    item(key = "identity.category") {
        WizardChipSection(
            title = "Category",
            options = WizardCategories,
            isSelected = { it == state.category },
            onToggle = vm::onCategorySelected,
            tag = "wizard.identity.category",
        )
    }
    item(key = "identity.genre") {
        SignupInputRow(
            label = "Genre",
            value = state.genre,
            onValueChange = vm::onGenreChanged,
            placeholder = "Indie / Folk-rock",
            modifier = Modifier.semantics { testTag = "wizard.identity.genre" },
        )
    }
}

/**
 * The handle field, plus whatever the availability check currently knows.
 *
 * The status shows up in three places at once and each carries a different part
 * of the message: the underline tint is peripheral (something is right/wrong
 * here), the trailing indicator is the verdict, and the hint below is the only
 * one that says what to do about it. Blocking states get the hint; a passing or
 * in-flight check stays quiet, because a field that narrates every keystroke is
 * noise.
 */
@Composable
private fun HandleRow(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val underline = when (state.handleStatus) {
        WizardHandleStatus.Available -> colors.brand.copy(alpha = 0.5f)
        WizardHandleStatus.Taken, WizardHandleStatus.Invalid -> colors.warm.copy(alpha = 0.5f)
        else -> null
    }
    Column {
        SignupInputRow(
            label = "Handle",
            value = state.handle,
            onValueChange = vm::onHandleChanged,
            placeholder = "yourname",
            prefix = "@",
            underline = underline,
            modifier = Modifier.semantics { testTag = "wizard.identity.handle" },
            trailing = { HandleStatusIndicator(state.handleStatus) },
        )
        wizardHandleHint(state.handleStatus)?.let { hint ->
            Spacer(Modifier.height(space.sm))
            Text(hint, style = AppTheme.type.caption, color = colors.warm)
        }
    }
}

@Composable
private fun HandleStatusIndicator(status: WizardHandleStatus) {
    val colors = AppTheme.colors
    when (status) {
        WizardHandleStatus.Checking -> CircularProgressIndicator(
            color = colors.ink3,
            strokeWidth = AppTheme.dimens.size.hairline,
            modifier = Modifier.size(AppTheme.dimens.size.iconMd),
        )
        WizardHandleStatus.Available -> Text(
            "AVAILABLE",
            style = AppTheme.type.monoMicro,
            color = colors.good,
            modifier = Modifier.semantics { contentDescription = "Handle available" },
        )
        WizardHandleStatus.Taken -> Text(
            "TAKEN",
            style = AppTheme.type.monoMicro,
            color = colors.warm,
            modifier = Modifier.semantics { contentDescription = "Handle taken" },
        )
        // Empty / Invalid / Error carry no verdict worth a badge: the hint below
        // covers Invalid, and Error deliberately reads as "unknown, carry on".
        WizardHandleStatus.Empty, WizardHandleStatus.Invalid, WizardHandleStatus.Error -> Unit
    }
}

// ── Location ─────────────────────────────────────────────────────────────────

fun LazyListScope.locationStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "location.city") {
        WizardChipSection(
            title = "Base city",
            options = WizardCities,
            isSelected = { it == state.baseCity },
            onToggle = vm::onBaseCitySelected,
            tag = "wizard.location.city",
        )
    }
}

// ── Pricing ──────────────────────────────────────────────────────────────────

fun LazyListScope.pricingStep(state: WizardUiState, vm: WizardViewModel) {
    // Computed once for the whole list rather than per row: it is a property of
    // the set, and asking it six times per recomposition is six list scans.
    val popularMeansSomething = popularBadgeWouldMeanSomething(state.packageRows)

    items(state.packageRows, key = { "pricing.${it.key}" }) { row ->
        PackageRowCard(
            row = row,
            popularMeansSomething = popularMeansSomething,
            canRemove = state.packageRows.size > 1,
            vm = vm,
        )
    }
    item(key = "pricing.add") {
        AddTierButton(
            enabled = state.packageRows.size < WIZARD_MAX_PACKAGES,
            onClick = vm::addPackageRow,
        )
    }
    item(key = "pricing.from") {
        // The "from" figure clients will see. Derived through PackagePricing so
        // the wizard, the profile and search all answer the question with the
        // same arithmetic — a locally re-derived minimum is how those three
        // surfaces start disagreeing about an artist's cheapest tier.
        val savable = previewPackages(state.packageRows)
        if (savable.isNotEmpty()) {
            Text(
                "Clients will see “from ${formatInr(PackagePricing.fromPrice(savable, fallback = 0))}”.",
                style = AppTheme.type.caption,
                color = AppTheme.colors.ink3,
            )
        }
    }
}

@Composable
private fun PackageRowCard(
    row: PackageRow,
    popularMeansSomething: Boolean,
    canRemove: Boolean,
    vm: WizardViewModel,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.bgCard)
            .padding(space.lg)
            .semantics { testTag = "wizard.pricing.row" },
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EpkField(
                value = row.name,
                onValueChange = { vm.onPackageNameChanged(row.key, it) },
                placeholder = "Tier name",
                textStyle = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
                contentDescription = "Tier name",
            )
            if (canRemove) {
                Spacer(Modifier.width(space.sm))
                // Removing the last tier would leave the step ungatable, so the
                // action disappears at one rather than failing on tap.
                EpkRowAction(
                    label = "Remove",
                    onClick = { vm.removePackageRow(row.key) },
                    tone = colors.hot,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
            EpkField(
                value = row.duration,
                onValueChange = { vm.onPackageDurationChanged(row.key, it) },
                placeholder = "Duration",
                textStyle = AppTheme.type.footnote,
                modifier = Modifier.weight(1f),
                contentDescription = "Duration",
            )
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("₹", style = AppTheme.type.monoPrice, color = colors.ink3)
                Spacer(Modifier.width(space.xs))
                EpkField(
                    value = row.price,
                    onValueChange = { vm.onPackagePriceChanged(row.key, it) },
                    placeholder = "0",
                    textStyle = AppTheme.type.monoPrice,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    contentDescription = "Price in rupees",
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Popular", style = AppTheme.type.footnote, color = colors.ink2)
                if (!popularMeansSomething) {
                    // A badge is a comparison. With one tier, or with all of them
                    // flagged, it splits nothing — say so rather than let the
                    // artist find out by looking at their own public profile.
                    Text(
                        "Marks one tier out from the rest.",
                        style = AppTheme.type.caption,
                        color = colors.ink3,
                    )
                }
            }
            Switch(
                checked = row.popular,
                onCheckedChange = { vm.onPackagePopularToggled(row.key) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.brandInk,
                    checkedTrackColor = colors.brand,
                    uncheckedThumbColor = colors.ink3,
                    uncheckedTrackColor = colors.bgSoft,
                    uncheckedBorderColor = colors.line,
                ),
                modifier = Modifier.semantics { testTag = "wizard.pricing.popular" },
            )
        }
        // Why this tier will not publish. The gate only asks for ONE savable row
        // and `packageDrafts` drops the rest, so an artist who fills three tiers
        // and leaves one price blank advances, publishes, and that tier is never
        // written — the row looked finished right up until it was gone.
        packageRowBlocker(row)?.let { blocker ->
            Text(
                blocker,
                style = AppTheme.type.caption,
                // Loud only once there is something to lose. A row that has just
                // been added is blank by definition, and warning about it on the
                // frame it appears blames the artist for pressing Add.
                color = if (packageRowIsPartiallyFilled(row)) colors.warm else colors.ink3,
            )
        }
    }
}

/**
 * Dashed outline rather than a filled button: adding a tier is an option, and a
 * second solid CTA on a screen that already has Continue reads as two equally
 * weighted next steps.
 */
@Composable
private fun AddTierButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(dimens.radii.md)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(dimens.size.hairline, if (enabled) colors.brand.copy(alpha = 0.4f) else colors.line, shape)
            .pressScale(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = dimens.space.lg)
            .semantics { testTag = "wizard.pricing.addTier" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (enabled) "Add tier" else "Six tiers is the maximum",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) colors.brand else colors.ink3,
        )
    }
}

// ── Tech rider ───────────────────────────────────────────────────────────────

fun LazyListScope.techStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "tech.presets") {
        WizardChipSection(
            title = "Common requirements",
            options = TECH_PRESETS,
            // Case-insensitive, matching the EPK's rider chips and the add/toggle
            // rules behind them: "4 Vocal Mics" IS the "4 vocal mics" preset, and
            // an exact-match test rendered it unselected while its twin sat in
            // "Yours" — two lines on one rider for one requirement.
            isSelected = { preset -> state.techItems.any { it.equals(preset, ignoreCase = true) } },
            onToggle = vm::toggleTechItem,
            tag = "wizard.tech.presets",
        )
    }
    // Anything the artist added by hand — rendered separately so a custom entry
    // is visibly theirs and removable, rather than lost among the presets.
    val custom = state.techItems.filterNot { item -> TECH_PRESETS.any { it.equals(item, ignoreCase = true) } }
    if (custom.isNotEmpty()) {
        item(key = "tech.custom") {
            WizardChipSection(
                title = "Yours",
                options = custom,
                isSelected = { true },
                onToggle = vm::toggleTechItem,
                tag = "wizard.tech.custom",
            )
        }
    }
    item(key = "tech.add") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            EpkField(
                value = state.techDraft,
                onValueChange = vm::onTechDraftChanged,
                placeholder = "Anything else you need",
                modifier = Modifier.weight(1f),
                contentDescription = "Custom rider item",
            )
            EpkRowAction(
                label = "Add",
                onClick = vm::addTechItem,
                tone = AppTheme.colors.brand,
                enabled = state.techDraft.isNotBlank(),
            )
        }
    }
}

// ── Availability ─────────────────────────────────────────────────────────────

fun LazyListScope.availabilityStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "availability.days") {
        WizardChipSection(
            title = "Days you're open to play",
            options = WizardWeekdays,
            isSelected = { it in state.daysAvailable },
            onToggle = vm::toggleDay,
            tag = "wizard.availability.days",
        )
    }
    item(key = "availability.times") {
        WizardChipSection(
            title = "Preferred start times",
            options = WizardTimeSlots,
            isSelected = { it in state.timeSlots },
            onToggle = vm::toggleTimeSlot,
            tag = "wizard.availability.times",
        )
    }
}

// ── Shared ───────────────────────────────────────────────────────────────────

/**
 * A caps section rule over a wrapping chip grid — the shape five of these steps
 * share.
 *
 * The chip itself is the EPK editor's, not a local copy: the wizard and the
 * post-wizard editor are two ways to set the same fields, and a chip that looks
 * one way during setup and another way afterwards reads as two different
 * products. [EpkChip] is also where the selected/unselected treatment (filled
 * accent vs hairline outline) is already settled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardChipSection(
    title: String,
    options: List<String>,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val space = AppTheme.dimens.space
    Column(modifier.semantics { testTag = tag }) {
        EpkSectionHeader(title = title)
        Spacer(Modifier.height(space.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            options.forEach { option ->
                val selected = isSelected(option)
                EpkChip(
                    label = option,
                    selected = selected,
                    onClick = { onToggle(option) },
                    modifier = Modifier.semantics {
                        contentDescription = "$option${if (selected) ", selected" else ""}"
                    },
                )
            }
        }
    }
}

/**
 * A one-line labelled value for the preview's stats strip.
 *
 * Ellipsised rather than clipped: a long handle cut mid-glyph at the cell edge
 * reads as a rendering bug, where "@fixtureart…" reads as a value that did not
 * fit.
 */
@Composable
fun WizardStat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier) {
        Text(
            value.ifBlank { "—" },
            style = AppTheme.type.monoStat,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(AppTheme.dimens.space.xs))
        Text(label.uppercase(), style = AppTheme.type.monoMicro, color = colors.ink3)
    }
}

/** Vertical hairline between stats. */
@Composable
fun WizardStatDivider() {
    Box(
        Modifier
            .width(AppTheme.dimens.size.hairline)
            .height(AppTheme.dimens.size.iconXl)
            .background(AppTheme.colors.lineSoft),
    )
}
