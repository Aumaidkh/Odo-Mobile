package com.hopcape.odo.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoomStateTest {

    private val bounds = Size(width = 1000f, height = 2000f)

    /** A pinch centred on the content, which is the case that should move nothing sideways. */
    private val centre = Offset(500f, 1000f)

    private fun ZoomState.pinched(zoomChange: Float, pan: Offset = Offset.Zero, at: Offset = centre) =
        transformedBy(zoomChange, pan, at, bounds)

    @Test
    fun `starts fitting the screen`() {
        val state = ZoomState()
        assertEquals(ZoomState.MIN_SCALE, state.scale)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed)
    }

    @Test
    fun `a pinch multiplies the scale`() {
        val state = ZoomState().pinched(2f)
        assertEquals(2f, state.scale)
        assertTrue(state.isZoomed)
    }

    @Test
    fun `the scale cannot leave its limits however hard the pinch`() {
        val zoomedIn = ZoomState().pinched(100f)
        assertEquals(ZoomState.MAX_SCALE, zoomedIn.scale)

        val zoomedOut = zoomedIn.pinched(0.001f)
        assertEquals(ZoomState.MIN_SCALE, zoomedOut.scale)
    }

    @Test
    fun `a pinch on the middle of the content does not slide it`() {
        assertEquals(Offset.Zero, ZoomState().pinched(2f).offset)
    }

    @Test
    fun `a pinch keeps what is under the fingers under the fingers`() {
        // Pinching the top-left corner to 2x has to push the content down and right by half
        // the box, or the corner being looked at would grow away off the screen.
        val state = ZoomState().pinched(2f, at = Offset.Zero)
        assertEquals(Offset(500f, 1000f), state.offset)
    }

    @Test
    fun `at rest the content cannot be dragged at all`() {
        assertEquals(Offset.Zero, ZoomState().pinched(1f, pan = Offset(500f, 500f)).offset)
    }

    @Test
    fun `a drag stops at the edge of the scaled content`() {
        // At 2x the content is twice the box, so half of it — 500 x 1000 — can move into view.
        assertEquals(Offset(500f, 1000f), ZoomState().pinched(2f, pan = Offset(9999f, 9999f)).offset)
        assertEquals(Offset(-500f, -1000f), ZoomState().pinched(2f, pan = Offset(-9999f, -9999f)).offset)
    }

    @Test
    fun `a drag within the edge is kept as it is`() {
        assertEquals(Offset(100f, -200f), ZoomState().pinched(2f, pan = Offset(100f, -200f)).offset)
    }

    @Test
    fun `zooming back out pulls the content back into place`() {
        val panned = ZoomState().pinched(4f, pan = Offset(9999f, 9999f))
        assertTrue(panned.offset.x > 0f)

        val reset = panned.pinched(0.25f)
        assertEquals(ZoomState.MIN_SCALE, reset.scale)
        assertEquals(Offset.Zero, reset.offset)
    }

    @Test
    fun `double tap zooms in from rest and returns from anywhere`() {
        assertEquals(ZoomState.DOUBLE_TAP_SCALE, ZoomState().toggled().scale)

        val backToRest = ZoomState(scale = 4f, offset = Offset(120f, 40f)).toggled()
        assertEquals(ZoomState.MIN_SCALE, backToRest.scale)
        assertEquals(Offset.Zero, backToRest.offset)
    }

    @Test
    fun `a gesture before the content is measured cannot move it`() {
        val unmeasured = ZoomState().transformedBy(2f, Offset(300f, 300f), centre, Size.Unspecified)
        assertEquals(Offset.Zero, unmeasured.offset)
    }
}
