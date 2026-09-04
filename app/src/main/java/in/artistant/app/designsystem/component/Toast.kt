package `in`.artistant.app.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.designsystem.theme.motion

/**
 * The transient confirmation: a dark capsule with an accent tick and one line of
 * copy, floating above the tab bar (screen 77).
 *
 * **It must not intercept touches once it clears.** That is the design's own
 * note, and it is the reason this is not a `Box(Modifier.fillMaxSize())` overlay
 * with an alpha on it: an invisible full-screen node still hit-tests, so a
 * faded-out toast would quietly eat every tap on the page under it. Here the
 * host composes NOTHING when there is no message — `AnimatedVisibility` removes
 * the node when the exit finishes — and even while visible the capsule only
 * occupies its own bounds and carries no click handler.
 *
 * Dark on a light page, deliberately. A toast is the one piece of UI that has to
 * be readable against whatever is behind it without knowing what that is, and
 * the palette's dark surfaces exist for exactly these moments (the splash and
 * this).
 *
 * The copy states the fact — "Venue address copied", not "Success!". That is a
 * house rule, not a suggestion.
 *
 * @param message the line to show, or null for nothing at all.
 * @param onDismiss called when the display window elapses, so the caller can
 *   clear its own state. Without it the toast would show once and stick.
 * @param key identity of the CURRENT message, when the caller has one.
 *
 * The display timer is keyed on this rather than on [message], because two
 * toasts carrying the same string are two toasts: keyed on the text, the second
 * one restarts nothing and is dismissed early by the first one's already-running
 * delay. Callers with no identity to hand leave it null and get the old
 * behaviour, where the text IS the key.
 */
@Composable
fun BoxScope.ToastHost(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Check,
    bottomPadding: Dp = AppTheme.dimens.component.toastGap,
    key: Any? = null,
) {
    // Latched so the text does not blank out mid-exit: the caller clears
    // `message` to start the dismissal, and the capsule needs something to keep
    // drawing while it slides away.
    var shown by remember { mutableStateOf(message) }
    if (message != null) shown = message

    LaunchedEffect(key ?: message) {
        if (message == null) return@LaunchedEffect
        kotlinx.coroutines.delay(TOAST_MILLIS)
        onDismiss()
    }

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(
                horizontal = AppTheme.dimens.component.gutter,
                vertical = bottomPadding,
            ),
        enter = fadeIn(androidx.compose.animation.core.tween(AppTheme.motion.tabSwitch)) +
            slideInVertically(
                androidx.compose.animation.core.tween(AppTheme.motion.tabSwitch),
            ) { it / SLIDE_DIVISOR },
        exit = fadeOut(androidx.compose.animation.core.tween(AppTheme.motion.tabSwitch)) +
            slideOutVertically(
                androidx.compose.animation.core.tween(AppTheme.motion.tabSwitch),
            ) { it / SLIDE_DIVISOR },
    ) {
        Toast(text = shown.orEmpty(), icon = icon)
    }
}

/**
 * The capsule itself, without the host's timing or placement — for a caller that
 * owns both (a preview, a screen with its own overlay stack).
 */
@Composable
fun Toast(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Check,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(dimens.space.md, RoundedCornerShape(dimens.radii.buttonLg))
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.ink)
            .padding(horizontal = dimens.space.lg, vertical = dimens.space.md)
            .semantics {
                // Announced when it appears rather than when focus reaches it —
                // a toast the user has to go looking for is not a toast.
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(
            Modifier
                .size(dimens.component.toastIcon)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
        Text(
            text = text,
            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How long a toast stays up. Long enough to read one line, no longer. */
private const val TOAST_MILLIS = 2_600L

/** The toast slides in from a fraction of its own height, not a whole screen. */
private const val SLIDE_DIVISOR = 2

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun ToastPreview() {
    ArtistantTheme {
        Box(Modifier.padding(AppTheme.dimens.component.gutter)) {
            Toast("Venue address copied")
        }
    }
}
