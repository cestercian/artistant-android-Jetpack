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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
                // The disc is a saturated hash gradient at v=0.40–0.55 — dark,
                // whatever the palette around it does. So the monogram takes the
                // dark-surface ink, not `ink`, which followed the page into
                // daylight and left the initials unreadable on their own circle.
                color = colors.onDark,
                // Dp → Sp through the density, NOT `size.value.sp`. That form
                // hands a dp magnitude to the sp scale, so the glyph tracked the
                // user's font-scale setting while the disc around it stayed a
                // fixed Dp — at 2× a 48dp avatar asked for a ~36sp monogram and
                // the initials ran outside their own circle.
                style = AppTheme.type.callout.copy(
                    fontSize = with(LocalDensity.current) { (size * 0.38f).toSp() },
                    fontWeight = FontWeight.Bold,
                ),
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

/**
 * The name-derived gradient an [Avatar] paints, for surfaces that need the same
 * fill on a different silhouette — the inbox thumbnail is a rounded square, not
 * a circle, but an un-hydrated one has to carry the same colour as the initials
 * badge sitting on it or the pair reads as two unrelated objects.
 */
fun avatarGradient(name: String): Brush = gradientFor(name)

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
