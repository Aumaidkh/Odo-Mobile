package com.hopcape.odo.infrastructure.billing.status

import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.core.domain.subscription.SubscriptionHealth
import com.hopcape.odo.core.domain.subscription.SubscriptionState
import com.hopcape.odo.core.domain.subscription.SubscriptionStatusSource
import com.hopcape.odo.infrastructure.billing.CustomerInfoStream
import com.hopcape.odo.infrastructure.billing.entitlement.RevenueCatEntitlementSource.Companion.PRO_ENTITLEMENT
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.EntitlementInfo
import com.revenuecat.purchases.kmp.models.PeriodType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * [SubscriptionStatusSource] over RevenueCat's `CustomerInfo`.
 *
 * Reads the same [CustomerInfoStream] the entitlement source does, because the SDK allows one
 * delegate and this is the second thing that needs customer info.
 *
 * Everything here is for one card. Nothing gates on it.
 */
@OptIn(ExperimentalTime::class)
internal class RevenueCatSubscriptionStatus(
    private val stream: CustomerInfoStream,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SubscriptionStatusSource {

    override fun observe(): Flow<SubscriptionState?> = stream.resolved.map { it?.toState() }

    private fun CustomerInfo.toState(): SubscriptionState? {
        val pro = entitlements[PRO_ENTITLEMENT]?.takeIf { it.isActive } ?: return null
        return SubscriptionState(
            period = pro.billingPeriod(),
            health = pro.health(),
            renewsOn = pro.expirationDate?.toLocalDateTime(timeZone)?.date,
            managementUrl = managementUrlString,
        )
    }

    /**
     * Which plan is being paid for.
     *
     * Read off the product's base-plan identifier rather than the period the store reports,
     * because RevenueCat does not carry a billing period on the entitlement. Anything that is
     * not recognisably the annual plan is treated as monthly: the card says "Monthly" and the
     * renewal date is still right, which is a smaller wrong than claiming a year.
     */
    private fun EntitlementInfo.billingPeriod(): BillingPeriod =
        if (productPlanIdentifier?.contains(ANNUAL, ignoreCase = true) == true ||
            productIdentifier.contains(ANNUAL, ignoreCase = true)
        ) {
            BillingPeriod.ANNUAL
        } else {
            BillingPeriod.MONTHLY
        }

    /**
     * The order matters.
     *
     * A failed payment is checked first, because it is the only one the owner has to act on
     * and it can be true during a trial or a cancelled-but-not-expired period alike. Then the
     * trial, then a cancellation, then the ordinary case.
     */
    private fun EntitlementInfo.health(): SubscriptionHealth = when {
        billingIssueDetectedAt != null -> SubscriptionHealth.BILLING_ISSUE
        periodType == PeriodType.TRIAL -> SubscriptionHealth.IN_TRIAL
        !willRenew -> SubscriptionHealth.CANCELLED
        else -> SubscriptionHealth.ACTIVE
    }

    private companion object {
        /** How the annual base plan is named in Play Console (`pro-annual`). */
        const val ANNUAL = "annual"
    }
}
