package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

// Port of iOS `DateScroller.swift`, kept as the pieces the app actually builds strips
// from: the chip anatomy (DateChipLines + its formatters) and one date card (DateCell).
// The strip itself belongs to its callers — BookingScreen and RequestQuoteScreen lay out
// their own LazyRow over server-supplied chips (BookingSlots.upcomingDateChips, which
// owns the availability rule: an artist with no weekday prefs is free, not busy, and a
// busy day is dimmed AND inert so a client cannot request a date the artist blocked).
// A second strip lived here with its own `remember { LocalDate.now() }` window and no
// caller; it was deleted rather than fixed, because a 30-day window pinned at first
// composition still shows yesterday as day one after midnight, and there was no screen
// to prove the fix against.

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
 * Chip lines for an epoch — the shape the booking funnel's chips carry.
 *
 * Resolved in the GIG zone, matching [BookingDateFormat]'s reader, so the same
 * day cannot render as two different chips depending on which side produced it.
 * That reader is Asia/Kolkata: a gig's date is the day it happens in India, and
 * the funnel's chip walk, its stored label and the instant `startEndIso` writes
 * all say so. Resolving here in the device zone instead put the visible chip a
 * day off its own label for a client whose calendar differs from India's — New
 * York at 22:00 is already the next morning in Kolkata, so the strip read "27"
 * while the tap booked the 28th.
 */
fun dateChipLines(epochMs: Long): DateChipLines =
    dateChipLines(Instant.ofEpochMilli(epochMs).atZone(GIG_ZONE).toLocalDate())

/** The clock every gig date is written, read and rendered in. */
private val GIG_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

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
            // Both of this cell's states are carried by paint alone — selection by
            // the brand fill plus a 1.06 scale, availability by a dot colour and a
            // dim — so neither reaches a screen reader unless it is said out loud.
            // `selectable` (not `clickable`) is what publishes the Selected
            // property; the dot's meaning has to be spelled out, in the same word
            // the strip's own Free/Busy legend uses.
            .semantics {
                contentDescription = "${lines.weekday} ${lines.day}".trim()
                if (!isFree) stateDescription = "Busy"
            }
            .selectable(
                selected = isSelected,
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
