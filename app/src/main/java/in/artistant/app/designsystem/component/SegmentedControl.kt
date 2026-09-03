package `in`.artistant.app.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween

/**
 * The inset segmented switch the design draws above a page that has two or three
 * views of the same subject — "Score · Stats · Opportunities" on screen 50.
 *
 * Not a [ChipRail]. A chip rail is a FILTER over a list: it scrolls, it can have
 * none or several selected, and its selected state is the screen's accent. This
 * is a VIEW SWITCH: fixed width, always exactly one selected, and the selected
 * segment is a raised hairline fill rather than lime — because the page below it
 * is spending the screen's one accent on its own content, and two accents
 * arguing across a fold is the thing §2 forbids.
 *
 * Selection is carried by fill *and* weight (700 vs 500), for the same reason
 * [Chip] carries both: the fill difference between `surface3` and `hairline` is
 * deliberately small, and weight is what survives it.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.control))
            .background(colors.surface3)
            .padding(dimens.space.xs),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        options.forEach { option ->
            Segment(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val motion = AppTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) colors.hairline else Color.Transparent,
        animationSpec = motionTween<Color>(motion.tabSwitch),
        label = "segmentFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.ink4,
        animationSpec = motionTween<Color>(motion.tabSwitch),
        label = "segmentInk",
    )
    Box(
        modifier = modifier
            // 36dp, composed rather than hard-coded: the switch has to stay
            // shorter than a control (50) and taller than a chip.
            .height(dimens.space.xl + dimens.space.md)
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppTheme.type.chip.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SegmentedControlPreview() {
    ArtistantTheme {
        Box(Modifier.padding(AppTheme.dimens.component.gutter)) {
            SegmentedControl(
                options = listOf("Score", "Stats", "Opportunities"),
                selected = "Opportunities",
                onSelect = {},
                label = { it },
            )
        }
    }
}
