package com.hopcape.odo.core.platform.camera

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuadDetectorTest {

    private val detector = QuadDetector()

    /** A [width]×[height] dark grid with a bright axis-aligned rectangle painted on. */
    private fun gridWithRectangle(
        width: Int = 96,
        height: Int = 72,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        dark: Int = 40,
        bright: Int = 220,
    ): IntArray = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        if (x in left..right && y in top..bottom) bright else dark
    }

    @Test
    fun `a bright rectangle on a dark surface is found with its corners`() {
        val quad = detector.detect(
            gridWithRectangle(left = 20, top = 14, right = 75, bottom = 57),
            width = 96,
            height = 72,
        )

        assertNotNull(quad)
        // Corners land on the rectangle's cells, within one grid cell of tolerance.
        assertTrue(abs(quad.topLeft.x - 20.5f / 96) < 0.02f)
        assertTrue(abs(quad.topLeft.y - 14.5f / 72) < 0.02f)
        assertTrue(abs(quad.bottomRight.x - 75.5f / 96) < 0.02f)
        assertTrue(abs(quad.bottomRight.y - 57.5f / 72) < 0.02f)
    }

    @Test
    fun `a flat frame has no paper to find`() {
        assertNull(detector.detect(IntArray(96 * 72) { 128 }, 96, 72))
    }

    @Test
    fun `a small highlight is not a paper`() {
        assertNull(
            detector.detect(gridWithRectangle(left = 40, top = 30, right = 47, bottom = 36), 96, 72),
        )
    }

    @Test
    fun `a frame filled edge to edge with paper has no edges to mark`() {
        assertNull(
            detector.detect(gridWithRectangle(left = 0, top = 0, right = 95, bottom = 71), 96, 72),
        )
    }

    @Test
    fun `only the largest bright region is measured`() {
        val grid = gridWithRectangle(left = 10, top = 10, right = 60, bottom = 50)
        // A second, smaller bright patch elsewhere must not drag a corner over to itself.
        for (y in 60..70) for (x in 80..95) grid[y * 96 + x] = 220

        val quad = detector.detect(grid, 96, 72)

        assertNotNull(quad)
        assertTrue(quad.bottomRight.x < 70f / 96)
    }

    @Test
    fun `rotation remaps corners to the upright frame`() {
        val quad = DetectedQuad(
            topLeft = QuadPoint(0.1f, 0.2f),
            topRight = QuadPoint(0.9f, 0.2f),
            bottomRight = QuadPoint(0.9f, 0.8f),
            bottomLeft = QuadPoint(0.1f, 0.8f),
            frameAspectRatio = 4f / 3f,
        )

        val rotated = quad.rotated(90)

        // The sensor's top-left lands on the upright frame's top-right.
        assertTrue(abs(rotated.topRight.x - 0.8f) < 0.001f)
        assertTrue(abs(rotated.topRight.y - 0.1f) < 0.001f)
        assertEquals(3f / 4f, rotated.frameAspectRatio)
    }

    @Test
    fun `viewport mapping centre-crops the frame like the preview does`() {
        val quad = DetectedQuad(
            topLeft = QuadPoint(0.25f, 0.25f),
            topRight = QuadPoint(0.75f, 0.25f),
            bottomRight = QuadPoint(0.75f, 0.75f),
            bottomLeft = QuadPoint(0.25f, 0.75f),
            frameAspectRatio = 1f,
        )

        // A square frame in a tall viewport: full height shown, sides cropped.
        val corners = quad.cornersInViewport(viewportWidth = 100f, viewportHeight = 200f)

        // The frame's vertical middle half maps straight through.
        assertTrue(abs(corners[0].y - 0.25f) < 0.001f)
        // Horizontally the frame is wider than the viewport shows: 0.25 in frame space is
        // left of centre by half the frame, scaled by the 2x crop → 0.0 in view space.
        assertTrue(abs(corners[0].x - 0.0f) < 0.001f)
        assertTrue(abs(corners[1].x - 1.0f) < 0.001f)
    }
}
