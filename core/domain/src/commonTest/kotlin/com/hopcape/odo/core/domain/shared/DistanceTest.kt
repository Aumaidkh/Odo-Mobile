package com.hopcape.odo.core.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceTest {

    @Test
    fun zero_isAccepted() {
        val result = Distance.of(0)
        assertTrue(result.isRight())
        assertEquals(0, result.getOrNull()?.km)
    }

    @Test
    fun positive_isAccepted() {
        assertEquals(45_000, Distance.of(45_000).getOrNull()?.km)
    }

    @Test
    fun negative_isRejected() {
        assertEquals(DomainError.NegativeOdometer, Distance.of(-1).leftOrNull())
    }

    @Test
    fun nullValue_isRejectedAsMissing() {
        assertEquals(DomainError.MissingOdometer, Distance.of(null).leftOrNull())
    }
}
