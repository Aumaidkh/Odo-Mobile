package com.hopcape.odo.core.platform.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Android actual — any single runtime permission, named by [permission].
 *
 * The generalised form of [CameraPermission.android.kt]'s original logic: two launchers,
 * because the two ways a permission can change both need to be noticed (the system dialog,
 * and the settings page's callback firing on return), plus a per-permission "was this ever
 * requested" flag so a denial can be told apart from "never asked" the way
 * `shouldShowRequestPermissionRationale` alone cannot.
 *
 * The one rule this holds itself to is that [PermissionController.request] never does
 * nothing visible. The system can answer a request without putting a dialog on screen, and a
 * button that looks ignored is worse than a button that says no; every path below either
 * shows the dialog or opens the settings page.
 */
@Composable
actual fun rememberPermissionController(permission: String): PermissionController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val statusState = remember(permission) {
        mutableStateOf(readPermissionStatus(context, activity, permission))
    }

    // Held across recompositions on purpose. `rememberLauncherForActivityResult` re-registers
    // its callback whenever the contract instance changes, so building a contract inline
    // means unregistering and re-registering on every recomposition — and a result that
    // arrives while that is happening is dropped, leaving the status stale.
    val requestContract = remember { ActivityResultContracts.RequestPermission() }
    val settingsContract = remember { ActivityResultContracts.StartActivityForResult() }

    val settingsLauncher = rememberLauncherForActivityResult(settingsContract) {
        statusState.value = readPermissionStatus(context, activity, permission)
    }

    // What the answer to a request has to be read against: whether this was the first ask,
    // and how long the owner had to answer it. Both are captured at launch. Plain fields
    // rather than state — nothing draws them, and writing state from a click would
    // recompose the screen for no reason.
    val inFlight = remember(permission) { LaunchedRequest() }

    val requestLauncher = rememberLauncherForActivityResult(requestContract) { granted ->
        // Only now can a denial be told from "never asked", so record that the dialog
        // happened at all.
        context.markPermissionRequested(permission)
        statusState.value = readPermissionStatus(context, activity, permission)

        if (answeredWithoutAsking(
                granted = granted,
                askedBefore = inFlight.askedBefore,
                statusAfterAnswer = statusState.value,
                elapsedMs = SystemClock.elapsedRealtime() - inFlight.launchedAt,
            )
        ) {
            // No dialog was ever shown, so the tap that got here produced nothing on screen.
            // The permission is off for good — the owner turned it down in an earlier install
            // or switched it off in system settings — and the switch that undoes it lives on
            // the app's settings page. Go there rather than let the button look broken.
            settingsLauncher.launch(appSettingsIntent(context))
        }
    }

    // The permission can also change while the app is away for reasons of its own — the owner
    // revoked it from system settings, or the OS did on an auto-reset of an unused app. Read
    // it on every resume rather than trusting what was remembered.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context, activity, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                statusState.value = readPermissionStatus(context, activity, permission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(context, activity, permission) {
        AndroidPermissionController(
            statusState = statusState,
            onRequest = {
                inFlight.askedBefore = context.wasPermissionRequested(permission)
                inFlight.launchedAt = SystemClock.elapsedRealtime()
                requestLauncher.launch(permission)
            },
            onOpenSettings = { settingsLauncher.launch(appSettingsIntent(context)) },
        )
    }
}

/** The request currently in flight, as it looked the moment it was launched. */
private class LaunchedRequest {

    /** Whether this permission had already been asked for before this request. */
    var askedBefore: Boolean = false

    /** `SystemClock.elapsedRealtime()` at launch; 0 until the first request. */
    var launchedAt: Long = 0L
}

@Stable
private class AndroidPermissionController(
    private val statusState: MutableState<PermissionStatus>,
    private val onRequest: () -> Unit,
    private val onOpenSettings: () -> Unit,
) : PermissionController {

    override val status: PermissionStatus get() = statusState.value

    override fun request() {
        // Askable is the only status a dialog can come out of. Granted has nothing to ask
        // for, and launching while blocked would return "denied" instantly and teach the
        // owner that the button is broken — the screen is expected to offer settings instead.
        if (status == PermissionStatus.Askable) onRequest()
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
): PermissionStatus {
    if (!isRuntimePermission(permission, Build.VERSION.SDK_INT)) {
        return notificationStatus(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    // Below Android 10 there is no separate background-location permission — fine location
    // covers the background too, and `checkSelfPermission` reports the platform-unknown
    // permission as denied forever. Mirror fine location's status instead, so a checklist
    // step for it resolves itself on those phones.
    if (permission == PlatformPermission.ACCESS_BACKGROUND_LOCATION && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return readPermissionStatus(context, activity, PlatformPermission.ACCESS_FINE_LOCATION)
    }
    return when {
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ->
            PermissionStatus.Granted
        // No activity means nothing can be asked from here yet, but nothing is blocked either.
        activity == null -> PermissionStatus.Askable
        !context.wasPermissionRequested(permission) -> PermissionStatus.Askable
        activity.shouldShowRequestPermissionRationale(permission) -> PermissionStatus.Askable
        else -> PermissionStatus.Blocked
    }
}

/**
 * Whether [permission] is something the system will hold a runtime dialog for on [sdkInt].
 *
 * Only `POST_NOTIFICATIONS` answers no, and only below Android 13, where the permission does
 * not exist. `checkSelfPermission` reports a permission the platform has never heard of as
 * denied, which used to make the notification prompt say "not granted, ask for it" on every
 * phone below 13 — and the request that followed was refused on the spot with nothing drawn.
 * Odo's minSdk is 26, so that was most of the range.
 */
internal fun isRuntimePermission(permission: String, sdkInt: Int): Boolean =
    permission != PlatformPermission.POST_NOTIFICATIONS || sdkInt >= Build.VERSION_CODES.TIRAMISU

/**
 * Where notifications stand on a phone with no notification permission to grant.
 *
 * Before Android 13 they are on by default and the only way to turn them off is the app's
 * own settings page, so there is no askable middle state: either they work, or the owner has
 * switched them off and settings is where they get switched back on.
 */
internal fun notificationStatus(enabled: Boolean): PermissionStatus =
    if (enabled) PermissionStatus.Granted else PermissionStatus.Blocked

/**
 * Whether the answer to a request came back without the system ever showing a dialog.
 *
 * This happens when the permission is already off for good and nothing in the app knew it —
 * the owner switched notifications off in system settings, or turned the permission down for
 * good before the stored flag existed. `launch` then returns a refusal on the spot, which is
 * a tap that produces nothing at all unless it is caught here.
 *
 * Three things have to hold together, because each on its own has an innocent explanation:
 *
 * - the request was refused, so a grant is never mistaken for this;
 * - it was the first ask, because from Android 11 a real first denial still leaves the
 *   permission askable and only the second one blocks it;
 * - the answer arrived too fast to have been read, which is what separates "no dialog" from a
 *   dialog the owner dismissed with the back gesture — a dismissal is not a denial, so it
 *   leaves the permission looking blocked on a first ask too.
 *
 * @param elapsedMs how long the request was in flight.
 */
internal fun answeredWithoutAsking(
    granted: Boolean,
    askedBefore: Boolean,
    statusAfterAnswer: PermissionStatus,
    elapsedMs: Long,
): Boolean = !granted &&
    !askedBefore &&
    statusAfterAnswer == PermissionStatus.Blocked &&
    elapsedMs < NO_DIALOG_MS

/**
 * Below this, a request cannot have been seen. Half a second is longer than the round trip
 * to a system activity that finishes without drawing, and shorter than reading a dialog and
 * reaching for a button.
 */
private const val NO_DIALOG_MS = 500L

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
