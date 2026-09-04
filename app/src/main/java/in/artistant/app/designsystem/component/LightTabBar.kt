package `in`.artistant.app.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween

/**
 * One destination in the [LightTabBar].
 *
 * [route] doubles as the stable selection identity AND as the test-tag suffix
 * (`tab.<route>`), so a UI test can address a tab without depending on its
 * label. The label is not drawn — see the bar's doc — but it IS the tab's
 * accessibility name, so it still has to be right.
 */
data class LightTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** The raised accent circle in the middle of the bar. */
data class LightTabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The app's global navigation chrome: an opaque light bar carrying four glyphs
 * with one raised accent circle between them (screens 02 / 10 / 19 / 26).
 *
 * Three things changed from the dark design's floating pill, and each is a
 * decision rather than a restyle:
 *
 * 1. **Pinned and opaque, not floating and translucent.** Content stops at the
 *    hairline instead of scrolling behind a blurred pane, which removes the
 *    whole two-layer scrim+veil approximation the old bar needed — there is
 *    nothing to see through any more, so there is nothing to fake.
 * 2. **No labels by default.** The design draws glyphs only. Every label is
 *    still carried as the cell's `contentDescription`, so a screen reader loses
 *    nothing; what is lost is four words of chrome off a phone screen. The
 *    accessibility screen (design 129) turns them back on for people who read the
 *    word faster than the pictogram — see [showLabels], which is an opt-IN rather
 *    than a second opinion about the default.
 * 3. **Selection is tint, not a travelling capsule.** Active `ink`, inactive
 *    `ink4`. A sliding highlight was the old bar's one piece of continuity
 *    motion; on an opaque light bar it reads as a grey lozenge sliding about,
 *    which is the Material convention this design is deliberately not.
 *
 * The centre [action] is a peer of nothing — it is the screen's primary verb,
 * lifted above the bar so it reads as an action rather than a fifth place. It
 * carries the app's one accent, which is why no tab cell ever tints lime.
 *
 * **On the design's 88 and why it is not hard-coded.** 34 of that 88 is iPhone
 * home-indicator zone — a fixed inset on a device with exactly one bezel.
 * Android's equivalent arrives at runtime and varies (gesture nav ~24dp,
 * three-button ~48dp), so the bar composes its height instead: `barTopPad` +
 * `tabIcon` + `barBottomPad` of real content, plus whatever the system says it
 * owes the navigation bar. That lands on ~88 for the hardware the design was
 * drawn against, and stays right on the hardware it was not.
 */
@Composable
fun LightTabBar(
    items: List<LightTabItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    action: LightTabAction? = null,
    /**
     * Draw each glyph's label under it — Accessibility, "Always show labels".
     *
     * Defaulted off, so every existing call site keeps the design's unlabelled bar and only a
     * host that reads the preference passes anything. The bar grows by the label's line height
     * when it is on and nothing else moves, because the height was already composed from its
     * parts rather than hard-coded.
     */
    showLabels: Boolean = false,
) {
    val colors = AppTheme.colors
    val chrome = AppTheme.dimens.chrome

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        HRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = chrome.barPadH,
                    end = chrome.barPadH,
                    top = chrome.barTopPad,
                    bottom = chrome.barBottomPad + systemNavigationInset(),
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // The action sits in the MIDDLE of the row, so the cells split
            // evenly either side of it. With an even item count that is exactly
            // two and two, which is what the design draws.
            val half = items.size / 2
            items.take(half).forEach {
                TabGlyph(it, it.route == selectedRoute, onSelect, showLabels)
            }
            action?.let { CentreAction(it) }
            items.drop(half).forEach {
                TabGlyph(it, it.route == selectedRoute, onSelect, showLabels)
            }
        }
    }
}

@Composable
private fun TabGlyph(
    item: LightTabItem,
    selected: Boolean,
    onSelect: (String) -> Unit,
    showLabels: Boolean,
) {
    val colors = AppTheme.colors
    val chrome = AppTheme.dimens.chrome
    val motion = AppTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.ink4,
        animationSpec = motionTween<Color>(motion.indicator, motion.standard),
        label = "tabTint",
    )
    Box(
        modifier = Modifier
            // The glyph is 24; the node is the 48dp tap-target floor around it.
            .size(AppTheme.dimens.size.controlMin)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(bounded = false),
                role = Role.Tab,
                onClick = { onSelect(item.route) },
            )
            // One description for the whole cell, so a screen reader announces
            // the tab once rather than once per layer.
            .semantics { testTag = "tab.${item.route}"; contentDescription = item.label },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(chrome.tabIcon)
                    .pressScale(interaction),
            )
            // The cell already carries the label as its content description, so this text is
            // decorative to a screen reader and must not be announced twice.
            if (showLabels) {
                Text(
                    text = item.label,
                    style = AppTheme.type.tabLabel,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun CentreAction(action: LightTabAction) {
    val colors = AppTheme.colors
    val chrome = AppTheme.dimens.chrome
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            // Lifted above the bar's top edge. Nothing clips it — a `Scaffold`
            // draws its bottom bar unclipped — so the circle breaks the
            // hairline, which is what makes it read as sitting ON the bar
            // rather than inside it.
            .offset(y = -chrome.actionLift)
            .size(chrome.actionSize)
            .clip(CircleShape)
            .background(colors.accent)
            .selectable(
                selected = false,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = colors.onAccent),
                role = Role.Button,
                onClick = action.onClick,
            )
            .semantics { testTag = "tab.action"; contentDescription = action.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = colors.onAccent,
            modifier = Modifier
                .size(chrome.actionIcon)
                .pressScale(interaction),
        )
    }
}

@Composable
private fun systemNavigationInset(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun LightTabBarPreview() {
    ArtistantTheme {
        LightTabBar(
            items = listOf(
                LightTabItem("discover", "Discover", Icons.Filled.Home),
                LightTabItem("search", "Search", Icons.Filled.Search),
                LightTabItem("messages", "Messages", Icons.Filled.ChatBubbleOutline),
                LightTabItem("profile", "Profile", Icons.Filled.PersonOutline),
            ),
            selectedRoute = "discover",
            onSelect = {},
            action = LightTabAction("New booking", Icons.Filled.Add) {},
        )
    }
}
