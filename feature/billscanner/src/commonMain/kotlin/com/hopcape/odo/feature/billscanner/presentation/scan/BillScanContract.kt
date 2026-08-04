package com.hopcape.odo.feature.billscanner.presentation.scan

import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.platform.camera.CameraFailure
import com.hopcape.odo.core.platform.permission.CameraPermissionStatus

/** What happened on the scan screen, as data. */
internal sealed interface BillScanEvent {

    /** A mode chip was tapped. */
    data class TargetSelected(val target: ScanTarget) : BillScanEvent

    /** The permission changed — answered, or re-read when the screen came back. */
    data class PermissionChanged(val status: CameraPermissionStatus) : BillScanEvent

    /** The owner chose "Not now" on the rationale. */
    data object PermissionDeclined : BillScanEvent

    /** The shutter produced a file at this storage key. */
    data class PhotoCaptured(val storageKey: String) : BillScanEvent

    /** A QR came into frame. Fires repeatedly while it stays there. */
    data class QrDetected(val payload: String) : BillScanEvent

    /** The camera is live. */
    data object CameraReady : BillScanEvent

    /** The camera could not be used. */
    data class CameraFailed(val failure: CameraFailure) : BillScanEvent

    data object ManualTapped : BillScanEvent

    data object CloseTapped : BillScanEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface BillScanEffect {

    /** A bill was photographed; the review screen reads it. */
    data class OpenBillReview(val photoKey: String) : BillScanEffect

    /** A paper was photographed; its confirm step reads it. */
    data class OpenDocumentReview(val photoKey: String) : BillScanEffect

    /** A payment code was read; the pay-at-pump flow takes over. */
    data class OpenPayment(val payload: String) : BillScanEffect

    /** Fall back to typing the entry by hand. */
    data object OpenManualEntry : BillScanEffect

    data object NavigateBack : BillScanEffect
}
