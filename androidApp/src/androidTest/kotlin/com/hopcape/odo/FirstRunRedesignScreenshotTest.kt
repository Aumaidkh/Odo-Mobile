package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import org.koin.core.context.GlobalContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The Before/After pictures for the first-run redesign.
 *
 * Three shots along the real flow: the car step that no longer asks for a registration
 * number, the value screen that pays for those answers, and the Home that follows with a
 * figure on every tile. Driving it here rather than by hand also proves the three join up,
 * which a screenshot of any one alone would not.
 *
 * Run it and collect the files with the commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunRedesignScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /** Emptied before the launch: where the app opens is decided once, at startup. */
    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                clearTheOwnersRows()
                silenceTheCoachMarks()
            },
        )
        .around(rule)

    /** Everything the owner has, and nothing that was seeded as reference data. */
    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
    }

    @Test
    fun capturesTheCarStepTheValueScreenAndHome() {
        rule.startFromWelcome()

        // Four pickers, nothing to type, and the fields below the model visibly waiting.
        rule.captureScreen("01-car-step")

        rule.answerTheCarStep()
        rule.onNodeWithText(Copy.CONTINUE).performClick()

        // Step 3: the first thing first run gives back.
        rule.waitUntilPresent(Copy.VALUE_SKIP)
        rule.captureScreen("02-car-value")

        rule.onNodeWithText(Copy.VALUE_SKIP).performClick()

        // Every tile carries a figure, and the ones that are modelled say so.
        rule.waitUntilPresent(Copy.HOME_RESALE)
        rule.captureScreen("03-home-day-one")
    }
}
