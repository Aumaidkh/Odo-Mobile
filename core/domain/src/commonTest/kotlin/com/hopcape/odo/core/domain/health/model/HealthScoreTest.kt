package com.hopcape.odo.core.domain.health.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HealthScoreTest {

    private fun score(maintenance: Int, documentation: Int, cost: Int, history: Int) = HealthScore(
        factors = listOf(
            HealthFactor.of(HealthFactorKind.MAINTENANCE, maintenance),
            HealthFactor.of(HealthFactorKind.DOCUMENTATION, documentation),
            HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, cost),
            HealthFactor.of(HealthFactorKind.HISTORY, history),
        ),
    )

    @Test
    fun theFourWeights_addUpToOneHundred() {
        assertEquals(100, HealthFactorKind.entries.sumOf { it.weight })
    }

    @Test
    fun totalIsTheSumOfTheFactors() {
        assertEquals(74, score(28, 24, 14, 8).total)
    }

    @Test
    fun bandCutOffsFollowThePrd() {
        assertEquals(HealthBand.EXCELLENT, HealthBand.of(100))
        assertEquals(HealthBand.EXCELLENT, HealthBand.of(85))
        assertEquals(HealthBand.GOOD, HealthBand.of(84))
        assertEquals(HealthBand.GOOD, HealthBand.of(70))
        assertEquals(HealthBand.FAIR, HealthBand.of(69))
        assertEquals(HealthBand.FAIR, HealthBand.of(50))
        assertEquals(HealthBand.POOR, HealthBand.of(49))
        assertEquals(HealthBand.POOR, HealthBand.of(0))
    }

    @Test
    fun earnedPointsAreClampedToTheFactorsWeight() {
        assertEquals(35, HealthFactor.of(HealthFactorKind.MAINTENANCE, 99).earned)
        assertEquals(0, HealthFactor.of(HealthFactorKind.MAINTENANCE, -3).earned)
    }

    @Test
    fun biggestGap_isTheFactorWithTheMostPointsLeft() {
        // Documentation is missing 26 of 30 — more than any other factor.
        val gap = score(maintenance = 30, documentation = 4, cost = 14, history = 12).biggestGap

        assertEquals(HealthFactorKind.DOCUMENTATION, gap?.kind)
        assertEquals(26, gap?.missing)
    }

    @Test
    fun biggestGap_breaksTiesTowardsTheHeavierFactor() {
        // Maintenance and documentation are both missing 10 points; the heavier factor
        // is where the owner's next action pays best.
        val gap = score(maintenance = 25, documentation = 20, cost = 20, history = 15).biggestGap

        assertEquals(HealthFactorKind.MAINTENANCE, gap?.kind)
    }

    @Test
    fun aPerfectScore_hasNothingToSuggest() {
        assertNull(score(35, 30, 20, 15).biggestGap)
    }

    @Test
    fun deltaIsNullWithoutAnEarlierScore() {
        assertNull(score(28, 24, 14, 8).deltaFrom(null))
    }

    @Test
    fun deltaCountsPointsGainedAndLost() {
        val now = score(28, 24, 14, 8)

        assertEquals(6, now.deltaFrom(score(22, 24, 14, 8)))
        assertEquals(-5, now.deltaFrom(score(28, 29, 14, 8)))
    }
}
