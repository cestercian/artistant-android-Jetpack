package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/** One named phase of a multi-step operation. */
data class NarratedStep(val label: String, val state: StepState)

enum class StepState { Done, Running, Pending }

/**
 * "Narrated, not a spinner" (screen 70).
 *
 * A spinner says only that something is happening. This says WHICH step is
 * happening, which is the difference between a slow save and a hung app — and
 * when it is slow, it says which part is slow.
 *
 * **Every step must advance on real work.** The steps are passed in, and the
 * caller is expected to move them as its own calls complete: a timer-driven
 * ladder would be a progress bar that lies, which is worse than the spinner it
 * replaced. The tail line is the expectation the screen ends on, not a promise
 * about delivery.
 *
 * Announced as a polite live region so a screen reader hears each phase as it
 * lands rather than only on the way out.
 */
@Composable
fun SendingNarration(
    title: String,
    body: String,
    steps: List<NarratedStep>,
    modifier: Modifier = Modifier,
    tail: String? = null,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
            .padding(horizontal = dimens.component.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(dimens.component.emptyGlyphCircle)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconXl),
            )
        }
        Spacer(Modifier.height(dimens.space.xl))
        Text(
            title,
            style = AppTheme.type.displayMedium,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(dimens.space.sm))
        Text(
            body,
            style = AppTheme.type.body,
            color = colors.ink4,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = dimens.component.readingMeasure),
        )
        Spacer(Modifier.height(dimens.space.xl))
        Column(
            modifier = Modifier
                .widthIn(max = dimens.component.emptyActionWidth)
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = steps.joinToString(". ") { step ->
                        "${step.label}: ${step.state.spoken}"
                    }
                    testTag = "sending.steps"
                },
            verticalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            steps.forEach { StepRow(it) }
        }
        if (!tail.isNullOrBlank()) {
            Spacer(Modifier.height(dimens.space.xl))
            Text(
                tail,
                style = AppTheme.type.caption,
                color = colors.ink4,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StepRow(step: NarratedStep) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(
            Modifier.size(dimens.size.iconLg),
            contentAlignment = Alignment.Center,
        ) {
            when (step.state) {
                StepState.Done -> Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(dimens.size.iconSm),
                    )
                }
                // The design draws the running step as a ring with one segment
                // in the hairline colour — a spinner. M3's indeterminate
                // indicator IS that shape and already animates; restyling it
                // costs two colour arguments and nothing has to be hand-drawn.
                StepState.Running -> CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.accent,
                    trackColor = colors.hairline,
                    strokeWidth = dimens.size.stroke,
                )
                StepState.Pending -> Box(
                    Modifier
                        .fillMaxSize()
                        .border(dimens.size.hairline, colors.lineStrong, CircleShape),
                )
            }
        }
        Text(
            step.label,
            style = AppTheme.type.rowTitle.copy(
                fontWeight = if (step.state == StepState.Pending) {
                    FontWeight.Normal
                } else {
                    FontWeight.SemiBold
                },
            ),
            color = if (step.state == StepState.Pending) colors.ink4 else colors.ink,
        )
    }
}

/** What a screen reader hears for each state. */
private val StepState.spoken: String
    get() = when (this) {
        StepState.Done -> "done"
        StepState.Running -> "in progress"
        StepState.Pending -> "waiting"
    }

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6, heightDp = 640)
@Composable
private fun SendingNarrationPreview() {
    ArtistantTheme {
        SendingNarration(
            title = "Accepting the quote…",
            body = "Locking ₹48,000 with The Tilt Collective.",
            steps = listOf(
                NarratedStep("Terms locked", StepState.Done),
                NarratedStep("Saving your answer", StepState.Running),
                NarratedStep("Opening the booking", StepState.Pending),
            ),
            tail = "The agreed terms stay in this conversation.",
        )
    }
}
