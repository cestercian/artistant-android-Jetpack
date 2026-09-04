package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The action bar pinned to the bottom of a pushed screen — screens 20 / 52 / 95
 * / 96 / 97 / 117 all end in one.
 *
 * Not [dockSurface]. That modifier rounds its top corners, which is right for a
 * panel that rises over a page (the funnel's dock reads as a sheet that stopped
 * halfway). This bar is not a panel: the light design draws it as the page's own
 * bottom edge — a full-bleed white band under a hairline — so rounding it would
 * cut two notches out of the screen and show the scroll through them.
 *
 * The hairline is the whole separation. There is no shadow and no scrim, which
 * is why the band is opaque [AppColors.surface] rather than the design's
 * `rgba(255,255,255,.96)`: at 96% the content scrolling under it stays faintly
 * visible, and a smear behind a destructive button is worse than a clean edge.
 *
 * The navigation-bar inset is applied here rather than by each caller, because
 * this is by definition the surface against the bottom of the display.
 */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hairlineTop()
            .background(colors.surface)
            .padding(
                start = dimens.component.gutter,
                end = dimens.component.gutter,
                top = dimens.space.lg,
                bottom = dimens.space.lg +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
        content = content,
    )
}
