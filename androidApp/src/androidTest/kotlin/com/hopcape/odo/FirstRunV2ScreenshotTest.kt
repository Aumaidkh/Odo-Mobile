package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** The After pictures for the 1.4.5 first run. See `.github/screenshots/README.md`. */
@RunWith(AndroidJUnit4::class)
class FirstRunV2ScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(firstRunV2())
        .around(
            DeviceState {
                val driver = GlobalContext.get().get<SqlDriver>()
                driver.execute(null, "DELETE FROM cars", 0)
                driver.execute(null, "DELETE FROM profiles", 0)
                silenceTheCoachMarks()
            },
        )
        .around(rule)

    @Test
    fun capturesTheMakeChoice() {
        rule.waitForText(V2Copy.WELCOME_TITLE, START_DESTINATION_TIMEOUT_MILLIS)
        rule.captureScreen("after-s1-welcome")

        rule.onNodeWithTag(V2Copy.OTHER_TILE).performClick()
        rule.waitForText(V2Copy.OTHER_SHEET_TITLE)
        rule.captureScreen("after-s1b-other-sheet")
    }

    @Test
    fun capturesModelThenYearAndFuel() {
        rule.pickMake("Hyundai")
        rule.waitForText(V2Copy.MODEL_TITLE)
        rule.captureScreen("after-s2-model")

        rule.pickModel("Creta")
        rule.waitForText(V2Copy.YEAR_FUEL_TITLE)
        rule.captureScreen("after-s3-year-fuel-empty")

        rule.pickYearAndFuel(2022, "PETROL")
        rule.captureScreen("after-s3-year-fuel-answered")
    }
}

/** The v2 first run's copy, mirrored from the features' `strings.xml`. */
internal object V2Copy {
    const val WELCOME_TITLE = "Find out if your mechanic overcharged you"
    const val MODEL_TITLE = "Which model?"
    const val YEAR_FUEL_TITLE = "A couple of quick taps"
    const val SAVE = "Save my car"
    const val CHANGE = "Change"
    const val OTHER_SHEET_TITLE = "Choose make"
    const val OTHER_TILE = "welcome_make_tile_other"
    fun tile(make: String) = "welcome_make_tile_$make"
}
