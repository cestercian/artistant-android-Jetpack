package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The empty / failed state: a glyph in a `surface3` disc, a title, a line of
 * copy, and up to two actions (screen 57).
 *
 * **Every empty state carries an action.** That is one of the design's stated
 * principles, and it is why [actionLabel] is not decorative: "nothing here" is a
 * demand signal, and screen 57 spends it ("Notify me when one joins") instead of
 * dead-ending. A caller with genuinely nothing to offer passes null and gets a
 * quieter block, but should first ask whether that is true.
 *
 * The body is width-capped rather than full-bleed. The design sets `max-width:
 * 30ch` on it, and the reason is legibility: centred copy running the full width
 * of a phone gives the eye no reliable place to return to on the next line.
 *
 * @param actionLabel the primary, accent-filled action.
 * @param secondaryLabel a quieter alternative under it — "Clear filters" beside
 *   "Notify me". Rendered only when [onSecondary] is also supplied.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        icon?.let {
            Box(
                Modifier
                    .size(dimens.component.emptyGlyphCircle)
                    .clip(CircleShape)
                    .background(colors.surface3),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = colors.ink4,
                    modifier = Modifier.size(dimens.component.emptyGlyph),
                )
            }
        }
        Text(
            text = title,
            style = AppTheme.type.displaySub,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = AppTheme.type.body,
                color = colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = dimens.component.readingMeasure),
            )
        }
        if (actionLabel != null && onAction != null) {
            Column(
                modifier = Modifier
                    .padding(top = dimens.space.md)
                    .widthIn(max = dimens.component.emptyActionWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                PrimaryButton(actionLabel, onAction, fullWidth = true)
                if (secondaryLabel != null && onSecondary != null) {
                    SecondaryButton(secondaryLabel, onSecondary, fullWidth = true)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun EmptyStatePreview() {
    ArtistantTheme {
        EmptyState(
            title = "No artists for this yet",
            body = "Nothing matches \"throat singing\" with your three filters on.",
            icon = Icons.Filled.SearchOff,
            actionLabel = "Notify me when one joins",
            onAction = {},
            secondaryLabel = "Clear filters",
            onSecondary = {},
        )
    }
}
