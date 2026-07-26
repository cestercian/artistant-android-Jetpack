package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.designsystem.theme.AppTheme

/** One vertical step (iOS `TimelineStep`). */
data class TimelineStep(val title: String, val sub: String)

/** Where a step sits relative to the current index (iOS `TimelineState`). */
enum class TimelineState { Done, Current, Pending }

/**
 * 4-step vertical booking timeline (port of iOS `StatusTimeline`). Steps before
 * [currentIndex] render Done (brand fill + check), the current one Current (brand
 * ring + inner dot), later ones Pending (hairline ring). The connector below a
 * Done dot is a faded-brand rule, else the soft hairline. Height is intrinsic so
 * the connector stretches to each step's text block.
 */
@Composable
fun StatusTimeline(steps: List<TimelineStep>, currentIndex: Int, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier) {
        steps.forEachIndexed { idx, step ->
            val state = when {
                idx < currentIndex -> TimelineState.Done
                idx == currentIndex -> TimelineState.Current
                else -> TimelineState.Pending
            }
            val isLast = idx == steps.lastIndex
            Row(Modifier.height(IntrinsicSize.Min)) {
                // Dot + connector column.
                Column(
                    Modifier.width(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Dot(state)
                    if (!isLast) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (state == TimelineState.Done) colors.brand.copy(alpha = 0.5f)
                                    else colors.lineSoft,
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    Modifier.padding(bottom = if (isLast) 0.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        step.title,
                        style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                        color = if (state == TimelineState.Pending) colors.ink3 else colors.ink,
                    )
                    Text(step.sub, style = AppTheme.type.footnote, color = colors.ink3)
                }
            }
        }
    }
}

@Composable
private fun Dot(state: TimelineState) {
    val colors = AppTheme.colors
    val fill = if (state == TimelineState.Done) colors.brand else colors.bgCard
    val stroke = when (state) {
        TimelineState.Done, TimelineState.Current -> colors.brand
        TimelineState.Pending -> colors.lineSoft
    }
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            TimelineState.Done -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.brandInk,
                modifier = Modifier.size(12.dp),
            )
            TimelineState.Current -> Box(Modifier.size(8.dp).clip(CircleShape).background(colors.brand))
            TimelineState.Pending -> {}
        }
    }
}

/**
 * The standard 4-step booking timeline (iOS `StatusTimeline.bookingDefault`).
 * Escrow/payment language was dropped for v1, so copy + current-index derive
 * purely from [status]. cancelled/disputed park at step 0.
 */
@Composable
fun BookingStatusTimeline(status: BookingStatus, modifier: Modifier = Modifier) {
    val steps = listOf(
        TimelineStep("Booked", "Your booking request is in."),
        TimelineStep("Awaiting confirm", "Artist will confirm within 24h."),
        TimelineStep("Show day", "Soundcheck, performance, applause."),
        TimelineStep("Completed", "Wrap-up after the show."),
    )
    val idx = when (status) {
        BookingStatus.PendingConfirm -> 1
        BookingStatus.Confirmed -> 2
        BookingStatus.Completed -> 3
        BookingStatus.Cancelled, BookingStatus.Disputed -> 0
    }
    StatusTimeline(steps = steps, currentIndex = idx, modifier = modifier)
}
