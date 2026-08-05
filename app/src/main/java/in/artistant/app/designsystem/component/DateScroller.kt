package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.reduceMotion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

/**
 * Horizontal ~30-day date strip with a per-day availability dot (port of iOS
 * `DateScroller`). [daysAvailable] is the artist's wizard weekday prefs (EEE
 * abbreviations, "Fri"/"Sat"); days NOT in the set render a dim "busy" dot, days
 * in it (or the empty/no-signal case) render a bright "free" dot — empty means
 * "no preference", NOT all-busy, so the whole flow doesn't get blocked. Selecting
 * a cell fills it brand and springs its scale. No `partial` state — there's no
 * half-busy data source yet (the legend keeps it for when per-date load lands).
 */
@Composable
fun DateScroller(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    dayCount: Int = 30,
    daysAvailable: List<String>? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = AppTheme.dimens.space.lg),
) {
    val today = remember { LocalDate.now() }
    val days = remember(today, dayCount) { (0 until dayCount).map { today.plusDays(it.toLong()) } }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        contentPadding = contentPadding,
    ) {
        items(days.size) { i ->
            val free = isFree(days[i], daysAvailable)
            DateCell(
                lines = dateChipLines(days[i]),
                isSelected = days[i] == selected,
                isFree = free,
                // A busy day is dimmed AND inert. Dimming alone leaves a live
                // control, so a client could still select — and then request —
                // a date the artist marked unavailable.
                enabled = free,
                onClick = { onSelect(days[i]) },
            )
        }
    }
}

/**
 * The two lines a date chip renders: an uppercase weekday abbreviation over the
 * bare day of the month — "WED" over "5". Same anatomy as iOS
 * `DateScroller.swift`, which shows only the day number (the month reads off the
 * run of surrounding cells; a month name in a 56dp chip is what forces the
 * truncation this type exists to prevent).
 *
 * A value type, not a formatted string, precisely so a caller can't reach for
 * `substringAfter`/`take` on a rendered date again. Both fields are already
 * chip-sized: nothing here needs clipping, and neither line can carry a
 * separator, because they're formatted from a parsed date rather than sliced out
 * of the source label.
 */
data class DateChipLines(val weekday: String, val day: String)

/**
 * Chip lines for a stored booking date label.
 *
 * Parsing delegates to [BookingDateFormat.parseLabel] — the single reader for
 * the canonical `"EEE, MMM d, yyyy"` shape and its tolerated variants
 * ("MMM d, yyyy", ISO) — so this shares one definition of "a date label" with
 * `monthLabelFromDateLabel` rather than growing a second, drifting one.
 *
 * The weekday is derived from the parsed DATE, not echoed from the label's own
 * weekday token: the date is the fact and the token is a copy, so a stale copy
 * self-corrects instead of rendering a lie.
 *
 * An unreadable label degrades to itself on the day line with a blank weekday —
 * the same "return the raw label" contract `monthLabelFromDateLabel` uses. The
 * chip still renders; it just doesn't invent a date it can't read.
 */
fun dateChipLines(dateLabel: String): DateChipLines {
    val parsed = BookingDateFormat.parseLabel(dateLabel)
        ?: return DateChipLines(weekday = "", day = dateLabel.trim())
    return dateChipLines(
        LocalDate.of(
            parsed.get(Calendar.YEAR),
            parsed.get(Calendar.MONTH) + 1, // Calendar.MONTH is 0-based, LocalDate's is 1-based
            parsed.get(Calendar.DAY_OF_MONTH),
        ),
    )
}

/**
 * Chip lines for an epoch — the shape the booking funnel's chips carry. Resolved
 * in the device zone, matching [BookingDateFormat]'s reader, so the same day
 * can't render as two different chips depending on which side produced it.
 */
fun dateChipLines(epochMs: Long): DateChipLines =
    dateChipLines(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate())

/** The one place either line is actually formatted. */
fun dateChipLines(date: LocalDate): DateChipLines = DateChipLines(
    weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US),
    day = date.dayOfMonth.toString(),
)

/**
 * One date card — a portrait 56×76 tile: uppercase weekday over a big mono day
 * numeral over an availability dot. Selected fills brand and springs its scale.
 *
 * Public because the booking funnel builds its own strip from server-supplied
 * chips (each carrying an epoch and an availability flag) rather than from a
 * plain run of dates, and the two strips must not drift. The alternative — the
 * funnel hand-rolling a lookalike — is what previously produced a squat two-line
 * chip there whose day line was carved out of a rendered label with
 * `substringAfter`/`take`. Taking [DateChipLines] (never a raw label) keeps that
 * closed: a caller has to go through a formatter to build one.
 *
 * A busy cell dims the WHOLE tile rather than recolouring each line, so the
 * weekday, the numeral and the dot all recede together and "unavailable" reads
 * as one state instead of three shades.
 */
@Composable
fun DateCell(
    lines: DateChipLines,
    isSelected: Boolean,
    isFree: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.md)
    val reduceMotion = AppTheme.reduceMotion
    // Spring the selected cell (the reference build uses `.spring(duration:
    // 0.25)`). Under reduce-motion the cell still grows — the size difference is
    // the *state* readout, not decoration, and losing it would leave selection
    // signalled by colour alone — but it arrives instantly instead of springing.
    val scale by animateFloatAsState(
        targetValue = if (isSelected) SELECTED_CELL_SCALE else 1f,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "dayScale",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier
            // Draw-phase read: selecting a date re-draws this cell rather than
            // recomposing the strip.
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pressScale(interaction)
            .alpha(if (enabled) 1f else BUSY_CELL_ALPHA)
            .width(dimens.size.dateCellW)
            .height(dimens.size.dateCellH)
            .clip(shape)
            .background(if (isSelected) colors.brand else colors.bgCard)
            .border(
                dimens.size.hairline,
                if (isSelected) Color.Transparent else colors.lineSoft,
                shape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            lines.weekday,
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) colors.brandInk else colors.ink3,
        )
        Text(
            lines.day,
            style = AppTheme.type.monoStat,
            color = if (isSelected) colors.brandInk else colors.ink,
        )
        Box(
            Modifier
                .padding(top = AppTheme.dimens.space.xs)
                .size(dimens.size.dot)
                .clip(CircleShape)
                .background(if (isFree) colors.good else colors.ink4),
        )
    }
}

/**
 * How far a busy date card recedes. Not full transparency: the day still has to
 * be readable so the strip reads as a calendar with gaps, not a calendar with
 * holes.
 */
private const val BUSY_CELL_ALPHA = 0.45f

/**
 * How much the selected date card grows. Enough to lift it out of the strip's
 * rhythm without overlapping its neighbours (the strip's gap is `space.sm`).
 */
private const val SELECTED_CELL_SCALE = 1.06f

// EEE abbreviation match against days_available (same Locale.US key the wizard
// writes + availabilityKicker reads). Empty/null → free (no-signal default).
private fun isFree(date: LocalDate, daysAvailable: List<String>?): Boolean {
    if (daysAvailable.isNullOrEmpty()) return true
    return daysAvailable.contains(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US))
}
