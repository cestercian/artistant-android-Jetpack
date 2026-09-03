package `in`.artistant.app.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * A filter chip: fully rounded, 9×16 padding (REDESIGN_2026-09 §2).
 *
 * Selection is carried by the accent fill AND by weight — 700 selected, 500 not.
 * Both, deliberately: the fill alone fails for anyone who cannot separate lime
 * from `surface2`, and the weight alone is too quiet to find in a scrolling rail.
 *
 * The colour cross-fades rather than switching, so a rail of chips settles on the
 * same beat as the content behind it reloads.
 */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val motion = AppTheme.motion
    val interaction = remember { MutableInteractionSource() }

    val fill by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.surface2,
        animationSpec = motionTween<Color>(motion.tabSwitch),
        label = "chipFill",
    )
    val ink by animateColorAsState(
        targetValue = when {
            !enabled -> colors.ink4
            selected -> colors.onAccent
            else -> colors.ink2
        },
        animationSpec = motionTween<Color>(motion.tabSwitch),
        label = "chipInk",
    )

    Text(
        text = label,
        style = AppTheme.type.chip.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        ),
        color = ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .pressScale(interaction)
            .clip(CircleShape)
            .background(fill)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(color = ink),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(
                horizontal = dimens.component.chipPadH,
                vertical = dimens.component.chipPadV,
            ),
    )
}

/**
 * A horizontally scrolling rail of [Chip]s — the filter strip on Discover,
 * Messages and Search.
 *
 * `LazyRow` rather than a `Row` in a scroll: a category list is server-supplied
 * and open-ended, and the rail has to keep its item identity across a reload so
 * the selected chip does not jump. [key] is what gives it that identity.
 */
@Composable
fun <T> ChipRail(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    key: (T) -> Any = { label(it) },
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(
            horizontal = AppTheme.dimens.component.gutter,
        ),
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        contentPadding = contentPadding,
    ) {
        items(items, key = { key(it) }) { item ->
            Chip(
                label = label(item),
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun ChipPreview() {
    ArtistantTheme {
        Row(
            Modifier.padding(AppTheme.dimens.component.gutter),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        ) {
            Chip("For you", selected = true, onClick = {})
            Chip("Bands", selected = false, onClick = {})
            Chip("DJs", selected = false, onClick = {})
        }
    }
}
