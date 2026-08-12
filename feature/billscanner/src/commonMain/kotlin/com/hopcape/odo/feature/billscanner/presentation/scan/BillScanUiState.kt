package com.hopcape.odo.feature.billscanner.presentation.scan

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.common.FeatureFlags
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.platform.camera.CameraFailure
import com.hopcape.odo.core.platform.camera.CameraFrameAnalysis
import com.hopcape.odo.core.platform.camera.DetectedQuad
import com.hopcape.odo.core.platform.permission.CameraPermissionStatus

/**
 * The modes the scanner offers, in chip order.
 *
 * **The one choke point for `FeatureFlags.PAY_VIA_QR_ENABLED`.** Two things read it — the mode
 * chips, and the ViewModel clamping whatever initial target it was handed — and between them
 * that is every way `ScanTarget.PaymentQr` could become the live target. Nothing downstream
 * needs its own guard: the frame analyser, the gallery decode and the navigation to
 * `PayAtPump` are all already conditioned on the target, so a mode that cannot be selected is
 * a feature that cannot run.
 *
 * A list rather than a filter at each call site, so the two can never disagree about what is
 * on offer.
 */
internal val scanTargets: List<ScanTarget> =
    if (FeatureFlags.PAY_VIA_QR_ENABLED) {
        ScanTarget.entries
    } else {
        ScanTarget.entries.filterNot { it == ScanTarget.PaymentQr }
    }

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

    /**
     * Whether the quota pill has anything true to say.
     *
     * It is off entirely while `FeatureFlags.PAYWALL_ENABLED` is false. "2 of 3 free" tells
     * the owner they are on a free plan and that a paid one exists, and there is no paid one
     * to move to yet. The count itself is unchanged and the pill returns with the flag.
     */
    val showQuota: Boolean
        get() = FeatureFlags.PAYWALL_ENABLED && freeTotal > 0 && target != ScanTarget.PaymentQr
}

/** Sample state for previews. */
internal fun sampleBillScanState(): BillScanUiState = BillScanUiState(
    freeRemaining = 2,
    freeTotal = 3,
    cameraPermission = CameraPermissionStatus.Granted,
)
