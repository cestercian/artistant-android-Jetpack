package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.designsystem.theme.AppTheme
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

/**
 * Month header. Used two ways: as the grid's title (with [onPrevMonth] /
 * [onNextMonth] wired, which draws the stepper chevrons) and as a plain group
 * header above each month's rows in the list below (both null → title only).
 *
 * Chevrons mirror iOS `MonthCalendarView`'s header steppers: trailing edge, bare
 * glyphs in the hairline header style (no bordered chrome, no accent), and NOT
 * clamped to a date range — past gigs and far-future holds are both legitimate
 * destinations there, so they are here too.
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
    val space = AppTheme.dimens.space
    // "August 2026" arrives as one string, but it is TWO pieces of information
    // at two levels of importance, and iOS sets them that way: the month is the
    // editorial headline (the serif display step), the year is a quiet mono
    // disambiguator beside it. Rendering the whole label at one serif size made
    // the year shout as loudly as the month and left the header reading like a
    // section title rather than like a calendar's masthead.
    //
    // Split on the LAST space so a spelled-out month survives; anything without
    // a trailing year token renders whole, which is what the plain group-header
    // use (both callbacks null) relies on.
    val split = monthLabel.trim().lastIndexOf(' ')
    val yearToken = if (split > 0) monthLabel.trim().substring(split + 1) else ""
    val isYear = yearToken.length == 4 && yearToken.all { it.isDigit() }
    val month = if (isYear) monthLabel.trim().substring(0, split) else monthLabel
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = space.lg, vertical = space.md),
        // Baseline, not centre: the year has to sit ON the month's baseline, and
        // centring two wildly different type sizes floats the small one.
        verticalAlignment = Alignment.Bottom,
    ) {
        if (onSelectMonth != null) {
            MonthMenu(monthName = month, onSelectMonth = onSelectMonth)
        } else {
            Text(
                text = month,
                style = AppTheme.type.displayTitle,
                color = AppTheme.colors.ink,
            )
        }
        if (isYear) {
            Spacer(Modifier.width(space.sm))
            Text(
                text = yearToken,
                style = AppTheme.type.monoYear,
                color = AppTheme.colors.ink3,
                // Nudged off the very bottom so it aligns to the serif's baseline
                // rather than to its descender line.
                modifier = Modifier.padding(bottom = space.sm),
            )
        }
        Spacer(Modifier.weight(1f))
        onPrevMonth?.let {
            MonthStepButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", it)
        }
        onNextMonth?.let {
            MonthStepButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", it)
        }
    }
}

/**
 * The month name as a dropdown trigger: the name at the display step with a
 * small caret after it, opening a list of all twelve.
 *
 * The caret is deliberately quiet (`ink3`, one caption step) — the month name is
 * the calendar's masthead and the affordance should hang off it, not compete
 * with it. A tick marks the live month so the open menu still answers "where am
 * I" without the header behind it.
 *
 * Picking holds the YEAR fixed, matching the reference. That is why this never
 * replaces the steppers: they remain the only way across a year boundary, which
 * is exactly the case a December gig makes.
 */
@Composable
private fun MonthMenu(monthName: String, onSelectMonth: (Int) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    var open by remember { mutableStateOf(false) }
    // [monthNames] — the same twelve strings the group headers below this grid are
    // built from — and NOT `DateFormatSymbols.getInstance(Locale.getDefault())`.
    // Nothing else on this surface is localised: [monthLabelFromEpoch] and
    // [selectedDayLabel] both format with `Locale.US` and the weekday row is
    // "M T W T F S S". So a default-locale menu was the one translated control on
    // an English screen, and worse, the tick below marks the live month by
    // comparing against the header's own name — on a hi-IN/bn-IN/ta-IN device
    // (all shipping locales in this market) the header read "August" and the menu
    // "अगस्त", nothing ever matched, and the open menu could no longer answer
    // "where am I".
    val months = monthNames
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.radii.sm))
                .clickable { open = true }
                .semantics { contentDescription = "$monthName, choose month" },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = monthName, style = AppTheme.type.displayTitle, color = colors.ink)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier
                    .size(dimens.size.iconLg)
                    // Sits on the name's baseline rather than under its
                    // descender, same nudge the year token takes.
                    .padding(bottom = space.sm),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.bgCard,
        ) {
            months.forEachIndexed { index, name ->
                val selected = name.equals(monthName, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            style = AppTheme.type.callout,
                            color = if (selected) colors.ink else colors.ink2,
                        )
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.brand,
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
 * a11y minimum even though the glyph itself is small — the same trade iOS makes
 * with its 44pt frame around a footnote-weight chevron.
 */
@Composable
private fun MonthStepButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val size = AppTheme.dimens.size
    IconButton(onClick = onClick, modifier = Modifier.size(size.rowMin)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AppTheme.colors.ink3,
            modifier = Modifier.size(size.iconLg),
        )
    }
}

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
 * The month grid: seven columns of day TILES.
 *
 * Rebuilt against the iOS original, which this had been approximating with bare
 * numerals in circles. Four things changed, and each of them was carrying
 * information the old grid could not:
 *
 *  - **Tiles, not circles.** Every current-month day is a filled, hairlined
 *    rounded tile. That is what makes the grid legible as a month at a glance
 *    instead of as scattered numbers, and it is the surface the states below
 *    paint onto.
 *  - **Adjacent-month fill.** Leading and trailing cells carry the neighbouring
 *    months' dates, dimmed and inert, so no week row has a hole in it.
 *  - **A status dot under every open day.** A blank day and a day with no data
 *    used to look identical; the dot is the difference between "nothing booked"
 *    and "nothing known".
 *  - **Booked days FILL brand** rather than tinting to 20%. A gig is the thing
 *    the screen exists to show, and lime is signal — a 20% wash spends the
 *    accent without earning attention.
 *
 * Selection and today are rings, not fills, so they can stack on top of a booked
 * day without competing with it. [busyDays] and [selectedDay] stay day-of-month
 * ints — the callers filter their own lists by that, and widening it here would
 * be a change to their contract, not a visual fix.
 */
@Composable
fun MonthDayGrid(
    year: Int,
    month: Int, // Calendar.MONTH 0-based
    busyDays: Set<Int>,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Both derivations below are pure functions of the displayed month, and this
    // grid recomposes on every day tap ([selectedDay] is a parameter) — so
    // unremembered, tapping a date re-read the wall clock and rebuilt all 42
    // cells (three `Calendar`s and a 42-element list) just to move one ring.
    // The clock read is keyed on the month too, which means a grid left on screen
    // across midnight keeps its ring on yesterday until the month is stepped —
    // cheaper and steadier than subscribing to a clock for one ring, and the
    // dates themselves are unaffected either way.
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
        modifier.padding(horizontal = space.lg),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                week.forEach { cell ->
                    DayTile(
                        cell = cell,
                        busy = cell.inMonth && cell.number in busyDays,
                        selected = cell.inMonth && selectedDay == cell.number,
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
    busy: Boolean,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.sm)

    // Ring rules, in priority order. Selected wins, and on a lime tile it has to
    // switch to ink or it disappears into its own fill. A booked-but-unselected
    // tile takes no ring at all — the fill has already said everything.
    val ring = when {
        selected -> if (busy) colors.ink else colors.brand
        isToday && !busy -> colors.ink
        busy -> Color.Transparent
        else -> colors.lineSoft
    }
    val ringWidth = if (selected || (isToday && !busy)) dimens.size.strokeEmphasis else dimens.size.hairline

    val base = modifier
        .height(dimens.size.gridCellH)
        .clip(shape)
        .background(
            when {
                !cell.inMonth -> colors.bgCard.copy(alpha = 0.35f)
                busy -> colors.brand
                else -> colors.bgCard
            },
        )

    Box(
        if (cell.inMonth) {
            base
                .border(ringWidth, ring, shape)
                .semantics {
                    contentDescription = buildString {
                        append("Day ${cell.number}")
                        if (busy) append(", has bookings")
                        if (selected) append(", selected")
                    }
                    this.selected = selected
                    if (busy) stateDescription = "Busy"
                }
                .clickable(onClick = onClick)
        } else {
            base
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
        ) {
            Text(
                "${cell.number}",
                style = AppTheme.type.monoDay.copy(
                    fontWeight = if (busy || isToday) FontWeight.Bold else FontWeight.Medium,
                ),
                color = when {
                    !cell.inMonth -> colors.ink4
                    busy -> colors.brandInk
                    else -> colors.ink
                },
            )
            // The dot occupies its slot even when it has nothing to say, so the
            // numerals stay on one baseline across a row where some days are
            // booked and some are not.
            Box(
                Modifier
                    .size(dimens.size.gridDot)
                    .clip(CircleShape)
                    .background(if (cell.inMonth && !busy) colors.ink3 else Color.Transparent),
            )
        }
    }
}

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
 * "Wednesday, August 12" — the heading that names the day a calendar tap has
 * selected, so a filtered list reads as filtered rather than as a short list.
 *
 * The reference prints this above the selected day's schedule and it is the only
 * thing on screen saying which day you are looking at; without it, tapping a day
 * here narrowed the list silently and an empty day showed a bare "no bookings"
 * line with no date attached to it.
 *
 * [month] is `Calendar.MONTH`, 0-based, matching every other helper in this file.
 */
fun selectedDayLabel(year: Int, month: Int, day: Int): String {
    val cal = Calendar.getInstance().apply {
        clear()
        set(year, month, day)
    }
    return SimpleDateFormat("EEEE, MMMM d", Locale.US).format(cal.time)
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
