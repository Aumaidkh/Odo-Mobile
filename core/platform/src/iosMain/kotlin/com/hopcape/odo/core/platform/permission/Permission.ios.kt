package com.hopcape.odo.core.platform.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * iOS actual — always-granted stub.
 *
 * The permissions this seam names ([PlatformPermission]) are Android runtime permissions
 * (Bluetooth, in particular) with no iOS equivalent, and every feature that calls
 * [rememberPermissionController] today (auto-odometer) is Android-only for the MVP per
 * CLAUDE.md. Treating iOS as always granted keeps the shared Compose UI compiling on both
 * targets without pretending to model a permission iOS does not have.
 *
 * [CameraPermission.ios.kt] keeps its own real AVFoundation-backed logic — that permission
 * does exist on iOS and stays accurate rather than degrading to this stub.
 */
@Composable
actual fun rememberPermissionController(permission: String): PermissionController =
    remember(permission) { AlwaysGrantedPermissionController }

@Stable
private object AlwaysGrantedPermissionController : PermissionController {
    override val status: PermissionStatus = PermissionStatus.Granted
    override fun request() = Unit
    override fun openAppSettings() = Unit
}
