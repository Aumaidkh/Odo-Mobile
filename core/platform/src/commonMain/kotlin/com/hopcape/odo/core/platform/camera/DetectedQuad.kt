package com.hopcape.odo.core.platform.camera

import androidx.compose.ui.geometry.Offset

/** One corner of a detected paper, normalized 0..1 against the upright camera frame. */
data class QuadPoint(val x: Float, val y: Float)

/**
 * The outline of a paper found in the live camera frames.
 *
 * Corners are named by where they sit in the upright frame and normalized 0..1, so the
 * value survives every resolution the analyser, the still capture and the preview happen
 * to use. [frameAspectRatio] (upright width over height) travels with it because the
 * preview fills its viewport by cropping — mapping a frame point onto the screen needs to
 * know how much of the frame the viewport is actually showing.
 */
data class DetectedQuad(
    val topLeft: QuadPoint,
    val topRight: QuadPoint,
    val bottomRight: QuadPoint,
    val bottomLeft: QuadPoint,
    val frameAspectRatio: Float,
) {

    /** Clockwise from the top-left — the order every consumer draws and maps in. */
    val corners: List<QuadPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /**
     * Where the corners land inside a viewport of [viewportWidth] × [viewportHeight] that
     * shows the frame centre-cropped to fill (the preview's behaviour), as offsets
     * normalized to the viewport. Corners may fall slightly outside 0..1 when the crop
     * cut that edge off — the drawing clips, so that is fine.
     */
    fun cornersInViewport(viewportWidth: Float, viewportHeight: Float): List<Offset> {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return emptyList()
        // Frame in abstract units: width = aspect, height = 1. Fill-centre scale covers
        // the viewport; whatever overflows is cropped equally on both sides.
        val scale = maxOf(viewportWidth / frameAspectRatio, viewportHeight)
        val shownWidth = frameAspectRatio * scale
        val shownHeight = scale
        val offsetX = (viewportWidth - shownWidth) / 2f
        val offsetY = (viewportHeight - shownHeight) / 2f
        return corners.map { corner ->
            Offset(
                x = (offsetX + corner.x * shownWidth) / viewportWidth,
                y = (offsetY + corner.y * shownHeight) / viewportHeight,
            )
        }
    }

    /** This quad blended toward [target] by [fraction] — the per-frame smoothing step. */
    fun lerp(target: DetectedQuad, fraction: Float): DetectedQuad {
        fun mix(a: QuadPoint, b: QuadPoint) =
            QuadPoint(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction)
        return DetectedQuad(
            topLeft = mix(topLeft, target.topLeft),
            topRight = mix(topRight, target.topRight),
            bottomRight = mix(bottomRight, target.bottomRight),
            bottomLeft = mix(bottomLeft, target.bottomLeft),
            frameAspectRatio = target.frameAspectRatio,
        )
    }

    /**
     * The same outline after the frame is rotated to upright by [degrees] (0/90/180/270,
     * clockwise — the sensor-to-display rotation the camera reports). Corner names are
     * re-assigned after rotating, because the sensor's top-left is not the screen's.
     */
    fun rotated(degrees: Int): DetectedQuad {
        val turn = ((degrees % 360) + 360) % 360
        if (turn == 0) return this
        val rotatedPoints = corners.map { point ->
            when (turn) {
                90 -> QuadPoint(1f - point.y, point.x)
                180 -> QuadPoint(1f - point.x, 1f - point.y)
                else -> QuadPoint(point.y, 1f - point.x) // 270
            }
        }
        val aspect = if (turn == 180) frameAspectRatio else 1f / frameAspectRatio
        return fromCorners(rotatedPoints, aspect) ?: this
    }

    companion object {

        /**
         * Assigns corner names to four unordered [points]. The extremes of x+y and x−y are
         * the diagonal corners of any roughly upright quad; a set where two extremes land
         * on the same point is not a quad and answers null.
         */
        fun fromCorners(points: List<QuadPoint>, frameAspectRatio: Float): DetectedQuad? {
            if (points.size != 4) return null
            val topLeft = points.minBy { it.x + it.y }
            val bottomRight = points.maxBy { it.x + it.y }
            val topRight = points.maxBy { it.x - it.y }
            val bottomLeft = points.minBy { it.x - it.y }
            val named = listOf(topLeft, topRight, bottomRight, bottomLeft)
            if (named.distinct().size != 4) return null
            return DetectedQuad(topLeft, topRight, bottomRight, bottomLeft, frameAspectRatio)
        }
    }
}
