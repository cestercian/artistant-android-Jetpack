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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/** Where one [EventStep] sits in its sequence. */
enum class EventStepState {
    /** Already happened. Accent disc. */
    Done,

    /** Happening, or the one everyone is waiting on. Accent ring, hollow. */
    Current,

    /** Hasn't happened yet. Quiet disc, quieter title. */
    Pending,
}

/** One row of an [EventTimeline]: a headline and an optional line under it. */
data class EventStep(
    val title: String,
    val detail: String? = null,
    val state: EventStepState = EventStepState.Pending,
)

/**
 * A vertical run of moments — the booking's run of show (screen 18) and the
 * request's progress (screen 95).
 *
 * Distinct from [StatusTimeline], which it does not replace. That one is the
 * dark design's four-step booking arc with 22dp checkmark discs, and the
 * post-request screen still draws it. This is the light design's timeline: an
 * 11dp dot, a 2dp rule between dots, and no glyph inside anything. It also takes
 * its steps whole rather than deriving them from a status, because the two
 * screens that use it are listing DIFFERENT things — the hours of a night, and
 * the stages of a request — and only one of those is a function of
 * `bookings.status`.
 *
 * The connector stretches to each step's text block ([IntrinsicSize.Min] plus a
 * weighted spacer), so a step with a detail line under it gets a longer rule and
 * the column stays continuous.
 */
@Composable
fun EventTimeline(steps: List<EventStep>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(modifier) {
        steps.forEachIndexed { index, step ->
            val isLast = index == steps.lastIndex
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(
                    Modifier.width(dimens.size.iconSm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EventDot(step.state)
                    if (!isLast) {
                        Box(
                            Modifier
                                .width(dimens.size.stroke)
                                .weight(1f)
                                .fillMaxHeight()
                                .background(colors.hairline),
                        )
                    }
                }
                Spacer(Modifier.width(dimens.space.lg))
                Column(
                    modifier = Modifier
                        .padding(bottom = if (isLast) dimens.space.xs else dimens.space.lg)
                        .semantics(mergeDescendants = true) {
                            contentDescription =
                                listOfNotNull(step.title, step.detail).joinToString(". ")
                        },
                    verticalArrangement = Arrangement.spacedBy(dimens.space.xs / 2),
                ) {
                    Text(
                        text = step.title,
                        style = AppTheme.type.rowTitle,
                        color = if (step.state == EventStepState.Pending) colors.ink3 else colors.ink,
                    )
                    step.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = AppTheme.type.caption, color = colors.ink4)
                    }
                }
            }
        }
    }
}

/**
 * The dot.
 *
 * [EventStepState.Current] is a RING rather than a fill, and that is the whole
 * grammar of the column: a filled dot means the moment is behind you, a hollow
 * one means it is the moment being waited on. Pending takes [AppColors.lineStrong]
 * — a grey that is visibly a dot rather than a gap, since the rule above it is
 * already a hairline and two hairline-weight marks in a column read as one broken
 * line.
 */
@Composable
private fun EventDot(state: EventStepState) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .size(dimens.size.iconSm)
            .clip(CircleShape)
            .background(
                when (state) {
                    EventStepState.Done -> colors.accent
                    EventStepState.Current -> Color.Transparent
                    EventStepState.Pending -> colors.lineStrong
                },
            )
            .then(
                if (state == EventStepState.Current) {
                    Modifier.border(dimens.size.strokeEmphasis, colors.accent, CircleShape)
                } else {
                    Modifier
                },
            ),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun EventTimelinePreview() {
    ArtistantTheme {
        Column(Modifier.padding(AppTheme.dimens.component.gutter)) {
            EventTimeline(
                steps = listOf(
                    EventStep("Request sent", "14 minutes ago", EventStepState.Done),
                    EventStep("Artist responds", "Accept, counter or decline", EventStepState.Current),
                    EventStep("Date is held", "Once they accept", EventStepState.Pending),
                ),
            )
        }
    }
}
