package `in`.artistant.app.feature.messages

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import `in`.artistant.app.data.repository.ConversationReportReasons
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Report this conversation (design 73).
 *
 * **Same shape as report artist**, deliberately: pick a reason, add an optional
 * note, submit. One pattern means safety reporting is learnable in one place —
 * whichever surface someone reaches it from, the steps are the same.
 *
 * Two-step rather than file-on-tap. The previous build filed the moment a reason
 * was tapped, which made an accidental report unrecoverable and left no room for
 * the note; a report is a serious, one-way action and deserves the second tap.
 *
 * The note is genuinely optional and genuinely private — it goes to
 * `reports.details`, which the reported person cannot read. The line under the
 * field says so, because "add a note" next to a conversation someone is scared of
 * otherwise reads as "write something they will see".
 */
@Composable
fun ReportConversationSheet(
    counterpartName: String,
    /**
     * A submission is in flight.
     *
     * This form stays on screen for the whole round trip — it has no outcome to
     * render yet — so the CTA has to say so itself, or a second tap files the
     * same report again.
     */
    submitting: Boolean,
    onSubmit: (reason: String, details: String?) -> Unit,
    /** The footer link out to trust & safety (design 131) — see [ThreadDetailsSheet]. */
    onOpenSafetyCentre: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    // Saveable: a rotation mid-report must not silently discard the reason and
    // the sentence someone just wrote about something that upset them.
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().semantics { testTag = "report.sheet" }) {
        Text(
            "Why are you reporting this conversation?",
            style = AppTheme.type.subtitle,
            color = colors.ink2,
        )
        Spacer(Modifier.height(dimens.space.md))

        ConversationReportReasons.forEach { candidate ->
            ReasonRow(
                label = candidate,
                selected = candidate == reason,
                onSelect = { reason = candidate },
            )
        }

        Spacer(Modifier.height(dimens.space.lg))
        AppTextField(
            value = note,
            onValueChange = { note = it.take(NOTE_MAX_CHARS) },
            label = "Add a note — optional",
            hint = "This won't be shared with them",
            singleLine = false,
            maxLines = NOTE_MAX_LINES,
            minHeight = dimens.component.cta,
            modifier = Modifier.semantics { testTag = "report.note" },
        )

        Spacer(Modifier.height(dimens.space.lg))
        PrimaryButton(
            // The label changes as well as the state: a disabled button with the
            // same words reads as "you did something wrong", and the one thing
            // this reader must not think is that their report did not go.
            text = if (submitting) "Sending report…" else "Submit report",
            onClick = { reason?.let { onSubmit(it, note.trim().ifBlank { null }) } },
            enabled = reason != null && !submitting,
            fullWidth = true,
            modifier = Modifier.semantics { testTag = "report.submit" },
        )
        Spacer(Modifier.height(dimens.space.md))
        Text(
            // NOT the design's "it fails loudly rather than queueing" — this
            // build's reports seam deliberately never throws (it falls back to an
            // on-device log so a moderation outage cannot block a conversation),
            // so it genuinely cannot tell delivered from queued. Saying it fails
            // loudly would describe a behaviour the app does not have.
            "Goes to our safety team. $counterpartName is never shown this report.",
            style = AppTheme.type.caption,
            color = colors.ink4,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(dimens.space.sm))
        // The footer link the safety screens all carry. Someone on this form has
        // decided something is wrong and may not be sure a report is the right
        // instrument — blocking, or simply keeping the deal on Artistant, often
        // is. The way to that advice belongs here rather than three screens away
        // in account settings.
        Text(
            "Read our trust & safety guide",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = colors.accentInk,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClick = onOpenSafetyCentre)
                .padding(vertical = dimens.space.md)
                .semantics { testTag = "report.safetyCentre" },
        )
        Text(
            "Back",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = if (submitting) colors.ink4 else colors.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                // Also off while the write is out: stepping back to the actions
                // list mid-flight hides the form that is about to answer, and
                // the answer then lands on a sheet the reader has left.
                .clickable(enabled = !submitting, onClick = onBack)
                .padding(vertical = dimens.space.md)
                .semantics { testTag = "report.back" },
        )
    }
}

/**
 * One reason, with a real radio.
 *
 * `Role.RadioButton` plus a merged description, so a screen reader announces
 * "Spam or a scam, radio button, selected" rather than reading a decorative
 * circle and a label as two separate nodes.
 */
@Composable
private fun ReasonRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = dimens.space.md)
                .semantics(mergeDescendants = true) {
                    role = Role.RadioButton
                    this.selected = selected
                    contentDescription = label
                    testTag = "report.reason"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Box(
                Modifier.size(dimens.size.iconLg),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        Modifier.fillMaxWidth().height(dimens.size.iconLg)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.onAccent,
                            modifier = Modifier.size(dimens.size.iconSm),
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(dimens.size.iconLg)
                            .border(dimens.size.hairline, colors.lineStrong, CircleShape),
                    )
                }
            }
            Text(
                label,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Medium),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
        }
        HRule()
    }
}

/** `reports.details` is free text; this is a note, not an essay. */
private const val NOTE_MAX_CHARS = 1_000
private const val NOTE_MAX_LINES = 4
