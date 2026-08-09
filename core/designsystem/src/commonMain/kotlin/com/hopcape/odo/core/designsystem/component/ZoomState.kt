package com.hopcape.odo.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified

/**
 * How far [OdoZoomable]'s content is scaled and how far it has been dragged.
 *
 * Kept out of the composable and free of any Compose runtime so the rules that are easy to get
 * wrong — the scale limits, and never dragging past the content's own edge — are ordinary
 * functions with ordinary tests.
 */
@Immutable
internal data class ZoomState(
    val scale: Float = MIN_SCALE,
    val offset: Offset = Offset.Zero,
) {

    val isZoomed: Boolean get() = scale > MIN_SCALE

    /**
     * Apply one gesture: [zoomChange] is a multiplier (1 means no pinch), [panChange] is the
     * drag in pixels, and [centroid] is the point between the owner's fingers. [bounds] is the
     * size the content is laid out at, which is what decides how far a drag is allowed to go.
     *
     * The zoom is anchored on [centroid], so whatever was under the fingers stays under them.
     * Zooming about the middle of the screen instead would slide the line being read away as
     * it grows, which on a bill is the one thing the owner is trying to do.
     */
    fun transformedBy(zoomChange: Float, panChange: Offset, centroid: Offset, bounds: Size): ZoomState {
        val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        if (!bounds.isSpecified) return ZoomState(newScale, Offset.Zero)
        // The scale actually applied, which differs from the gesture's once it hits a limit.
        val applied = newScale / scale
        val fromCentre = centroid - Offset(bounds.width / 2f, bounds.height / 2f)
        val anchored = fromCentre * (1f - applied) + offset * applied
        return ZoomState(newScale, clamp(anchored + panChange, bounds, newScale))
    }

    /** Double-tap: zoom in from rest, or go straight back to fitting the screen. */
    fun toggled(): ZoomState = if (isZoomed) ZoomState() else ZoomState(DOUBLE_TAP_SCALE)

    /**
     * Keep the drag inside the scaled content, so a page can never be pulled off the screen
     * leaving a band of empty background where the paper should be. At rest the content
     * exactly fills its box, so the only allowed offset is zero.
     */
    private fun clamp(candidate: Offset, bounds: Size, scale: Float): Offset {
        if (!bounds.isSpecified) return Offset.Zero
        val maxX = bounds.width * (scale - MIN_SCALE) / 2f
        val maxY = bounds.height * (scale - MIN_SCALE) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f

        /** Where a double-tap lands: close enough to read small print, short of the ceiling. */
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}
