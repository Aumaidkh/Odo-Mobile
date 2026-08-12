package com.hopcape.odo.infrastructure.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.hopcape.odo.core.platform.camera.uprightBy
import java.io.ByteArrayInputStream

/**
 * Getting a photo ready for ML Kit: decode it small enough, stand it upright, size it so the
 * text clears the recogniser's minimum height, and measure whether it is worth reading at all.
 *
 * Top-level and shared because every kind of scan needs the same preparation — a bill, an
 * insurance certificate and a PUC slip are all a photographed paper. Only what happens to the
 * *text* afterwards differs, and that lives in each extractor.
 */


/**
 * Decodes the capture without materialising more pixels than OCR uses. A modern phone
 * writes ~50MP JPEGs; `inSampleSize` halves the decode until the long edge is near
 * [MAX_LONG_EDGE], and [sizedForOcr] does the final exact scale.
 */
internal fun decodeBounded(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    var sample = 1
    var longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    while (longEdge / 2 >= MAX_LONG_EDGE) {
        sample *= 2
        longEdge /= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/**
 * The bitmap with its EXIF rotation applied. CameraX writes portrait captures as
 * landscape pixels plus an orientation tag; OCR on sideways text finds nothing.
 */
internal fun Bitmap.uprightFrom(bytes: ByteArray): Bitmap =
    runCatching { uprightBy(ExifInterface(ByteArrayInputStream(bytes))) }.getOrDefault(this)

internal fun Bitmap.sizedForOcr(): Bitmap {
    val longEdge = maxOf(width, height)
    val target = when {
        longEdge > MAX_LONG_EDGE -> MAX_LONG_EDGE
        longEdge < MIN_LONG_EDGE -> UPSCALED_LONG_EDGE
        else -> return this
    }
    val scale = target.toFloat() / longEdge
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}

/** A small grayscale copy — computed once, read by both quality checks. */
internal class GraySample(val luma: IntArray, val width: Int, val height: Int)

internal fun Bitmap.graySample(): GraySample {
    val sample = Bitmap.createScaledBitmap(
        this,
        GRAY_SAMPLE_WIDTH,
        (GRAY_SAMPLE_WIDTH * height / width).coerceAtLeast(1),
        true,
    )
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
    val luma = IntArray(pixels.size)
    pixels.forEachIndexed { index, pixel ->
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        luma[index] = (red * 299 + green * 587 + blue * 114) / 1000
    }
    return GraySample(luma, sample.width, sample.height)
}

/**
 * Whether the photo is too soft to trust — the variance of a Laplacian over the gray
 * sample. Sharp text produces strong second-order gradients; a focus miss or a shake
 * flattens them. The classic, cheap blur measure.
 */
internal fun GraySample.measuresBlurry(): Boolean {
    if (width < 3 || height < 3) return false

    var sum = 0.0
    var squaredSum = 0.0
    var count = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val index = y * width + x
            val laplacian = (4 * luma[index] -
                luma[index - 1] - luma[index + 1] -
                luma[index - width] - luma[index + width]).toDouble()
            sum += laplacian
            squaredSum += laplacian * laplacian
            count++
        }
    }
    if (count == 0) return false
    val mean = sum / count
    val variance = squaredSum / count - mean * mean
    return variance < BLUR_VARIANCE_FLOOR
}

/**
 * Stretches a narrow brightness range across the full scale — a dim or washed-out
 * photo becomes one with dark ink on light paper, which is what the recogniser is
 * best at. A photo that already spans the range is returned untouched; stretching it
 * further would only amplify noise.
 */
internal fun Bitmap.contrastStretched(gray: GraySample): Bitmap {
    val (low, high) = gray.lumaPercentiles() ?: return this
    if (high - low >= WIDE_LUMA_RANGE) return this

    val scale = LUMA_LEVELS_F / (high - low).coerceAtLeast(MIN_LUMA_RANGE)
    val offset = -low * scale
    val matrix = ColorMatrix().apply { setSaturation(0f) }
    matrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(this, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
    return output
}

/** The 2nd and 98th luminance percentiles of the gray sample. */
internal fun GraySample.lumaPercentiles(): Pair<Float, Float>? {
    val total = luma.size
    if (total == 0) return null

    val histogram = IntArray(LUMA_LEVELS)
    luma.forEach { histogram[it]++ }

    var count = 0
    var low = -1f
    var high = -1f
    for (level in 0 until LUMA_LEVELS) {
        count += histogram[level]
        if (low < 0 && count >= total * LOW_PERCENTILE) low = level.toFloat()
        if (high < 0 && count >= total * HIGH_PERCENTILE) {
            high = level.toFloat()
            break
        }
    }
    return if (low < 0 || high <= low) null else low to high
}

/* Sizing: ML Kit reads best when text is comfortably above its minimum height. */
internal const val MAX_LONG_EDGE = 2600
internal const val MIN_LONG_EDGE = 1200
internal const val UPSCALED_LONG_EDGE = 1700

/* Photo quality, measured on one shared gray sample. */
internal const val GRAY_SAMPLE_WIDTH = 256
internal const val BLUR_VARIANCE_FLOOR = 80.0

/* Contrast stretch. */
internal const val LUMA_LEVELS = 256
internal const val LUMA_LEVELS_F = 255f
internal const val WIDE_LUMA_RANGE = 170f
internal const val MIN_LUMA_RANGE = 30f
internal const val LOW_PERCENTILE = 0.02f
internal const val HIGH_PERCENTILE = 0.98f
