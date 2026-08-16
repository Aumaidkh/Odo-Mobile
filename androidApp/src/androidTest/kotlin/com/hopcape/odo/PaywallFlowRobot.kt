package com.hopcape.odo

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.core.domain.subscription.Offer
import com.hopcape.odo.core.domain.subscription.PlanOption
import com.hopcape.odo.core.domain.subscription.SubscriptionCatalog
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/**
 * The words the paywall puts on screen, mirrored from its `strings.xml`.
 *
 * Prices are **not** here as constants for the app to match. They come from the store, so a
 * test asserts the strings it installed itself through [installOffer] — which is the whole
 * point of the screen and the one thing worth proving end to end.
 */
internal object PaywallCopy {
    const val HEADLINE = "Everything Odo can do, unlocked."

    /** On the profile, not the paywall — the card that opens it. */
    const val GO_PRO = "Go Pro"
    const val RESTORE = "Restore"
    const val MONTHLY = "Monthly"
    const val ANNUAL = "Annual"
    const val RETRY = "Try again"
    const val NOTHING_FOR_SALE = "Odo Pro isn’t available to buy right now. Please try again later."
    const val UNAVAILABLE = "Couldn’t reach the store. Check your connection and try again."

    /** `"Start %1$d-day free trial"`. */
    fun trialCta(days: Int) = "Start $days-day free trial"

    /** `"Start Pro · %1$s"`. */
    fun cta(price: String) = "Start Pro · $price"

    /** `"SAVE %1$d%%"`. */
    fun saving(percent: Int) = "SAVE $percent%"

    /** `"%1$d days free, then %2$s. Renews automatically until you cancel."` */
    fun trialTerms(days: Int, price: String) =
        "$days days free, then $price. Renews automatically until you cancel."
}

private typealias PaywallTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ The store ------------------------------ */

/**
 * Put an offer in front of the paywall.
 *
 * The shipped catalog on a test build has no RevenueCat key and answers "nothing for sale",
 * so overriding it is the only way to walk the half of this screen that has prices on it.
 * The strings passed here are what the assertions look for: the screen must render what the
 * store said, not something it composed itself.
 */
internal fun installOffer(
    monthlyPrice: String = "₹149.00",
    annualPrice: String = "₹1,490.00",
    annualPerMonth: String = "₹124.17",
    trialDays: Int? = 7,
) {
    val offer = Offer(
        id = "default",
        plans = listOf(
            PlanOption(
                id = "\$rc_monthly",
                period = BillingPeriod.MONTHLY,
                formattedPrice = monthlyPrice,
                formattedPricePerMonth = monthlyPrice,
                amountMicros = 149_000_000,
                currencyCode = "INR",
                freeTrialDays = trialDays,
            ),
            PlanOption(
                id = "\$rc_annual",
                period = BillingPeriod.ANNUAL,
                formattedPrice = annualPrice,
                formattedPricePerMonth = annualPerMonth,
                amountMicros = 1_490_000_000,
                currencyCode = "INR",
                freeTrialDays = trialDays,
            ),
        ),
    )
    installCatalog(SubscriptionCatalog { offer.right() })
}

/** A store that cannot be reached — the state that must show a retry and no price. */
internal fun installUnreachableStore() {
    installCatalog(SubscriptionCatalog { DomainError.StoreUnavailable.left() })
}

/** Restore the shipped behaviour for a build with no store key. */
internal fun installNoStore() {
    installCatalog(SubscriptionCatalog { DomainError.NothingForSale.left() })
}

private fun installCatalog(catalog: SubscriptionCatalog) {
    GlobalContext.get().loadModules(
        listOf(module { single<SubscriptionCatalog> { catalog } }),
        allowOverride = true,
    )
}

/* ------------------------------ Getting there ------------------------------ */

/** Open the paywall from the profile's upsell card, which is where a free owner sees it. */
internal fun PaywallTestRule.goPro() {
    onNodeWithText(PaywallCopy.GO_PRO).performClick()
    waitForIdle()
}

/* ------------------------------ Acting ------------------------------ */

/** Choose the monthly plan. */
internal fun PaywallTestRule.selectMonthly() {
    onNodeWithText(PaywallCopy.MONTHLY).performClick()
    waitForIdle()
}

/** Choose the annual plan. */
internal fun PaywallTestRule.selectAnnual() {
    onNodeWithText(PaywallCopy.ANNUAL).performClick()
    waitForIdle()
}

/** Tap the retry under a failed offer. */
internal fun PaywallTestRule.retryOffer() {
    onNodeWithText(PaywallCopy.RETRY).performClick()
    waitForIdle()
}
