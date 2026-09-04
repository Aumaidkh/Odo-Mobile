package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

/**
 * The paywall, reached the way an owner reaches it: from the profile's "Go Pro" card.
 *
 * **What is proved here and nowhere else** is that every figure on the screen is the store's.
 * Each test installs an offer with prices of its own choosing and then looks for exactly those
 * strings, so a paywall that fell back to a hardcoded figure — the one failure that could
 * charge somebody something they were not shown — fails here.
 *
 * **What is deliberately not covered:** buying. A purchase needs Play Billing, a signed build
 * on an internal track and a licence tester, none of which exist in an instrumented test. The
 * ViewModel's purchase, cancel and restore branches are unit-tested against a fake purchaser;
 * what this suite covers is everything up to the tap.
 */
@RunWith(AndroidJUnit4::class)
class PaywallEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start from a free owner on a set-up device, before the activity launches.
     *
     * The car matters even though no test looks at one. The paywall is reached from the
     * profile, the profile is reached from Home, and Home is only where the app opens for an
     * owner who has finished setup — so without a seeded profile every test here waits out
     * the full start-up timeout on the welcome carousel. The seed has to run before the
     * launch, because where the app opens is decided once per launch and then held.
     *
     * The plan is set explicitly rather than left to whatever the build's store answers,
     * because a test that overrides it changes a definition that outlives it.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState { startFromAFreeOwner() })
        .around(rule)

    private fun startFromAFreeOwner() {
        resetProfile()
        seedOnboardedOwner()
        setProEntitlement(isPro = false)
        installNoStore()
        installNoOneTimeProducts()
    }

    /**
     * Reached from the profile's "Go Pro" card rather than the health-score lock.
     *
     * Both open the same screen; this one needs no score history, so what a test fails on is
     * the paywall rather than a fixture.
     */
    private fun openPaywall() {
        rule.openProfile()
        rule.goPro()
        rule.awaitText(PaywallCopy.HEADLINE)
    }

    /* ------------------------------ what the store said ------------------------------ */

    @Test
    fun everyPriceOnScreenIsTheOneTheStoreGave() {
        // Deliberately not the real prices. If the screen ever fell back to a figure of its
        // own, these assertions are what would catch it.
        installOffer(monthlyPrice = "₹99.00", annualPrice = "₹990.00", annualPerMonth = "₹82.50")

        openPaywall()

        rule.onNodeWithText("₹99.00").assertIsDisplayed()
        rule.onNodeWithText("₹990.00").assertIsDisplayed()
        rule.awaitText("≈ ₹82.50 / mo")
    }

    @Test
    fun theTrialIsWhatTheCtaOffersWhenThereIsOne() {
        installOffer(trialDays = 14)

        openPaywall()

        rule.awaitText(PaywallCopy.trialCta(14))
    }

    @Test
    fun aPlanWithNoTrialShowsItsPriceInstead() {
        installOffer(monthlyPrice = "₹99.00", annualPrice = "₹990.00", trialDays = null)

        openPaywall()

        // Annual opens selected, so the CTA is the annual price.
        rule.awaitText(PaywallCopy.cta("₹990.00"))
    }

    @Test
    fun theTermsSayWhatWillBeChargedBeforeTheButton() {
        // Play wants price, renewal and trial length before the tap, not after it.
        installOffer(annualPrice = "₹990.00", trialDays = 7)

        openPaywall()

        rule.awaitText(PaywallCopy.trialTerms(days = 7, price = "₹990.00"))
    }

    @Test
    fun theSavingBadgeIsWorkedOutFromTheTwoPrices() {
        // 149 x 12 = 1,788 against 1,490 — 16%. Nothing writes that number down.
        installOffer()

        openPaywall()

        rule.awaitText(PaywallCopy.saving(16))
    }

    /* ------------------------------ switching plans ------------------------------ */

    @Test
    fun choosingMonthlyChangesWhatTheCtaWouldCharge() {
        installOffer(monthlyPrice = "₹99.00", annualPrice = "₹990.00", trialDays = null)
        openPaywall()

        rule.selectMonthly()

        rule.awaitText(PaywallCopy.cta("₹99.00"))
    }

    /* ------------------------------ nothing to show ------------------------------ */

    @Test
    fun aStoreThatCannotBeReachedOffersARetryAndNoPrice() {
        installUnreachableStore()

        openPaywall()

        rule.awaitText(PaywallCopy.UNAVAILABLE)
        rule.onNodeWithText(PaywallCopy.RETRY).assertIsDisplayed()
        assertEquals("no plan card is drawn without an offer", 0, rule.textCount(PaywallCopy.MONTHLY))
    }

    @Test
    fun retryingAfterAnOutageShowsTheOffer() {
        installUnreachableStore()
        openPaywall()
        rule.awaitText(PaywallCopy.UNAVAILABLE)

        installOffer()
        rule.retryOffer()

        rule.awaitText(PaywallCopy.MONTHLY)
    }

    @Test
    fun aBuildWithNoStoreSaysSoRatherThanShowingAnEmptyPaywall() {
        // The state of every checkout with no RevenueCat key, and of CI.
        openPaywall()

        rule.awaitText(PaywallCopy.NOTHING_FOR_SALE)
        assertEquals("retrying cannot fix a missing key", 0, rule.textCount(PaywallCopy.RETRY))
    }

    /* ------------------------------ restore ------------------------------ */

    @Test
    fun restoreIsAlwaysOfferedBecauseEveryStoreRequiresIt() {
        // The same account on a new phone has already paid and must be able to say so.
        installOffer()

        openPaywall()

        rule.onNodeWithText(PaywallCopy.RESTORE).assertIsDisplayed()
    }

    /* ------------------------------ Buy it once ------------------------------ */

    /**
     * The sheet is a real destination, not a dialog the screen draws itself: opening it
     * leaves the paywall underneath, and closing it puts the owner back on the plans rather
     * than out of the paywall entirely.
     */
    @Test
    fun theOneTimeSheetOpensOverThePlansAndComesBackToThem() {
        installOffer()
        installOneTimePrices()
        openPaywall()

        rule.openOneTimeOffers()
        rule.awaitText(PaywallCopy.ONE_TIME_TITLE)

        Espresso.pressBack()
        rule.awaitText(PaywallCopy.HEADLINE)
        // The plans stay composed *underneath* a sheet, so the headline alone would pass
        // whether or not the back press closed anything. This is the half the name claims.
        rule.onNodeWithText(PaywallCopy.ONE_TIME_TITLE).assertDoesNotExist()
    }

    /**
     * A store that cannot be asked is not an empty catalogue.
     *
     * The distinction is invisible from the app otherwise: the shipped adapter used to
     * swallow every store error into a null price, so an offline owner was told nothing was
     * for sale and given no retry.
     */
    @Test
    fun aStoreThatCannotBeReachedOffersARetryRatherThanSayingNothingIsOnSale() {
        installOffer()
        installUnreachableOneTimeStore()
        openPaywall()

        rule.openOneTimeOffers()

        rule.awaitText(PaywallCopy.UNAVAILABLE)
        rule.onNodeWithText(PaywallCopy.ONE_TIME_EMPTY).assertDoesNotExist()
    }

    @Test
    fun everyOneTimePriceOnScreenIsTheOneTheStoreGave() {
        installOffer()
        installOneTimePrices(billCheckSingle = "₹39", billCheckPack = "₹89", recordExport = "₹129")
        openPaywall()

        rule.openOneTimeOffers()

        rule.awaitText(PaywallCopy.BILL_CHECK_SINGLE)
        rule.onNodeWithText("₹39").assertIsDisplayed()
        rule.onNodeWithText("₹89").assertIsDisplayed()
        rule.onNodeWithText("₹129").assertIsDisplayed()
    }

    /**
     * A product nobody has created in the store has no price, and a row with no price is
     * either a lie or a dead end.
     */
    @Test
    fun aProductTheStoreDoesNotKnowIsNotOnTheSheet() {
        installOffer()
        installOneTimePrices(billCheckSingle = null, billCheckPack = null)
        openPaywall()

        rule.openOneTimeOffers()

        rule.awaitText(PaywallCopy.EXPORT_RECORDS)
        rule.onNodeWithText(PaywallCopy.BILL_CHECK_SINGLE).assertDoesNotExist()
        rule.onNodeWithText(PaywallCopy.BILL_CHECK_PACK).assertDoesNotExist()
    }

    /** Today's real state: none of the three exist yet, so the sheet says so. */
    @Test
    fun withNothingOnSaleTheSheetSaysSoRatherThanShowingAnEmptyList() {
        installOffer()
        installNoOneTimeProducts()
        openPaywall()

        rule.openOneTimeOffers()

        rule.awaitText(PaywallCopy.ONE_TIME_EMPTY)
    }
}
