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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The accent-tinted aside — a lime-washed block with a lime rim, carrying one
 * short paragraph the page wants read but does not want shouted.
 *
 * It appears on six of section BN's screens (52, 83, 89, 95, 117, 122) and
 * always says the same KIND of thing: what this state means for you, in plain
 * words, next to the state itself. That is why it is not a [Banner]. A banner is
 * a notice about something unresolved and carries a tone that ranks it; this is
 * an annotation on the content it sits under, and ranking it would be wrong —
 * "Terms are frozen and read-only now" is not a warning, it is the page
 * explaining itself.
 *
 * The fill is the accent at 22% rather than `brandSoft` (12%): at banner size
 * over `page` the softer tint is nearly invisible, and the design draws this one
 * a step stronger precisely so the block reads as a block. Same derivation
 * [Pill] uses for its status washes — the tint follows the token, so retuning
 * the accent retunes this.
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
            .background(colors.accent.copy(alpha = NOTE_FILL))
            .border(dimens.size.hairline, colors.accent.copy(alpha = NOTE_LINE), shape)
            .padding(horizontal = dimens.space.lg, vertical = dimens.space.md),
        content = content,
    )
}

/** The common case: an info glyph and a paragraph. */
@Composable
fun AccentNote(text: String, modifier: Modifier = Modifier) =
    AccentNote(AnnotatedString(text), modifier)

/**
 * The same, with emphasis inside the sentence — "You told us the reason was
 * **the event moved**". The bold run is the caller's, because only the caller
 * knows which words are the user's own answer being read back to them.
 */
@Composable
fun AccentNote(text: AnnotatedString, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    AccentNoteCard(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = colors.accentInk,
                modifier = Modifier.size(dimens.size.iconLg),
            )
            Text(text, style = AppTheme.type.subtitle, color = colors.ink2)
        }
    }
}

/** The accent at 22% — the design's own wash for this block, not `brandSoft`. */
private const val NOTE_FILL = 0.22f

/** Its rim: the same hue at 60%, which is what makes the wash read as an edge. */
private const val NOTE_LINE = 0.6f

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun AccentNotePreview() {
    ArtistantTheme {
        Column(Modifier.padding(AppTheme.dimens.component.gutter)) {
            AccentNote(
                "Terms are frozen and read-only now. The thread stays open so you " +
                    "can rebook or explain.",
            )
        }
    }
}
