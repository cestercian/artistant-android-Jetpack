package `in`.artistant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * M0 stand-in for every real screen: a centered label on the app background,
 * drawn with the design tokens so the theme is actually exercised. Replaced
 * per-feature as screens land.
 */
@Composable
fun Placeholder(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppTheme.type.displaySmall,
            color = AppTheme.colors.ink,
        )
    }
}
