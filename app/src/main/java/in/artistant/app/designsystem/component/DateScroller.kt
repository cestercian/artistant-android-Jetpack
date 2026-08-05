package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.designsystem.theme.AppTheme
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
            DayCell(
                date = days[i],
                isSelected = days[i] == selected,
                isFree = isFree(days[i], daysAvailable),
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

@Composable
private fun DayCell(date: LocalDate, isSelected: Boolean, isFree: Boolean, onClick: () -> Unit) {
    val lines = dateChipLines(date)
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.dimens.radii.sm)
    // Spring the selected cell (iOS `.spring(duration: 0.25)`).
    val scale by animateFloatAsState(if (isSelected) 1.06f else 1f, spring(), label = "dayScale")
    val interaction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .scale(scale)
            .width(56.dp)
            .height(76.dp)
            .clip(shape)
            .background(if (isSelected) colors.brand else colors.bgCard)
            .border(1.dp, if (isSelected) Color.Transparent else colors.lineSoft, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
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
            style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) colors.brandInk else colors.ink,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isFree) colors.good else colors.ink4),
        )
    }
}

// EEE abbreviation match against days_available (same Locale.US key the wizard
// writes + availabilityKicker reads). Empty/null → free (no-signal default).
private fun isFree(date: LocalDate, daysAvailable: List<String>?): Boolean {
    if (daysAvailable.isNullOrEmpty()) return true
    return daysAvailable.contains(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US))
}
