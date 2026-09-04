package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Takes the screenshots the paywall's PR carries, rather than describing the change in words.
 *
 * Not an assertion — nothing here can fail on the product. It exists because a hand-driven
 * screenshot of this screen is useless: a local debug build has no RevenueCat key, so the
 * paywall photographs "Odo Pro isn't available to buy right now" and the sheet photographs
 * its empty state. The E2E harness injects the store's answers, so these are what an owner
 * with a real store sees.
 *
 * Run it and collect the files with the two commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class PaywallScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                resetProfile()
                seedOnboardedOwner()
                setProEntitlement(isPro = false)
                installNoStore()
                installNoOneTimeProducts()
            },
        )
        .around(rule)

    @Test
    fun capturesThePaywallAndItsOneTimeSheet() {
        installOffer()
        installOneTimePrices()

        rule.openProfile()
        rule.goPro()
        rule.awaitText(PaywallCopy.HEADLINE)
        Screenshots.capture("paywall")

        rule.openOneTimeOffers()
        rule.awaitText(PaywallCopy.ONE_TIME_TITLE)
        Screenshots.capture("one-time-offers-sheet")
    }

    /**
     * What the sheet looks like until the products exist in Play Console — which is what it
     * looks like today, and the state a reviewer should see rather than be told about.
     */
    @Test
    fun capturesTheSheetWithNothingOnSale() {
        installOffer()
        installNoOneTimeProducts()

        rule.openProfile()
        rule.goPro()
        rule.awaitText(PaywallCopy.HEADLINE)

        rule.openOneTimeOffers()
        rule.awaitText(PaywallCopy.ONE_TIME_EMPTY)
        Screenshots.capture("one-time-offers-empty")
    }
}
