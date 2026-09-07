package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The app-status gate (`docs/APP_STATUS_PLAN.md`) as an owner meets it: a sheet that cannot be
 * got rid of, over the app it is holding back.
 *
 * The verdict is injected rather than fetched — a debug build reads `maintenance_mode` from
 * Remote Config, so a real block would need the console changed to photograph one screen.
 */
@RunWith(AndroidJUnit4::class)
class AppStatusGateEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                resetProfile()
                seedOnboardedOwner()
                installMaintenanceBlock()
            },
        )
        .around(rule)

    /** The verdict outlives the activity, so a later test would open into a blocked app. */
    @After
    fun releaseTheBlock() {
        installAppAllowed()
    }

    /**
     * The point of the sheet: the owner still sees the app they are locked out of, which a
     * full-screen stop cannot show them.
     */
    @Test
    fun theDashboardStaysOnScreenBehindAMaintenanceBlock() {
        rule.awaitText(AppStatusCopy.MAINTENANCE_TITLE)

        rule.awaitHomeLoaded()
    }

    /** A sheet that swipes or backs away is a suggestion; this one has to be a wall. */
    @Test
    fun aMaintenanceBlockSurvivesTheBackButton() {
        rule.awaitText(AppStatusCopy.MAINTENANCE_TITLE)

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithText(AppStatusCopy.MAINTENANCE_TITLE).assertIsDisplayed()
    }

    /** The update block is the same wall with a Play Store button on it. */
    @Test
    fun anUpdateBlockSurvivesTheBackButton() {
        installUpdateRequired()
        rule.awaitText(AppStatusCopy.UPDATE_TITLE)

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithText(AppStatusCopy.UPDATE_NOW).assertIsDisplayed()
    }
}
