package com.hopcape.odo.feature.billscanner.presentation.scan

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.platform.camera.CameraFailure
import com.hopcape.odo.core.platform.camera.CameraFrameAnalysis
import com.hopcape.odo.core.platform.camera.DetectedQuad
import com.hopcape.odo.core.platform.permission.CameraPermissionStatus

/**
 * Display state for the scan screen.
 *
 * It carries what is being scanned, the free-scan quota shown in the top-bar pill (mirrored
 * read-only from the server entitlement), the camera permission, and any camera failure.
 *
 * The permission decides which of three things the screen is: the rationale, a live preview,
 * or a placeholder with a nudge. Keeping all three on one state means there is no window in
 * which the screen is none of them.
 */
@Immutable
internal data class BillScanUiState(
    val target: ScanTarget = ScanTarget.Bill,
    val freeRemaining: Int = 0,
    val freeTotal: Int = 0,
    val cameraPermission: CameraPermissionStatus = CameraPermissionStatus.Askable,
    val cameraFailure: CameraFailure? = null,
    /** True once the owner chose "Not now" — the rationale gives way to the nudge. */
    val rationaleDismissed: Boolean = false,
    /** The paper outline the live frames currently hold, for the corner markers. */
    val detectedQuad: DetectedQuad? = null,
    /** True once the owner tapped to pin the outline — detection updates stop moving it. */
    val edgesLocked: Boolean = false,
    /**
     * What went wrong with the last picture the owner chose from their gallery — a file that
     * could not be copied, or a picture with no payment code in it. Cleared on the next
     * attempt, and on a mode switch, so a stale complaint never outlives what caused it.
     */
    val failure: UiText? = null,
) {
    /** Whether a live preview can be shown at all. */
    val cameraGranted: Boolean get() = cameraPermission == CameraPermissionStatus.Granted

    /** Whether the nudge should offer settings rather than another try at the prompt. */
    val cameraBlocked: Boolean get() = cameraPermission == CameraPermissionStatus.Blocked

    /** Show the full rationale: not granted, and the owner has not waved it away yet. */
    val showRationale: Boolean get() = !cameraGranted && !rationaleDismissed

    /** What the live frames are analysed for: the payment mode reads QRs, paper modes find edges. */
    val frameAnalysis: CameraFrameAnalysis
        get() = if (target == ScanTarget.PaymentQr) CameraFrameAnalysis.Qr else CameraFrameAnalysis.DocumentEdges

    /** Whether the quota pill has anything true to say. */
    val showQuota: Boolean get() = freeTotal > 0 && target != ScanTarget.PaymentQr
}

/** Sample state for previews. */
internal fun sampleBillScanState(): BillScanUiState = BillScanUiState(
    freeRemaining = 2,
    freeTotal = 3,
    cameraPermission = CameraPermissionStatus.Granted,
)
