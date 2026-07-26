package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Fixed-aspect media surface with a never-empty gradient floor (iOS
 * `MediaContainer`). Coil's [AsyncImage] draws the [remoteUrl] cover cropped to
 * fill; while it loads or on failure it renders transparent, so the gradient
 * painted BEHIND it shows through — the same "photo in `.background()`, gradient
 * is the floor" trick iOS uses (HANDOFF §9), which also means the image can
 * never push the container's measured size.
 *
 * Video covers are M5 — this is image + gradient only.
 */
@Composable
fun MediaContainer(
    aspectRatio: Float,
    remoteUrl: String?,
    fallbackGradient: List<Color>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    contentDescription: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(fallbackGradient)),
    ) {
        if (!remoteUrl.isNullOrEmpty()) {
            AsyncImage(
                model = remoteUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        overlay()
    }
}

/**
 * The 4-stop bottom-darken scrim (iOS `BottomDarkenScrim`) — gentle top vignette,
 * clear middle, deep bottom for headline legibility over any cover. Drop it as
 * the first overlay child above a [MediaContainer]'s image.
 */
@Composable
fun BoxScope.BottomDarkenScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    // location → alpha, mirroring the iOS stops.
                    0.0f to Color.Black.copy(alpha = 0.20f),
                    0.30f to Color.Transparent,
                    0.65f to Color.Black.copy(alpha = 0.45f),
                    1.0f to Color.Black.copy(alpha = 0.85f),
                ),
            ),
    )
}
