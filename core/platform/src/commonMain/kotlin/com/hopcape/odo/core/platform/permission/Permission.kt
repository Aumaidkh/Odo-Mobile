package com.hopcape.odo.core.platform.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Whether a runtime permission may be used right now, and what asking again would do.
 *
 * The permission-agnostic twin of [CameraPermissionStatus] — same three states, same
 * reasoning, generalised so every runtime permission (Bluetooth, location, notifications)
 * shares one blueprint instead of a copy of this enum per permission.
 */
enum class PermissionStatus {

    /** The permission is held; the guarded action can run now. */
    Granted,

    /** Not granted, but the system will still show its dialog when asked. */
    Askable,

    /**
     * Not granted, and the system will no longer ask. The owner turned it down for good
     * (Android) or denied it once (iOS, where there is only ever one prompt). Changing it
     * means going to the app's settings page.
     */
    Blocked,
}

/** True when the guarded action can run without asking for anything first. */
val PermissionStatus.isGranted: Boolean
    get() = this == PermissionStatus.Granted

/**
 * Reads and changes one runtime permission for the screen that holds it.
 *
 * [status] is Compose state: it updates when the system dialog is answered and when the
 * owner comes back from the settings page, so a screen reading it re-renders without polling.
 */
@Stable
interface PermissionController {

    /** The current permission, as of the last time it could have changed. */
    val status: PermissionStatus

    /**
     * Show the system permission dialog.
     *
     * Does nothing when the status is [PermissionStatus.Blocked], because the system would
     * not show a dialog anyway. Call [openAppSettings] in that case instead.
     */
    fun request()

    /**
     * Open the app's page in the system settings, where the permission toggle lives.
     *
     * This leaves Odo. The owner may come back having granted it, denied it, or having
     * changed nothing, so [status] is re-read on return rather than assumed.
     */
    fun openAppSettings()
}

/**
 * The controller for one runtime permission, for the current screen.
 *
 * A composable rather than an injected port: asking for a permission needs the thing
 * hosting the UI (an Activity on Android, the key window on iOS), which no Koin singleton
 * can hold without leaking it. [rememberCameraPermissionController] is this function with
 * the permission fixed to the camera; new call sites should use this one directly.
 *
 * @param permission the platform's raw permission identifier (an Android manifest
 *   permission string; see [PlatformPermission]). The iOS actual never reads it — this
 *   feature set is Android-only for the MVP, so the iOS side is an always-granted stub.
 */
@Composable
expect fun rememberPermissionController(permission: String): PermissionController

/**
 * Raw Android manifest permission strings for use with [rememberPermissionController].
 *
 * Declared as plain strings (not `android.Manifest.permission.*` constants) so a
 * `:feature:*` module can name a permission from `commonMain` without importing
 * `android.Manifest`, which does not exist on iOS.
 */
object PlatformPermission {

    /** The camera — bills, documents and the pump's UPI QR. */
    const val CAMERA: String = "android.permission.CAMERA"

    /** Lets the app read the phone's bonded Bluetooth devices (auto-odometer's device picker). */
    const val BLUETOOTH_CONNECT: String = "android.permission.BLUETOOTH_CONNECT"

    /** Lets the app show the live "trip in progress" notification (auto-odometer M4 step 2). */
    const val POST_NOTIFICATIONS: String = "android.permission.POST_NOTIFICATIONS"

    /** While-using location — what a trip needs to measure distance (auto-odometer M4 step 3). */
    const val ACCESS_FINE_LOCATION: String = "android.permission.ACCESS_FINE_LOCATION"

    /** Lets the app tell driving apart from walking or transit (auto-odometer's NO_STEREO path). */
    const val ACTIVITY_RECOGNITION: String = "android.permission.ACTIVITY_RECOGNITION"

    /**
     * "Allow all the time" location — the M4 funnel deliberately never asks for this; it is
     * requested later on the first trip-logged screen (plan §5 step 5, built in F7). Declared
     * here now so that screen doesn't need its own raw manifest string.
     */
    const val ACCESS_BACKGROUND_LOCATION: String = "android.permission.ACCESS_BACKGROUND_LOCATION"
}
