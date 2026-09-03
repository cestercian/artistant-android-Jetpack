package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A settings/navigation row: optional leading slot, title, optional subtitle,
 * and a trailing chevron or value — separated from the next row by a hairline
 * (screen 26).
 *
 * The hairline is drawn as an overlay on the row's own bottom edge
 * ([hairlineBottom]) rather than laid out as a sibling. A `HorizontalDivider`
 * between rows has a height, so a stack of N rows comes out N units taller than
 * the rows themselves and the row pitch stops being the row height — a small
 * error that compounds down a settings list.
 *
 * [value] and the chevron are alternatives, not both: a row that states a value
 * ("UPI · 2 cards" sits under the title as [subtitle]; "On" sits at the trailing
 * edge as [value]) is answering a question, and one that pushes a screen is
 * asking one. Showing both makes the row look like the value is the tap target.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showHairline: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(if (showHairline) Modifier.hairlineBottom() else Modifier)
            .defaultMinSize(minHeight = dimens.component.row)
            .padding(vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, subtitle, value).joinToString(". ")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.type.rowTitle.copy(fontSize = AppTheme.type.body.fontSize),
                color = if (destructive) colors.danger else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        when {
            trailing != null -> trailing()
            value != null -> Text(
                text = value,
                style = AppTheme.type.subtitle,
                color = colors.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            onClick != null -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.ink4,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun ListRowPreview() {
    ArtistantTheme {
        Column(Modifier.padding(horizontal = AppTheme.dimens.component.gutter)) {
            ListRow("Saved artists", subtitle = "12 acts", onClick = {})
            ListRow("Notifications", onClick = {})
            ListRow("Sync gigs to calendar", value = "On")
            ListRow("Delete account", onClick = {}, destructive = true, showHairline = false)
        }
    }
}
