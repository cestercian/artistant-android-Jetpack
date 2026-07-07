package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import `in`.artistant.app.designsystem.theme.AppTheme

/** Tone drives the capsule fill + text color. Neutral is the default chrome-free chip. */
enum class PillTone { Neutral, Brand, Good, Warm, Hot }

/** Small capsule label — the Pill port. Mono-ish caption text, no border. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Neutral) {
    val colors = AppTheme.colors
    val (bg, fg) = when (tone) {
        PillTone.Neutral -> colors.bgSoft to colors.ink2
        PillTone.Brand -> colors.brandSoft to colors.brand
        PillTone.Good -> Color(0x1A5BE074) to colors.good
        PillTone.Warm -> Color(0x1AFFB454) to colors.warm
        PillTone.Hot -> Color(0x1AFF5A5F) to colors.hot
    }
    Text(
        text = text,
        style = AppTheme.type.caption,
        color = fg,
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = AppTheme.dimens.space.md, vertical = AppTheme.dimens.space.xs),
    )
}
