package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Booking compose — package, date chips, time grid, venue/guests.
 * Port of iOS `BookingView`; draft lands in [BookingDraftStore] on Continue.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val radii = AppTheme.dimens.radii

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
            Column(modifier.fillMaxSize().background(colors.bg)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                    }
                    Text(
                        "Book ${artist.name}",
                        style = AppTheme.type.headline,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = space.lg),
                ) {
                    SectionTitle("Pick a package")
                    Spacer(Modifier.height(space.sm))
                    if (artist.packages.isEmpty()) {
                        Text(
                            "Custom · ${formatInr(artist.price)}",
                            style = AppTheme.type.body,
                            color = colors.ink2,
                        )
                    } else {
                        artist.packages.forEachIndexed { index, pkg ->
                            PackagePickerRow(
                                pkg = pkg,
                                selected = index == state.packageIndex,
                                onClick = { viewModel.selectPackage(index) },
                            )
                            Spacer(Modifier.height(space.sm))
                        }
                    }
                    Spacer(Modifier.height(space.xl))
                    SectionTitle("Pick a date")
                    Spacer(Modifier.height(space.sm))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        state.dateChips.forEach { chip ->
                            val selected = chip.epochMs == state.selectedDateEpochMs
                            DateChipView(
                                chip = chip,
                                selected = selected,
                                onClick = { viewModel.selectDate(chip) },
                            )
                        }
                    }
                    Spacer(Modifier.height(space.xl))
                    SectionTitle("Pick a time")
                    Spacer(Modifier.height(space.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(space.sm),
                        verticalArrangement = Arrangement.spacedBy(space.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.timeSlots.forEach { slot ->
                            val selected = slot == state.selectedTime
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(radii.sm))
                                    .background(if (selected) colors.brand else colors.bg)
                                    .border(
                                        1.dp,
                                        if (selected) colors.brand else colors.line,
                                        RoundedCornerShape(radii.sm),
                                    )
                                    .clickable { viewModel.selectTime(slot) }
                                    .padding(horizontal = space.lg, vertical = space.md),
                            ) {
                                Text(
                                    slot,
                                    style = AppTheme.type.monoSmall,
                                    color = if (selected) colors.brandInk else colors.ink,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(space.xl))
                    SectionTitle("Venue & guests")
                    Spacer(Modifier.height(space.sm))
                    Text("Venue", style = AppTheme.type.caption, color = colors.ink3)
                    Spacer(Modifier.height(space.xs))
                    BasicTextField(
                        value = state.venue,
                        onValueChange = viewModel::setVenue,
                        textStyle = AppTheme.type.body.copy(color = colors.ink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radii.sm))
                            .background(colors.bgSoft)
                            .padding(space.md),
                        decorationBox = { inner ->
                            if (state.venue.isEmpty()) {
                                Text("e.g. Hard Rock Café, Bangalore", style = AppTheme.type.body, color = colors.ink3)
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.height(space.lg))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Guests", style = AppTheme.type.caption, color = colors.ink3)
                            Text("${state.guests}", style = AppTheme.type.monoLarge, color = colors.ink)
                        }
                        PrimaryButton(
                            text = "−",
                            onClick = { viewModel.setGuests(state.guests - 10) },
                            variant = ButtonVariant.Ghost,
                        )
                        Spacer(Modifier.padding(horizontal = space.xs))
                        PrimaryButton(
                            text = "+",
                            onClick = { viewModel.setGuests(state.guests + 10) },
                            variant = ButtonVariant.Ghost,
                        )
                    }
                    Spacer(Modifier.height(space.lg))
                    Text("Directions / notes", style = AppTheme.type.caption, color = colors.ink3)
                    Spacer(Modifier.height(space.xs))
                    BasicTextField(
                        value = state.venueNotes,
                        onValueChange = viewModel::setVenueNotes,
                        textStyle = AppTheme.type.body.copy(color = colors.ink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radii.sm))
                            .background(colors.bgSoft)
                            .padding(space.md),
                    )
                    Spacer(Modifier.height(space.xl))
                    HRule()
                    Spacer(Modifier.height(space.lg))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Artist fee", style = AppTheme.type.callout, color = colors.ink2)
                        Text(formatInr(fee), style = AppTheme.type.monoMedium, color = colors.ink)
                    }
                    Spacer(Modifier.height(space.xxl))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.bgElev)
                        .padding(space.lg),
                ) {
                    PrimaryButton(
                        text = "Continue",
                        onClick = {
                            if (viewModel.onContinue()) onContinue()
                        },
                        fullWidth = true,
                        enabled = state.canContinue && state.selectedTime.isNotBlank(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = AppTheme.type.headline, color = AppTheme.colors.ink)
}

@Composable
private fun PackagePickerRow(pkg: ArtistPackage, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(if (selected) colors.brandSoft else colors.bg)
            .border(
                1.dp,
                if (selected) colors.brand else colors.line,
                RoundedCornerShape(AppTheme.dimens.radii.md),
            )
            .clickable(onClick = onClick)
            .padding(space.lg),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pkg.name, style = AppTheme.type.callout, color = colors.ink)
            Text(formatInr(pkg.price), style = AppTheme.type.monoSmall, color = colors.ink)
        }
        Text(pkg.duration, style = AppTheme.type.caption, color = colors.ink3)
        if (pkg.popular) {
            Spacer(Modifier.height(space.xs))
            Pill("Popular", tone = PillTone.Brand)
        }
    }
}

@Composable
private fun DateChipView(chip: DateChip, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val radii = AppTheme.dimens.radii
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .clip(RoundedCornerShape(radii.sm))
            .background(if (selected) colors.brand else colors.bg)
            .border(1.dp, if (selected) colors.brand else colors.line, RoundedCornerShape(radii.sm))
            .clickable(enabled = chip.available, onClick = onClick)
            .padding(horizontal = space.md, vertical = space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            chip.weekdayAbbrev,
            style = AppTheme.type.caption,
            color = when {
                !chip.available -> colors.ink4
                selected -> colors.brandInk
                else -> colors.ink3
            },
        )
        Text(
            chip.label.substringAfter(", ").take(6),
            style = AppTheme.type.monoSmall,
            color = when {
                !chip.available -> colors.ink4
                selected -> colors.brandInk
                else -> colors.ink
            },
        )
    }
}
