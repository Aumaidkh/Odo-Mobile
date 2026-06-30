package com.hopcape.odo.core.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OdometerReadingTest {

    @Test
    fun zero_isAccepted() {
        val result = OdometerReading.of(0)
        assertTrue(result.isRight())
        assertEquals(0, result.getOrNull()?.km)
    }

    @Test
    fun positive_isAccepted() {
        assertEquals(45_000, OdometerReading.of(45_000).getOrNull()?.km)
    }

    @Test
    fun negative_isRejected() {
        assertEquals(DomainError.NegativeOdometer, OdometerReading.of(-1).leftOrNull())
    }

    @Test
    fun nullValue_isRejectedAsMissing() {
        assertEquals(DomainError.MissingOdometer, OdometerReading.of(null).leftOrNull())
    }
}
