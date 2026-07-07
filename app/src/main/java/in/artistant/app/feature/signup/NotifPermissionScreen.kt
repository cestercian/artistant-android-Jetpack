package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.platform.permissions.rememberNotificationPermissionRequest

/**
 * Notification permission ask (iOS `SignupNotifPermissionView`): bell, headline, subtitle, and
 * Allow / Maybe-later — both advance (no nag). "Allow" requests POST_NOTIFICATIONS via the M1a
 * permission helper (API 33+; a no-op grant on older devices). FCM token registration itself is
 * M4 — this only secures the permission.
 */
@Composable
fun NotifPermissionScreen(
    progress: ProgressBar?,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var requesting by remember { mutableStateOf(false) }
    // Whatever the user chooses (grant or deny), advance — the permission result doesn't gate
    // the flow, it only decides whether push works later.
    val requestPermission = rememberNotificationPermissionRequest { _ ->
        requesting = false
        onAdvance()
    }

    Column(
        modifier = modifier.fillMaxSize().background(colors.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SignupProgressDots(bar = progress)
        Spacer(Modifier.height(space.xxl))
        Spacer(Modifier.weight(1f))

        Icon(Icons.Outlined.Notifications, contentDescription = null, tint = colors.brand, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(space.xl))
        Text("Stay in the loop.", style = AppTheme.type.displayTitle, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(space.lg))
        Text(
            "Gig requests, booking confirmations, and status updates.",
            style = AppTheme.type.callout,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )

        Spacer(Modifier.weight(1f))

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = space.xl).padding(bottom = space.xxl), verticalArrangement = Arrangement.spacedBy(space.md)) {
            PrimaryButton(
                text = if (requesting) "Requesting…" else "Allow notifications",
                onClick = { requesting = true; requestPermission() },
                fullWidth = true,
                enabled = !requesting,
                modifier = Modifier.semantics { testTag = "notif.allow" },
            )
            PrimaryButton(
                text = "Maybe later",
                onClick = onAdvance,
                variant = ButtonVariant.Ghost,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "notif.skip" },
            )
        }
    }
}
