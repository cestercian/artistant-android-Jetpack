package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A settings row whose control is a switch (design screens 47 / 69 / 124 / 129).
 *
 * [ListRow]'s sibling — same title/subtitle typography, same hairline, same 56dp floor — so a
 * list that mixes pushes and toggles keeps one rhythm. It is a separate function rather than a
 * `trailing = { Switch(…) }` on [ListRow] because the switch has to own the ROW's tap: a
 * `ListRow` with an `onClick` draws a chevron and announces itself as a button, and a row that
 * is both a button and a switch is two controls fighting over one rectangle.
 *
 * **The tap target is the switch alone, deliberately.** The design's rows are dense (eight of
 * them on screen 124), and making the whole row toggle is how a scroll that starts on a label
 * flips a setting nobody meant to touch. Material's `Switch` already expands itself to the
 * 48dp minimum, so the control is comfortably hittable on its own.
 *
 * M3's `Switch` for the behaviour — thumb drag, state announcement, the a11y role — repainted
 * in the tokens, never an M3 default colour (REDESIGN_2026-09 §7).
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    showHairline: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (showHairline) Modifier.hairlineBottom() else Modifier)
            .defaultMinSize(minHeight = dimens.component.row)
            // `sm`, not the `md` [ListRow] uses: Material's Switch expands to the 48dp
            // minimum interactive size, so it already carries 8dp of slop above and below its
            // own track. Padding it like a text row double-counts that and makes every toggle
            // the tallest thing in the list.
            .padding(vertical = dimens.space.sm),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(title, subtitle).joinToString(". ")
                },
        ) {
            Text(
                title,
                style = AppTheme.type.rowTitle.copy(fontSize = AppTheme.type.body.fontSize),
                color = if (enabled) colors.ink else colors.ink4,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.surface,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.hairline,
                uncheckedBorderColor = colors.lineStrong,
                disabledCheckedThumbColor = colors.surface,
                disabledCheckedTrackColor = colors.hairline,
                disabledCheckedBorderColor = colors.hairline,
                disabledUncheckedThumbColor = colors.surface,
                disabledUncheckedTrackColor = colors.hairline,
                disabledUncheckedBorderColor = colors.hairline,
            ),
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SwitchRowPreview() {
    ArtistantTheme {
        Column(Modifier.padding(AppTheme.dimens.component.gutter)) {
            SwitchRow("Quotes and replies", true, {}, subtitle = "Push + email")
            SwitchRow("Tips and offers", false, {}, subtitle = "Product news and promotions")
            SwitchRow(
                "Sync gigs to calendar",
                false,
                {},
                subtitle = "Calendar access is off — tap to enable",
                showHairline = false,
            )
        }
    }
}
