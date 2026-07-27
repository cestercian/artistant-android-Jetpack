package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Initials avatar with a deterministic DJB2 hue gradient — port of iOS `Avatar`.
 * Optional [ring] draws a brand stroke; [badge] is a bottom-trailing status dot.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = AppTheme.dimens.size.avatarMd,
    ring: Boolean = false,
    badge: Color? = null,
) {
    val colors = AppTheme.colors
    val initials = remember(name) { initialsFor(name) }
    val gradient = remember(name) { gradientFor(name) }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(gradient)
                .then(
                    if (ring) Modifier.border(AppTheme.dimens.size.stroke, colors.brand, CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        badge?.let { dot ->
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(dot)
                    .border(AppTheme.dimens.size.stroke, colors.bg, CircleShape),
            )
        }
    }
}

private fun initialsFor(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifEmpty { "?" }

/** DJB2 hash → hue pair, matching iOS `Avatar.gradient`. */
private fun gradientFor(name: String): Brush {
    var h = 5381L
    for (c in name) {
        h = ((h shl 5) + h) + c.code
    }
    val hue = ((((h % 360) + 360) % 360).toFloat()) / 360f
    val hue2 = (hue + 0.08f) % 1f
    return Brush.linearGradient(
        colors = listOf(
            Color.hsv(hue * 360f, 0.55f, 0.55f),
            Color.hsv(hue2 * 360f, 0.70f, 0.40f),
        ),
    )
}
