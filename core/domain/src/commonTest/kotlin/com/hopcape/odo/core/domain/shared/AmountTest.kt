package com.hopcape.odo.core.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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

    @Test
    fun timesScalesTheAmount() {
        // ₹1.05/km over 200 km.
        assertEquals(21_000L, (amount(105) * 200).paise)
        assertEquals(0L, (amount(105) * 0).paise)
    }

    @Test
    fun timesRejectsANegativeFactor() {
        assertFailsWith<IllegalArgumentException> { amount(105) * -1 }
    }

    @Test
    fun perKmRoundsToTheNearestPaise() {
        // ₹1,020 over 220 km = 463.6 paise/km.
        assertEquals(464L, amount(102_000).perKm(distance(220))?.paise)
        // 462.4 paise/km rounds down.
        assertEquals(462L, amount(101_740).perKm(distance(220))?.paise)
    }

    @Test
    fun perKmOverNoDistance_hasNoRate() {
        assertNull(amount(102_000).perKm(distance(0)))
    }

    private fun amount(paise: Long): Amount = Amount.of(paise).getOrNull()!!

    private fun distance(km: Int): Distance = Distance.of(km).getOrNull()!!
}
