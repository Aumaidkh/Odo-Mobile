package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The Before/After pictures for issue #440 — a refused Done on the last-service step now
 * says which half is missing.
 *
 * Two shots, one per field, both taken after the tap that used to do nothing visible. Driven
 * through the real flow rather than posed, because the state being photographed only exists
 * after a save is actually refused.
 *
 * Run it and collect the files with the commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class LastServiceRefusalScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Empty the owner's rows before the launch, not after: where the app opens is decided once
     * per launch, so a reset inside the test would come too late to send it to Welcome.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                clearTheOwnersRows()
                installStubVehicleRegistry()
            },
        )
        .around(rule)

    @Test
    fun capturesBothHalvesOfARefusedDone() {
        rule.reachTheLastServiceStep()

        // A month with no reading. Done refuses this, correctly — the picture is of whether
        // it says so.
        rule.pickFirstOfTheMonth()
        rule.onNodeWithText(Copy.DONE).performClick()
        rule.captureScreen("last-service-missing-odometer")

        // The other half, on the other component: the date field rather than the drum.
        rule.onNodeWithText(Copy.LAST_SERVICE_FORGOT).performClick()
        rule.onNodeWithText(Copy.LAST_SERVICE_FORGOT).performClick()
        rule.setOdometer(thousands = 3, fieldTag = OnboardingTestTags.LAST_SERVICE_ODOMETER_FIELD)
        rule.onNodeWithText(Copy.DONE).performClick()
        rule.captureScreen("last-service-missing-date")
    }

    /** Everything the owner has, and nothing that was seeded as reference data. */
    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
    }
}
