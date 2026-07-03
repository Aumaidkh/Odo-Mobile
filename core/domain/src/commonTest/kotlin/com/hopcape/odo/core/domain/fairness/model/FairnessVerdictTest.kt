package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FairnessVerdictTest {

    private fun amt(paise: Long) = Amount.of(paise).getOrNull()!!

    // Rs. 2,400 average, reliable sample.
    private fun estimate(avgPaise: Long = 240_000, sample: Int = 30) =
        FairnessEstimate(ServiceCategory.BRAKES, "Pune", amt(avgPaise), sample)

    @Test
    fun withinBand_isFair() {
        assertEquals(FairnessVerdict.Fair, FairnessVerdict.of(amt(250_000), estimate()))
    }

    @Test
    fun aboveBand_isOver_byDifference() {
        val v = FairnessVerdict.of(amt(330_000), estimate())
        assertIs<FairnessVerdict.Over>(v)
        assertEquals(90_000L, v.by.paise)
    }

    @Test
    fun belowBand_isUnder_byDifference() {
        val v = FairnessVerdict.of(amt(150_000), estimate())
        assertIs<FairnessVerdict.Under>(v)
        assertEquals(90_000L, v.by.paise)
    }

    @Test
    fun smallSample_isLowConfidence_notAVerdict() {
        val v = FairnessVerdict.of(amt(500_000), estimate(sample = 3))
        assertIs<FairnessVerdict.LowConfidence>(v)
        assertEquals(3, v.estimate.sampleSize)
    }

    @Test
    fun confidenceBands() {
        assertEquals(FairnessConfidence.LOW, FairnessConfidence.of(4))
        assertEquals(FairnessConfidence.MEDIUM, FairnessConfidence.of(10))
        assertEquals(FairnessConfidence.HIGH, FairnessConfidence.of(50))
    }
}
