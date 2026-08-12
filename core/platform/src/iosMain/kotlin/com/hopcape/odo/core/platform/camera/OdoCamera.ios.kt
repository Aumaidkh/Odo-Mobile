package com.hopcape.odo.core.platform.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.hopcape.odo.core.platform.file.absolutePathFor
import com.hopcape.odo.core.platform.file.ensureParentDirectory
import com.hopcape.odo.core.platform.file.StorageKey
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.hasTorch
import platform.AVFoundation.torchMode
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.writeToFile
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * iOS actual — an `AVCaptureSession` shown through a `UIView` hosted by Compose.
 *
 * The structure mirrors the Android side: the preview starts when the composable appears and
 * stops when it leaves, photos are written into app storage and reported as a relative key,
 * and QR codes come off the live frames on the device.
 *
 * Everything that touches the session runs on its own serial queue. `startRunning` blocks
 * until the camera is actually up, which on the main thread is a visible freeze, and
 * AVFoundation requires configuration to be serialized anyway.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun OdoCameraPreview(
    state: OdoCameraState,
    onEvent: (CameraEvent) -> Unit,
    modifier: Modifier,
    // DocumentEdges is accepted and ignored: on-device edge detection is Android-only for
    // the MVP, so iOS never sends CameraEvent.EdgesDetected and the screen keeps its
    // static frame.
    analysis: CameraFrameAnalysis,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val session = remember { IosCameraSession() }

    // The session outlives any single composition, so it calls through this rather than
    // holding the callback it was given first.
    SideEffect { session.onEvent = { event -> currentOnEvent(event) } }

    DisposableEffect(session, analysis) {
        session.start(detectQr = analysis == CameraFrameAnalysis.Qr) { ready -> state.isReady = ready }
        onDispose { session.stop() }
    }

    LaunchedEffect(session, state.isTorchOn) { session.setTorch(state.isTorchOn) }

    LaunchedEffect(session, state.captureRequest) {
        // Zero is the initial value, not a shutter tap.
        if (state.captureRequest == 0) return@LaunchedEffect
        session.capture()
    }

    UIKitView(
        factory = { session.previewView },
        modifier = modifier,
    )
}

/**
 * Owns the capture session, its outputs and the view they are drawn into.
 *
 * The delegates are held as fields on purpose. AVFoundation keeps only weak references to
 * them, so a delegate created inline would be collected before the photo it is waiting for
 * ever arrives.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
private class IosCameraSession {

    var onEvent: (CameraEvent) -> Unit = {}

    private val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private val queue = dispatch_queue_create("com.hopcape.odo.camera", null)

    private var device: AVCaptureDevice? = null
    private var photoDelegate: PhotoCaptureDelegate? = null
    private var qrDelegate: QrMetadataDelegate? = null

    val previewView = CameraPreviewView(session)

    fun start(detectQr: Boolean, onReady: (Boolean) -> Unit) {
        dispatch_async(queue) {
            if (!configure(detectQr)) {
                onMain {
                    onReady(false)
                    onEvent(CameraEvent.Failed(CameraFailure.Unavailable))
                }
                return@dispatch_async
            }
            if (!session.isRunning()) session.startRunning()
            // startRunning returns once frames are flowing, so this is the honest moment to
            // tell the screen its placeholder can go.
            onMain {
                onReady(true)
                onEvent(CameraEvent.Ready)
            }
        }
    }

    fun stop() {
        dispatch_async(queue) {
            if (session.isRunning()) session.stopRunning()
        }
    }

    fun capture() {
        dispatch_async(queue) {
            val delegate = PhotoCaptureDelegate { data ->
                val event = data?.let(::writePhoto)
                    ?: CameraEvent.Failed(CameraFailure.CaptureFailed)
                onMain { onEvent(event) }
            }
            photoDelegate = delegate
            photoOutput.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), delegate)
        }
    }

    fun setTorch(on: Boolean) {
        dispatch_async(queue) {
            val target = device ?: return@dispatch_async
            if (!target.hasTorch) return@dispatch_async
            if (!target.lockForConfiguration(null)) return@dispatch_async
            target.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
            target.unlockForConfiguration()
        }
    }

    /**
     * Wire up the camera, the photo output and — when asked — QR detection.
     *
     * Returns false rather than throwing when there is no usable camera. That covers a
     * simulator with no hardware and a permission that was never granted, and both should
     * reach the screen as the same "camera unavailable" rather than as a crash.
     */
    private fun configure(detectQr: Boolean): Boolean {
        if (session.inputs.isNotEmpty()) return true

        val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        device = camera
        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null) ?: return false

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetPhoto
        if (session.canAddInput(input)) session.addInput(input) else return abandon()
        if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput) else return abandon()

        if (detectQr) {
            val metadataOutput = AVCaptureMetadataOutput()
            if (session.canAddOutput(metadataOutput)) {
                session.addOutput(metadataOutput)
                val delegate = QrMetadataDelegate { payload ->
                    onMain { onEvent(CameraEvent.QrDetected(payload)) }
                }
                qrDelegate = delegate
                metadataOutput.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
                // Only valid once the output is attached — before that the session does not
                // yet know which types this camera can even produce.
                metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            }
        }

        session.commitConfiguration()
        return true
    }

    private fun abandon(): Boolean {
        session.commitConfiguration()
        return false
    }

    /** Write the JPEG into app storage and answer with the key it went to. */
    private fun writePhoto(data: NSData): CameraEvent {
        val key = StorageKey.of(SCAN_DIRECTORY, Uuid.random().toString(), "jpg")
        if (!ensureParentDirectory(key)) return CameraEvent.Failed(CameraFailure.CaptureFailed)
        val written = data.writeToFile(absolutePathFor(key), atomically = true)
        return if (written) {
            CameraEvent.PhotoCaptured(key)
        } else {
            CameraEvent.Failed(CameraFailure.CaptureFailed)
        }
    }

    private fun onMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue(), block)
    }

    private companion object {
        const val SCAN_DIRECTORY = "scans"
    }
}

/**
 * The view the preview is drawn into.
 *
 * A `UIView` subclass rather than a plain view with a layer added to it, because a `CALayer`
 * does not follow its parent's size. Without `layoutSubviews` the preview keeps whatever
 * frame it had when it was created, which on a rotation or a keyboard is the wrong one.
 */
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView(session: AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {

    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Layer frame changes animate by default, which reads as the preview sliding into
        // place every time the view is laid out.
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        previewLayer.setFrame(bounds)
        CATransaction.commit()
    }
}

/** Receives one photo, then is done. A new one is created per shot. */
@OptIn(ExperimentalForeignApi::class)
private class PhotoCaptureDelegate(
    private val onFinished: (NSData?) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        onFinished(if (error != null) null else didFinishProcessingPhoto.fileDataRepresentation())
    }
}

/**
 * Reports QR codes seen in the live frames.
 *
 * Fires for as long as the code stays in view, matching the Android analyser. Deciding what to
 * do about the second sighting is the screen's job, not the camera's.
 */
private class QrMetadataDelegate(
    private val onPayload: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        didOutputMetadataObjects
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstNotNullOfOrNull { it.stringValue }
            ?.let(onPayload)
    }
}
