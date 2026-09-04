package com.hopcape.odo.feature.paywall.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.feature.paywall.presentation.state.Loadable

/**
 * Why the paywall was shown — frames the same offer with a context-specific badge, headline
 * and subtitle. [SAVINGS] uses [PaywallUiState.amountPaise]; [SCANS_EXHAUSTED] uses
 * [PaywallUiState.freeScans].
 */
internal enum class PaywallTrigger { GENERIC, SCANS_EXHAUSTED, SAVINGS, SMART_REFUEL, RECORD_EXPORT }

/**
 * One plan as the paywall draws it.
 *
 * Every price is a string the store gave us, already localized and already carrying the right
 * symbol. Nothing here is formatted by Odo, and nothing is written down in a strings file: a
 * price change in Play Console reaches this screen with no release.
 */
@Immutable
internal data class PaywallPlanCard(
    /** The store's package id. What a purchase is started with, and never shown. */
    val id: String,
    val period: BillingPeriod,
    /** The store's price for one billing period, e.g. "₹1,490.00". */
    val price: String,
    /** The same price per month, also the store's own string. */
    val pricePerMonth: String,
    /** Days of free trial this plan opens with, or null when it has none. */
    val trialDays: Int?,
)

/**
 * The offer, once it has loaded.
 *
 * [selectedPlanId] rather than an index, so a plan the store stopped offering cannot be
 * selected by an index that still happens to be in range.
 */
@Immutable
internal data class PaywallOffer(
    val plans: List<PaywallPlanCard>,
    val selectedPlanId: String,
    /** What annual saves against twelve monthly charges, when there is an honest number. */
    val savingPercent: Int?,
) {
    /** The plan the CTA will buy. */
    val selected: PaywallPlanCard? get() = plans.firstOrNull { it.id == selectedPlanId }
}

/**
 * Display state for the Pro paywall.
 *
 * The offer is [Loadable] because it is read from the store every time this screen opens.
 * There is deliberately no cached price and no fallback copy: a figure the app cannot confirm
 * is worse than a retry button, because it risks charging someone something they were not
 * shown. [Loadable.Failed] is what the owner sees instead, and the CTA is not drawn at all.
 */
@Immutable
internal data class PaywallUiState(
    val trigger: PaywallTrigger = PaywallTrigger.GENERIC,
    val offer: Loadable<PaywallOffer> = Loadable.Loading,
    /** The savings figure that framed this paywall, in paise. Only used by [PaywallTrigger.SAVINGS]. */
    val amountPaise: Long = 0L,
    /** The free-scan cap that framed this paywall. Only used by [PaywallTrigger.SCANS_EXHAUSTED]. */
    val freeScans: Int = 0,
    /** The store's purchase sheet is open, or the purchase is completing. */
    val purchasing: Boolean = false,
    /** A restore is in flight. */
    val restoring: Boolean = false,
    /**
     * A single line under the CTA — what a restore found, or why a payment did not go
     * through. Cleared the moment the owner acts again, so it never lingers past the thing it
     * was about.
     */
    val notice: UiText? = null,
) {
    /** Whether anything is in flight, which is what stops a second tap. */
    val busy: Boolean get() = purchasing || restoring
}
