package com.hopcape.odo

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * The four wired screens, photographed as they ship.
 *
 * The confirmation is pushed rather than walked to: a ticket number is the thing it exists to
 * show, and there is none until reports become tickets. Pushing it with the figures a real
 * submission would carry is the honest way to photograph it meanwhile.
 *
 * The two diagnostics screens are not here. They have no destination in this branch — the
 * upload is one indivisible bundle today, so a screen offering a switch per line cannot
 * honour any of them, and the answer to that is not to wire it up yet.
 */
@RunWith(AndroidJUnit4::class)
class SupportTicketsScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                resetProfile()
                seedOnboardedOwner()
                installNoStore()
            },
        )
        .around(rule)

    @Test
    fun capturesEveryWiredTicketScreen() {
        // The dashboard first: a push before the nav host is collecting lands nowhere, and
        // the screen it should have replaced is what gets photographed.
        rule.awaitText(HOME_TAB)

        rule.push(OdoDestination.Support.ReportProblem)
        rule.awaitText(REPORT_WHERE)
        // Typed rather than empty: the screenshot that matters is a filled-in form, since an
        // empty one says nothing about whether Send unlocks.
        rule.onNodeWithText(REPORT_HINT).performTextInput(REPORT_TEXT)
        rule.captureScreen("support-report-problem")

        rule.push(
            OdoDestination.Support.ReportSent(
                ticket = "ODO-4821",
                area = "BILL_SCAN",
                photos = 1,
                logsAttached = true,
                maskedReplyTo = "r•••@gmail.com",
            ),
        )
        rule.awaitText(REPORT_SENT)
        rule.captureScreen("support-report-sent")

        seedFeatureIdeas()
        rule.push(OdoDestination.Support.SuggestIdea)
        rule.awaitText(IDEA_LABEL)
        // The list is what this screen is for, and it is where the layout broke: the vote
        // pill filled the row and squeezed every title to one character per line. Waiting on
        // a real title is what makes the capture show the list rather than an empty section.
        rule.awaitTextContaining(IDEA_TITLE)
        rule.captureScreen("support-suggest-idea")

        rule.push(
            OdoDestination.Support.FlagPriceData(
                lineName = "AC service",
                lowPaise = 140_000L,
                highPaise = 180_000L,
                city = "Srinagar",
                workshop = "company centre",
                segment = "1.2L petrol hatchback",
            ),
        )
        rule.awaitText(FLAG_BAND_LABEL)
        rule.onNodeWithText(FLAG_TOO_LOW).performClick()
        rule.captureScreen("support-flag-wrong-price")
    }

    private companion object {
        const val REPORT_WHERE = "WHERE DID IT HAPPEN"
        const val REPORT_HINT = "What were you doing, and what happened instead?"
        const val REPORT_TEXT = "The labour charge came out as Rs. 450 but the bill says Rs. 4,500."
        const val REPORT_SENT = "Report sent"
        const val IDEA_LABEL = "YOUR IDEA"
        const val IDEA_TITLE = "Two cars"
        const val FLAG_BAND_LABEL = "THE BAND YOU’RE FLAGGING"
        const val FLAG_TOO_LOW = "Too low for my city"
        const val HOME_TAB = "Home"
    }
}

private typealias SupportTestRule =
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Pushes a destination straight onto the back stack — two of these have no flow to walk. */
private fun SupportTestRule.push(destination: OdoDestination) {
    runOnUiThread { GlobalContext.get().get<NavigationManager>().navigateTo(destination) }
}
