package `in`.artistant.app.designsystem.component

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * "Add to calendar" — the ZERO-permission path (port of iOS `AddToCalendarButton`).
 * Fires an `ACTION_INSERT` on `CalendarContract.Events.CONTENT_URI` with the show
 * pre-filled, which hands off to the system Calendar app's own compose screen —
 * no calendar permission, no usage prompt, we never read the store. This is the
 * ONLY calendar bit in M3; the auto-mirror [CalendarSyncService] is M6.
 *
 * Hidden when the booking has no resolvable start window — nothing sensible to
 * prefill, and dating it "now" would be a lie the user might save.
 */
@Composable
fun AddToCalendarButton(booking: Booking, modifier: Modifier = Modifier) {
    val start = booking.startDatetime ?: return
    val colors = AppTheme.colors
    val context = LocalContext.current
    // End defaults to +2h (matches BookingsRepository.startEnd's placeholder).
    val end = booking.endDatetime ?: start.plusSeconds(2 * 3600)

    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .border(1.dp, colors.lineSoft, CircleShape)
            .clickable {
                val intent = Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, "Artistant booking")
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, booking.venue)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toEpochMilli())
                // May not resolve on a device with no calendar app — guard so the
                // tap is a no-op rather than a crash.
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Add to calendar",
            style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
        )
    }
}
