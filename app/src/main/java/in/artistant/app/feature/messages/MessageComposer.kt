package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * One keystroke's worth of composer, as a value.
 *
 * Pure so the rule that broke can be pinned in a JVM test: **the send affordance
 * is always present, and typing only ever changes its emphasis.** The bug this
 * replaces made the send button vanish the moment there was something to send —
 * the exact opposite — because the button's appearance was gated on
 * `Modifier.alpha`, whose `if (alpha >= 1f) this else graphicsLayer(...)`
 * short-circuit drops an element out of the modifier chain at 1f, and the frame
 * that dropped it stopped painting the `background` beneath it. Nothing here
 * decides whether the disc exists; it decides only what it says and whether it
 * responds.
 */
data class ComposerState(
    val draft: String = "",
    val enabled: Boolean = true,
) {
    /** What would actually be sent — the seam trims, so this is the real payload. */
    val payload: String get() = draft.trim()

    /** A blank-but-not-empty draft (a stray space) is not a message. */
    val canSend: Boolean get() = enabled && payload.isNotEmpty()

    /**
     * Accept typed text, capped.
     *
     * The cap is applied on the way IN rather than at send: someone pasting an
     * essay should watch it stop at the limit, not discover on send that the tail
     * was silently dropped.
     */
    fun typed(text: String, maxChars: Int = MAX_MESSAGE_CHARS): ComposerState =
        copy(draft = text.take(maxChars))

    /** After a send: the field empties, the disc stays. */
    fun cleared(): ComposerState = copy(draft = "")
}

/**
 * The message input, shared by the chat thread and the support assistant
 * (design 08 / 88).
 *
 * **It owns its own draft on purpose.** Hoisting the text into the screen (or
 * worse, the ViewModel) means every keystroke invalidates the screen composable,
 * and the transcript above it is a lazy list that then re-reads its item lambdas
 * on every character. Keeping the draft in the leaf confines a keystroke to this
 * one composable, which is the difference between typing that feels instant and
 * typing that stutters on a long thread. `rememberSaveable` so a rotation or a
 * process death doesn't eat a half-written message.
 *
 * The send control is a disc that is ALWAYS drawn, in one of two token states —
 * `surface2`/`ink4` with nothing to send, `accent`/`onAccent` with something —
 * rather than an opacity over a single state. That is the same rule
 * `PrimaryButton` follows (screen 118: a real disabled fill, not a dimmed
 * enabled one), and it is what makes the control's presence independent of the
 * draft: the row never reflows, and there is no modifier that appears and
 * disappears under it.
 */
@Composable
fun MessageComposer(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Message…",
    enabled: Boolean = true,
    maxChars: Int = MAX_MESSAGE_CHARS,
    testTag: String = "chat.composer",
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    var draft by rememberSaveable { mutableStateOf("") }
    val state = ComposerState(draft = draft, enabled = enabled)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = dimens.component.gutter, vertical = dimens.space.md),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        AppTextField(
            value = draft,
            onValueChange = { draft = state.typed(it, maxChars).draft },
            hint = placeholder,
            enabled = enabled,
            // Multi-line so a paragraph wraps instead of scrolling sideways, but
            // capped: the composer is pinned above the keyboard and an unbounded
            // field would eat the transcript it belongs to.
            singleLine = false,
            maxLines = COMPOSER_MAX_LINES,
            minHeight = dimens.size.rowMin,
            modifier = Modifier
                .weight(1f)
                .semantics { this.testTag = testTag },
        )
        SendButton(canSend = state.canSend) {
            onSend(state.payload)
            draft = state.cleared().draft
        }
    }
}

/**
 * The send disc.
 *
 * Extracted so its two states sit side by side in one place and neither can be
 * expressed as an effect applied to the other. Both branches produce the same
 * node with the same modifiers in the same order — only two colours differ — so
 * there is no frame in which the chain changes shape.
 */
@Composable
private fun SendButton(canSend: Boolean, onSend: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .size(dimens.size.rowMin)
            .clip(CircleShape)
            .background(if (canSend) colors.accent else colors.surface2)
            .clickable(enabled = canSend, onClick = onSend)
            .semantics {
                contentDescription = "Send"
                testTag = "chat.send"
                if (!canSend) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (canSend) colors.onAccent else colors.ink4,
            modifier = Modifier.size(dimens.size.iconLg),
        )
    }
}

/**
 * The composer plus the hairline the design draws above it (88), and a slot for
 * whatever the screen needs to say between the two — a failed-refresh strip, the
 * gig caption, a funnel CTA.
 *
 * The slot is inside the bar rather than above it because everything in here has
 * to ride the keyboard inset together; anything left outside would be under the
 * keyboard the moment the field is focused.
 */
@Composable
fun ComposerBar(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Message…",
    enabled: Boolean = true,
    testTag: String = "chat.composer",
    above: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.surface)) {
        HRule()
        above()
        MessageComposer(
            onSend = onSend,
            placeholder = placeholder,
            enabled = enabled,
            testTag = testTag,
        )
    }
}

/** Server-side sanity cap, mirrored on both clients. */
const val MAX_MESSAGE_CHARS = 4_000

private const val COMPOSER_MAX_LINES = 4
