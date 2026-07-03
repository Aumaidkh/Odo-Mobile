package com.hopcape.odo.core.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmountTest {

    @Test
    fun nullAmount_defaultsToZeroPaise() {
        val result = Amount.of(null)
        assertEquals(0L, result.getOrNull()?.paise)
        assertEquals(Amount.ZERO, result.getOrNull())
    }

    @Test
    fun zeroAmount_isAccepted() {
        assertEquals(0L, Amount.of(0).getOrNull()?.paise)
    }

    @Test
    fun positiveAmount_isStoredAsPaise() {
        // ₹2,800 → 280000 paise.
        assertEquals(280_000L, Amount.of(280_000).getOrNull()?.paise)
    }

    @Test
    fun negativeAmount_isRejected() {
        val result = Amount.of(-1)
        assertTrue(result.isLeft())
        assertEquals(DomainError.NegativeAmount, result.leftOrNull())
    }
}
