package com.hopcape.odo.core.platform.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

/**
 * Finds a paper's outline in the live frames and reports a steadied version of it.
 *
 * Everything heavy is delegated: the frame's Y plane is downsampled to a small grid and
 * [QuadDetector] (pure, tested) does the finding. What this class owns is the camera-side
 * plumbing — reading the plane with its row stride, rotating the result upright, and
 * calming the per-frame answers so the markers on screen do not flicker: detections are
 * blended together, and a lost outline is only reported gone after a few consecutive
 * misses, because a single bad frame is noise rather than news.
 *
 * Once the blend has settled (the paper and the phone are still), the same value is kept
 * and nothing is reported — otherwise the asymptotic blend would emit a fresh quad every
 * frame forever and recompose the screen for no visible change.
 */
internal class DocumentEdgeAnalyzer(
    private val onQuad: (DetectedQuad?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = QuadDetector()
    private var smoothed: DetectedQuad? = null
    private var missStreak = 0

    /** Reused across frames — CameraX delivers them serially. */
    private var luma = IntArray(0)

    override fun analyze(imageProxy: ImageProxy) {
        val detected = runCatching { detectIn(imageProxy) }.getOrNull()
        imageProxy.close()

        if (detected == null) {
            missStreak++
            if (missStreak >= MISS_LIMIT && smoothed != null) {
                smoothed = null
                onQuad(null)
            }
            return
        }

        missStreak = 0
        val previous = smoothed
        val next = previous?.lerp(detected, SMOOTHING) ?: detected
        if (previous != null && previous.settledAgainst(next)) return
        smoothed = next
        onQuad(next)
    }

    /** Whether the blend has effectively stopped moving — nothing worth redrawing. */
    private fun DetectedQuad.settledAgainst(other: DetectedQuad): Boolean =
        corners.zip(other.corners).all { (a, b) ->
            abs(a.x - b.x) < SETTLE_EPSILON && abs(a.y - b.y) < SETTLE_EPSILON
        }

    private fun detectIn(imageProxy: ImageProxy): DetectedQuad? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val sourceWidth = imageProxy.width
        val sourceHeight = imageProxy.height
        val step = (sourceWidth / TARGET_GRID_WIDTH).coerceAtLeast(1)
        val gridWidth = sourceWidth / step
        val gridHeight = sourceHeight / step
        if (gridWidth < 1 || gridHeight < 1) return null

        val size = gridWidth * gridHeight
        if (luma.size < size) luma = IntArray(size)
        for (gridY in 0 until gridHeight) {
            val rowStart = gridY * step * plane.rowStride
            for (gridX in 0 until gridWidth) {
                val position = rowStart + gridX * step * plane.pixelStride
                if (position >= buffer.capacity()) return null
                luma[gridY * gridWidth + gridX] = buffer.get(position).toInt() and LUMA_MASK
            }
        }

        return detector.detect(luma, gridWidth, gridHeight)
            ?.rotated(imageProxy.imageInfo.rotationDegrees)
    }

    private companion object {
        /** ~96 columns keeps the grid around five thousand cells — per-frame cheap. */
        const val TARGET_GRID_WIDTH = 96

        /** How far each new detection pulls the drawn outline toward itself. */
        const val SMOOTHING = 0.4f

        /** Consecutive empty frames before the outline is declared gone. */
        const val MISS_LIMIT = 5

        /** Corner movement below this (normalized) is jitter, not motion. */
        const val SETTLE_EPSILON = 0.002f

        const val LUMA_MASK = 0xFF
    }
}
