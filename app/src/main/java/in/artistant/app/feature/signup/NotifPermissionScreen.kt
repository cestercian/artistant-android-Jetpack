package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.navigation.rememberPushService
import `in`.artistant.app.platform.permissions.rememberNotificationPermissionRequest

/** The three things a notification will actually be about (screen 13). */
private val notificationKinds = listOf<Pair<ImageVector, String>>(
    Icons.Filled.RequestQuote to "New quotes and replies",
    Icons.Filled.EventAvailable to "Show-day reminders",
    Icons.Filled.Update to "Booking status changes",
)

/**
 * Screen 13 — **ask with a reason**.
 *
 * The design's note is a instruction about copy: "names the loss the host avoids — three
 * types, no vague 'stay updated'." So the headline is the consequence ("Quotes expire. We'll
 * tell you first."), the body is the mechanism that makes it true (artists reply in about an
 * hour, a hold lasts 48), and the three rows say exactly what will be sent. The old copy —
 * "Stay in the loop" over "Gig requests, booking confirmations, and status updates" — is the
 * vague version the note rules out.
 *
 * Both buttons advance. The permission result decides whether push works later; it does not
 * decide whether the user gets to finish signing up, and a screen that will not let you past
 * it is a screen people learn to deny out of spite.
 */
@Composable
fun NotifPermissionScreen(
    progress: ProgressBar?,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var requesting by remember { mutableStateOf(false) }
    val pushService = rememberPushService()
    val requestPermission = rememberNotificationPermissionRequest { granted ->
        requesting = false
        if (granted) pushService.registerAfterPermission()
        onAdvance()
    }
    NotifPermissionContent(
        progress = progress,
        requesting = requesting,
        onAllow = { requesting = true; requestPermission() },
        onSkip = onAdvance,
        modifier = modifier,
    )
}

@Composable
private fun NotifPermissionContent(
    progress: ProgressBar?,
    requesting: Boolean,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.notif" },
        header = if (progress != null) {
            { SignupHeader(middle = { SignupProgressStrip(progress) }) }
        } else {
            null
        },
        // The design fills this screen to the viewport: the two actions sit at the bottom of
        // the body rather than in a pinned bar, because there is nothing above them to scroll.
        scrollable = false,
    ) {
        Spacer(Modifier.height(space.xl))
        Box(
            Modifier
                .size(dimens.component.emptyGlyphCircle)
                .clip(RoundedCornerShape(dimens.radii.xl))
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconXl),
            )
        }

        Spacer(Modifier.height(space.xl))
        Text(
            "Quotes expire.\nWe'll tell you first.",
            style = AppTheme.type.displayHero,
            color = colors.ink,
        )
        Spacer(Modifier.height(space.md))
        Text(
            "Artists reply in about an hour and a hold lasts 48. Alerts are how you keep the date.",
            style = AppTheme.type.body,
            color = colors.ink4,
        )

        Spacer(Modifier.height(space.xl))
        notificationKinds.forEach { (glyph, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = space.md)
                    .clip(RoundedCornerShape(dimens.radii.buttonLg))
                    .background(colors.surface3)
                    .padding(space.lg)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                Box(
                    Modifier
                        .size(dimens.component.iconCircleSm)
                        .clip(CircleShape)
                        .background(colors.hairline),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        glyph,
                        contentDescription = null,
                        tint = colors.ink2,
                        modifier = Modifier.size(dimens.size.iconLg),
                    )
                }
                Text(
                    label,
                    style = AppTheme.type.rowTitle,
                    color = colors.ink,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = if (requesting) "Requesting…" else "Turn on alerts",
            onClick = onAllow,
            fullWidth = true,
            enabled = !requesting,
            modifier = Modifier.semantics { testTag = "notif.allow" },
        )
        Spacer(Modifier.height(space.sm))
        Text(
            "Not now",
            style = AppTheme.type.rowTitle.copy(
                fontSize = AppTheme.type.body.fontSize,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.ink4,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radii.buttonLg))
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(vertical = space.md)
                .semantics { testTag = "notif.skip" },
        )
        Spacer(Modifier.height(space.lg))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 820)
@Composable
private fun NotifPermissionPreview() {
    ArtistantTheme {
        NotifPermissionContent(progress = null, requesting = false, onAllow = {}, onSkip = {})
    }
}
