package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import `in`.artistant.app.designsystem.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Lightweight month header for bookings lists — not a full calendar grid.
 * Port slice of iOS `MonthCalendarView` (header + event rows only).
 */
@Composable
fun MonthCalendarHeader(
    monthLabel: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = monthLabel,
        style = AppTheme.type.displaySmall,
        color = AppTheme.colors.ink,
        modifier = modifier.padding(
            horizontal = AppTheme.dimens.space.lg,
            vertical = AppTheme.dimens.space.md,
        ),
    )
}

/** Format "April 2026" from a booking date label or epoch. */
fun monthLabelFromDateLabel(dateLabel: String): String {
    // dateLabel is "EEE, MMM d, yyyy" — extract month + year.
    val parts = dateLabel.split(", ")
    return if (parts.size >= 2) {
        val dayPart = parts[1] // "May 16, 2026"
        val tokens = dayPart.split(" ")
        if (tokens.size >= 3) "${tokens[0]} ${tokens[2]}" else dateLabel
    } else {
        dateLabel
    }
}

fun monthLabelFromEpoch(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val f = SimpleDateFormat("MMMM yyyy", Locale.US)
    return f.format(cal.time)
}

@Composable
fun BookingDayRow(
    dateLabel: String,
    timeLabel: String,
    title: String,
    subtitle: String,
    statusLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = space.lg, vertical = space.md)
            .then(Modifier),
    ) {
        Text(dateLabel, style = AppTheme.type.caption, color = colors.ink3)
        Spacer(Modifier.height(space.xs))
        Text(title, style = AppTheme.type.headline, color = colors.ink)
        Text("$timeLabel · $subtitle", style = AppTheme.type.footnote, color = colors.ink2)
        Spacer(Modifier.height(space.xs))
        Text(statusLabel, style = AppTheme.type.caption, color = colors.brand)
    }
    HRule(modifier = Modifier.padding(horizontal = space.lg))
}
