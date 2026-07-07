package `in`.artistant.app.designsystem.component

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme

/** A 1dp hairline in the `line` token — the HRule port. */
@Composable
fun HRule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = AppTheme.colors.line)
}
