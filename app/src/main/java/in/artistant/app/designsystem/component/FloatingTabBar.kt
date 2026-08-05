package `in`.artistant.app.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.max
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.MotionSpecs
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion
import kotlin.math.roundToInt

/**
 * One entry in the [FloatingTabBar] pill.
 *
 * [route] doubles as the stable identity used for selection AND as the test-tag
 * suffix (`tab.<route>`), so a UI test can address a tab without depending on
 * its label copy.
 */
data class FloatingTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** The detached circular action beside the pill (client side: Search). */
data class FloatingTabAction(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * The app's global navigation chrome: a floating, detached tab bar.
 *
 * ```
 *  ╭─────────────────────────────────────────────╮   ╭───────╮
 *  │ (Discover) Bookings  Messages  Profile      │   │   Q   │
 *  ╰─────────────────────────────────────────────╯   ╰───────╯
 *   4-tab capsule, selected item in its own          detached
 *   lighter capsule                                  action
 * ```
 *
 * Three things make this deliberately un-Material:
 *
 * 1. **It floats.** Inset from all three edges rather than a full-bleed strip
 *    pinned to the bottom, so page content runs behind and around it.
 * 2. **Search is not a tab.** When [trailing] is supplied it renders as a
 *    separate circle beside the pill rather than as a fifth cell. Search is an
 *    action on the current context, not a peer destination, and the shape says so.
 * 3. **No blur.** The reference design floats this on a blurred material; blur
 *    is intentionally excluded here, so the fill is the flat two-layer
 *    scrim+veil approximation documented on `AppColors.glassScrim`.
 *
 * Geometry lives in `AppTheme.dimens.chrome`; nothing here inlines a raw dp.
 */
@Composable
fun FloatingTabBar(
    items: List<FloatingTabItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: FloatingTabAction? = null,
) {
    val chrome = AppTheme.dimens.chrome
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = chrome.tabBarInset,
                end = chrome.tabBarInset,
                top = chrome.tabBarTopGap,
                // The bar sits `tabBarInset` off the bottom edge — but never
                // closer to the edge than the system navigation bar, which on
                // 3-button nav is far taller than the reference platform's home
                // indicator. Taking the max preserves the intended look on
                // gesture nav (inset ≈ 24dp, near the design's 21) without
                // burying the bar under nav buttons.
                bottom = max(chrome.tabBarInset, systemNavigationInset()),
            ),
        verticalAlignment = Alignment.Bottom,
    ) {
        // The pill is a stack, not just a Row: one selection capsule slides
        // beneath the cells rather than each cell painting its own. See
        // [SelectionCapsule] for why that distinction matters.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(chrome.tabBarHeight)
                .glassSurface(CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
            SelectionCapsule(
                selectedIndex = selectedIndex,
                cellWidth = maxWidth / items.size.coerceAtLeast(1),
                height = chrome.tabSelectionHeight,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    TabCell(
                        item = item,
                        selected = item.route == selectedRoute,
                        onClick = { onSelect(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        trailing?.let { action ->
            Spacer(Modifier.width(chrome.tabBarGap))
            TrailingCircle(action)
        }
    }
}

/**
 * The travelling selection capsule.
 *
 * This is the app's one piece of *continuity* motion: the highlight is a single
 * object that moves between tabs, rather than one capsule switching off while
 * another switches on. The difference is the whole point — a moving highlight
 * says "you are still in the same bar, now over here", which is what makes four
 * peer destinations feel like one control instead of four.
 *
 * Positioned by translating a single cell-width box. Cells are equal-width
 * (`weight(1f)` each), so the capsule's left edge is exactly `index × cellWidth`
 * and no measurement of the individual cells is needed.
 *
 * `Modifier.offset {}` takes a lambda so the animated position is read in the
 * LAYOUT phase: sliding the capsule re-lays-out one box and skips recomposition
 * entirely. The alpha rides in the same deferred way via [graphicsLayer].
 *
 * @param selectedIndex index into the pill's items, or -1 when the active
 *   destination is not in the pill at all (the client's Search circle) — the
 *   capsule then fades out in place rather than sliding to a wrong tab.
 */
@Composable
private fun BoxScope.SelectionCapsule(
    selectedIndex: Int,
    cellWidth: Dp,
    height: Dp,
) {
    val colors = AppTheme.colors
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val spec = tween<Float>(
        durationMillis = MotionSpecs.durationMillis(motion.indicator, reduceMotion),
        easing = motion.emphasized,
    )

    // Hold the last in-pill position so leaving for Search fades the capsule out
    // where it stood; without this it would animate to index 0 on the way out
    // and back from index 0 on the way in — motion describing a journey the user
    // never took.
    var lastIndex by remember { mutableIntStateOf(selectedIndex.coerceAtLeast(0)) }
    if (selectedIndex >= 0) lastIndex = selectedIndex

    val position by animateFloatAsState(lastIndex.toFloat(), spec, label = "tabIndicator")
    val alpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = spec,
        label = "tabIndicatorAlpha",
    )

    Box(
        Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(x = (position * cellWidth.toPx()).roundToInt(), y = 0) }
            .width(cellWidth)
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(colors.glassSelected),
    )
}

/**
 * A single tab. Selection is carried by the shared [SelectionCapsule] behind the
 * content plus a brand-tinted glyph and label — NOT by dimming the unselected
 * ones. The reference design keeps every unselected tab at full-strength ink and
 * moves only the accent; the Material default recedes them to a grey that reads
 * as disabled.
 *
 * The tint cross-fades rather than switching, so the colour change lands on the
 * same beat as the capsule's arrival instead of a frame ahead of it.
 */
@Composable
private fun TabCell(
    item: FloatingTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val chrome = AppTheme.dimens.chrome
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (selected) colors.brand else colors.ink,
        animationSpec = tween(
            durationMillis = MotionSpecs.durationMillis(motion.indicator, reduceMotion),
            easing = motion.standard,
        ),
        label = "tabTint",
    )
    Box(
        modifier = modifier
            .height(chrome.tabSelectionHeight)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = colors.brand),
                role = Role.Tab,
                onClick = onClick,
            )
            // One description for the whole cell; the glyph and label below stay
            // silent so a screen reader announces the tab once, not three times.
            .semantics { testTag = "tab.${item.route}"; contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Only the glyph+label scale on press, never the cell box — scaling
            // the box would drag the selection capsule's geometry with it.
            modifier = Modifier.pressScale(interaction),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(chrome.tabIconLabelGap),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(chrome.tabIconSize),
            )
            Text(
                text = item.label,
                style = AppTheme.type.tabLabel,
                color = tint,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun TrailingCircle(action: FloatingTabAction) {
    val colors = AppTheme.colors
    val chrome = AppTheme.dimens.chrome
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    // No travelling capsule out here — the circle stands alone, so selection is
    // carried by the glyph tint. Cross-faded on the same curve and duration the
    // pill uses, so the two halves of the bar stay in step.
    val tint by animateColorAsState(
        targetValue = if (action.selected) colors.brand else colors.ink,
        animationSpec = tween(
            durationMillis = MotionSpecs.durationMillis(motion.indicator, reduceMotion),
            easing = motion.standard,
        ),
        label = "searchTint",
    )
    Box(
        modifier = Modifier
            .size(chrome.tabSearchSize)
            .glassSurface(CircleShape)
            .selectable(
                selected = action.selected,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = colors.brand),
                role = Role.Tab,
                onClick = action.onClick,
            )
            .semantics { testTag = "tab.${action.route}"; contentDescription = action.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(chrome.tabSearchIconSize)
                .pressScale(interaction),
        )
    }
}

@Composable
private fun systemNavigationInset(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Flat stand-in for the reference platform's blurred chrome material: clip to
 * [shape], darken whatever is behind, then lift the result. Two `background`
 * layers is the whole trick — the second draws over the first, which is the
 * compositing order we want. See `AppColors.glassScrim` for why one flat fill
 * can't do this job alone.
 */
@Composable
private fun Modifier.glassSurface(shape: Shape): Modifier {
    val colors = AppTheme.colors
    return this
        .clip(shape)
        .background(colors.glassScrim)
        .background(colors.glassVeil)
}

/**
 * The softer sibling of [glassSurface], for controls floating directly on a
 * photo (Discover's status capsule and save button). Same construction, gentler
 * alphas so a bright hero doesn't get flattened into grey.
 */
@Composable
fun Modifier.heroGlass(shape: Shape): Modifier {
    val colors = AppTheme.colors
    return this
        .clip(shape)
        .background(colors.glassSoftScrim)
        .background(colors.glassSoftVeil)
}
