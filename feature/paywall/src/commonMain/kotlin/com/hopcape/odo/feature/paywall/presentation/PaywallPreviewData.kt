package com.hopcape.odo.feature.paywall.presentation

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.feature.paywall.presentation.state.Loadable
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_error_store_unavailable

/**
 * Preview states for the paywall.
 *
 * The prices here are literals, and they are the only place in this feature where that is
 * true. A preview has no store to ask, and the alternative — a preview with no prices — would
 * not show the thing the screen is for. They are examples, not the app's idea of a price.
 */

private const val MONTHLY_ID = "\$rc_monthly"
private const val ANNUAL_ID = "\$rc_annual"
private const val LIFETIME_ID = "\$rc_lifetime"

private fun offer(selected: String = ANNUAL_ID, trialDays: Int? = 7) = PaywallOffer(
    plans = listOf(
        PaywallPlanCard(
            id = MONTHLY_ID,
            period = BillingPeriod.MONTHLY,
            price = "₹149.00",
            pricePerMonth = "₹149.00",
            trialDays = trialDays,
        ),
        PaywallPlanCard(
            id = ANNUAL_ID,
            period = BillingPeriod.ANNUAL,
            price = "₹499.00",
            pricePerMonth = "₹41.58",
            trialDays = trialDays,
        ),
        // Growth Plan v3's anchor (#245). Here so the previews show what three stacked
        // rows actually look like — the case the side-by-side layout could not hold.
        PaywallPlanCard(
            id = LIFETIME_ID,
            period = BillingPeriod.LIFETIME,
            price = "₹1,299.00",
            pricePerMonth = "₹1,299.00",
            trialDays = null,
        ),
    ),
    selectedPlanId = selected,
    savingPercent = 16,
)

/** Generic "everything unlocked" framing, offer loaded. */
internal fun samplePaywallGeneric() = PaywallUiState(
    trigger = PaywallTrigger.GENERIC,
    offer = Loadable.Ready(offer()),
)

/** "0 free scans left" framing after the monthly free quota is used up. */
internal fun samplePaywallScans() = PaywallUiState(
    trigger = PaywallTrigger.SCANS_EXHAUSTED,
    offer = Loadable.Ready(offer(selected = MONTHLY_ID)),
    freeScans = 3,
)

/** "You just saved Rs. 700" framing right after a fairness win. */
internal fun samplePaywallSavings() = PaywallUiState(
    trigger = PaywallTrigger.SAVINGS,
    offer = Loadable.Ready(offer()),
    amountPaise = 70_000L,
)

/** Waiting on the store — the state every open passes through. */
internal fun samplePaywallLoading() = PaywallUiState(offer = Loadable.Loading)

/** The store could not be reached, so there is a retry and no price at all. */
internal fun samplePaywallUnavailable() = PaywallUiState(
    offer = Loadable.Failed(UiText(Res.string.pw_error_store_unavailable)),
)
