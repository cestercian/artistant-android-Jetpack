package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The month a calendar grid is showing: a year plus a `Calendar.MONTH` (0-based,
 * so it can be handed straight to [MonthDayGrid] and `Calendar.set`).
 *
 * Exists so month navigation is arithmetic on a value rather than mutation of a
 * shared `Calendar` — a `Calendar` in Compose state is a mutable object whose
 * identity never changes, so `add(MONTH, 1)` on it wouldn't recompose anything.
 */
data class CalendarMonth(val year: Int, val month: Int) {

    /**
     * The month [delta] months away (negative steps back).
     *
     * Done in absolute months (`year * 12 + month`) rather than by nudging the
     * month field, because the year boundary is exactly where the naive version
     * breaks: `month + 1` off December yields an out-of-range 12, and `month - 1`
     * off January yields -1. `floorDiv`/`mod` (not `/` and `%`) carry the sign
     * correctly for backwards steps — plain `%` would hand back a negative month.
     */
    fun stepped(delta: Int): CalendarMonth {
        val absoluteMonths = year * 12 + month + delta
        return CalendarMonth(year = absoluteMonths.floorDiv(12), month = absoluteMonths.mod(12))
    }

    /** Midnight on the 1st — what [monthLabelFromEpoch] formats for the header. */
    val firstDayEpochMs: Long
        get() = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }.timeInMillis
}

/** The month containing [nowMs] — where a grid starts before anyone steps it. */
fun currentCalendarMonth(nowMs: Long = System.currentTimeMillis()): CalendarMonth {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    return CalendarMonth(year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH))
}

// ─────────────────────────────────────────────────────────────────────────────
// The five day states (screen 78)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * What a day tile is SAYING, which is what decides its fill.
 *
 * Screen 78 exists to document exactly this, in the designer's words: "Five day
 * states, a month dropdown, and multi-event days" — and its note explains why it
 * is a screen at all rather than a paragraph: "Documenting the shared component
 * is what stops Bookings and Gigs drifting apart."
 *
 * Four fills, plus one ring:
 *
 *  - [Booked] — accent. There is a gig here. The one thing both surfaces exist to
 *    show, so it takes the screen's one accent.
 *  - [Unavailable] — a grey fill. The day is spoken for by something that is not
 *    a booking (an artist's blocked-out date). Deliberately a FILL and not a
 *    strike or an opacity: an unavailable day is as definite as a booked one, and
 *    a faded day reads as "loading".
 *  - [Open] — no fill at all. Most of a month is open, and filling all of it
 *    turns the grid into a wall.
 *  - [Selected] — ink. Near-black beats both accent and grey, which is what lets
 *    the selection sit ON a booked day without either mark being lost.
 *
 * TODAY is not in this list because it is not exclusive with the others: today
 * can be booked, or unavailable, or the day you just tapped. It renders as a
 * ring around whatever fill the day already has (see [MonthDayGrid]), and the
 * ring is dropped only when the day is selected — an ink ring on an ink fill is
 * invisible, and the selection has already answered "which day is this".
 */
enum class MonthDayFill { Open, Booked, Unavailable, Selected }

/**
 * Which fill a day takes, as a pure function so the precedence is testable
 * without a Compose runtime.
 *
 * Precedence is selection → booked → unavailable → open, and the order is the
 * argument: a tap must always be visible (otherwise the grid stops responding to
 * the user), and a booking must out-rank an availability block (an artist who
 * blocked a date and then accepted a gig on it has a gig on it).
 */
fun monthDayFill(booked: Boolean, unavailable: Boolean, selected: Boolean): MonthDayFill = when {
    selected -> MonthDayFill.Selected
    booked -> MonthDayFill.Booked
    unavailable -> MonthDayFill.Unavailable
    else -> MonthDayFill.Open
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Month header. Used two ways: as the grid's title (with [onPrevMonth] /
 * [onNextMonth] wired, which draws the stepper chevrons) and as a plain group
 * header above each month's rows in a list (both null → title only).
 *
 * The light design sets the whole label — month AND year — at one section-title
 * step with a caret after it (screen 78). The dark design split them, month in a
 * 26sp serif and year in mono beside it; that made a calendar's masthead the
 * largest thing on a page whose actual subject is the list under it.
 */
@Composable
fun MonthCalendarHeader(
    monthLabel: String,
    modifier: Modifier = Modifier,
    onPrevMonth: (() -> Unit)? = null,
    onNextMonth: (() -> Unit)? = null,
    /**
     * Jump straight to a month of the DISPLAYED year (0-based, `Calendar.MONTH`).
     *
     * Null keeps the month name inert, which is what the plain group-header use
     * below the grid wants. Wired, the name becomes a menu — because stepping is
     * fine for "next month" and useless for "March": from August that is five
     * taps, and the steppers give no indication they would ever get you there.
     * The steppers stay regardless; they are the only control that crosses a
     * year boundary, since picking a month holds the year fixed.
     */
    onSelectMonth: ((Int) -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    // The menu offers months, so it needs the month name alone; the header
    // renders the full label. Split on the LAST space so a spelled-out month
    // survives, and only when the tail really is a 4-digit year.
    val trimmed = monthLabel.trim()
    val split = trimmed.lastIndexOf(' ')
    val yearToken = if (split > 0) trimmed.substring(split + 1) else ""
    val isYear = yearToken.length == 4 && yearToken.all { it.isDigit() }
    val monthName = if (isYear) trimmed.substring(0, split) else trimmed

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSelectMonth != null) {
            MonthMenu(label = trimmed, monthName = monthName, onSelectMonth = onSelectMonth)
        } else {
            Text(
                text = trimmed,
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        onPrevMonth?.let {
            MonthStepButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", it)
        }
        onNextMonth?.let {
            MonthStepButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", it)
        }
        // Balance the row when it has no steppers, so a bare group header's text
        // still starts on the same x as one that does.
        if (onPrevMonth == null && onNextMonth == null) Spacer(Modifier.width(dimens.space.xs))
    }
}

/**
 * The month label as a dropdown trigger: the label with a small caret after it,
 * opening a list of all twelve months.
 *
 * A tick marks the live month so the open menu still answers "where am I"
 * without the header behind it. Picking holds the YEAR fixed, matching the
 * design — which is why this never replaces the steppers: they remain the only
 * way across a year boundary, exactly the case a December gig makes.
 */
@Composable
private fun MonthMenu(label: String, monthName: String, onSelectMonth: (Int) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    var open by remember { mutableStateOf(false) }
    // [monthNames] — the same twelve strings the group headers are built from —
    // and NOT `DateFormatSymbols.getInstance(Locale.getDefault())`. Nothing else
    // on this surface is localised: [monthLabelFromEpoch] and [selectedDayLabel]
    // both format with `Locale.US` and the weekday row is "M T W T F S S". So a
    // default-locale menu was the one translated control on an English screen,
    // and worse, the tick below marks the live month by comparing against the
    // header's own name — on a hi-IN/bn-IN/ta-IN device (all shipping locales in
    // this market) the header read "August" and the menu "अगस्त", nothing ever
    // matched, and the open menu could no longer answer "where am I".
    val months = monthNames
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.radii.sm))
                .clickable { open = true }
                .semantics { contentDescription = "$label, choose month" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.surface,
        ) {
            months.forEachIndexed { index, name ->
                val selected = name.equals(monthName, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            style = AppTheme.type.rowTitle,
                            color = if (selected) colors.ink else colors.ink2,
                        )
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.accentInk,
                                modifier = Modifier.size(dimens.size.iconMd),
                            )
                        }
                    },
                    onClick = {
                        onSelectMonth(index)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * One stepper glyph. Sized to `size.rowMin` (44dp) so the tap target clears the
 * a11y minimum even though the glyph itself is small.
 */
@Composable
private fun MonthStepButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val size = AppTheme.dimens.size
    IconButton(onClick = onClick, modifier = Modifier.size(size.rowMin)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AppTheme.colors.ink,
            modifier = Modifier.size(size.iconLg),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One cell of the month grid.
 *
 * [inMonth] false marks an adjacent-month filler: the previous month's trailing
 * days and the next month's leading ones, drawn dim and inert purely so every
 * week row has seven tiles. Without them the first and last rows end in holes,
 * and a grid with holes in it stops reading as a grid.
 */
private data class DayCell(val number: Int, val inMonth: Boolean)

/**
 * The visible month as complete Monday-first weeks, adjacent-month days
 * included.
 *
 * Kept as a pure function of (year, month) so the fill arithmetic — which is
 * where off-by-one and year-boundary bugs live — is assertable without a
 * Compose test. The trailing fill counts up from 1 because the next month always
 * starts at 1; the leading fill counts back from the previous month's length.
 */
internal fun monthGridCells(year: Int, month: Int): List<List<Int>> =
    monthGridDays(year, month).chunked(7).map { week -> week.map { it.number } }

private fun monthGridDays(year: Int, month: Int): List<DayCell> {
    val cal = Calendar.getInstance().apply {
        clear()
        set(year, month, 1)
    }
    // Calendar.DAY_OF_WEEK is Sunday=1; +5 mod 7 rebases it to Monday=0.
    val leading = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val prevLength = Calendar.getInstance().apply {
        clear()
        set(year, month, 1)
        add(Calendar.MONTH, -1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = ArrayList<DayCell>(42)
    for (i in leading downTo 1) cells += DayCell(prevLength - i + 1, inMonth = false)
    for (d in 1..daysInMonth) cells += DayCell(d, inMonth = true)
    var next = 1
    while (cells.size % 7 != 0) cells += DayCell(next++, inMonth = false)
    return cells
}

/**
 * The month grid: seven columns of day cells, in the four fills [MonthDayFill]
 * describes plus today's ring.
 *
 * Numerals on a quiet ground, not a wall of tiles. The dark design filled every
 * day with a hairlined tile and hung a status dot under each numeral; the light
 * design fills only the days that are SAYING something, which is what lets the
 * accent on a booked day be the thing the eye finds. The dot went with the
 * tiles: an open day is now open by having no mark at all, which is both the
 * design and one less thing to explain.
 *
 * [busyDays] and [unavailableDays] stay day-of-month ints — the callers filter
 * their own lists by that, and widening it here would be a change to their
 * contract, not a visual fix.
 */
@Composable
fun MonthDayGrid(
    year: Int,
    month: Int, // Calendar.MONTH 0-based
    busyDays: Set<Int>,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Days the owner of this calendar has blocked out — the artist's
     * `days_available` gaps, on the Gigs seat. Empty on the client's Bookings
     * seat, which has no such concept, and that is why it is the one optional
     * parameter: the two surfaces share every other state.
     */
    unavailableDays: Set<Int> = emptySet(),
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    // Both derivations below are pure functions of the displayed month, and this
    // grid recomposes on every day tap ([selectedDay] is a parameter) — so
    // unremembered, tapping a date re-read the wall clock and rebuilt all 42
    // cells just to move one ring. The clock read is keyed on the month too,
    // which means a grid left on screen across midnight keeps its ring on
    // yesterday until the month is stepped — cheaper and steadier than
    // subscribing to a clock for one ring, and the dates are unaffected either
    // way.
    val todayDay = remember(year, month) {
        val today = Calendar.getInstance()
        if (today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month) {
            today.get(Calendar.DAY_OF_MONTH)
        } else {
            null
        }
    }
    val weeks = remember(year, month) { monthGridDays(year, month).chunked(7) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.space.xs)) {
            // Weekend letters drop a rung on the ink ladder — the only thing
            // distinguishing S and S from the rest of a mono row of capitals.
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
                Text(
                    d,
                    style = AppTheme.type.monoWeekday,
                    color = if (i >= 5) colors.ink4 else colors.ink3,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        weeks.forEach { week ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
            ) {
                week.forEach { cell ->
                    DayTile(
                        cell = cell,
                        fill = monthDayFill(
                            booked = cell.inMonth && cell.number in busyDays,
                            unavailable = cell.inMonth && cell.number in unavailableDays,
                            selected = cell.inMonth && selectedDay == cell.number,
                        ),
                        isToday = cell.inMonth && cell.number == todayDay,
                        onClick = { onDayClick(cell.number) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTile(
    cell: DayCell,
    fill: MonthDayFill,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.md)
    val marked = fill != MonthDayFill.Open

    val background = when (fill) {
        MonthDayFill.Open -> Color.Transparent
        MonthDayFill.Booked -> colors.accent
        // No token sits between `hairline` and `lineStrong`, and the design's
        // unavailable grey is nearly `hairline` — close enough that adding a
        // palette entry for the gap would be a theme change made for two pixels.
        MonthDayFill.Unavailable -> colors.hairline
        MonthDayFill.Selected -> colors.ink
    }
    val ink = when {
        !cell.inMonth -> colors.lineStrong
        fill == MonthDayFill.Selected -> colors.onDark
        fill == MonthDayFill.Booked -> colors.ink
        fill == MonthDayFill.Unavailable -> colors.ink3
        else -> colors.ink
    }
    // Today is a ring around whatever fill the day already carries — except when
    // it is selected, where an ink ring on an ink fill would be invisible and the
    // selection has already answered the question.
    val ringed = isToday && fill != MonthDayFill.Selected

    Box(
        modifier = modifier
            .heightIn(min = dimens.size.rowMin)
            .clip(shape)
            .background(background)
            .then(
                if (ringed) Modifier.border(dimens.size.strokeEmphasis, colors.ink, shape)
                else Modifier,
            )
            .then(
                if (cell.inMonth) {
                    Modifier
                        .semantics {
                            contentDescription = buildString {
                                append("Day ${cell.number}")
                                when (fill) {
                                    MonthDayFill.Booked -> append(", booked")
                                    MonthDayFill.Unavailable -> append(", unavailable")
                                    MonthDayFill.Selected -> append(", selected")
                                    MonthDayFill.Open -> Unit
                                }
                                if (isToday) append(", today")
                            }
                            selected = fill == MonthDayFill.Selected
                            if (fill == MonthDayFill.Booked) stateDescription = "Booked"
                        }
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${cell.number}",
            style = AppTheme.type.chip.copy(
                fontWeight = if (marked || isToday) FontWeight.Bold else FontWeight.Medium,
            ),
            color = ink,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legend + card
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The key under the grid — the only place the five states are named.
 *
 * It is not optional chrome. A colour-coded grid with no key asks the reader to
 * infer that lime means booked and grey means blocked, and the two surfaces that
 * share this component disagree about which of those they even use — so the
 * legend is also what tells a client that "Unavailable" is not a state their
 * calendar has.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthCalendarLegend(
    modifier: Modifier = Modifier,
    showUnavailable: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        LegendItem("Booked", fill = colors.accent)
        if (showUnavailable) LegendItem("Unavailable", fill = colors.hairline)
        LegendItem("Open", fill = Color.Transparent, stroke = colors.lineStrong)
        LegendItem("Selected", fill = colors.ink)
        LegendItem("Today", fill = Color.Transparent, stroke = colors.ink, emphasis = true)
    }
}

@Composable
private fun LegendItem(
    label: String,
    fill: Color,
    stroke: Color? = null,
    emphasis: Boolean = false,
) {
    val dimens = AppTheme.dimens
    val shape: Shape = RoundedCornerShape(dimens.space.xs)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm - dimens.space.xs / 2),
    ) {
        Box(
            Modifier
                .size(dimens.size.iconSm)
                .clip(shape)
                .background(fill)
                .then(
                    if (stroke != null) {
                        Modifier.border(
                            if (emphasis) dimens.size.strokeEmphasis else dimens.size.hairline,
                            stroke,
                            shape,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.ink3)
    }
}

/**
 * Header, weekdays, grid and legend inside one rounded `surface3` card — the
 * whole calendar as screen 78 draws it.
 *
 * The card is what makes the calendar a single object on a page that also
 * carries a day's schedule under it. Both Bookings and Gigs take this rather
 * than assembling the four pieces themselves, which is the drift the design's
 * note is about.
 */
@Composable
fun MonthCalendarCard(
    month: CalendarMonth,
    busyDays: Set<Int>,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
    onStepMonth: (Int) -> Unit,
    onSelectMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
    unavailableDays: Set<Int> = emptySet(),
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.xl))
            .background(colors.surface3)
            .padding(horizontal = dimens.space.lg, vertical = dimens.space.lg),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        MonthCalendarHeader(
            monthLabel = monthLabelFromEpoch(month.firstDayEpochMs),
            onPrevMonth = { onStepMonth(-1) },
            onNextMonth = { onStepMonth(1) },
            onSelectMonth = onSelectMonth,
        )
        MonthDayGrid(
            year = month.year,
            month = month.month,
            busyDays = busyDays,
            selectedDay = selectedDay,
            onDayClick = onDayClick,
            unavailableDays = unavailableDays,
        )
        MonthCalendarLegend(showUnavailable = unavailableDays.isNotEmpty())
    }
}

/**
 * One entry on the selected day's schedule (screen 78): the clock in mono at the
 * leading edge, a coloured spine, and what is happening.
 *
 * The spine is per-event and takes the accent only for the thing that IS the
 * booking; anything around it (a load-in note, a hold) takes the quiet grey. On
 * a day with two entries that tint is the only thing saying which of them is the
 * gig.
 */
@Composable
fun DayEventRow(
    time: String,
    meridiem: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accented: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.lg))
            .background(colors.surface3)
            .clickable(onClick = onClick)
            .padding(dimens.space.lg)
            .heightIn(min = dimens.size.rowMin)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    listOfNotNull(time, meridiem).joinToString(" ").takeIf { it.isNotBlank() },
                    title,
                    subtitle,
                ).joinToString(". ")
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClockColumn(time, meridiem)
        Box(
            Modifier
                .width(dimens.size.hairline * 3)
                .height(dimens.size.iconXl)
                .clip(RoundedCornerShape(dimens.space.xs))
                .background(if (accented) colors.accent else colors.hairline),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.lineStrong,
            modifier = Modifier.size(dimens.size.iconMd),
        )
    }
}

/**
 * The clock at the leading edge of a day's schedule row, stacked: "8:00" over
 * "pm" (design screens 36 and 78, which draw it identically).
 *
 * Two lines and an INTRINSIC width, both from the design, and both load-bearing.
 * A gig row that printed "8:00 PM" on one line inside a fixed 62dp column
 * clipped it — mono at 12sp needs about 92dp for seven glyphs — and widening the
 * column to fit the longest possible label ("11:30 AM") would spend that width
 * on every row that doesn't need it. Split, the widest line is four glyphs.
 *
 * Feed it from [splitClockLabel]; a label with no readable meridiem passes null
 * and prints on one line.
 */
@Composable
fun ClockColumn(time: String, meridiem: String?, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, style = AppTheme.type.monoSmall, color = colors.ink4)
        meridiem?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = AppTheme.type.monoMeridiem,
                color = colors.ink3,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
        }
    }
}

/**
 * Split "8:00 PM" into the clock and its meridiem, for [ClockColumn]'s stacked
 * time column. Anything that isn't two tokens comes back whole with no
 * meridiem — a label we cannot read is still a label, and stacking half of it is
 * worse than printing all of it on one line.
 */
fun splitClockLabel(timeLabel: String): Pair<String, String?> {
    val parts = timeLabel.trim().split(' ').filter { it.isNotEmpty() }
    return when (parts.size) {
        0 -> "" to null
        2 -> parts[0] to parts[1].lowercase(Locale.US)
        else -> timeLabel.trim() to null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Labels
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The twelve month names this whole calendar surface speaks: [MonthMenu]'s
 * entries, the keys behind [monthsByToken], and — by position — what the menu's
 * `onSelectMonth(index)` means.
 *
 * English on purpose, to agree with [monthLabelFromEpoch]'s `Locale.US` header;
 * `internal` so a unit test can pin that agreement without a Compose runtime,
 * the same trade [monthGridCells] makes for the fill arithmetic.
 */
internal val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * Lowercased month token → the full name we render. Holds both spellings a
 * label can legitimately carry: the "MMM" abbreviation the app writes ("Sep")
 * and the spelled-out month another client could send ("September").
 *
 * Lookup is an EXACT match on the whole token — deliberately not a prefix
 * match. Matching on the first three letters fabricated months out of any
 * lookalike word that happened to sit next to a plausible year ("Maybe, 2026"
 * → "May 2026", "Mars 3, 2026" → "March 2026"), which invented a header and
 * collapsed unrelated unreadable rows under it. `take(3)` here only *derives*
 * the abbreviation key at construction — it matches `SimpleDateFormat("MMM",
 * Locale.US)` output for all twelve months.
 */
private val monthsByToken: Map<String, String> = buildMap {
    monthNames.forEach { name ->
        put(name.lowercase(), name)
        put(name.take(3).lowercase(), name)
    }
}

/**
 * Format "April 2026" from a booking date label — the group key behind
 * `groupedByMonth()` on the Bookings and Gigs lists.
 *
 * Labels arrive as `BookingDateFormat.PATTERN` ("EEE, MMM d, yyyy" →
 * "Sat, May 16, 2026"); `Booking.date` is the `date_label` column verbatim, and
 * the reader also tolerates the weekday-less "MMM d, yyyy". Splitting either on
 * ", " leaves the month-and-day part second-to-last and the year last, so we
 * read off the TAIL. The previous version indexed `parts[1]` and expected three
 * space-separated tokens there, but on the canonical shape `parts[1]` is
 * "May 16" — only ever two tokens — so the check could never pass and every
 * label fell through to the `dateLabel` return. That handed each booking its
 * own monthKey and rendered one month header per row.
 *
 * Anything we can't read confidently (ISO "2026-05-16", an empty `date_label`,
 * garbage) is returned unchanged: grouping under the raw label is wrong-ish but
 * stable, and the row still renders rather than throwing.
 */
fun monthLabelFromDateLabel(dateLabel: String): String {
    val parts = dateLabel.split(", ")
    if (parts.size >= 2) {
        val year = parts.last().trim()
        // "May 16" → "May". substringBefore is safe on a token with no space.
        val monthToken = parts[parts.size - 2].trim().substringBefore(' ')
        val month = monthsByToken[monthToken.lowercase()]
        // Both guards are load-bearing: the token must BE a month (not merely
        // start like one) and the tail must be a 4-digit year, so a comma-bearing
        // non-date ("TBD, soon", "Maybe, 2026") keeps its raw label.
        if (month != null && year.length == 4 && year.all { it.isDigit() }) {
            // Full month name, not the "MMM" abbreviation: the day grid above
            // these group headers renders `monthLabelFromEpoch` ("MMMM yyyy"),
            // so "Apr 2026" under "April 2026" would read as two months.
            return "$month $year"
        }
    }
    return dateLabel
}

fun monthLabelFromEpoch(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val f = SimpleDateFormat("MMMM yyyy", Locale.US)
    return f.format(cal.time)
}

/**
 * "Sat 12 October" — the heading that names the day a calendar tap has selected,
 * so a filtered list reads as filtered rather than as a short list.
 *
 * The design prints this above the selected day's schedule and it is the only
 * thing on screen saying which day you are looking at; without it, tapping a day
 * narrowed the list silently and an empty day showed a bare "no bookings" line
 * with no date attached to it.
 *
 * [month] is `Calendar.MONTH`, 0-based, matching every other helper in this file.
 */
fun selectedDayLabel(year: Int, month: Int, day: Int): String {
    val cal = Calendar.getInstance().apply {
        clear()
        set(year, month, day)
    }
    return SimpleDateFormat("EEE d MMMM", Locale.US).format(cal.time)
}

/**
 * Day-of-month for [dateLabel] only when it falls in [year]/[month]
 * (Calendar.MONTH 0-based). Prevents cross-month collisions on the grid.
 */
fun dayOfMonthInMonth(dateLabel: String, year: Int, month: Int): Int? {
    val c = BookingDateFormat.parseLabel(dateLabel) ?: return null
    if (c.get(Calendar.YEAR) != year || c.get(Calendar.MONTH) != month) return null
    return c.get(Calendar.DAY_OF_MONTH)
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun MonthCalendarPreview() {
    ArtistantTheme {
        Column(Modifier.padding(AppTheme.dimens.component.gutter)) {
            MonthCalendarCard(
                month = CalendarMonth(2026, Calendar.OCTOBER),
                busyDays = setOf(4, 24),
                unavailableDays = setOf(7, 15, 20),
                selectedDay = 12,
                onDayClick = {},
                onStepMonth = {},
                onSelectMonth = {},
            )
        }
    }
}
