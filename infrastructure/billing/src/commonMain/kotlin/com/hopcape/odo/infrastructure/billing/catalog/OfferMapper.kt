package com.hopcape.odo.infrastructure.billing.catalog

import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.core.domain.subscription.Offer
import com.hopcape.odo.core.domain.subscription.PlanOption
import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType
import com.revenuecat.purchases.kmp.models.Period
import com.revenuecat.purchases.kmp.models.PeriodUnit
import com.revenuecat.purchases.kmp.models.StoreProduct

/**
 * RevenueCat's offering to Odo's [Offer].
 *
 * Kept apart from the adapter so it can be tested without a store: everything RevenueCat
 * hands over here is an interface, so a package with a price and a trial is a few lines of
 * fake rather than a device with a Play account on it.
 */
internal object OfferMapper {

    /**
     * Maps [offering], dropping any package that is not one of the two plans Odo sells.
     *
     * Returns null when nothing is left. A dashboard can hold weekly or lifetime packages
     * quite legitimately; this app has one screen and it shows a monthly and an annual plan,
     * so anything else is not something it can render honestly. [onDropped] is called for
     * each one so the drop is visible rather than silent — an offering that renders as an
     * empty paywall must not be a mystery.
     */
    fun map(offering: Offering, onDropped: (packageId: String, type: String) -> Unit): Offer? {
        val plans = offering.availablePackages.mapNotNull { candidate ->
            val period = candidate.packageType.toBillingPeriod()
            if (period == null) {
                onDropped(candidate.identifier, candidate.packageType.name)
                null
            } else {
                candidate.toPlanOption(period)
            }
        }
        return if (plans.isEmpty()) null else Offer(id = offering.identifier, plans = plans)
    }

    /** Only the two Odo sells. Everything else is deliberately not guessed at. */
    private fun PackageType.toBillingPeriod(): BillingPeriod? = when (this) {
        PackageType.MONTHLY -> BillingPeriod.MONTHLY
        PackageType.ANNUAL -> BillingPeriod.ANNUAL
        else -> null
    }

    private fun Package.toPlanOption(period: BillingPeriod): PlanOption {
        val price = storeProduct.price
        return PlanOption(
            id = identifier,
            period = period,
            formattedPrice = price.formatted,
            // The store derives and formats the per-month figure, so neither the division nor
            // the rounding is Odo's to get wrong. Monthly plans have no separate one.
            formattedPricePerMonth = storeProduct.pricePerMonth?.formatted ?: price.formatted,
            amountMicros = price.amountMicros,
            currencyCode = price.currencyCode,
            freeTrialDays = storeProduct.freeTrialDays(),
        )
    }

    /**
     * How long this product's free trial runs, or null when it has none.
     *
     * A trial is a pricing phase that costs nothing, so that is what is looked for rather
     * than a flag: it reads the same on both stores. `subscriptionOptions.freeTrial` is
     * Play's shape and is preferred because the store has already filtered it by eligibility
     * — an owner who used their trial is not shown one. `defaultOption` is the fallback, and
     * covers StoreKit.
     */
    private fun StoreProduct.freeTrialDays(): Int? {
        val phases = subscriptionOptions?.freeTrial?.pricingPhases
            ?: defaultOption?.pricingPhases
            ?: return null
        val free = phases.firstOrNull { it.price.amountMicros == 0L } ?: return null
        return free.billingPeriod?.inDays()?.takeIf { it > 0 }
    }

    /**
     * A period in days.
     *
     * Months and years are approximated, which is fine for the only thing this is used for:
     * a trial is configured in days or weeks in practice, and "7-day free trial" is copy, not
     * an expiry date the app enforces. The store enforces the real one.
     */
    private fun Period.inDays(): Int = when (unit) {
        PeriodUnit.DAY -> value
        PeriodUnit.WEEK -> value * DAYS_IN_WEEK
        PeriodUnit.MONTH -> value * DAYS_IN_MONTH
        PeriodUnit.YEAR -> value * DAYS_IN_YEAR
        PeriodUnit.UNKNOWN -> 0
    }

    private const val DAYS_IN_WEEK = 7
    private const val DAYS_IN_MONTH = 30
    private const val DAYS_IN_YEAR = 365
}
