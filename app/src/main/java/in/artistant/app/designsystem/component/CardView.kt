package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * A container surface — hairline border, no drop shadow / heavy chrome (the
 * design rule). Just bgCard + a 1dp line + rounded corners.
 */
@Composable
fun CardView(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.dimens.radii.lg)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.bgCard)
            .border(1.dp, colors.line, shape)
            .padding(AppTheme.dimens.space.lg),
        content = content,
    )
}
