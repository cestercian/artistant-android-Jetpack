package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * What one item in a checked list is doing.
 *
 * The four states are measured off the design, not invented: the export progress list draws
 * all of [Done] / [Active] / [Pending] on one screen (82) and swaps the middle for [Failed] on
 * the next (113), and the same dot does duty as a radio tick on the language picker (130) and
 * the delete-reason list (115) and as a perk bullet on the Pro screens (25 / 93 / 116).
 */
enum class MarkState { Done, Active, Pending, Failed }

/**
 * The 22dp mark itself: a filled accent disc with a check, an accent ring, a quiet outline, or
 * a danger disc with a cross.
 *
 * **Active is a RING, not a spinner.** The design draws the in-progress step as an unfilled
 * accent circle beside the words "In progress" — the state is carried by the sentence, and an
 * indeterminate spinner on a job measured in HOURS is the "narrated, not a spinner" rule
 * (REDESIGN_2026-09 §2) being broken in the one place it matters most. The single spinner on
 * the export screen is the big 40dp one at the top of the card, which is about the screen, not
 * about a step.
 */
@Composable
fun CheckDot(
    state: MarkState,
    modifier: Modifier = Modifier,
    size: Dp = AppTheme.dimens.component.checkbox,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val fill = when (state) {
        MarkState.Done -> colors.accent
        MarkState.Failed -> colors.dangerSoft
        MarkState.Active, MarkState.Pending -> Color.Transparent
    }
    val ring = when (state) {
        MarkState.Active -> colors.accent
        MarkState.Pending -> colors.lineStrong
        MarkState.Done, MarkState.Failed -> null
    }
    Box(
        modifier = modifier
            .size(size)
            .background(fill, CircleShape)
            .then(
                if (ring != null) {
                    Modifier.border(dimens.component.checkboxStroke, ring, CircleShape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            MarkState.Done -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(size * GLYPH_SHARE),
            )
            MarkState.Failed -> Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(size * GLYPH_SHARE),
            )
            MarkState.Active, MarkState.Pending -> Unit
        }
    }
}

/**
 * A [CheckDot] with a title and an optional second line beside it — the perk bullet, the
 * progress step, the radio row, the receipt item.
 *
 * [onClick] turns the row into a radio: the whole row is the target, because a 22dp disc is
 * under the 48dp minimum and asking someone to hit it exactly is how a settings list becomes
 * unusable one-handed. The content description merges title and subtitle.
 *
 * **A radio has to announce WHICH one is chosen**, and this used to announce only that it was a
 * radio. `Modifier.clickable(role = RadioButton)` sets the role and nothing else, so TalkBack
 * read six identical "radio button, Hindi / Marathi / …" rows with no way to hear which one was
 * already selected — the tick is drawn by [CheckDot], which is decorative, so a screen reader
 * user could not tell their current language or their picked deletion reason apart from the
 * five they had not chosen. `Modifier.selectable` carries the selected state into the semantics
 * tree, and [MarkState.Done] is what "selected" means on both of this row's radio call sites
 * (screens 130 and 115): the ticked one.
 */
@Composable
fun CheckRow(
    title: String,
    state: MarkState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    dotSize: Dp = AppTheme.dimens.component.checkbox,
    onClick: (() -> Unit)? = null,
    showHairline: Boolean = false,
    /** Pending steps grey their title out (screens 82 / 113); perk bullets never do. */
    dimWhenPending: Boolean = false,
    /**
     * Override the subtitle's colour.
     *
     * A [MarkState.Failed] subtitle defaults to `danger` because on the export screen it IS the
     * failure — "Stopped at bookings" is what went wrong (113). On the delete screen the same
     * mark means "this is what you lose", and the detail beside it is a plain fact ("Both sides
     * lose the thread history") that the design sets in `ink4`; painting four cards' worth of
     * body copy red there turns a careful list into an alarm. Hence an override rather than a
     * second component.
     */
    subtitleColor: Color? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val titleColor = when {
        state == MarkState.Failed -> colors.ink
        state == MarkState.Pending && dimWhenPending -> colors.ink4
        else -> colors.ink
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = state == MarkState.Done,
                        interactionSource = interaction,
                        indication = ripple(),
                        role = Role.RadioButton,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(if (showHairline) Modifier.hairlineBottom() else Modifier)
            .defaultMinSize(minHeight = dimens.size.rowMin)
            .padding(vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, subtitle).joinToString(". ")
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.Top,
    ) {
        CheckDot(state = state, size = dotSize)
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.rowTitle, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = AppTheme.type.caption,
                    color = subtitleColor
                        ?: if (state == MarkState.Failed) colors.danger else colors.ink4,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
    }
}

/**
 * The glyph inside the disc, as a share of the disc.
 *
 * A fraction rather than a Dp because [CheckDot] is drawn at two sizes (22 for a step or a
 * radio, 24 for a perk bullet) and a fixed glyph would be visibly off-centre-weight at one of
 * them. 0.6 is what the design's SVGs measure at both.
 */
private const val GLYPH_SHARE = 0.6f

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun CheckListPreview() {
    ArtistantTheme {
        Column(Modifier.padding(AppTheme.dimens.component.gutter)) {
            CheckRow("Request received", MarkState.Done, subtitle = "Just now")
            CheckRow("Collecting your records", MarkState.Active, subtitle = "In progress")
            CheckRow(
                "File ready to download",
                MarkState.Pending,
                subtitle = "We'll notify you here",
                dimWhenPending = true,
            )
            CheckRow("Collecting your records", MarkState.Failed, subtitle = "Stopped at bookings")
        }
    }
}
