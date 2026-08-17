package com.hopcape.odo.core.domain.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
                ProFeature.BILL_SCANS -> Quota.UpTo(5)
                ProFeature.HEALTH_BREAKDOWN -> Quota.None
                ProFeature.RECORD_EXPORT -> Quota.UpTo(3)
                ProFeature.COST_ANALYSIS -> Quota.None
                ProFeature.SCORE_HISTORY -> Quota.None
            }
            assertEquals(expected, PlanLimits.quota(Plan.FREE, feature), "free plan, $feature")
        }
    }

    /**
     * The three things Growth Plan v3 says must never be gated (#249, #251).
     *
     * Reminders are the retention engine *and* the affiliate engine — the insurance and PUC
     * referral revenue hangs off the expiry reminder, so a gated reminder is a gated
     * referral. Auto odometer and refuel logging are the habit engines: capping them stops
     * the habit forming for exactly the owners who have not paid yet.
     *
     * The registry is deliberately easy to extend — a new gate is one enum entry and one
     * table row — and that cuts both ways. Nothing else would fail if someone added
     * `REMINDERS` in a hurry, so this is the thing that has to. **Deleting this test is the
     * decision**, and whoever deletes it is choosing to break the plan's firmest rule.
     */
    @Test
    fun nothingTheGrowthPlanForbidsGatingIsInTheRegistry() {
        val forbidden = listOf("REMINDER", "ODOMETER", "TRIP", "REFUEL", "FILL", "FUEL_LOG")
        ProFeature.entries.forEach { feature ->
            forbidden.forEach { word ->
                assertFalse(
                    feature.name.contains(word),
                    "$feature looks like a gate on something Growth Plan v3 says is never gated " +
                        "(matched \"$word\"). Reminders drive the referral revenue; auto odometer " +
                        "and refuel logging are the habit engines. If this is deliberate, the plan " +
                        "changed and this test should say so.",
                )
            }
        }
    }

    @Test
    fun proGrantsEverythingWithoutACap() {
        ProFeature.entries.forEach { feature ->
            assertEquals(Quota.Unlimited, PlanLimits.quota(Plan.PRO, feature), "pro plan, $feature")
        }
    }
}
