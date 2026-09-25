package com.hopcape.odo

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The Before/After picture for where the app opens on the launch after setup.
 *
 * It waits for whichever start screen appears rather than asserting one, so the same test
 * photographs the bug on the base branch and the fix on this one.
 */
@RunWith(AndroidJUnit4::class)
class RelaunchAfterSetupScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                clearTheOwnersRows()
                silenceTheCoachMarks()
            },
        )
        .around(rule)

    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
    }

    @Test
    fun capturesTheLaunchAfterSetup() {
        rule.startFromWelcome()
        rule.answerTheCarStep()
        rule.onNodeWithText(Copy.CONTINUE).performClick()
        rule.waitUntilPresent(Copy.VALUE_SKIP)
        rule.onNodeWithText(Copy.VALUE_SKIP).performClick()
        rule.waitUntilPresent(Copy.HOME_RESALE)

        rule.relaunchTheApp().use {
            rule.waitUntil(START_DESTINATION_TIMEOUT_MILLIS) {
                rule.isShowing(Copy.HOME_RESALE) || rule.isShowing(Copy.WELCOME_HEADLINE)
            }
            rule.captureScreen("relaunch-after-setup")
        }
    }
}

private fun ComposeTestRule.isShowing(text: String): Boolean =
    onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
