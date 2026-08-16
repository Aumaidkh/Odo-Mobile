package com.hopcape.odo.core.domain.subscription

/** How often a plan bills. */
enum class BillingPeriod {

    /** Charged every month. */
    MONTHLY,

    /** Charged once a year. */
    ANNUAL,
}

/**
 * One plan the owner can buy, as the store describes it right now.
 *
 * **Every price here is the store's own string**, already localized and already carrying the
 * right currency symbol. Nothing in Odo formats money for the paywall, and nothing hardcodes
 * a figure: change a price in Play Console and the app shows the new one with no release.
 * That is why [formattedPrice] is a `String` and not an amount — it is not the app's number
 * to render.
 *
 * [amountMicros] is here anyway, for the one thing a formatted string cannot do: comparing
 * the two plans to work out what the annual one saves. It is never shown.
 */
data class PlanOption(

    /**
     * The package identifier the store knows this plan by, e.g. `$rc_monthly`.
     *
     * Opaque on purpose. It is what a purchase is started with, and nothing else should read
     * meaning into it — the plan's shape is [period].
     */
    val id: String,

    /** How often it bills. */
    val period: BillingPeriod,

    /** The store's price for one billing period, e.g. "₹1,490". */
    val formattedPrice: String,

    /**
     * The same price expressed per month, e.g. "₹124" for an annual plan.
     *
     * Also the store's own string — RevenueCat derives and formats it, so the division and
     * the rounding are not the app's to get wrong. Equal to [formattedPrice] on a monthly
     * plan.
     */
    val formattedPricePerMonth: String,

    /** What one billing period costs, for comparing plans. Never shown. */
    val amountMicros: Long,

    /** The currency [amountMicros] is in, e.g. "INR". Two plans only compare within one. */
    val currencyCode: String,

    /**
     * How many days of free trial this plan opens with, or `null` when it has none.
     *
     * Null also when the store says this owner is not eligible — someone who has already
     * used a trial is offered the plain price rather than one they cannot have. The store
     * decides that, not the app.
     */
    val freeTrialDays: Int?,
)

/**
 * What is for sale, as one offer.
 *
 * Holds at least one plan: an offer with nothing in it is `DomainError.NothingForSale`
 * instead, because a paywall with no plans is a bug to fix in the dashboard rather than an
 * empty state to design.
 */
data class Offer(

    /** The offering identifier in RevenueCat, e.g. `default`. Carried for telemetry. */
    val id: String,

    /** The plans, in the order they should be shown. */
    val plans: List<PlanOption>,
) {

    /** The monthly plan, when the offering has one. */
    val monthly: PlanOption? get() = plans.firstOrNull { it.period == BillingPeriod.MONTHLY }

    /** The annual plan, when the offering has one. */
    val annual: PlanOption? get() = plans.firstOrNull { it.period == BillingPeriod.ANNUAL }

    /** Whether any plan opens with a free trial, for the paywall's headline. */
    val hasFreeTrial: Boolean get() = plans.any { it.freeTrialDays != null }

    /**
     * What the annual plan saves against paying monthly for a year, as a whole percent, or
     * `null` when there is nothing honest to claim.
     *
     * Null when either plan is missing, when the two are priced in different currencies —
     * comparing across currencies would produce a number that means nothing — or when annual
     * is not actually cheaper. The badge is computed rather than written down for the same
     * reason the prices are: a price change in Play Console must not leave the app claiming a
     * discount that stopped being true.
     */
    val annualSavingPercent: Int?
        get() {
            val monthly = monthly ?: return null
            val annual = annual ?: return null
            if (monthly.currencyCode != annual.currencyCode) return null
            val yearOfMonthly = monthly.amountMicros * MONTHS_IN_YEAR
            if (yearOfMonthly <= 0) return null
            val saved = yearOfMonthly - annual.amountMicros
            if (saved <= 0) return null
            return ((saved * PERCENT) / yearOfMonthly).toInt()
        }

    private companion object {
        const val MONTHS_IN_YEAR = 12
        const val PERCENT = 100
    }
}
