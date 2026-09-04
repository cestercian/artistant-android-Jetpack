package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Screen 17 — "Request a quote", the brief.
 *
 * The design's note: *six fields is everything an artist needs to price a night
 * — the reason replies land in an hour.* So the page is those six over a
 * free-text brief, and nothing else: no summary, no fee maths, no chrome.
 *
 * Two of the six have no column on `gig_requests` (occasion, start time) and
 * travel in the message instead — see [quoteBriefMessage], which is where that
 * decision lives and is unit-tested. Budget is a single amount rather than the
 * design's range, because `proposed_amount_inr` is one integer and flattening a
 * range into it silently would be worse than asking for the number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestQuoteScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestQuoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val gutter = dimens.component.gutter

    if (state.success) {
        QuoteSent(
            artistName = state.artistName,
            replyLabel = state.replyLabel,
            onDone = onSuccess,
            modifier = modifier,
        )
        return
    }

    var showDates by remember { mutableStateOf(false) }
    var showTimes by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        FunnelBar(
            title = "Request a quote",
            subtitle = quoteReplyLine(state.artistName, state.replyLabel),
            onLeading = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
                .padding(top = space.lg, bottom = space.xl),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            state.errorMessage?.let { message ->
                Banner(title = message, tone = BannerTone.Failure)
            }

            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                FieldLabel("What's the occasion?")
                // Wrapped, not a scrolling rail: four options fit and an option
                // that scrolls out of sight is an option nobody picks.
                Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    QuoteOccasions.chunked(OCCASION_COLUMNS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                            row.forEach { option ->
                                Chip(
                                    label = option,
                                    selected = state.occasion == option,
                                    onClick = { viewModel.selectOccasion(option) },
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                PickerField(
                    label = "Date",
                    value = state.dateLabel,
                    hint = "Pick a date",
                    icon = Icons.Filled.CalendarMonth,
                    onClick = { showDates = true },
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    label = "Start",
                    value = state.startTime,
                    hint = "Pick a time",
                    icon = Icons.Filled.Schedule,
                    onClick = { showTimes = true },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                AppTextField(
                    value = state.guests,
                    onValueChange = viewModel::setGuests,
                    label = "Guests",
                    hint = "200",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                AppTextField(
                    value = state.venue,
                    onValueChange = viewModel::setVenue,
                    label = "Venue",
                    hint = "Outdoor lawn",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            AppTextField(
                value = state.budgetInr,
                onValueChange = viewModel::setBudget,
                label = "Budget, all-in",
                hint = "35000",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leading = {
                    Text("₹", style = AppTheme.type.body, color = colors.ink3)
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                AppTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    label = "Anything they should know?",
                    hint = "Lawn set-up, power is 20 m from the stage. Two 45-minute sets ideally.",
                    singleLine = false,
                    minHeight = dimens.funnel.notesField,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                )
                Text(
                    "${state.note.length} / $QUOTE_NOTE_MAX",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
        CtaBar {
            PrimaryButton(
                text = if (state.isSubmitting) "Sending request…" else "Send request",
                onClick = viewModel::submit,
                fullWidth = true,
                enabled = !state.isSubmitting,
            )
            CtaCaption("Nothing is charged until you accept a quote")
        }
    }

    if (showDates) {
        val sheet = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showDates = false },
            sheetState = sheet,
            dragHandle = null,
            containerColor = colors.surface,
        ) {
            SheetScaffold(title = "When's the show?") {
                FunnelCalendar(
                    monthLabel = state.monthLabel,
                    days = state.monthDays,
                    selectableDays = state.selectableDays,
                    selectedDay = state.selectedDay,
                    canGoBack = state.canStepBack,
                    onPrevMonth = { viewModel.stepMonth(-1) },
                    onNextMonth = { viewModel.stepMonth(1) },
                    onDay = { day ->
                        viewModel.selectDay(day)
                        showDates = false
                    },
                )
            }
        }
    }

    if (showTimes) {
        val sheet = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showTimes = false },
            sheetState = sheet,
            dragHandle = null,
            containerColor = colors.surface,
        ) {
            SheetScaffold(title = "What time do you start?") {
                Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    state.timeSlots.chunked(TIME_SLOT_COLUMNS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                            row.forEach { slot ->
                                Chip(
                                    label = slot,
                                    selected = state.startTime == slot,
                                    onClick = {
                                        viewModel.selectStartTime(slot)
                                        showTimes = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val OCCASION_COLUMNS = 3
private const val TIME_SLOT_COLUMNS = 3

/**
 * "Saanjh usually replies in < 24h" — or nothing.
 *
 * `artists.response_label` is the artist's own published speed. With no artist
 * loaded there is no claim to make, and a subtitle that says "usually replies in
 * 2 hours" about somebody we could not read is a fact about nobody.
 */
internal fun quoteReplyLine(artistName: String, replyLabel: String): String? {
    val who = artistName.trim()
    val speed = replyLabel.trim()
    if (who.isEmpty() || speed.isEmpty()) return null
    return "$who usually replies in $speed"
}

/** The design's field label: 12.5 semibold `ink4`, over the control it names. */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.ink4,
        modifier = modifier,
    )
}

/**
 * A field that opens a sheet instead of a keyboard — date and start time.
 *
 * Drawn as a text field rather than as a button because that is what it is on the
 * design and what it behaves like: a labelled well holding a value, with a glyph
 * on the trailing edge saying which kind of picker is behind it. Empty, it shows
 * the hint in `hint` ink, exactly as an unfilled [AppTextField] does — a picker
 * that renders empty as a blank box reads as broken.
 */
@Composable
private fun PickerField(
    label: String,
    value: String,
    hint: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.control)
    Column(modifier) {
        FieldLabel(label, Modifier.padding(bottom = dimens.space.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface2)
                .border(dimens.size.hairline, colors.hairline, shape)
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = dimens.component.control)
                .padding(horizontal = dimens.space.lg, vertical = dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            Text(
                value.ifBlank { hint },
                style = AppTheme.type.body,
                color = if (value.isBlank()) colors.hint else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                icon,
                contentDescription = null,
                tint = colors.ink4,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
    }
}

/**
 * What the host sees once the request lands.
 *
 * Says the outcome rather than "success", the same rule screens 07 and 94
 * follow: the request is with the artist and the host will be told when they
 * answer. The reply speed is repeated here because it is the one thing they now
 * want to know.
 */
@Composable
private fun QuoteSent(
    artistName: String,
    replyLabel: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val who = artistName.trim().ifEmpty { "The artist" }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = dimens.component.gutter)
                .padding(top = space.xxl),
        ) {
            OutcomeMark()
            Text(
                "Your brief is with $who.",
                style = AppTheme.type.displayHero,
                color = colors.ink,
                modifier = Modifier.padding(top = space.xl),
            )
            Text(
                if (replyLabel.isBlank()) {
                    "We'll notify you the moment they answer."
                } else {
                    "They usually reply in ${replyLabel.trim()} — we'll notify you the " +
                        "moment they answer."
                },
                style = AppTheme.type.body,
                color = colors.ink4,
                modifier = Modifier.padding(top = space.md),
            )
        }
        CtaBar {
            PrimaryButton(text = "Done", onClick = onDone, fullWidth = true)
        }
    }
}
