package com.hopcape.odo.core.platform.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

/**
 * The bitmap with [exif]'s rotation applied — how Odo makes a capture upright.
 *
 * CameraX writes portrait captures as landscape pixels plus an orientation tag, so every
 * consumer of a captured photo (OCR, cropping) needs this same correction. One public
 * helper, because two silently-twinned copies of a rotation table is how one of them ends
 * up missing an orientation.
 */
fun Bitmap.uprightBy(exif: ExifInterface): Bitmap {
    val orientation = runCatching {
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return this
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
