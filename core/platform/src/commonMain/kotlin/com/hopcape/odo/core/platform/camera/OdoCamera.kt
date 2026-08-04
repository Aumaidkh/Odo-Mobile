package com.hopcape.odo.core.platform.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * What the camera can do wrong. Kept here rather than in `DomainError` because none of it is
 * a rule the domain has an opinion about — it is hardware and file IO.
 */
enum class CameraFailure {

    /** The camera could not be opened: no camera, another app holds it, or the OS refused. */
    Unavailable,

    /** The camera is running, but this shot did not produce a usable file. */
    CaptureFailed,
}

/** Something the camera produced, reported to the screen that is showing the preview. */
sealed interface CameraEvent {

    /** The preview is live. Until this arrives the screen shows its own placeholder. */
    data object Ready : CameraEvent

    /**
     * A photo was taken and written to app-private storage.
     *
     * [storageKey] is relative, in the same form [com.hopcape.odo.core.platform.file.PlatformFileStore]
     * hands back, so a captured photo and a picked file are the same kind of thing from here on.
     */
    data class PhotoCaptured(val storageKey: String) : CameraEvent

    /**
     * A QR code was read from the live preview. [payload] is the raw string in the code —
     * for a payment QR, a `upi://pay?...` URI that still needs parsing.
     *
     * Fires repeatedly while the code stays in frame. The screen decides what to do with the
     * second one; the camera does not try to guess.
     */
    data class QrDetected(val payload: String) : CameraEvent

    /** The camera failed. The preview is not usable and the screen should offer a way out. */
    data class Failed(val failure: CameraFailure) : CameraEvent
}

/**
 * The handle a screen uses to drive [OdoCameraPreview] — take a photo, turn the torch on,
 * and know whether the preview is live yet.
 *
 * State-driven rather than a platform object with methods on it, which is what keeps the
 * preview a single `expect` composable: the screen changes this state, and each platform's
 * preview reacts. Nothing platform-specific leaks into the caller.
 */
@Stable
class OdoCameraState internal constructor() {

    /**
     * Bumped by [capture]. The preview watches it and takes one photo per change.
     *
     * A counter rather than a boolean flag the preview has to reset: two shutter taps in a row
     * are two distinct values, so the second is never swallowed as "already capturing".
     */
    internal var captureRequest by mutableIntStateOf(0)
        private set

    /** Whether the torch should be on. Ignored by devices that have none. */
    var isTorchOn: Boolean by mutableStateOf(false)
        private set

    /** True once frames are reaching the screen. */
    var isReady: Boolean by mutableStateOf(false)
        internal set

    /** Take one photo. The result arrives as [CameraEvent.PhotoCaptured] or [CameraEvent.Failed]. */
    fun capture() {
        captureRequest++
    }

    /** Turn the torch on or off. */
    fun toggleTorch() {
        isTorchOn = !isTorchOn
    }
}

/** The camera state for the current screen, surviving recomposition. */
@Composable
fun rememberOdoCameraState(): OdoCameraState = remember { OdoCameraState() }

/**
 * A live camera preview, filling [modifier]'s bounds.
 *
 * The preview starts when it enters composition and stops when it leaves, so a screen that
 * navigates away releases the camera without being asked. It does **not** check permission:
 * call it only once [com.hopcape.odo.core.platform.permission.CameraPermissionStatus] is
 * granted, otherwise it reports [CameraFailure.Unavailable].
 *
 * @param detectQr run QR detection on the live frames. Off by default because it costs a
 *   frame analyser that a photo-only screen has no use for. Detection happens on the device —
 *   no network, no AI call.
 */
@Composable
expect fun OdoCameraPreview(
    state: OdoCameraState,
    onEvent: (CameraEvent) -> Unit,
    modifier: Modifier = Modifier,
    detectQr: Boolean = false,
)
