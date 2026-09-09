package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Takes the screenshots the app-status PR carries, rather than describing the change in words.
 *
 * Not an assertion — nothing here can fail on the product. It exists because the block is
 * driven by Remote Config: photographing one by hand means putting the live project into
 * maintenance, which every installed phone would also see.
 *
 * Run it and collect the files with the two commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class AppStatusScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                resetProfile()
                seedOnboardedOwner()
                installAppAllowed()
            },
        )
        .around(rule)

    /** The verdict outlives the activity, so a later test would open into a blocked app. */
    @After
    fun releaseTheBlock() {
        installAppAllowed()
    }

    @Test
    fun capturesBothBlockedStates() {
        // Let the dashboard finish first: it is what the sheet is held over, and a block
        // applied mid-load photographs the skeleton behind the scrim.
        rule.awaitHomeLoaded()

        installMaintenanceBlock()
        rule.awaitText(AppStatusCopy.MAINTENANCE_TITLE)
        rule.captureScreen("maintenance-block")

        installUpdateRequired()
        rule.awaitText(AppStatusCopy.UPDATE_TITLE)
        rule.captureScreen("update-required")
    }
}
