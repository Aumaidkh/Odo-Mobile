package com.hopcape.odo

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The way in.
 *
 * The check was unreachable until now — every screen and every rule was built and nothing in
 * the app opened it. This walks the route an owner actually takes: open a logged bill, tap the
 * action that used to run the older fairness report, and land on the check.
 */
@RunWith(AndroidJUnit4::class)
class BillCheckEntryPointTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            // Seeded after the launch, like the fairness suite: the bill goes in through
            // hand-written SQL and `notifyListeners` is what tells an already-collecting
            // screen about it.
            DeviceState {
                resetOwnerData()
                seedOnboardedOwner()
                installNoStore()
            },
        )
        .around(rule)

    @Test
    fun theEntryDetailOpensTheBillCheck() {
        seedBillToCheck()
        rule.openServiceLog()
        rule.openEntryDetail(BillCheckFixtures.BILL_ID, BillCheckFixtures.WORKSHOP)

        rule.onNodeWithText(CHECK_ACTION).performClick()

        rule.awaitTextContaining(RESULT_HEADLINE)
    }

    private companion object {
        /** The detail screen's own wording, unchanged — only where it leads has moved. */
        const val CHECK_ACTION = "Check fairness"
        const val RESULT_HEADLINE = "worth asking about"
    }
}
