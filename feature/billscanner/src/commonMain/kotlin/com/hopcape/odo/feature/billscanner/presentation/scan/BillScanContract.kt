package com.hopcape.odo.feature.billscanner.presentation.scan

import com.hopcape.odo.core.navigation.FuelFillDraftInput
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.platform.camera.CameraFailure
import com.hopcape.odo.core.platform.camera.DetectedQuad
import com.hopcape.odo.core.platform.permission.CameraPermissionStatus

/** What happened on the scan screen, as data. */
internal sealed interface BillScanEvent {

    /** A mode chip was tapped. */
    data class TargetSelected(val target: ScanTarget) : BillScanEvent

    /**
     * The free-scan pill was tapped.
     *
     * The pill is the only place the scanner says the owner is on a free plan, so it is the
     * honest place to let them leave it. Tapping it opens the paywall whether or not the
     * quota is spent — someone reading "1 of 3 free" is already thinking about the limit.
     */
    data object QuotaTapped : BillScanEvent

    /** The permission changed — answered, or re-read when the screen came back. */
    data class PermissionChanged(val status: CameraPermissionStatus) : BillScanEvent

    /** The owner chose "Not now" on the rationale. */
    data object PermissionDeclined : BillScanEvent

    /** The shutter produced a file at this storage key. */
    data class PhotoCaptured(val storageKey: String) : BillScanEvent

    /**
     * A picture was chosen from the gallery, or the picker was cancelled (`null`).
     *
     * Carries the picker's own reference, not a storage key: the file still has to be
     * copied into app storage before anything can keep it.
     */
    data class GalleryPicked(val pickedRef: String?) : BillScanEvent


    /** The live frames found a paper's outline, or lost it (null). */
    data class EdgesDetected(val quad: DetectedQuad?) : BillScanEvent

    /** The owner tapped the viewfinder — pin the detected outline, or release it. */
    data object EdgeLockToggled : BillScanEvent

    /** The camera is live. */
    data object CameraReady : BillScanEvent

    /** The camera could not be used. */
    data class CameraFailed(val failure: CameraFailure) : BillScanEvent

    data object GalleryTapped : BillScanEvent

    data object ManualTapped : BillScanEvent

    data object CloseTapped : BillScanEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface BillScanEffect {

    /** A bill was photographed; the review screen reads it. */
    data class OpenBillReview(val photoKey: String) : BillScanEffect

    /** A paper was photographed; its confirm step reads it. */
    data class OpenDocumentReview(val photoKey: String) : BillScanEffect

    /**
     * A pump display was read; the refuel confirm step takes the numbers.
     *
     * The draft travels as the navigation layer's own type rather than a domain one, because
     * that is what the confirm destination's key holds — and it is what lets the scanner hand
     * over to refuel without either feature importing the other.
     */
    data class OpenPumpConfirm(val draft: FuelFillDraftInput) : BillScanEffect

    /** Ask the platform for a picture from the gallery. */
    data object PickFromGallery : BillScanEffect

    /** Fall back to typing the entry by hand. */
    data object OpenManualEntry : BillScanEffect

    data object NavigateBack : BillScanEffect

    /** Open the paywall, framed by how many free scans the plan gives. */
    data class OpenPaywall(val freeScans: Int) : BillScanEffect
}
