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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.SectionHeader
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.epk.PackageRow
import `in`.artistant.app.feature.epk.TECH_PRESETS
import `in`.artistant.app.feature.epk.packageRowBlocker
import `in`.artistant.app.feature.epk.packageRowIsPartiallyFilled
import `in`.artistant.app.feature.epk.popularBadgeWouldMeanSomething

/**
 * The five required steps: who you are, where you play, what you charge, what
 * you need from the venue, and when you're open.
 *
 * They are the ones a client's search result and booking sheet read from, which
 * is why all five gate the Continue button while the optional steps do not.
 */

// ── Identity (screen 37) ─────────────────────────────────────────────────────

fun LazyListScope.identityStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "identity.stageName") {
        AppTextField(
            value = state.stageName,
            onValueChange = vm::onStageNameChanged,
            label = "Stage name",
            hint = "The Tilt Collective",
            modifier = Modifier.semantics { testTag = "wizard.identity.stageName" },
        )
    }
    item(key = "identity.handle") { HandleField(state, vm) }
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
        AppTextField(
            value = state.genre,
            onValueChange = vm::onGenreChanged,
            label = "Genre",
            hint = "Indie folk",
            modifier = Modifier.semantics { testTag = "wizard.identity.genre" },
        )
    }
    item(key = "identity.seedNote") {
        // The step-1-feeds-step-3 promise, made where the category is chosen
        // rather than discovered on the pricing step. Named tiers rather than a
        // market rate: the seed is ours, and the number is editable.
        val band = pricingBandFor(state.category)
        Banner(
            title = "Category seeds your pricing tiers.",
            tone = BannerTone.Info,
            detail = if (band == null) {
                "Pick one and step 3 opens with tiers to edit instead of an empty form."
            } else {
                "Step 3 opens with ${band.tiers} tiers between " +
                    "${formatInr(band.low)} and ${formatInr(band.high)}. Every number stays editable."
            },
        )
    }
}

/**
 * The handle field, plus whatever the availability check currently knows.
 *
 * The verdict shows up in two places and each carries a different half of the
 * message: the trailing tick or cross is the answer, and the error line under
 * the field is the only one that says what to do about it. A passing or
 * in-flight check stays quiet below the field, because one that narrates every
 * keystroke is noise.
 *
 * The address under the field is not decoration either — it is the first time
 * the handle is shown as somewhere a client can go, which is what stops artists
 * treating it as a username they will never see again.
 */
@Composable
private fun HandleField(state: WizardUiState, vm: WizardViewModel) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column {
        AppTextField(
            value = state.handle,
            onValueChange = vm::onHandleChanged,
            label = "Handle",
            hint = "yourname",
            error = wizardHandleHint(state.handleStatus),
            leading = {
                Text("@", style = AppTheme.type.body, color = colors.ink4)
            },
            trailing = { HandleStatusIndicator(state.handleStatus) },
            modifier = Modifier.semantics { testTag = "wizard.identity.handle" },
        )
        wizardPublicAddress(state.handle)?.let { address ->
            Spacer(Modifier.height(dimens.space.sm))
            Text(address, style = AppTheme.type.caption, color = colors.ink4)
        }
    }
}

@Composable
private fun HandleStatusIndicator(status: WizardHandleStatus) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    when (status) {
        WizardHandleStatus.Checking -> CircularProgressIndicator(
            color = colors.ink4,
            strokeWidth = dimens.size.hairline,
            modifier = Modifier.size(dimens.size.iconMd),
        )
        WizardHandleStatus.Available -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
            modifier = Modifier.semantics { contentDescription = "Handle available" },
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.accentInk,
                modifier = Modifier.size(dimens.size.iconMd),
            )
            Text(
                "Available",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.accentInk,
            )
        }
        WizardHandleStatus.Taken -> Icon(
            Icons.Filled.Close,
            contentDescription = "Handle taken",
            tint = colors.danger,
            modifier = Modifier.size(dimens.size.iconMd),
        )
        // Empty / Invalid / Error carry no verdict worth a glyph: Invalid is
        // covered by the error line, and Error deliberately reads as "unknown,
        // carry on" — a network blip must not look like "someone owns this".
        WizardHandleStatus.Empty, WizardHandleStatus.Invalid, WizardHandleStatus.Error -> Unit
    }
}

// ── Location (screen 38) ─────────────────────────────────────────────────────

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
    item(key = "location.radius") {
        WizardChipSection(
            title = "Travel radius",
            options = WizardTravelRadii.map(::travelRadiusLabel),
            isSelected = { it == travelRadiusLabel(state.travelRadiusKm) },
            onToggle = { label ->
                WizardTravelRadii.firstOrNull { travelRadiusLabel(it) == label }
                    ?.let(vm::onTravelRadiusSelected)
            },
            tag = "wizard.location.radius",
        )
    }
    item(key = "location.eventTypes") {
        WizardChipSection(
            title = "Event types you play",
            options = WizardEventTypes,
            isSelected = { it in state.eventTypes },
            onToggle = vm::toggleEventType,
            tag = "wizard.location.eventTypes",
        )
    }
    item(key = "location.note") {
        // Honest about the seam. Base city IS published and IS what search
        // filters on; the radius has no column on this backend and this client
        // has no writer for `event_types`. Saying "these are the filters hosts
        // search with" and leaving it there would have the artist believe two
        // answers reach their profile that do not.
        Banner(
            title = "Base city is what hosts search by.",
            tone = BannerTone.Info,
            detail = "Travel is quoted on top of your fee, never taken out of it. " +
                "Radius and event types are saved with your setup — this app doesn't publish them yet.",
        )
    }
}

// ── Pricing (screen 24) ──────────────────────────────────────────────────────

fun LazyListScope.pricingStep(state: WizardUiState, vm: WizardViewModel) {
    // Computed once for the whole list rather than per row: it is a property of
    // the set, and asking it six times per recomposition is six list scans.
    val popularMeansSomething = popularBadgeWouldMeanSomething(state.packageRows)

    item(key = "pricing.header") { SectionHeader(title = "Packages") }
    items(state.packageRows, key = { "pricing.${it.key}" }) { row ->
        PackageRowCard(
            row = row,
            popularMeansSomething = popularMeansSomething,
            canRemove = state.packageRows.size > 1,
            vm = vm,
        )
    }
    item(key = "pricing.add") {
        DashedAction(
            label = if (state.packageRows.size < WIZARD_MAX_PACKAGES) {
                "Add a package"
            } else {
                "Six packages is the maximum"
            },
            enabled = state.packageRows.size < WIZARD_MAX_PACKAGES,
            onClick = vm::addPackageRow,
            tag = "wizard.pricing.addTier",
        )
    }
    item(key = "pricing.seed") {
        val band = pricingBandFor(state.category)
        if (band != null) {
            // What we seeded and where it came from — NOT what other acts
            // charge. There is no market aggregate on this backend, and printing
            // an invented one beside real money the artist is about to publish
            // is the worst place in the app to guess.
            Banner(
                title = "We seeded these from your category.",
                tone = BannerTone.Info,
                detail = "${state.category} starts at ${formatInr(band.low)}–${formatInr(band.high)} " +
                    "in this form. Change every number — hosts only ever see yours.",
            )
        }
    }
}

/**
 * One tier: name and duration on top, the fee big in mono, and the number the
 * host actually sees under a hairline.
 *
 * Both numbers, always. The step's whole premise is that the artist reasons in
 * take-home and the host reasons in total, and a form that shows only one of
 * them makes every artist do the 5%-then-18% arithmetic in their head — or, more
 * often, not do it, and be surprised by their own listing.
 */
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
    val highlighted = row.popular && popularMeansSomething
    val shape = RoundedCornerShape(dimens.radii.card)
    val allIn = packageAllInInr(row.price)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) colors.brandSoft else colors.surface3)
            .border(
                dimens.size.hairline,
                if (highlighted) colors.accent else colors.hairline,
                shape,
            )
            .padding(space.lg)
            .semantics { testTag = "wizard.pricing.row" },
        verticalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                value = row.name,
                onValueChange = { vm.onPackageNameChanged(row.key, it) },
                hint = "Package name",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(space.sm))
            if (canRemove) {
                // Removing the last tier would leave the step ungatable, so the
                // action disappears at one rather than failing on tap.
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove this package",
                    tint = colors.ink4,
                    modifier = Modifier
                        .size(dimens.size.rowMin)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { vm.removePackageRow(row.key) }
                        .padding(space.md),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(
                value = row.duration,
                onValueChange = { vm.onPackageDurationChanged(row.key, it) },
                hint = "60 min",
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = row.price,
                onValueChange = { vm.onPackagePriceChanged(row.key, it) },
                hint = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leading = { Text("₹", style = AppTheme.type.body, color = colors.ink4) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "your fee",
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier.weight(1f),
            )
            WizardToggleChip(
                label = "Most booked",
                selected = row.popular,
                onClick = { vm.onPackagePopularToggled(row.key) },
                tag = "wizard.pricing.popular",
            )
        }
        androidx.compose.material3.HorizontalDivider(
            thickness = dimens.size.hairline,
            color = if (highlighted) colors.accent.copy(alpha = HIGHLIGHT_RULE_ALPHA) else colors.hairline,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Host sees all-in",
                style = AppTheme.type.subtitle,
                color = colors.ink3,
                modifier = Modifier.weight(1f),
            )
            Text(
                // Null means "no price typed yet", which is not the same as a
                // free gig — an em dash says so without claiming a number.
                allIn?.let(::formatInr) ?: "—",
                style = AppTheme.type.monoDock,
                color = colors.ink,
            )
        }
        if (!popularMeansSomething && row.popular) {
            // A badge is a comparison. With one tier, or with all of them
            // flagged, it splits nothing — say so rather than let the artist
            // find out by looking at their own public profile.
            Text(
                "This only stands out once another package isn't marked.",
                style = AppTheme.type.caption,
                color = colors.ink4,
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
                color = if (packageRowIsPartiallyFilled(row)) colors.warm else colors.ink4,
            )
        }
    }
}

/** The rule inside a highlighted card, softened so it reads as a fold, not an edge. */
private const val HIGHLIGHT_RULE_ALPHA = 0.5f

// ── Tech rider (screen 39) ───────────────────────────────────────────────────

/**
 * Presets as checkable rows, anything else as chips.
 *
 * A rider is machine-readable on purpose — the design's note is that a clash can
 * be flagged before the date — and that only holds while the artist is picking
 * from a shared vocabulary. So the presets get the wide, obvious treatment and
 * the free-text field sits under them rather than above, where it would read as
 * the primary way in.
 */
fun LazyListScope.techStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "tech.presetsLabel") { EyebrowLabel("Common requirements") }
    items(TECH_PRESETS, key = { "tech.preset.$it" }) { preset ->
        // Case-insensitive, matching the EPK's rider chips and the add/toggle
        // rules behind them: "4 Vocal Mics" IS the "4 vocal mics" preset, and an
        // exact-match test rendered it unselected while its twin sat in "Yours"
        // — two lines on one rider for one requirement.
        CheckRow(
            label = preset,
            checked = state.techItems.any { it.equals(preset, ignoreCase = true) },
            onToggle = { vm.toggleTechItem(preset) },
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
        Column {
            EyebrowLabel("Anything else")
            Spacer(Modifier.height(AppTheme.dimens.space.sm))
            AppTextField(
                value = state.techDraft,
                onValueChange = vm::onTechDraftChanged,
                hint = "One 16A power point",
                trailing = {
                    Text(
                        "Add",
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = if (state.techDraft.isBlank()) {
                            AppTheme.colors.ink4
                        } else {
                            AppTheme.colors.accentInk
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                            .clickable(
                                enabled = state.techDraft.isNotBlank(),
                                role = Role.Button,
                                onClick = vm::addTechItem,
                            )
                            .padding(AppTheme.dimens.space.sm)
                            .semantics { testTag = "wizard.tech.add" },
                    )
                },
            )
        }
    }
}

/**
 * A preset the venue either provides or does not: a tick box, a label, and the
 * one line that says what "provides" means here.
 *
 * Checkboxes rather than chips for this block because the answers are not a
 * taxonomy the artist is browsing — they are a list of demands, and a venue
 * manager reads them top to bottom.
 */
@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .background(if (checked) colors.brandSoft else colors.surface3)
            .border(dimens.size.hairline, if (checked) colors.accent else colors.hairline, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                haptics.select()
                onToggle()
            }
            .padding(dimens.space.lg)
            .semantics {
                testTag = "wizard.tech.preset"
                contentDescription = "$label${if (checked) ", selected" else ""}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(
            Modifier
                .size(dimens.size.radio)
                .clip(RoundedCornerShape(dimens.radii.sm))
                .background(if (checked) colors.accent else colors.surface)
                .border(
                    dimens.size.hairline,
                    if (checked) colors.accent else colors.hairline,
                    RoundedCornerShape(dimens.radii.sm),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
            }
        }
        Text(
            label,
            style = AppTheme.type.rowTitle,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Availability (screen 40) ─────────────────────────────────────────────────

fun LazyListScope.availabilityStep(state: WizardUiState, vm: WizardViewModel) {
    item(key = "availability.days") {
        Column {
            EyebrowLabel("Days you play")
            Spacer(Modifier.height(AppTheme.dimens.space.md))
            DayStrip(selected = state.daysAvailable, onToggle = vm::toggleDay)
        }
    }
    item(key = "availability.times") {
        WizardChipSection(
            // The stored vocabulary is clock times, because that is what the
            // client's booking grid renders out of `default_time_slots`. The
            // badge below is where they turn into a phrase.
            title = "Usual start times",
            options = WizardTimeSlots,
            isSelected = { it in state.timeSlots },
            onToggle = vm::toggleTimeSlot,
            tag = "wizard.availability.times",
        )
    }
    item(key = "availability.badge") { AvailabilityBadgeCard(state) }
}

/**
 * Seven letters, one per weekday.
 *
 * Letters rather than "Mon"/"Tue" because the row has to fit seven of them
 * across a phone and still leave each one a real tap target; the strip's shape
 * is what makes it read as a week rather than as seven filter chips. The
 * accessible name is the full day, so the abbreviation never reaches a screen
 * reader.
 */
@Composable
private fun DayStrip(selected: Set<String>, onToggle: (String) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val haptics = rememberHaptics()
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
        WizardWeekdays.forEach { day ->
            val isOn = day in selected
            val interaction = remember(day) { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .pressScale(interaction)
                    .clip(RoundedCornerShape(dimens.radii.lg))
                    .background(if (isOn) colors.accent else colors.surface2)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                        haptics.select()
                        onToggle(day)
                    }
                    .semantics {
                        testTag = "wizard.availability.day.$day"
                        contentDescription = "$day${if (isOn) ", selected" else ""}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.take(1),
                    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = if (isOn) colors.onAccent else colors.ink4,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The compressed form, shown while it is still editable.
 *
 * This is the step's whole point: thirteen toggles above collapse into the one
 * pill a search row has space for, and an artist who never sees that collapse
 * has no way to know that Tue/Thu/Sat reads as three separate days while
 * Thu-through-Sun reads as a weekend act. Empty says so plainly rather than
 * hiding — no badge is a real outcome of picking no days.
 */
@Composable
private fun AvailabilityBadgeCard(state: WizardUiState) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val badge = availabilityBadge(state.daysAvailable, state.timeSlots)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .padding(dimens.space.lg)
            .semantics { testTag = "wizard.availability.badge" },
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        EyebrowLabel("How hosts see it")
        if (badge == null) {
            Text(
                "No badge yet — pick at least one day and one start time.",
                style = AppTheme.type.subtitle,
                color = colors.ink3,
            )
        } else {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(colors.accent)
                    .padding(
                        horizontal = dimens.component.chipPadH,
                        vertical = dimens.component.chipPadV,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                Box(
                    Modifier
                        .size(dimens.component.statusDot)
                        .clip(CircleShape)
                        .background(colors.onAccent),
                )
                Text(
                    badge,
                    style = AppTheme.type.chip.copy(fontWeight = FontWeight.Bold),
                    color = colors.onAccent,
                )
            }
        }
    }
}

// ── Shared ───────────────────────────────────────────────────────────────────

/**
 * An eyebrow over a wrapping chip grid — the shape six of these steps share.
 *
 * The chip is the design system's, not a local copy: the wizard and the
 * post-wizard editor are two ways to set the same fields, and a chip that looks
 * one way during setup and another way afterwards reads as two products.
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
    val dimens = AppTheme.dimens
    // The selection tick lives on the shared section rather than on the one step
    // iOS happens to buzz (its identity step). Six steps render this exact grid;
    // a tick on the category chips and silence on the city chips beside them
    // reads as a bug, not as parity.
    val haptics = rememberHaptics()
    Column(modifier.semantics { testTag = tag }) {
        EyebrowLabel(title)
        Spacer(Modifier.height(dimens.space.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            options.forEach { option ->
                val selected = isSelected(option)
                Chip(
                    label = option,
                    selected = selected,
                    onClick = {
                        haptics.select()
                        onToggle(option)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "$option${if (selected) ", selected" else ""}"
                    },
                )
            }
        }
    }
}

/** One chip on its own, for a boolean that is not part of a grid. */
@Composable
fun WizardToggleChip(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    val haptics = rememberHaptics()
    Chip(
        label = label,
        selected = selected,
        onClick = {
            haptics.select()
            onClick()
        },
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = "$label${if (selected) ", selected" else ""}"
        },
    )
}

/**
 * Dashed outline rather than a filled button: adding a row is an option, and a
 * second solid CTA on a screen that already has Continue reads as two equally
 * weighted next steps.
 *
 * Compose has no dashed border primitive that survives a rounded clip cleanly,
 * so this is a hairline in `lineStrong` — one step darker than a divider, which
 * is what a dashed rule reads as at this size anyway.
 */
@Composable
fun DashedAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    Box(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .border(dimens.size.hairline, if (enabled) colors.lineStrong else colors.hairline, shape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = dimens.space.lg)
            .semantics { testTag = tag },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (enabled) "＋  $label" else label,
            style = AppTheme.type.rowTitle,
            color = if (enabled) colors.ink else colors.ink4,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        EyebrowLabel(label, color = colors.ink4)
    }
}

/** Vertical hairline between stats. */
@Composable
fun WizardStatDivider() {
    Box(
        Modifier
            .width(AppTheme.dimens.size.hairline)
            .height(AppTheme.dimens.size.iconXl)
            .background(AppTheme.colors.hairline),
    )
}
