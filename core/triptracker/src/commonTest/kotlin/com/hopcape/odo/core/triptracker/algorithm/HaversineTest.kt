package com.hopcape.odo.core.triptracker.algorithm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HaversineTest {

    @Test
    fun knownDistance_matchesExpectedRange() {
        // Mumbai CST to Pune station, great-circle ~119 km.
        val meters = haversineMeters(18.9398, 72.8355, 18.5204, 73.8567)
        assertTrue(meters in 110_000.0..130_000.0, "got $meters")
    }

    @Test
    fun samePoint_isZero() {
        assertEquals(0.0, haversineMeters(19.0, 72.8, 19.0, 72.8), absoluteTolerance = 0.0001)
    }

    @Test
    fun isSymmetric() {
        val ab = haversineMeters(19.0, 72.8, 19.1, 72.9)
        val ba = haversineMeters(19.1, 72.9, 19.0, 72.8)
        assertEquals(ab, ba, absoluteTolerance = 0.0001)
    }
}
