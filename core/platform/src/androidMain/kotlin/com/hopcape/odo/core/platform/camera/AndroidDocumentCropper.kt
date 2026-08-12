package com.hopcape.odo.core.platform.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.hopcape.odo.core.platform.file.storedFile
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [DocumentCropper] — a perspective warp with [Matrix.setPolyToPoly].
 *
 * The photo is made upright first (CameraX stores rotation in EXIF, and the quad was
 * detected in upright space), then the four corners map onto a straight rectangle whose
 * sides are the quad's average edge lengths — so the output keeps the bill's own
 * proportions instead of stretching it to some fixed page shape.
 *
 * The crop is written next to the original as `<name>-crop.jpg` rather than over it. The
 * original is the capture; a warp gone wrong must leave it retrievable.
 */
internal class AndroidDocumentCropper(
    private val context: Context,
) : DocumentCropper {

    override suspend fun crop(storageKey: String, quad: DetectedQuad): String =
        withContext(Dispatchers.Default) {
            runCatching { croppedKey(storageKey, quad) }.getOrNull() ?: storageKey
        }

    private fun croppedKey(storageKey: String, quad: DetectedQuad): String? {
        val source = context.storedFile(storageKey)
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?.uprightBy(ExifInterface(source))
            ?: return null

        // The live quad came off a coarse preview grid; the still has far more pixels to
        // find the same outline in, so it is re-detected here and the sharper answer wins.
        // A small outward margin then keeps edge-hugging text out of the knife's path.
        val refined = (refinedQuad(bitmap) ?: quad).withMargin(CROP_MARGIN)

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        fun px(point: QuadPoint) = floatArrayOf(point.x * width, point.y * height)
        val (tl, tr, br, bl) = refined.corners.map(::px)

        val topWidth = hypot(tr[0] - tl[0], tr[1] - tl[1])
        val bottomWidth = hypot(br[0] - bl[0], br[1] - bl[1])
        val leftHeight = hypot(bl[0] - tl[0], bl[1] - tl[1])
        val rightHeight = hypot(br[0] - tr[0], br[1] - tr[1])
        val outWidth = ((topWidth + bottomWidth) / 2f).roundToInt()
        val outHeight = ((leftHeight + rightHeight) / 2f).roundToInt()
        if (outWidth < MIN_OUTPUT_SIDE || outHeight < MIN_OUTPUT_SIDE) return null

        val matrix = Matrix()
        val mapped = matrix.setPolyToPoly(
            floatArrayOf(tl[0], tl[1], tr[0], tr[1], br[0], br[1], bl[0], bl[1]),
            0,
            floatArrayOf(0f, 0f, outWidth.toFloat(), 0f, outWidth.toFloat(), outHeight.toFloat(), 0f, outHeight.toFloat()),
            0,
            POINT_COUNT,
        )
        if (!mapped) return null

        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

        val croppedKey = storageKey.substringBeforeLast('.') + CROP_SUFFIX
        FileOutputStream(context.storedFile(croppedKey)).use { stream ->
            if (!output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) return null
        }
        return croppedKey
    }

    /** The outline re-detected on the still itself, on a finer grid than the live pass. */
    private fun refinedQuad(bitmap: Bitmap): DetectedQuad? {
        val gridWidth = REFINE_GRID_WIDTH.coerceAtMost(bitmap.width)
        val gridHeight = (gridWidth * bitmap.height / bitmap.width).coerceAtLeast(1)
        val grid = Bitmap.createScaledBitmap(bitmap, gridWidth, gridHeight, false)

        val pixels = IntArray(gridWidth * gridHeight)
        grid.getPixels(pixels, 0, gridWidth, 0, 0, gridWidth, gridHeight)
        val luma = IntArray(pixels.size)
        pixels.forEachIndexed { index, pixel ->
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            luma[index] = (red * 299 + green * 587 + blue * 114) / 1000
        }
        return QuadDetector().detect(luma, gridWidth, gridHeight)
    }

    /** Corners pushed outward from the centroid by [margin], clamped to the frame. */
    private fun DetectedQuad.withMargin(margin: Float): DetectedQuad {
        val centreX = corners.map { it.x }.average().toFloat()
        val centreY = corners.map { it.y }.average().toFloat()
        fun pushed(point: QuadPoint) = QuadPoint(
            x = (centreX + (point.x - centreX) * (1f + margin)).coerceIn(0f, 1f),
            y = (centreY + (point.y - centreY) * (1f + margin)).coerceIn(0f, 1f),
        )
        return DetectedQuad(
            topLeft = pushed(topLeft),
            topRight = pushed(topRight),
            bottomRight = pushed(bottomRight),
            bottomLeft = pushed(bottomLeft),
            frameAspectRatio = frameAspectRatio,
        )
    }

    private companion object {
        const val POINT_COUNT = 4
        const val JPEG_QUALITY = 92
        const val CROP_SUFFIX = "-crop.jpg"

        /** An output smaller than this is a quad that collapsed, not a bill. */
        const val MIN_OUTPUT_SIDE = 200

        /** The still is sampled onto a grid twice as fine as the live analyser's. */
        const val REFINE_GRID_WIDTH = 192

        /** How far the crop reaches past the detected edge — text often hugs it. */
        const val CROP_MARGIN = 0.03f
    }
}
