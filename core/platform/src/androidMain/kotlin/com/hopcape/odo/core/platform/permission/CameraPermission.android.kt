package com.hopcape.odo.core.platform.permission

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * Android actual — the runtime `CAMERA` permission.
 *
 * Delegates to [rememberPermissionController], the generalised form of this same logic
 * (Permission.android.kt). [CameraPermissionStatus] stays its own type rather than a type
 * alias of [PermissionStatus] so call sites that only ever mean "the camera" keep reading
 * that way, but the two enums share the same three cases and [toCameraStatus] is a
 * lossless mapping between them.
 */
@Composable
actual fun rememberCameraPermissionController(): CameraPermissionController {
    val generic = rememberPermissionController(Manifest.permission.CAMERA)
    return remember(generic) { DelegatingCameraPermissionController(generic) }
}

@Stable
private class DelegatingCameraPermissionController(
    private val delegate: PermissionController,
) : CameraPermissionController {
    override val status: CameraPermissionStatus get() = delegate.status.toCameraStatus()
    override fun request() = delegate.request()
    override fun openAppSettings() = delegate.openAppSettings()
}

private fun PermissionStatus.toCameraStatus(): CameraPermissionStatus = when (this) {
    PermissionStatus.Granted -> CameraPermissionStatus.Granted
    PermissionStatus.Askable -> CameraPermissionStatus.Askable
    PermissionStatus.Blocked -> CameraPermissionStatus.Blocked
}
