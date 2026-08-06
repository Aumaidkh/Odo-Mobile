package com.hopcape.odo.core.platform.camera

import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hopcape.odo.core.platform.file.storedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Android [QrImageDecoder] — the same ML Kit reader [QrFrameAnalyzer] uses, pointed at a
 * stored photo instead of a live frame.
 *
 * The picture is made upright from its EXIF first. A screenshot has none, but a photographed
 * code usually does, and ML Kit reads a sideways code far less reliably than a straight one.
 *
 * Any failure answers `null` rather than throwing: a picture that will not decode and a
 * picture with no code in it lead to the same place for the owner, and the caller has to
 * handle the second one anyway.
 */
internal class AndroidQrImageDecoder(
    private val context: Context,
) : QrImageDecoder {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    override suspend fun decode(storageKey: String): String? = withContext(Dispatchers.Default) {
        runCatching {
            val source = context.storedFile(storageKey)
            val bitmap = BitmapFactory.decodeFile(source.absolutePath)
                ?.uprightBy(ExifInterface(source))
                ?: return@runCatching null
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .await()
                .firstNotNullOfOrNull { it.rawValue }
        }.getOrNull()
    }
}
