package com.hopcape.odo

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** The v2 first run against the real app: make tile, model tile, year and fuel chips, save. */
@RunWith(AndroidJUnit4::class)
class FirstRunV2EndToEndTest {

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
    fun fiveTaps_saveTheCarAndOpenTheValueScreen() {
        rule.pickMake("Hyundai")
        rule.pickModel("Creta")
        rule.pickYearAndFuel(2022, "PETROL")
        rule.saveTheCar()

        rule.waitUntilPresent(Copy.VALUE_SKIP, 10_000)
        val car = runBlocking { GlobalContext.get().get<CarRepository>().observePrimaryCar().first() }
        assertEquals("Hyundai", car?.make)
        assertEquals("Creta", car?.model)
        assertTrue("v2 saves with the odometer pending", car?.isOdometerPending == true)
    }

    @Test
    fun theModelStepShowsTheMake_andHasNoContinue() {
        rule.pickMake("Hyundai")
        rule.waitForText(V2Copy.MODEL_TITLE)

        rule.onNodeWithText("HYUNDAI").assertExists()
        rule.onNodeWithText(Copy.CONTINUE).assertDoesNotExist()
    }

    @Test
    fun saveStaysOffWithOnlyAYear() {
        rule.pickMake("Hyundai")
        rule.pickModel("Creta")
        rule.waitForText(V2Copy.YEAR_FUEL_TITLE)
        rule.tapTag(OnboardingTestTags.yearChip(2022))

        rule.onNodeWithTag(OnboardingTestTags.SAVE_CAR).assertIsNotEnabled()
    }

    @Test
    fun backFromModel_returnsToTheMakeGrid() {
        rule.pickMake("Hyundai")
        rule.waitForText(V2Copy.MODEL_TITLE)

        Espresso.pressBack()

        rule.waitForText(V2Copy.WELCOME_TITLE)
    }

    @Test
    fun change_returnsToTheMakeGrid() {
        rule.pickMake("Hyundai")
        rule.waitForText(V2Copy.MODEL_TITLE)

        rule.onNodeWithTag(OnboardingTestTags.CHANGE_MAKE).performClick()

        rule.waitForText(V2Copy.WELCOME_TITLE)
    }
}
