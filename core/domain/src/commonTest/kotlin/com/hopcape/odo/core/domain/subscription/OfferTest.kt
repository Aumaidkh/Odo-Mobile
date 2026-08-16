package com.hopcape.odo.core.domain.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The saving badge, which is the only number Odo works out about a price rather than
 * printing what the store said.
 */
class OfferTest {

    @Test
    fun theAnnualPlanSavingIsMeasuredAgainstTwelveMonthlyCharges() {
        // 149 x 12 = 1,788 against 1,490 — a saving of 298, which is 16%.
        val offer = offer(monthly = plan(BillingPeriod.MONTHLY, 149), annual = plan(BillingPeriod.ANNUAL, 1_490))

        assertEquals(16, offer.annualSavingPercent)
    }

    @Test
    fun nothingIsClaimedWhenAnnualIsNotActuallyCheaper() {
        val offer = offer(monthly = plan(BillingPeriod.MONTHLY, 100), annual = plan(BillingPeriod.ANNUAL, 1_200))

        assertNull(offer.annualSavingPercent, "paying the same is not a saving")
    }

    @Test
    fun nothingIsClaimedWhenAnnualCostsMore() {
        val offer = offer(monthly = plan(BillingPeriod.MONTHLY, 100), annual = plan(BillingPeriod.ANNUAL, 1_500))

        assertNull(offer.annualSavingPercent)
    }

    @Test
    fun nothingIsClaimedAcrossTwoCurrencies() {
        // The figures would divide, and the answer would mean nothing.
        val offer = offer(
            monthly = plan(BillingPeriod.MONTHLY, 149, currency = "INR"),
            annual = plan(BillingPeriod.ANNUAL, 1_490, currency = "USD"),
        )

        assertNull(offer.annualSavingPercent)
    }

    @Test
    fun nothingIsClaimedWithOnlyOnePlan() {
        val offer = Offer(id = "default", plans = listOf(plan(BillingPeriod.MONTHLY, 149)))

        assertNull(offer.annualSavingPercent)
    }

    @Test
    fun plansAreReachableByPeriod() {
        val offer = offer(monthly = plan(BillingPeriod.MONTHLY, 149), annual = plan(BillingPeriod.ANNUAL, 1_490))

        assertEquals(BillingPeriod.MONTHLY, offer.monthly?.period)
        assertEquals(BillingPeriod.ANNUAL, offer.annual?.period)
    }

    @Test
    fun aTrialOnEitherPlanCountsAsOne() {
        val withTrial = offer(
            monthly = plan(BillingPeriod.MONTHLY, 149, trialDays = 7),
            annual = plan(BillingPeriod.ANNUAL, 1_490),
        )
        val withoutTrial = offer(
            monthly = plan(BillingPeriod.MONTHLY, 149),
            annual = plan(BillingPeriod.ANNUAL, 1_490),
        )

        assertTrue(withTrial.hasFreeTrial)
        assertTrue(!withoutTrial.hasFreeTrial)
    }

    private fun offer(monthly: PlanOption, annual: PlanOption) =
        Offer(id = "default", plans = listOf(monthly, annual))

    private fun plan(
        period: BillingPeriod,
        rupees: Long,
        currency: String = "INR",
        trialDays: Int? = null,
    ) = PlanOption(
        id = "plan-${period.name.lowercase()}",
        period = period,
        formattedPrice = "₹$rupees",
        formattedPricePerMonth = "₹$rupees",
        amountMicros = rupees * 1_000_000,
        currencyCode = currency,
        freeTrialDays = trialDays,
    )
}
