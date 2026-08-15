package com.hopcape.odo.core.domain.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals

class PlanLimitsTest {

    /**
     * States what the free plan gives for every feature there is.
     *
     * The `when` is exhaustive, so adding a [ProFeature] stops this test compiling until
     * someone decides what the free plan does with it. That is the point of the test: a
     * feature with no row in the table is silently denied, and a compile error is a better
     * way to find that out than an owner reporting a locked screen.
     */
    @Test
    fun freePlanAnswersForEveryFeature() {
        ProFeature.entries.forEach { feature ->
            val expected = when (feature) {
                ProFeature.DOCUMENTS -> Quota.UpTo(3)
                ProFeature.BILL_SCANS -> Quota.UpTo(3)
                ProFeature.HEALTH_BREAKDOWN -> Quota.None
                ProFeature.RECORD_EXPORT -> Quota.UpTo(3)
                ProFeature.SMART_REFUEL_DETECT -> Quota.UpTo(10)
            }
            assertEquals(expected, PlanLimits.quota(Plan.FREE, feature), "free plan, $feature")
        }
    }

    @Test
    fun proGrantsEverythingWithoutACap() {
        ProFeature.entries.forEach { feature ->
            assertEquals(Quota.Unlimited, PlanLimits.quota(Plan.PRO, feature), "pro plan, $feature")
        }
    }
}
