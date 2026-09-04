package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Screen 05 — "When's the show?", step one of the two-step booking funnel.
 *
 * The design's note is the whole shape of this page: **the calendar is the source
 * of truth.** Artists maintain availability in their studio, so a host never even
 * sees a dead date offered — closed days are drawn dim and are inert, and the
 * grid opens on the first month the artist actually has something in.
 *
 * The old screen asked the same questions as a run of small-caps labels over a
 * horizontally scrolling 14-day strip. Two things changed and both are the
 * design's:
 *
 * - **A month, not a fortnight.** A strip can only offer what fits in two weeks,
 *   which is the wrong horizon for a wedding. The grid can be stepped, and the
 *   step is bounded at the current month because there is nothing to book behind
 *   it.
 * - **The step count is the chrome.** "Step 1 of 2" sits in the bar and the real
 *   headline is in the scroll, so the funnel reads as a form you are partway
 *   through rather than as a page called "Book".
 *
 * Time, venue and guests stay on this step. The design draws 05 as date +
 * package and 06 as a read-only review, so these three have nowhere else to be
 * asked — and `bookings.start_datetime` is composed from the start time, so it is
 * not optional. They sit under the package list, in the design's field style.
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
    val dimens = AppTheme.dimens
    val space = dimens.space
    val gutter = dimens.component.gutter

    when {
        state.isLoading && state.artist == null -> {
            Box(
                modifier
                    .fillMaxSize()
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accentInk)
            }
        }
        state.artist == null -> {
            Column(
                modifier
                    .fillMaxSize()
                    .background(colors.surface),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = gutter, vertical = space.sm),
                ) {
                    IconCircle(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        size = dimens.component.iconCircleSm,
                    )
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
                Column(
                    modifier
                        .fillMaxSize()
                        .background(colors.surface),
                ) {
                    FunnelStepBar(step = "Step 1 of 2", onClose = onBack)
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = gutter)
                            .padding(top = space.lg, bottom = space.xl),
                    ) {
                        Text(
                            "When's the show?",
                            style = AppTheme.type.screenTitle,
                            color = colors.ink,
                        )
                        Text(
                            // Says which fact the dimming carries. "Greyed dates"
                            // with no sentence is a colour nobody can read.
                            "Greyed dates are already booked out.",
                            style = AppTheme.type.subtitle,
                            color = colors.ink4,
                            modifier = Modifier.padding(top = space.sm / 2),
                        )
                        FunnelCalendar(
                            monthLabel = state.monthLabel,
                            days = state.monthDays,
                            selectableDays = state.selectableDays,
                            selectedDay = state.selectedDay,
                            canGoBack = state.canStepBack,
                            onPrevMonth = { viewModel.stepMonth(-1) },
                            onNextMonth = { viewModel.stepMonth(1) },
                            onDay = viewModel::selectDay,
                            modifier = Modifier.padding(top = space.xl),
                        )
                        if (state.selectableDays.isEmpty()) {
                            // Loaded-and-nothing-open is not the same as
                            // still-loading, and an empty month with no sentence
                            // reads as a broken grid. Both the fact and the way
                            // out (step forward) are stated.
                            Text(
                                "${artist.name} has nothing open in ${state.monthLabel}. " +
                                    "Try the next month.",
                                style = AppTheme.type.subtitle,
                                color = colors.ink3,
                                modifier = Modifier.padding(top = space.md),
                            )
                        }

                        SectionTitle("Package", Modifier.padding(top = space.xl))
                        PackageList(
                            packages = artist.packages,
                            fallbackPrice = artist.price,
                            selectedIndex = state.packageIndex,
                            onSelect = viewModel::selectPackage,
                            modifier = Modifier.padding(top = space.md),
                        )

                        SectionTitle("Start time", Modifier.padding(top = space.xl))
                        TimeSlots(
                            slots = state.timeSlots,
                            selected = state.selectedTime,
                            onSelect = viewModel::selectTime,
                            modifier = Modifier.padding(top = space.md),
                        )

                        SectionTitle("Venue & guests", Modifier.padding(top = space.xl))
                        AppTextField(
                            value = state.venue,
                            onValueChange = viewModel::setVenue,
                            label = "Venue",
                            hint = "e.g. Hard Rock Café, Bengaluru",
                            modifier = Modifier.padding(top = space.md),
                        )
                        GuestsRow(
                            guests = state.guests,
                            onChange = viewModel::setGuests,
                            modifier = Modifier.padding(top = space.lg),
                        )
                        AppTextField(
                            value = state.venueNotes,
                            onValueChange = viewModel::setVenueNotes,
                            label = "Anything they should know?",
                            hint = "Gate, parking, load-in… (optional)",
                            singleLine = false,
                            minHeight = dimens.funnel.notesField,
                            modifier = Modifier.padding(top = space.lg),
                        )
                        Text(
                            // Live count on a field the ViewModel bounds — the
                            // cap is enforced in the setter, so this is the only
                            // warning a paste gets.
                            "${state.venueNotes.length} / $VENUE_NOTES_MAX",
                            style = AppTheme.type.footnote,
                            color = AppTheme.colors.ink3,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = space.sm),
                        )
                    }
                    CtaBar {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(space.md),
                        ) {
                            Text(
                                bookingSummaryLine(
                                    dateLabel = state.selectedDateLabel,
                                    time = state.selectedTime,
                                    duration = selectedPkg?.duration.orEmpty(),
                                ),
                                style = AppTheme.type.subtitle,
                                color = colors.ink4,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatInr(fee),
                                style = AppTheme.type.subtitle.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.ink,
                                maxLines = 1,
                            )
                        }
                        PrimaryButton(
                            text = "Request this date",
                            onClick = { if (viewModel.onContinue()) onContinue() },
                            fullWidth = true,
                            enabled = state.canContinue,
                            modifier = Modifier.padding(top = space.md),
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Sat 12 Oct · 8:00 pm · 90 min" — the dock's reminder of what is about to be
 * asked for.
 *
 * Blank parts are dropped rather than joined, so a state with no date yet reads
 * as the parts that ARE decided instead of as a line of dangling separators. An
 * empty line falls back to the instruction, because a dock with a live price and
 * no words above it invites the tap the disabled CTA is about to refuse.
 */
internal fun bookingSummaryLine(dateLabel: String, time: String, duration: String): String {
    val parts = listOf(dateLabel, time, duration).map { it.trim() }.filter { it.isNotEmpty() }
    return if (parts.isEmpty()) "Pick a date to continue" else parts.joinToString(" · ")
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.sectionTitle, color = AppTheme.colors.ink, modifier = modifier)
}

/** The design's field label: 12.5 semibold `ink4`, sitting on its own control. */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.ink4,
        modifier = modifier,
    )
}

@Composable
private fun PackageList(
    packages: List<ArtistPackage>,
    fallbackPrice: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    if (packages.isEmpty()) {
        // An artist with no published tier still has a price, and the funnel
        // still sends at it — the row states that rather than leaving a gap
        // where the choice would be.
        Text(
            "Custom · ${formatInr(fallbackPrice)}",
            style = AppTheme.type.body,
            color = AppTheme.colors.ink2,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.size.optionCardGap),
    ) {
        packages.forEachIndexed { index, pkg ->
            PackageChoiceRow(
                name = pkg.name,
                includes = packageIncludesLine(pkg),
                price = pkg.price,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

/**
 * The artist's published start times as chips.
 *
 * A wrapping row of the library [Chip] rather than the old three-across grid of
 * bordered pills: the light design has exactly one chip shape, and a second one
 * that exists only here would be a fork of it. The list is short and
 * artist-published, so it wraps rather than scrolls — a slot that scrolled out of
 * sight is a slot nobody picks.
 */
@Composable
private fun TimeSlots(
    slots: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = AppTheme.dimens.space
    if (slots.isEmpty()) {
        Text(
            "No start times left today — pick another date.",
            style = AppTheme.type.subtitle,
            color = AppTheme.colors.ink3,
            modifier = modifier,
        )
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.sm)) {
        slots.chunked(TIME_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                row.forEach { slot ->
                    Chip(
                        label = slot,
                        selected = slot == selected,
                        onClick = { onSelect(slot) },
                    )
                }
            }
        }
    }
}

private const val TIME_COLUMNS = 3

/**
 * Head count: the number, and a joined −/+ capsule to nudge it.
 *
 * One capsule split by a hairline, not two buttons: two buttons read as two
 * unrelated actions, and the joined shape reads as one value being nudged, which
 * is what it is.
 */
@Composable
private fun GuestsRow(guests: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.space.xs)) {
            FieldLabel("Guests")
            Text("$guests", style = AppTheme.type.sectionTitle, color = colors.ink)
        }
        Row(
            Modifier
                .height(IntrinsicSize.Min)
                .clip(CircleShape)
                .background(colors.surface2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(Icons.Filled.Remove, "Fewer guests") { onChange(guests - GUEST_STEP) }
            Box(
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = dimens.space.sm)
                    .width(dimens.size.hairline)
                    .background(colors.hairline),
            )
            StepperButton(Icons.Filled.Add, "More guests") { onChange(guests + GUEST_STEP) }
        }
    }
}

/** Guests move in tens: a 100-guest party is not booked one head at a time. */
private const val GUEST_STEP = 10

@Composable
private fun StepperButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .size(width = dimens.size.controlMin, height = dimens.size.rowMin)
            .clip(CircleShape)
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
