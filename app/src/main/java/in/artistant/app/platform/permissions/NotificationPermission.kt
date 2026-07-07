package `in`.artistant.app.platform.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

/**
 * POST_NOTIFICATIONS request path (API 33+). Used later at the notif onboarding step
 * (M1b) and before push registration. On API < 33 the permission is granted at install,
 * so [isNotificationPermissionGranted] returns true and the request is a no-op.
 *
 * ponytail: the runtime permission is the only Android-specific bit; no wrapper class —
 * a top-level check + a Compose launcher remember is the whole surface.
 */
fun isNotificationPermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        true
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

/**
 * Remembers a launcher that requests POST_NOTIFICATIONS and reports the grant result.
 * On pre-33 devices invoking [rememberNotificationPermissionRequest]'s returned lambda
 * immediately calls [onResult] with true (nothing to request).
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult,
    )
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onResult(true)
            else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
