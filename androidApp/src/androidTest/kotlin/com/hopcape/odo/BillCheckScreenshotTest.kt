package com.hopcape.odo

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Takes the screenshots the bill check's PR carries, rather than describing the change.
 *
 * The destinations are pushed rather than walked to: the check is reached by scanning a bill,
 * and the reader behind it is a stub until the reference tables exist — so there is no flow
 * to walk yet, only screens to photograph.
 *
 * Run it and collect the files with the two commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class BillCheckScreenshotTest {

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
                // The reader takes a real service-log entry now, so the screen needs one.
                seedBillToCheck()
            },
        )
        .around(rule)

    @Test
    fun capturesTheResultAndItsBasis() {
        // The dashboard first. A push before the nav host is collecting is a push that
        // lands nowhere, and the screen it should have replaced is still what gets
        // photographed.
        rule.awaitText(HOME_TAB)

        rule.push(OdoDestination.BillCheck.Result(billId = BILL))
        rule.awaitTextContaining(RESULT_HEADLINE)
        rule.captureScreen("billcheck-result")

        rule.push(OdoDestination.BillCheck.Basis(billId = BILL, lineName = "AC service"))
        rule.awaitText(BASIS_RUNGS)
        rule.captureScreen("billcheck-how-we-know")

        // The figures a real result would carry through, so the card is photographed on the
        // same numbers the result screen above it shows.
        rule.push(
            OdoDestination.BillCheck.Share(amountPaise = 240_000L, flagged = 1, lines = 1),
        )
        rule.awaitText(CARD_LABEL)
        rule.captureScreen("billcheck-share-card")
    }

    private companion object {
        const val BILL = BillCheckFixtures.BILL_ID

        /** The bottom bar, so the wait does not depend on seeded content. */
        const val HOME_TAB = "Home"

        /** The month-6 stub: three flagged lines out of a Rs. 18,400 bill. */
        /** Whatever the check finds — the point is that it read a real bill. */
        const val RESULT_HEADLINE = "worth asking about"
        const val CARD_LABEL = "SAVED ON TODAY’S SERVICE"
        const val BASIS_RUNGS = "WHICH RUNG ANSWERED"
    }
}

private typealias BillCheckTestRule =
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Pushes a destination straight onto the back stack — there is no flow to walk yet. */
private fun BillCheckTestRule.push(destination: OdoDestination) {
    runOnUiThread { GlobalContext.get().get<NavigationManager>().navigateTo(destination) }
}
