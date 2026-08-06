package com.hopcape.odo.feature.billscanner.presentation.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.model.ScanId
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.platform.camera.CameraFailure
import com.hopcape.odo.core.platform.camera.DetectedQuad
import com.hopcape.odo.core.platform.camera.DocumentCropper
import com.hopcape.odo.core.platform.camera.QrImageDecoder
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.permission.CameraPermissionStatus
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTelemetry
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_error_gallery_no_qr
import com.hopcape.odo.feature.billscanner.resources.bs_error_gallery_unreadable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the scan screen: what is being scanned, whether the camera can run, and
 * where each capture goes next.
 *
 * It deliberately does no extraction. A photo travels onward as a storage key and the screen
 * that reviews it does the reading, which keeps the navigation keys to a single string and
 * means backing out of a review and returning re-reads the same photo rather than needing the
 * result carried around. Re-reading costs nothing on the server side, where scans are cached
 * by image hash (TDD §7.5).
 *
 * The QR case is the one piece of real logic here: detection fires on every frame the code
 * stays in view, so the first one wins and the rest are dropped. Without that, an owner
 * holding their phone over a pump QR would launch the payment flow thirty times a second.
 */
internal class BillScanViewModel(
    initialTarget: ScanTarget,
    private val allowance: ScanAllowance,
    private val cropper: DocumentCropper,
    private val files: PlatformFileStore,
    private val qrDecoder: QrImageDecoder,
    private val ids: IdGenerator,
    private val telemetry: BillScannerTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(BillScanUiState(target = initialTarget))
    val state: StateFlow<BillScanUiState> = _state.asStateFlow()

    private val _effects = Channel<BillScanEffect>(Channel.BUFFERED)
    val effects: Flow<BillScanEffect> = _effects.receiveAsFlow()

    /** Set once a QR has been handed on, so the repeat detections are ignored. */
    private var qrHandled = false

    init {
        telemetry.scannerOpened(initialTarget.name)
        loadAllowance()
    }

    fun onEvent(event: BillScanEvent) = when (event) {
        is BillScanEvent.TargetSelected -> selectTarget(event.target)
        is BillScanEvent.PermissionChanged -> permissionChanged(event.status)
        BillScanEvent.PermissionDeclined -> declined()
        is BillScanEvent.PhotoCaptured -> photoCaptured(event.storageKey)
        is BillScanEvent.QrDetected -> qrDetected(event.payload)
        // Deliberately silent in telemetry — this fires per frame, and a log per frame is
        // a log nobody can read. A pinned outline stays put: that is what the pin is for.
        is BillScanEvent.EdgesDetected -> _state.update {
            if (it.edgesLocked) it else it.copy(detectedQuad = event.quad)
        }

        BillScanEvent.EdgeLockToggled -> toggleEdgeLock()
        BillScanEvent.CameraReady -> _state.update { it.copy(cameraFailure = null) }
        is BillScanEvent.CameraFailed -> cameraFailed(event.failure)
        BillScanEvent.GalleryTapped -> galleryTapped()
        is BillScanEvent.GalleryPicked -> event.pickedRef?.let(::galleryPicked) ?: Unit
        BillScanEvent.ManualTapped -> emit(BillScanEffect.OpenManualEntry)
        BillScanEvent.CloseTapped -> emit(BillScanEffect.NavigateBack)
    }

    /**
     * The owner's remaining free scans, for the top-bar pill.
     *
     * Read once when the screen opens rather than watched: nothing can change it while the
     * viewfinder is up, since spending a scan happens on the screen after this one.
     *
     * Guarded because this is a `launch`: a real entitlement adapter reads the database, and a
     * throw would take the whole ViewModel scope down rather than just leaving the pill blank.
     */
    private fun loadAllowance() {
        viewModelScope.launch {
            val limit = runCatching { allowance.current() }
                .onFailure { telemetry.readFailed(BillScannerTelemetry.Read.ALLOWANCE, it) }
                .getOrNull()
                ?: return@launch
            _state.update {
                it.copy(freeRemaining = limit.remaining ?: 0, freeTotal = limit.cap ?: 0)
            }
        }
    }

    private fun selectTarget(target: ScanTarget) {
        if (target == _state.value.target) return
        telemetry.targetSwitched(target.name)
        // Switching away from and back to QR should scan again, so the guard is released
        // with the mode it belongs to. The outline and its pin belong to the old mode's frames.
        qrHandled = false
        _state.update {
            it.copy(
                target = target,
                cameraFailure = null,
                detectedQuad = null,
                edgesLocked = false,
                failure = null,
            )
        }
    }

    /**
     * Pin the outline where it is, or release it. Locking with nothing detected is a
     * no-op rather than an error — a stray tap on the viewfinder should do nothing.
     */
    private fun toggleEdgeLock() {
        val current = _state.value
        val locked = !current.edgesLocked && current.detectedQuad != null
        if (locked == current.edgesLocked) return
        telemetry.edgeLockToggled(locked)
        _state.update { it.copy(edgesLocked = locked) }
    }

    private fun permissionChanged(status: CameraPermissionStatus) {
        if (status == _state.value.cameraPermission) return
        telemetry.cameraPermissionAnswered(status.name)
        _state.update { it.copy(cameraPermission = status) }
    }

    private fun declined() {
        telemetry.cameraDeclined()
        _state.update { it.copy(rationaleDismissed = true) }
    }

    private fun photoCaptured(storageKey: String) {
        val target = _state.value.target
        val quad = _state.value.detectedQuad
        telemetry.photoCaptured(target.name)
        viewModelScope.launch {
            // Cropped to the outline that was on screen at the shutter, when there was one.
            // The cropper answers the original key on any failure, so this can only improve
            // the photo, never lose it.
            val key = cropTo(quad, storageKey, target)
            _effects.send(
                when (target) {
                    ScanTarget.Bill -> BillScanEffect.OpenBillReview(key)
                    ScanTarget.Document -> BillScanEffect.OpenDocumentReview(key)
                    // The shutter does nothing useful in QR mode — the code is read off the live
                    // frames — so a stray tap reviews the photo as a bill rather than dropping it.
                    ScanTarget.PaymentQr -> BillScanEffect.OpenBillReview(key)
                },
            )
        }
    }

    private fun galleryTapped() {
        telemetry.galleryOpened(_state.value.target.name)
        _state.update { it.copy(failure = null) }
        emit(BillScanEffect.PickFromGallery)
    }

    /**
     * Take a picture the owner chose and send it the same way a capture goes.
     *
     * The file is copied into app storage first. What the picker hands back is a borrowed
     * handle that stops resolving once this process dies, so a review screen opened from it
     * tomorrow would read nothing — and a bill photo is what earns an entry its Verified
     * badge, which makes losing it worse than never storing it.
     *
     * No cropping: the outline the camera detects belongs to the live frames, and a picture
     * from the gallery has none. It goes to the reader whole.
     */
    private fun galleryPicked(pickedRef: String) {
        val target = _state.value.target
        viewModelScope.launch(telemetry.op(BillScannerTelemetry.Trace.GALLERY_IMPORT)) {
            _state.update { it.copy(failure = null) }
            val key = files.save(pickedRef, SCAN_DIRECTORY, ScanId.new(ids).value)
                .getOrNull()
            if (key == null) {
                telemetry.galleryImportFailed(target.name)
                _state.update { it.copy(failure = UiText(Res.string.bs_error_gallery_unreadable)) }
                return@launch
            }
            telemetry.galleryImported(target.name)
            _effects.send(effectFor(target, key) ?: return@launch)
        }
    }

    /**
     * Where a picked picture goes, by what the owner came to scan. The payment mode is the
     * one that has to read the picture here rather than passing it on: every other target
     * hands the photo to a screen that reads it, but a QR is a payload, not a document, and
     * the pay screen takes the payload.
     */
    private suspend fun effectFor(target: ScanTarget, key: String): BillScanEffect? = when (target) {
        ScanTarget.Bill -> BillScanEffect.OpenBillReview(key)
        ScanTarget.Document -> BillScanEffect.OpenDocumentReview(key)
        ScanTarget.PaymentQr -> {
            val payload = runCatching { qrDecoder.decode(key) }.getOrNull()
            if (payload == null) {
                telemetry.galleryQrMissing()
                _state.update { it.copy(failure = UiText(Res.string.bs_error_gallery_no_qr)) }
                null
            } else {
                BillScanEffect.OpenPayment(payload)
            }
        }
    }

    private suspend fun cropTo(quad: DetectedQuad?, storageKey: String, target: ScanTarget): String {
        if (quad == null || target == ScanTarget.PaymentQr) return storageKey
        val cropped = runCatching { cropper.crop(storageKey, quad) }.getOrDefault(storageKey)
        telemetry.photoCropped(target.name, applied = cropped != storageKey)
        return cropped
    }

    private fun qrDetected(payload: String) {
        if (qrHandled || _state.value.target != ScanTarget.PaymentQr) return
        qrHandled = true
        emit(BillScanEffect.OpenPayment(payload))
    }

    private fun cameraFailed(failure: CameraFailure) {
        telemetry.cameraFailed(failure.name)
        _state.update { it.copy(cameraFailure = failure) }
    }

    private fun emit(effect: BillScanEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        /** Where captures already live, so a picked picture is stored like any other scan. */
        const val SCAN_DIRECTORY = "scans"
    }
}
