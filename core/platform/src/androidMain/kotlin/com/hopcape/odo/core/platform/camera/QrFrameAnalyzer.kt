package com.hopcape.odo.core.platform.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage

/**
 * Reads QR codes off the live camera frames with ML Kit, on the device.
 *
 * Restricted to QR codes rather than every barcode format ML Kit knows: a fuel pump's payment
 * code is a QR, and narrowing the formats makes detection both faster and less likely to fire
 * on a barcode printed somewhere else on the same counter.
 *
 * Frames arrive faster than they can be scanned. Each one is closed as soon as its scan
 * finishes, which is what lets CameraX deliver the next; a frame left open stalls the
 * analyser permanently.
 */
internal class QrFrameAnalyzer(
    private val onDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val frame = imageProxy.image
        if (frame == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(frame, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onDetected)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Release the ML Kit scanner. Called when QR detection is switched off or the screen leaves. */
    fun close() {
        runCatching { scanner.close() }
    }
}
