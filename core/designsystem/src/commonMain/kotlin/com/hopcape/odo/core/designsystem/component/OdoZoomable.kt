package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Wraps content the owner needs to look at closely — a scanned bill, a policy page — in
 * pinch-to-zoom, drag-to-pan and double-tap-to-zoom.
 *
 * Panning is bounded by the content's own edges, so the page cannot be dragged off the screen.
 * A double-tap toggles between fitting the screen and [ZoomState.DOUBLE_TAP_SCALE].
 *
 * ```
 * OdoZoomable(resetKey = pageIndex) {
 *     Image(bitmap = page, contentDescription = null, contentScale = ContentScale.Fit)
 * }
 * ```
 *
 * Safe to put inside a pager: while the content is not zoomed it lets drags through, so the
 * pager still turns pages. It only takes them once there is something to move.
 *
 * @param resetKey change this to drop back to the unzoomed view — pass the page index in a
 *   pager, so turning the page does not arrive mid-zoom on the previous one.
 */
@Composable
fun OdoZoomable(
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var bounds by remember { mutableStateOf(Size.Zero) }
    var zoom by remember(resetKey) { mutableStateOf(ZoomState()) }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        zoom = zoom.transformedBy(zoomChange, panChange, centroid, bounds)
    }

    Box(
        modifier = modifier
            .onSizeChanged { bounds = it.toSize() }
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { zoom = zoom.toggled() })
            }
            // A drag is only ours once the content is bigger than its box. At rest this
            // modifier would otherwise swallow every one-finger drag, and a pager wrapping
            // this would never see a swipe — which is what stopped a multi-page PDF turning.
            // Pinching is unaffected: this gates panning, not zooming.
            .transformable(transformState, canPan = { zoom.isZoomed })
            // Last, so the gestures above still measure in the untransformed coordinates the
            // owner's finger is actually moving through.
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@OdoThemePreviews
@Composable
private fun OdoZoomablePreview() = OdoPreview {
    OdoZoomable(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(OdoTheme.colors.surfaceRaised)) {
            OdoText("Pinch to zoom", modifier = Modifier.align(Alignment.Center))
        }
    }
}
