package com.hopcape.odo.core.domain.trip.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TripDistanceTest {

    @Test
    fun zero_isAccepted() {
        assertEquals(0L, TripDistance.of(0).meters)
    }

    @Test
    fun negative_throws() {
        assertFailsWith<IllegalArgumentException> { TripDistance.of(-1) }
    }

    @Test
    fun plus_sumsMeters() {
        val sum = TripDistance.of(300) + TripDistance.of(700)
        assertEquals(1_000L, sum.meters)
    }

    @Test
    fun toDistance_floorsToWholeKm() {
        assertEquals(1, TripDistance.of(1_999).toDistance().km)
        assertEquals(2, TripDistance.of(2_000).toDistance().km)
    }

    @Test
    fun km_isMetersDividedByThousand() {
        assertEquals(1.5, TripDistance.of(1_500).km)
    }
}
