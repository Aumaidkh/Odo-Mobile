package com.hopcape.odo.core.platform.camera

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hopcape.odo.core.platform.file.StorageKey
import java.io.File
import java.util.concurrent.Executors
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Android actual — CameraX inside an [AndroidView].
 *
 * [LifecycleCameraController] rather than the lower-level `ProcessCameraProvider`: it owns the
 * preview, the still capture and the frame analyser as one unit and binds them to the
 * lifecycle, so leaving the screen releases the camera without any teardown code here. What
 * this file adds on top is the parts CameraX has no opinion about — where the file goes, and
 * turning barcodes into events.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
actual fun OdoCameraPreview(
    state: OdoCameraState,
    onEvent: (CameraEvent) -> Unit,
    modifier: Modifier,
    analysis: CameraFrameAnalysis,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // The callback is read from inside long-lived CameraX listeners. Without this, a listener
    // registered on first composition would keep calling the first `onEvent` forever.
    val currentOnEvent by rememberUpdatedState(onEvent)

    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        runCatching { controller.bindToLifecycle(lifecycleOwner) }
            .onFailure { currentOnEvent(CameraEvent.Failed(CameraFailure.Unavailable)) }
        onDispose { controller.unbind() }
    }

    // The analyser slot carries at most one job, attached and removed with the mode so a
    // photo-only screen is not paying for per-frame analysis.
    DisposableEffect(controller, analysis) {
        when (analysis) {
            CameraFrameAnalysis.Qr -> {
                // The main executor is fine here: ML Kit's process() only *starts* the scan
                // and does its work off-thread.
                val analyzer = QrFrameAnalyzer { payload -> currentOnEvent(CameraEvent.QrDetected(payload)) }
                controller.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
                onDispose {
                    controller.clearImageAnalysisAnalyzer()
                    analyzer.close()
                }
            }

            CameraFrameAnalysis.DocumentEdges -> {
                // Unlike the QR analyzer, this one does all its work inline — so it gets
                // its own thread rather than a slice of every frame's UI budget. The
                // event still reaches Compose safely: state writes are thread-agnostic.
                val executor = Executors.newSingleThreadExecutor()
                val analyzer = DocumentEdgeAnalyzer { quad -> currentOnEvent(CameraEvent.EdgesDetected(quad)) }
                controller.setImageAnalysisAnalyzer(executor, analyzer)
                onDispose {
                    controller.clearImageAnalysisAnalyzer()
                    executor.shutdown()
                }
            }

            CameraFrameAnalysis.None -> onDispose { }
        }
    }

    LaunchedEffect(controller, state.isTorchOn) {
        runCatching { controller.enableTorch(state.isTorchOn) }
    }

    // Zero is the state's initial value, which no shutter tap has produced — reacting to it
    // would take a photo the moment the screen opened.
    LaunchedEffect(controller, state.captureRequest) {
        if (state.captureRequest == 0) return@LaunchedEffect
        controller.takePictureTo(context) { result ->
            currentOnEvent(result)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                this.controller = controller
                // The viewfinder is a fixed frame the owner aligns a bill inside, so the
                // preview fills it and crops rather than letter-boxing to the sensor ratio.
                scaleType = PreviewView.ScaleType.FILL_CENTER
                previewStreamState.observe(lifecycleOwner) { streamState ->
                    val ready = streamState == PreviewView.StreamState.STREAMING
                    state.isReady = ready
                    if (ready) currentOnEvent(CameraEvent.Ready)
                }
            }
        },
    )
}

/**
 * Take one photo into app-private storage and report the key it was written under.
 *
 * The file is written before the callback, so a screen that receives
 * [CameraEvent.PhotoCaptured] can read the photo straight away. `scans/` matches where the
 * file store puts owner-supplied papers, which is what lets a captured bill and an uploaded
 * one be handled by the same code from here on.
 */
@OptIn(ExperimentalUuidApi::class)
private fun LifecycleCameraController.takePictureTo(
    context: Context,
    onResult: (CameraEvent) -> Unit,
) {
    val key = StorageKey.of(SCAN_DIRECTORY, Uuid.random().toString(), "jpg")
    val target = File(context.filesDir, key)
    val created = runCatching { target.parentFile?.mkdirs() }.isSuccess
    if (!created) {
        onResult(CameraEvent.Failed(CameraFailure.CaptureFailed))
        return
    }

    takePicture(
        ImageCapture.OutputFileOptions.Builder(target).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onResult(CameraEvent.PhotoCaptured(key))
            }

            override fun onError(exception: ImageCaptureException) {
                // A half-written file would look like a bill photo that will not decode later.
                runCatching { target.delete() }
                onResult(CameraEvent.Failed(CameraFailure.CaptureFailed))
            }
        },
    )
}

/** Where captured photos live under app storage, as a path segment. */
private const val SCAN_DIRECTORY = "scans"
