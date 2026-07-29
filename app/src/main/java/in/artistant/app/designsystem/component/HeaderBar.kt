package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Compact screen header — port of iOS `HeaderBar`.
 * Title (+ optional subtitle) between optional leading/trailing slots,
 * with a hairline rule under the bar.
 */
@Composable
fun HeaderBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(modifier.fillMaxWidth().background(colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.lg, vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Box(Modifier.widthIn(min = AppTheme.dimens.size.avatarSm), contentAlignment = Alignment.CenterStart) {
                leading?.invoke()
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
                subtitle?.let {
                    Text(it, style = AppTheme.type.footnote, color = colors.ink3)
                }
            }
            Box(Modifier.widthIn(min = AppTheme.dimens.size.avatarSm), contentAlignment = Alignment.CenterEnd) {
                trailing?.invoke()
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(AppTheme.dimens.size.hairline)
                .background(colors.lineSoft),
        )
    }
}
