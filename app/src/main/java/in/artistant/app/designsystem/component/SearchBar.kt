package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The editable search field: 48dp, radius 15, `surface3`, hint in `hint`
 * (REDESIGN_2026-09 §2).
 *
 * `BasicTextField` rather than an M3 `TextField`, which would bring an indicator
 * line, its own container colour and a floating label — three pieces of Material
 * chrome the design does not draw. What is wanted here is the geometry and the
 * text behaviour; everything visible is ours.
 */
@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    SearchShell(modifier = modifier) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = LocalTextStyle.current.merge(
                    AppTheme.type.body.copy(color = colors.ink),
                ),
                singleLine = true,
                cursorBrush = SolidColor(colors.accentInk),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(
                    text = hint,
                    style = AppTheme.type.body,
                    color = colors.hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value.isNotEmpty() && onClear != null) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = colors.ink4,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .clickable(role = Role.Button, onClick = onClear)
                    .size(dimens.size.iconLg),
            )
        }
        trailing?.invoke(this)
    }
}

/**
 * The same bar, not editable — a button that opens the search screen.
 *
 * Discover draws this (screen 02): tapping it navigates rather than raising a
 * keyboard, because the search experience there is a whole screen with recents
 * and filters, not a field on the feed. Giving it a real text field would put a
 * cursor in a control that cannot accept one.
 */
@Composable
fun SearchBarButton(
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    SearchShell(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(),
            role = Role.Button,
            onClick = onClick,
        ),
    ) {
        Text(
            text = hint,
            style = AppTheme.type.body,
            color = colors.hint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** The shared shell — fill, radius, height, gutter, leading magnifier. */
@Composable
private fun SearchShell(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.component.searchBar)
            .clip(RoundedCornerShape(dimens.radii.control))
            .background(colors.surface3)
            .padding(horizontal = dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = colors.hint,
            modifier = Modifier.size(dimens.size.iconLg),
        )
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun SearchBarPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            SearchBarButton("Search artists, genres, cities", onClick = {})
            SearchBar(
                value = "throat singing",
                onValueChange = {},
                hint = "Search artists, genres, cities",
                onClear = {},
            )
        }
    }
}
