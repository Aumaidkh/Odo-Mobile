package com.hopcape.odo

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
 * The Before/After picture for issue #452 — a refused car save now says why.
 *
 * One shot, taken after the tap that used to do nothing visible. Driven through the real flow
 * rather than posed, because the state being photographed only exists once a save is actually
 * refused.
 *
 * Run it and collect the file with the commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class CarStepRefusalScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                clearTheOwnersRows()
                installStubVehicleRegistry()
                installRefusingCarStore()
            },
        )
        .around(rule)

    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
    }

    @Test
    fun capturesTheRefusedCarSave() {
        rule.reachARefusedCarSave()
        rule.onNodeWithText(Copy.CONTINUE).performClick()
        rule.captureScreen("car-step-refused")
    }
}
