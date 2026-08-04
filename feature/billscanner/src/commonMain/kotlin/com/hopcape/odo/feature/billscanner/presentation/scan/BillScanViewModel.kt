package com.hopcape.odo.feature.billscanner.presentation.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.platform.camera.CameraFailure
import com.hopcape.odo.core.platform.permission.CameraPermissionStatus
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTelemetry
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
        BillScanEvent.CameraReady -> _state.update { it.copy(cameraFailure = null) }
        is BillScanEvent.CameraFailed -> cameraFailed(event.failure)
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
        // with the mode it belongs to.
        qrHandled = false
        _state.update { it.copy(target = target, cameraFailure = null) }
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
        telemetry.photoCaptured(target.name)
        emit(
            when (target) {
                ScanTarget.Bill -> BillScanEffect.OpenBillReview(storageKey)
                ScanTarget.Document -> BillScanEffect.OpenDocumentReview(storageKey)
                // The shutter does nothing useful in QR mode — the code is read off the live
                // frames — so a stray tap reviews the photo as a bill rather than dropping it.
                ScanTarget.PaymentQr -> BillScanEffect.OpenBillReview(storageKey)
            },
        )
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
}
