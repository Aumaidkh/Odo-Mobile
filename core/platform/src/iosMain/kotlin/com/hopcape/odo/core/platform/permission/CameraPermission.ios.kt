package com.hopcape.odo.core.platform.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS actual — the `NSCameraUsageDescription` permission, read through AVFoundation.
 *
 * iOS prompts once and never again, so a denial is [CameraPermissionStatus.Blocked]
 * immediately. There is no equivalent of Android's second chance, which is the strongest
 * argument for showing the rationale screen before the prompt rather than after it.
 *
 * Settings has no result to return, so the change is picked up from the app becoming active
 * again. Granting camera access from Settings restarts the app on iOS, but denying or
 * changing something else does not, and that path has to update the screen too.
 */
@Composable
actual fun rememberCameraPermissionController(): CameraPermissionController {
    val statusState = remember { mutableStateOf(readCameraStatus()) }

    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { statusState.value = readCameraStatus() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }

    return remember { IosCameraPermissionController(statusState) }
}

@Stable
private class IosCameraPermissionController(
    private val statusState: MutableState<CameraPermissionStatus>,
) : CameraPermissionController {

    override val status: CameraPermissionStatus get() = statusState.value

    override fun request() {
        if (status == CameraPermissionStatus.Blocked) return
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
            // The answer arrives on an arbitrary queue. Compose state is read on the main
            // thread, so the hop is not optional.
            @OptIn(ExperimentalForeignApi::class)
            dispatch_async(dispatch_get_main_queue()) {
                statusState.value = readCameraStatus()
            }
        }
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}

private fun readCameraStatus(): CameraPermissionStatus =
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> CameraPermissionStatus.Granted
        AVAuthorizationStatusNotDetermined -> CameraPermissionStatus.Askable
        // Denied and restricted both mean the prompt will not come back. Restricted is a
        // parental control or MDM policy, which the owner may not be able to change at all.
        else -> CameraPermissionStatus.Blocked
    }
