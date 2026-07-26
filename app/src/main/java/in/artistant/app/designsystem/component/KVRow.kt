package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * A key/value row — muted label left, value right (iOS `KVRow`). [bold]/[lime]/
 * [big] promote the value for a headline figure (e.g. the artist fee). No card
 * chrome; callers stack these with [HRule] between.
 */
@Composable
fun KVRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    lime: Boolean = false,
    big: Boolean = false,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key,
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink3,
        )
        Text(
            value,
            style = if (big) AppTheme.type.monoMedium else AppTheme.type.footnote.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (lime) colors.brand else colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
