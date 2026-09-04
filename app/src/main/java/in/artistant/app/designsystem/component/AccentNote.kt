package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The accent-tinted note the design puts under a headline to say how the thing
 * above it works — "a cancellation inside seven days costs about nine points"
 * (16), "this is how clients see your profile" (103), "reply speed is already
 * being counted" (79).
 *
 * Not a [Banner]. A banner reports a CONDITION — something failed, something is
 * pending, something needs an action — and its four tones all mean "attend to
 * this". This means the opposite: nothing is wrong and nothing is required. So
 * it takes no action slot and cannot be tapped, and it is drawn in a wash of the
 * accent at [FILL_ALPHA] rather than in a semantic tone, which is why it can sit
 * on a page that is already spending its one accent elsewhere without competing
 * with it.
 *
 * [lead] is set bold in full-strength ink and runs into [text] as one paragraph,
 * the way the design sets it; a caller with nothing to emphasise passes null and
 * gets a plain note.
 */
@Composable
fun AccentNote(
    text: String,
    modifier: Modifier = Modifier,
    lead: String? = null,
    icon: ImageVector = Icons.Outlined.Info,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = FILL_ALPHA))
            .border(dimens.size.hairline, colors.accent.copy(alpha = STROKE_ALPHA), shape)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(lead, text).joinToString(" ")
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accentInk,
            modifier = Modifier.size(dimens.size.iconLg),
        )
        Column(verticalArrangement = Arrangement.spacedBy(dimens.space.xs)) {
            if (!lead.isNullOrBlank()) {
                Text(
                    text = lead,
                    style = AppTheme.type.caption.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = AppTheme.type.body.letterSpacing,
                    ),
                    color = colors.ink,
                )
            }
            Text(
                text = text,
                style = AppTheme.type.caption.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = AppTheme.type.body.letterSpacing,
                    lineHeight = AppTheme.type.body.lineHeight,
                ),
                color = colors.ink2,
            )
        }
    }
}

/**
 * The same block with its content left to the caller.
 *
 * [AccentNote] is the shape this wash is nearly always in — a glyph and a
 * paragraph — but not always: screen 89's profile nudge is a title, a subtitle,
 * a dark "Go" pill and a dismiss cross inside the identical frame. Rebuilding
 * the fill and the rim at that call site would be two more places for the wash
 * to drift, so the frame is what is shared and the arrangement is what varies.
 */
@Composable
fun AccentNoteCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = FILL_ALPHA))
            .border(dimens.size.hairline, colors.accent.copy(alpha = STROKE_ALPHA), shape)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.md),
        content = content,
    )
}

/** The accent as a wash — readable ink over it, still obviously the accent. */
private const val FILL_ALPHA = 0.22f

/** The same accent at the strength a hairline needs to survive the wash. */
private const val STROKE_ALPHA = 0.6f

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AccentNotePreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            AccentNote(
                lead = "This is how clients see your profile.",
                text = "Booking controls are replaced with availability while " +
                    "you're looking at your own act.",
            )
            AccentNote(
                text = "A cancellation inside seven days costs about nine points. " +
                    "Nothing on this screen can be bought.",
            )
        }
    }
}
