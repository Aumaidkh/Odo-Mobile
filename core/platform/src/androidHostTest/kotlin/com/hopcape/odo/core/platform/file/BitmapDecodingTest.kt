package com.hopcape.odo.core.platform.file

import kotlin.test.Test
import kotlin.test.assertEquals

class BitmapDecodingTest {

    @Test
    fun `an image no bigger than the target is decoded whole`() {
        assertEquals(1, sampleSizeFor(sourceWidthPx = 800, targetWidthPx = 1080))
        assertEquals(1, sampleSizeFor(sourceWidthPx = 1080, targetWidthPx = 1080))
    }

    @Test
    fun `shrinking stops before the result would be narrower than the target`() {
        // 4000 -> 2000 at 2; halving again would give 1000, under the 1080 asked for.
        assertEquals(2, sampleSizeFor(sourceWidthPx = 4000, targetWidthPx = 1080))
        // 4000 -> 500 at 8; the next step would give 250.
        assertEquals(8, sampleSizeFor(sourceWidthPx = 4000, targetWidthPx = 400))
    }

    @Test
    fun `the answer is always a power of two which is all BitmapFactory honours`() {
        val samples = listOf(1200, 2400, 4000, 8000, 12000).map { sampleSizeFor(it, 1080) }
        samples.forEach { sample ->
            assertEquals(0, sample and (sample - 1), "$sample is not a power of two")
        }
    }

    @Test
    fun `a source or target that makes no sense decodes whole rather than dividing by zero`() {
        assertEquals(1, sampleSizeFor(sourceWidthPx = 0, targetWidthPx = 1080))
        assertEquals(1, sampleSizeFor(sourceWidthPx = 4000, targetWidthPx = 0))
        assertEquals(1, sampleSizeFor(sourceWidthPx = -1, targetWidthPx = -1))
    }
}
