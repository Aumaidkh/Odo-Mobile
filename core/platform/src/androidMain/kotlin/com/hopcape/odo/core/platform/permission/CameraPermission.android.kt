package com.hopcape.odo.core.platform.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual — the runtime `CAMERA` permission.
 *
 * Two launchers, because the two ways a permission can change both need to be noticed. The
 * first shows the system dialog. The second opens the settings page as an activity *for
 * result*, which is not for the result itself (there is none) but for the callback: it fires
 * when the owner comes back, and that is when the permission is re-read. Watching the
 * lifecycle would do the same job and pull in a dependency this module does not otherwise
 * need.
 */
@Composable
actual fun rememberCameraPermissionController(): CameraPermissionController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val statusState = remember { mutableStateOf(readCameraStatus(context, activity)) }

    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // The dialog was answered. Record that it was shown at all, because after a denial
        // Android cannot tell "never asked" from "asked and blocked" on its own.
        context.markCameraRequested()
        statusState.value = readCameraStatus(context, activity)
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        statusState.value = readCameraStatus(context, activity)
    }

    // The permission can also change while the app is away for reasons of its own — the owner
    // revoked it from system settings, or the OS did on an auto-reset of an unused app. Read
    // it once when the screen appears rather than trusting what was remembered.
    LaunchedEffect(context, activity) {
        statusState.value = readCameraStatus(context, activity)
    }

    return remember(context, activity) {
        AndroidCameraPermissionController(
            statusState = statusState,
            onRequest = { requestLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { settingsLauncher.launch(appSettingsIntent(context)) },
        )
    }
}

@Stable
private class AndroidCameraPermissionController(
    private val statusState: MutableState<CameraPermissionStatus>,
    private val onRequest: () -> Unit,
    private val onOpenSettings: () -> Unit,
) : CameraPermissionController {

    override val status: CameraPermissionStatus get() = statusState.value

    override fun request() {
        // Launching while blocked would return "denied" instantly and teach the owner that the
        // button is broken. The screen is expected to offer settings instead.
        if (status != CameraPermissionStatus.Blocked) onRequest()
    }

    override fun openAppSettings() = onOpenSettings()
}

/**
 * What the system will do about the camera right now.
 *
 * The blocked case is the one Android makes awkward. `shouldShowRequestPermissionRationale`
 * is false both before the first request and after the owner has turned it down for good, so
 * it only separates the two once we know a request has happened — hence the stored flag.
 */
private fun readCameraStatus(context: Context, activity: Activity?): CameraPermissionStatus = when {
    context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
        CameraPermissionStatus.Granted
    // No activity means nothing can be asked from here yet, but nothing is blocked either.
    activity == null -> CameraPermissionStatus.Askable
    !context.wasCameraRequested() -> CameraPermissionStatus.Askable
    activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ->
        CameraPermissionStatus.Askable
    else -> CameraPermissionStatus.Blocked
}

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))

/**
 * The Activity behind a composable's context.
 *
 * `LocalContext` is usually the Activity already, but a Compose view hosted inside another
 * view hierarchy can hand out a wrapper, so the chain is walked rather than cast.
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val PERMISSION_PREFS = "odo_permissions"
private const val KEY_CAMERA_REQUESTED = "camera_requested"

private fun Context.wasCameraRequested(): Boolean =
    getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_CAMERA_REQUESTED, false)

private fun Context.markCameraRequested() {
    getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_CAMERA_REQUESTED, true)
        .apply()
}
