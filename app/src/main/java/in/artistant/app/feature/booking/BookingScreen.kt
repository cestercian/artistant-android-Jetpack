package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.designsystem.component.CardView
import `in`.artistant.app.designsystem.component.DateScroller
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Booking-funnel compose screen (port of iOS `BookingView`). Package radio picker,
 * a [DateScroller] over the artist's real availability, a preferred-slot time
 * grid, venue + guests, and a fee-only summary (v1 hides platform/GST). Continue
 * → Checkout ([onCheckout]).
 */
@Composable
fun BookingScreen(
    onBack: () -> Unit,
    onCheckout: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        FunnelHeader("Book ${artist?.name ?: ""}", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = space.lg),
            verticalArrangement = Arrangement.spacedBy(space.xl),
        ) {
            Spacer(Modifier.height(space.xs))

            // Package picker.
            val a = artist
            if (a != null && draft != null) {
                Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                    SectionLabel("Pick a package")
                    a.packages.forEachIndexed { idx, pkg ->
                        PackageRow(pkg, selected = idx == draft!!.packageIndex) { viewModel.setPackage(idx) }
                    }
                }
            }

            // Date.
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionLabel("Pick a date")
                DateScroller(
                    selected = draft?.dateRaw ?: java.time.LocalDate.now(),
                    onSelect = viewModel::setDate,
                    daysAvailable = artist?.daysAvailable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Time — 3-column grid built from chunked rows (a LazyGrid can't nest
            // inside a vertical scroll).
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionLabel("Pick a time")
                viewModel.timeSlots().chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                        row.forEach { slot ->
                            TimeCell(slot, selected = draft?.time == slot, Modifier.weight(1f)) {
                                viewModel.setTime(slot)
                            }
                        }
                        // Pad a short final row so cells keep their column width.
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // Venue + guests.
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionLabel("Venue & guests")
                CardView {
                    OutlinedTextField(
                        value = draft?.venue ?: "",
                        onValueChange = viewModel::setVenue,
                        label = { Text("Venue", color = colors.ink3) },
                        placeholder = { Text("e.g. Hard Rock Café, Bangalore", color = colors.ink4) },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(space.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Guests")
                            Text(
                                "${draft?.guests ?: 0}",
                                style = AppTheme.type.monoLarge,
                                color = colors.ink,
                            )
                        }
                        Stepper(
                            value = draft?.guests ?: 100,
                            onChange = viewModel::setGuests,
                        )
                    }
                }
            }

            // Summary — v1 shows the artist fee only.
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                SectionLabel("Summary")
                CardView {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Artist fee",
                            style = AppTheme.type.callout.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatInr(viewModel.draftFee()),
                            style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.brand,
                        )
                    }
                }
            }
            Spacer(Modifier.height(space.lg))
        }

        CtaBar {
            PrimaryButton(
                text = "Continue →",
                onClick = onCheckout,
                fullWidth = true,
                enabled = draft != null,
            )
        }
    }
}

@Composable
private fun PackageRow(pkg: ArtistPackage, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val shape = RoundedCornerShape(AppTheme.dimens.radii.md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.brandSoft else Color.Transparent)
            .border(1.dp, if (selected) colors.brand else colors.lineSoft, shape)
            .clickable(onClick = onClick)
            .padding(space.md),
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) colors.brand else colors.lineSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.brand))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Text(
                    pkg.name,
                    style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (pkg.popular) Pill("Popular", tone = PillTone.Brand)
                Spacer(Modifier.weight(1f))
                Text(
                    formatInr(pkg.price),
                    style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
            }
            Text(
                "${pkg.duration} · ${pkg.includes.joinToString(" · ")}",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        }
    }
}

@Composable
private fun TimeCell(slot: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.dimens.radii.sm)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) colors.brand else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else colors.lineSoft, shape)
            .clickable(onClick = onClick)
            .padding(vertical = AppTheme.dimens.space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            slot,
            style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.brandInk else colors.ink,
        )
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−") { onChange(value - 10) }
        StepButton("+") { onChange(value + 10) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.bgSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppTheme.type.title, color = colors.ink)
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppTheme.colors.ink,
    unfocusedTextColor = AppTheme.colors.ink,
    focusedBorderColor = AppTheme.colors.brand,
    unfocusedBorderColor = AppTheme.colors.line,
    cursorColor = AppTheme.colors.brand,
    focusedContainerColor = AppTheme.colors.bgSoft,
    unfocusedContainerColor = AppTheme.colors.bgSoft,
)
