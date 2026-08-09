package com.hopcape.odo.core.platform.file

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** The width a decode aims for when the caller has no layout width to offer. */
internal const val DEFAULT_TARGET_WIDTH_PX = 1080

/**
 * How much to shrink a source image by while decoding it.
 *
 * `BitmapFactory` only halves, so the answer is always a power of two. It is the largest one
 * that still leaves the bitmap at least [targetWidthPx] wide: shrinking past the width it will
 * be drawn at is what makes a photo look soft, and stopping one step early is what makes a
 * 12 MP camera scan a 48 MB allocation.
 */
internal fun sampleSizeFor(sourceWidthPx: Int, targetWidthPx: Int): Int {
    if (sourceWidthPx <= 0 || targetWidthPx <= 0 || sourceWidthPx <= targetWidthPx) return 1
    var sample = 1
    while (sourceWidthPx / (sample * 2) >= targetWidthPx) sample *= 2
    return sample
}

/**
 * Decodes [file] shrunk to roughly [targetWidthPx], or `null` if it is not an image this
 * device can read.
 *
 * Two passes: the first reads only the header for the source size, the second does the real
 * decode with the sample size that size implies.
 */
internal fun decodeBounded(file: File, targetWidthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, targetWidthPx)
    }
    return BitmapFactory.decodeFile(file.path, options)
}
