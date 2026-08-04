package com.hopcape.odo.core.platform.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Whether the app may open the camera, and what asking again would do.
 *
 * Three states rather than a boolean because "not granted" is two different situations with
 * two different fixes. If the system will still show its dialog, the answer is to ask. If it
 * will not, asking does nothing at all and the only way forward is the app's settings page —
 * a screen that keeps offering "Allow" there is a button that silently does nothing.
 */
enum class CameraPermissionStatus {

    /** The camera can be opened now. */
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

/** True when the camera can be opened without asking for anything first. */
val CameraPermissionStatus.isGranted: Boolean
    get() = this == CameraPermissionStatus.Granted

/**
 * Reads and changes the camera permission for the screen that holds it.
 *
 * [status] is Compose state: it updates when the system dialog is answered and when the owner
 * comes back from the settings page, so a screen reading it re-renders without polling.
 */
@Stable
interface CameraPermissionController {

    /** The current permission, as of the last time it could have changed. */
    val status: CameraPermissionStatus

    /**
     * Show the system permission dialog.
     *
     * Does nothing when the status is [CameraPermissionStatus.Blocked], because the system
     * would not show a dialog anyway. Call [openAppSettings] in that case instead.
     */
    fun request()

    /**
     * Open the app's page in the system settings, where the camera toggle lives.
     *
     * This leaves Odo. The owner may come back having granted it, denied it, or having
     * changed nothing, so [status] is re-read on return rather than assumed.
     */
    fun openAppSettings()
}

/**
 * The camera permission controller for the current screen.
 *
 * A composable rather than an injected port: asking for a permission needs the thing hosting
 * the UI (an Activity on Android, the key window on iOS), which no Koin singleton can hold
 * without leaking it.
 */
@Composable
expect fun rememberCameraPermissionController(): CameraPermissionController
