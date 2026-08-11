package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The message input, shared by the chat thread and the support assistant.
 *
 * **It owns its own draft on purpose.** Hoisting the text into the screen (or
 * worse, the ViewModel) means every keystroke invalidates the screen composable,
 * and the transcript above it is a lazy list that then re-reads its item lambdas
 * on every character. Keeping the draft in the leaf confines a keystroke to this
 * one composable, which is the difference between typing that feels instant and
 * typing that stutters on a long thread. `rememberSaveable` so a rotation or a
 * process death doesn't eat a half-written message.
 *
 * The cap is applied here rather than only at send: a person pasting an essay
 * should see it stop at the limit, not watch it silently truncate on send.
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
    val space = AppTheme.dimens.space
    var draft by rememberSaveable { mutableStateOf("") }
    val canSend = enabled && draft.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = space.lg, vertical = space.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        val shape = CircleShape
        Box(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(colors.bgCard)
                .border(AppTheme.dimens.size.hairline, colors.line, shape)
                .padding(horizontal = space.lg, vertical = space.md),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(maxChars) },
                enabled = enabled,
                textStyle = AppTheme.type.callout.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
                maxLines = COMPOSER_MAX_LINES,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { this.testTag = testTag },
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(placeholder, style = AppTheme.type.callout, color = colors.ink3)
                    }
                    inner()
                },
            )
        }
        Box(
            Modifier
                .size(AppTheme.dimens.size.controlMin)
                .clip(CircleShape)
                .background(colors.brand)
                // Dimmed rather than hidden: a send button that disappears when
                // the field empties makes the row jump on every message sent.
                .alpha(if (canSend) 1f else DISABLED_ALPHA)
                .clickable(enabled = canSend) {
                    onSend(draft)
                    draft = ""
                }
                .semantics { contentDescription = "Send" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = null,
                tint = colors.brandInk,
                modifier = Modifier.size(AppTheme.dimens.size.iconLg),
            )
        }
    }
}

/** Server-side sanity cap, mirrored on both clients. */
const val MAX_MESSAGE_CHARS = 4_000

private const val COMPOSER_MAX_LINES = 4
private const val DISABLED_ALPHA = 0.4f
