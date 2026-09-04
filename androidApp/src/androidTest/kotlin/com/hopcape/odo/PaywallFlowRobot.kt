package com.hopcape.odo

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.core.domain.subscription.Offer
import com.hopcape.odo.core.domain.subscription.CompletedPurchase
import com.hopcape.odo.core.domain.subscription.OneTimeProducts
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
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

    /** The card's button — the only thing on it that actually opens the paywall. */
    const val SEE_PLANS = "See plans"
    const val RESTORE = "Restore"
    const val MONTHLY = "Monthly"
    const val ANNUAL = "Annual"
    const val RETRY = "Try again"
    const val NOTHING_FOR_SALE = "Odo Pro isn’t available to buy right now. Please try again later."
    const val UNAVAILABLE = "Couldn’t reach the store. Check your connection and try again."

    /* One-time offers sheet. */
    const val ONE_TIME_OPEN = "Just want one thing?"
    const val ONE_TIME_TITLE = "Buy it once"
    const val BILL_CHECK_SINGLE = "Bill Check × 1"
    const val BILL_CHECK_PACK = "Bill Check × 3"
    const val EXPORT_RECORDS = "Export Records"
    const val ONE_TIME_EMPTY = "Nothing is on sale one at a time just yet."
    const val ONE_TIME_BILL_TITLE = "See the full answer"
    const val ONE_TIME_BILL_FOOTER =
        "If a check fails the credit comes back — that’s our bug, not your rupee."

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
    installCatalog { offer.right() }
}

/** A store that cannot be reached — the state that must show a retry and no price. */
internal fun installUnreachableStore() {
    installCatalog { DomainError.StoreUnavailable.left() }
}

/** Restore the shipped behaviour for a build with no store key. */
internal fun installNoStore() {
    installCatalog { DomainError.NothingForSale.left() }
}

/**
 * One catalog for the whole process, whose answer the tests move.
 *
 * The obvious implementation — rebind `SubscriptionCatalog` in Koin on every install — works
 * right up to the retry test and cannot work there at all. [PaywallViewModel] takes its
 * catalog as a constructor dependency, so the instance it holds is the one that existed when
 * the screen opened; a later rebinding changes what the *next* ViewModel would get and
 * nothing about the live one. Tapping "Try again" therefore re-asked the broken store and
 * the offer never arrived.
 *
 * Holding a mutable answer behind a single binding means retry re-enters the same object and
 * sees whatever the test last installed, which is what "the store came back" actually looks
 * like.
 */
private object SwitchableCatalog : SubscriptionCatalog {
    @Volatile
    var answer: () -> Either<DomainError, Offer> = { DomainError.NothingForSale.left() }

    override suspend fun current(): Either<DomainError, Offer> = answer()
}

/**
 * What the store charges for each consumable, for the one-time offers sheet.
 *
 * Held behind one binding for the same reason [SwitchableCatalog] is: the sheet's ViewModel
 * takes its purchaser as a constructor dependency, so rebinding later would change what the
 * *next* one gets and nothing about the live one.
 */
private object SwitchablePurchaser : OneTimePurchaser {
    @Volatile
    var prices: Map<String, String> = emptyMap()

    /**
     * Refused, not thrown. `ShareRecordViewModel.buyExport` does not catch, so a throw here
     * would crash whichever later suite reaches the paid-export path rather than failing an
     * assertion — and this binding outlives the class that installed it.
     */
    override suspend fun purchase(productId: String): Either<DomainError, Unit> =
        DomainError.NothingForSale.left()

    override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
        if (unreachable) DomainError.StoreUnavailable.left() else prices.filterKeys { it in productIds }.right()

    /** Nothing outstanding: no purchase in a test ever completes at the store. */
    override suspend fun completedPurchases(): Either<DomainError, List<CompletedPurchase>> =
        emptyList<CompletedPurchase>().right()

    @Volatile
    var unreachable: Boolean = false
}

private var purchaserBound = false

/**
 * Price the one-time products the store would return.
 *
 * The shipped build has no RevenueCat key and prices nothing, which is also what production
 * looks like today — the products have not been created. Overriding is the only way to walk
 * the half of the sheet that has rows on it.
 */
internal fun installOneTimePrices(
    billCheckSingle: String? = "₹49",
    billCheckPack: String? = "₹99",
    recordExport: String? = "₹99",
) {
    SwitchablePurchaser.unreachable = false
    SwitchablePurchaser.prices = buildMap {
        billCheckSingle?.let { put(OneTimeProducts.BILL_CHECK_SINGLE, it) }
        billCheckPack?.let { put(OneTimeProducts.BILL_CHECK_PACK, it) }
        recordExport?.let { put(OneTimeProducts.RECORD_EXPORT, it) }
    }
    if (purchaserBound) return
    GlobalContext.get().loadModules(
        listOf(module { single<OneTimePurchaser> { SwitchablePurchaser } }),
        allowOverride = true,
    )
    purchaserBound = true
}

/** Nothing on sale one at a time — today's real state, and the sheet's empty case. */
internal fun installNoOneTimeProducts() {
    SwitchablePurchaser.unreachable = false
    installOneTimePrices(null, null, null)
}

/** A store that cannot be asked at all — which must not read as an empty catalogue. */
internal fun installUnreachableOneTimeStore() {
    installOneTimePrices(null, null, null)
    SwitchablePurchaser.unreachable = true
}

private var catalogBound = false

private fun installCatalog(answer: () -> Either<DomainError, Offer>) {
    SwitchableCatalog.answer = answer
    if (catalogBound) return
    GlobalContext.get().loadModules(
        listOf(module { single<SubscriptionCatalog> { SwitchableCatalog } }),
        allowOverride = true,
    )
    catalogBound = true
}

/* ------------------------------ Getting there ------------------------------ */

/**
 * Open the paywall from the profile's upsell card, which is where a free owner sees it.
 *
 * The card's button, not its "Go Pro" heading. The heading is a plain label with no click
 * action of its own and the card is not clickable as a whole, so a tap on the heading lands
 * on nothing and the test then fails on the paywall being absent rather than on never having
 * asked for it. The button is also below the four feature rows, so it needs scrolling to.
 */
internal fun PaywallTestRule.goPro() {
    // Waited for, not assumed. The upsell card is drawn from the entitlement stream, so on a
    // cold start it can arrive after the profile screen itself — and `performScrollTo` on a
    // node that is not there yet fails on the profile rather than on the paywall.
    awaitText(PaywallCopy.SEE_PLANS)
    onNodeWithText(PaywallCopy.SEE_PLANS).performScrollTo().performClick()
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

/** Open the "buy it once" sheet from the link under the plans. */
internal fun PaywallTestRule.openOneTimeOffers() {
    onNodeWithText(PaywallCopy.ONE_TIME_OPEN).performScrollTo().performClick()
    waitForIdle()
}

/** Tap the retry under a failed offer. */
internal fun PaywallTestRule.retryOffer() {
    onNodeWithText(PaywallCopy.RETRY).performClick()
    waitForIdle()
}
