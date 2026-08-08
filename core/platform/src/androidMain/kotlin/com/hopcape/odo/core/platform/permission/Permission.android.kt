package com.hopcape.odo.core.platform.permission

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
import androidx.core.content.edit

/**
 * Android actual — any single runtime permission, named by [permission].
 *
 * The generalised form of [CameraPermission.android.kt]'s original logic: two launchers,
 * because the two ways a permission can change both need to be noticed (the system dialog,
 * and the settings page's callback firing on return), plus a per-permission "was this ever
 * requested" flag so a denial can be told apart from "never asked" the way
 * `shouldShowRequestPermissionRationale` alone cannot.
 */
@Composable
actual fun rememberPermissionController(permission: String): PermissionController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val statusState = remember(permission) {
        mutableStateOf(readPermissionStatus(context, activity, permission))
    }

    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // The dialog was answered. Record that it was shown at all, because after a denial
        // Android cannot tell "never asked" from "asked and blocked" on its own.
        context.markPermissionRequested(permission)
        statusState.value = readPermissionStatus(context, activity, permission)
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        statusState.value = readPermissionStatus(context, activity, permission)
    }

    // The permission can also change while the app is away for reasons of its own — the owner
    // revoked it from system settings, or the OS did on an auto-reset of an unused app. Read
    // it once when the screen appears rather than trusting what was remembered.
    LaunchedEffect(context, activity, permission) {
        statusState.value = readPermissionStatus(context, activity, permission)
    }

    return remember(context, activity, permission) {
        AndroidPermissionController(
            statusState = statusState,
            onRequest = { requestLauncher.launch(permission) },
            onOpenSettings = { settingsLauncher.launch(appSettingsIntent(context)) },
        )
    }
}

@Stable
private class AndroidPermissionController(
    private val statusState: MutableState<PermissionStatus>,
    private val onRequest: () -> Unit,
    private val onOpenSettings: () -> Unit,
) : PermissionController {

    override val status: PermissionStatus get() = statusState.value

    override fun request() {
        // Launching while blocked would return "denied" instantly and teach the owner that the
        // button is broken. The screen is expected to offer settings instead.
        if (status != PermissionStatus.Blocked) onRequest()
    }

    override fun openAppSettings() = onOpenSettings()
}

/**
 * What the system will do about [permission] right now.
 *
 * The blocked case is the one Android makes awkward. `shouldShowRequestPermissionRationale`
 * is false both before the first request and after the owner has turned it down for good, so
 * it only separates the two once we know a request has happened — hence the stored flag.
 */
internal fun readPermissionStatus(
    context: Context,
    activity: Activity?,
    permission: String,
): PermissionStatus = when {
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ->
        PermissionStatus.Granted
    // No activity means nothing can be asked from here yet, but nothing is blocked either.
    activity == null -> PermissionStatus.Askable
    !context.wasPermissionRequested(permission) -> PermissionStatus.Askable
    activity.shouldShowRequestPermissionRationale(permission) -> PermissionStatus.Askable
    else -> PermissionStatus.Blocked
}

internal fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))

/**
 * The Activity behind a composable's context.
 *
 * `LocalContext` is usually the Activity already, but a Compose view hosted inside another
 * view hierarchy can hand out a wrapper, so the chain is walked rather than cast.
 */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val PERMISSION_PREFS = "odo_permissions"

private fun requestedPrefsKey(permission: String) = "requested_$permission"

private fun Context.wasPermissionRequested(permission: String): Boolean =
    getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .getBoolean(requestedPrefsKey(permission), false)

private fun Context.markPermissionRequested(permission: String) {
    getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit {
            putBoolean(requestedPrefsKey(permission), true)
        }
}
