package `in`.artistant.app.feature.wizard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Just-in-time media pickers for the wizard, built entirely on `ActivityResultContracts` (no
 * custom camera UI — the M5b spec's explicit ask). Each helper returns a `() -> Unit` you wire
 * to a button:
 *   - [rememberImageLibraryPicker] / [rememberVideoLibraryPicker] — the Photo Picker
 *     (`PickVisualMedia`); no storage permission needed on any API level.
 *   - [rememberAudioPicker] — the audio document picker (Files/Drive/on-device Music).
 *   - [rememberCameraPhotoCapture] / [rememberCameraVideoCapture] — `TakePicture`/`CaptureVideo`
 *     into a FileProvider cache uri, gated by a just-in-time CAMERA permission request. Video
 *     mic is handled by the system camera app, so we don't request RECORD_AUDIO ourselves.
 *
 * DEVICE-DEPENDENT: the actual camera/gallery UIs are system surfaces; only the Uri plumbing is
 * ours. Verified on-device, not in unit tests.
 */

@Composable
fun rememberImageLibraryPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

@Composable
fun rememberVideoLibraryPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPicked)
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    }
}

/** Audio picker — returns the picked Uri + its display name (for the sample title). */
@Composable
fun rememberAudioPicker(onPicked: (Uri, String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(it, queryDisplayName(context, it)) }
    }
    return remember(launcher) { { launcher.launch(arrayOf("audio/*")) } }
}

@Composable
fun rememberCameraPhotoCapture(onCaptured: (Uri) -> Unit): () -> Unit =
    rememberCameraCapture(ext = "jpg", capture = { CameraContract.Photo }, onCaptured = onCaptured)

@Composable
fun rememberCameraVideoCapture(onCaptured: (Uri) -> Unit): () -> Unit =
    rememberCameraCapture(ext = "mp4", capture = { CameraContract.Video }, onCaptured = onCaptured)

/** Which system capture contract a camera helper drives (kept internal to this file). */
private enum class CameraContract { Photo, Video }

/**
 * Shared camera-capture wiring: create a FileProvider cache uri, request CAMERA if we don't
 * already hold it, then launch TakePicture/CaptureVideo into that uri. Both contracts return a
 * Boolean success; only then do we forward the uri.
 */
@Composable
private fun rememberCameraCapture(
    ext: String,
    capture: () -> CameraContract,
    onCaptured: (Uri) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let(onCaptured)
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        if (ok) pendingUri?.let(onCaptured)
    }

    fun launchCapture() {
        val uri = createCaptureUri(context, ext)
        pendingUri = uri
        when (capture()) {
            CameraContract.Photo -> photoLauncher.launch(uri)
            CameraContract.Video -> videoLauncher.launch(uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCapture()
    }

    return remember(photoLauncher, videoLauncher, permissionLauncher) {
        {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCapture()
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

/** A `content://` uri under `cacheDir/wizard-capture/` the system camera can write to. */
private fun createCaptureUri(context: Context, ext: String): Uri {
    val dir = File(context.cacheDir, "wizard-capture").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.$ext")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Best-effort human display name for a picked document (drives the sample title). */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
