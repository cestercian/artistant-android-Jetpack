package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.designsystem.component.DateCell
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.dateChipLines
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.PackagePricing

/**
 * Booking compose — package, date strip, time grid, venue and guests.
 *
 * The page is a run of small-caps section labels over the controls they name;
 * the only card chrome on it belongs to things the client picks between (the
 * package tiers, the date cards) and to the one grouped form block at the
 * bottom. Everything the client chose lands in [BookingDraftStore] on Continue,
 * which is what Checkout sends — this screen holds the whole request.
 */
@Composable
fun BookingScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    when {
        state.isLoading && state.artist == null -> {
            Box(modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
        }
        state.artist == null -> {
            Column(modifier.fillMaxSize().background(colors.bg)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                }
                EmptyState(
                    title = "Artist not found",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }
        }
        else -> {
            val artist = state.artist!!
            val selectedPkg = artist.packages.getOrNull(state.packageIndex)
            val fee = selectedPkg?.price ?: artist.price
            RevealOnAppear {
                Column(modifier.fillMaxSize().background(colors.bg)) {
                    FunnelHeader(title = "Book ${artist.name}", onBack = onBack)
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = space.md, bottom = space.xl),
                        verticalArrangement = Arrangement.spacedBy(space.xl),
                    ) {
                        PackageSection(
                            packages = artist.packages,
                            fallbackPrice = artist.price,
                            selectedIndex = state.packageIndex,
                            onSelect = viewModel::selectPackage,
                        )
                        DateSection(
                            chips = state.dateChips,
                            selectedEpochMs = state.selectedDateEpochMs,
                            onSelect = viewModel::selectDate,
                        )
                        TimeSection(
                            slots = state.timeSlots,
                            selected = state.selectedTime,
                            onSelect = viewModel::selectTime,
                        )
                        VenueSection(
                            venue = state.venue,
                            onVenueChange = viewModel::setVenue,
                            guests = state.guests,
                            onGuestsChange = viewModel::setGuests,
                            notes = state.venueNotes,
                            onNotesChange = viewModel::setVenueNotes,
                        )
                        SummarySection(fee = fee)
                    }
                    CtaBar {
                        FunnelCta(
                            text = "Continue",
                            onClick = { if (viewModel.onContinue()) onContinue() },
                            fullWidth = true,
                            enabled = state.canContinue && state.selectedTime.isNotBlank(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section header + body, on the page grid.
 *
 * Every section on this screen is the same shape, so it is one composable: the
 * label is a small-caps caption in `ink3`, never a sentence-case headline. The
 * label naming a control has to sit under the control it belongs to in the
 * visual hierarchy — a headline-weight title competes with the package names and
 * the time slots, which are the things being read.
 *
 * [bleed] drops the horizontal page padding for the body so a horizontally
 * scrolling strip can run to both screen edges while its label stays on grid.
 */
@Composable
private fun Section(
    label: String,
    modifier: Modifier = Modifier,
    bleed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val space = AppTheme.dimens.space
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionLabel(label, modifier = Modifier.padding(horizontal = space.lg))
        if (bleed) {
            content()
        } else {
            Box(Modifier.padding(horizontal = space.lg)) { content() }
        }
    }
}

@Composable
private fun PackageSection(
    packages: List<ArtistPackage>,
    fallbackPrice: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val space = AppTheme.dimens.space
    Section("Pick a package") {
        if (packages.isEmpty()) {
            Text(
                "Custom · ${formatInr(fallbackPrice)}",
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
            )
        } else {
            // Whole-set question, asked once: rows already on the server carry
            // `popular = true` on every package, and a badge every row shares
            // distinguishes nothing.
            val badgesMeanSomething = PackagePricing.popularBadgeIsMeaningful(packages)
            // `optionCardGap`, not `space.md`: the cards in a picker are one
            // block of choices, and the reference stacks them two units tighter
            // than the ramp's nearest step. Paired with `optionCardPad` — the
            // two together are what the reference's row pitch is made of.
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.size.optionCardGap)) {
                packages.forEachIndexed { index, pkg ->
                    PackageOptionRow(
                        pkg = pkg,
                        selected = index == selectedIndex,
                        showPopularBadge = badgesMeanSomething && pkg.popular,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

/**
 * The date strip: a legend, then a run of portrait date cards that bleeds to
 * both screen edges so a partly-visible card advertises the scroll.
 *
 * The cards come from [DateCell] rather than a local lookalike, and their two
 * lines come from [dateChipLines] rather than from slicing a rendered label —
 * the shipped version carved the day line out with `substringAfter(", ").take(6)`,
 * which truncated real dates.
 */
@Composable
private fun DateSection(chips: List<DateChip>, selectedEpochMs: Long, onSelect: (DateChip) -> Unit) {
    val space = AppTheme.dimens.space
    Section("Pick a date", bleed = true) {
        Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
            AvailabilityLegend(Modifier.padding(horizontal = space.lg))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = space.lg),
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                chips.forEach { chip ->
                    DateCell(
                        lines = dateChipLines(chip.epochMs),
                        isSelected = chip.epochMs == selectedEpochMs,
                        isFree = chip.available,
                        enabled = chip.available,
                        onClick = { onSelect(chip) },
                    )
                }
            }
        }
    }
}

/**
 * Time slots as a three-across grid of equal-width mono pills.
 *
 * Rows are chunked by hand rather than delegating to a lazy grid because this
 * screen is one long scroll and a nested scrollable inside it cannot measure.
 * `weight(1f)` per cell is what makes the columns line up; a `FlowRow` of
 * intrinsic-width pills produced a ragged right edge on a screen whose whole
 * rhythm is the page grid.
 */
@Composable
private fun TimeSection(slots: List<String>, selected: String, onSelect: (String) -> Unit) {
    val space = AppTheme.dimens.space
    Section("Pick a time") {
        Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
            slots.chunked(TIME_COLUMNS).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    row.forEach { slot ->
                        TimePill(
                            slot = slot,
                            selected = slot == selected,
                            onClick = { onSelect(slot) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keep the last row's cells the same width as a full row's
                    // instead of letting two slots stretch across three columns.
                    repeat(TIME_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val TIME_COLUMNS = 3

@Composable
private fun TimePill(
    slot: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.md)
    Box(
        modifier
            .clip(shape)
            // Unselected is transparent, not a filled surface: a grid of filled
            // chips gives every slot the weight of the chosen one.
            .background(if (selected) colors.brand else Color.Transparent)
            .border(
                dimens.size.hairline,
                if (selected) Color.Transparent else colors.lineSoft,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = dimens.space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            slot,
            style = AppTheme.type.monoChip,
            color = if (selected) colors.brandInk else colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Venue, head count and directions, grouped inside one card.
 *
 * These three are the only free-text/number entry on the screen, and grouping
 * them is what stops the page reading as an endless form: everything above is a
 * choice between rendered options, this is the part you type. The card is the
 * boundary of "your details".
 */
@Composable
private fun VenueSection(
    venue: String,
    onVenueChange: (String) -> Unit,
    guests: Int,
    onGuestsChange: (Int) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Section("Venue & guests") {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radii.lg))
                .background(colors.bgCard)
                .padding(space.lg),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            FieldLabel("Venue")
            SunkenTextField(
                value = venue,
                onValueChange = onVenueChange,
                placeholder = "e.g. Hard Rock Café, Bangalore",
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs)) {
                    FieldLabel("Guests")
                    Text("$guests", style = AppTheme.type.monoLarge, color = colors.ink)
                }
                GuestStepper(
                    onDecrement = { onGuestsChange(guests - GUEST_STEP) },
                    onIncrement = { onGuestsChange(guests + GUEST_STEP) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("Directions for the artist", modifier = Modifier.weight(1f))
                    // Live counter on a bounded field — the cap is enforced in
                    // the setter, so the count is the only warning a paste gets.
                    Text(
                        "${notes.length}/$NOTES_MAX",
                        style = AppTheme.type.monoMicroSoft,
                        color = colors.ink3,
                    )
                }
                SunkenTextField(
                    value = notes,
                    onValueChange = { onNotesChange(it.take(NOTES_MAX)) },
                    placeholder = "Gate, parking, load-in… (optional)",
                    minLines = 2,
                )
            }
        }
    }
}

/** Guests move in tens: a 100-guest party is not booked one head at a time. */
private const val GUEST_STEP = 10

/** Matches the server column's bound, enforced in the setter so a paste can't exceed it. */
private const val NOTES_MAX = 500

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.ink3, modifier = modifier)
}

/**
 * An input well: a darker fill inside the card, no border. The card already drew
 * the boundary — a second outline inside it is chrome on chrome.
 */
@Composable
private fun SunkenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = AppTheme.type.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.brand),
        minLines = minLines,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.bgSoft)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.md),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, style = AppTheme.type.body, color = colors.ink3)
            }
            inner()
        },
    )
}

/**
 * A joined −/+ segmented control: one capsule split by a hairline, not two
 * separate buttons. Two buttons read as two unrelated actions; the joined shape
 * reads as one value being nudged, which is what it is.
 */
@Composable
private fun GuestStepper(onDecrement: () -> Unit, onIncrement: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .height(IntrinsicSize.Min)
            .clip(CircleShape)
            .background(colors.bgSoft),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(Icons.Filled.Remove, "Fewer guests", onDecrement)
        Box(
            Modifier
                .fillMaxHeight()
                .padding(vertical = dimens.space.sm)
                .width(dimens.size.hairline)
                .background(colors.line),
        )
        StepperButton(Icons.Filled.Add, "More guests", onIncrement)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .size(width = dimens.size.controlMin, height = dimens.size.rowMin)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = AppTheme.colors.ink,
            modifier = Modifier.size(dimens.size.iconLg),
        )
    }
}

/**
 * The artist's quoted fee, and only that. v1 is a matchmaker: there is no
 * platform fee or GST line to show, because there is no payment behind it.
 */
@Composable
private fun SummarySection(fee: Int) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.padding(horizontal = space.lg),
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        HRule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Artist fee", style = AppTheme.type.callout, color = colors.ink2)
            Text(formatInr(fee), style = AppTheme.type.monoMedium, color = colors.brand)
        }
    }
}
