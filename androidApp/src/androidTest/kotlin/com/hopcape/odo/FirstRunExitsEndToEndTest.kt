package com.hopcape.odo

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Every way out of the v2 value screen leaves first run behind, so back lands on Home. */
@RunWith(AndroidJUnit4::class)
class FirstRunExitsEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(firstRunV2())
        .around(PinnedConfig("bill_check_enabled", value = "false", compiledDefault = "false"))
        .around(
            DeviceState {
                resetScanner()
                val driver = GlobalContext.get().get<SqlDriver>()
                driver.execute(null, "DELETE FROM cars", 0)
                driver.execute(null, "DELETE FROM profiles", 0)
                driver.execute(null, "DELETE FROM setup_draft", 0)
                silenceTheCoachMarks()
                seedCapturedPhoto()
                installDefaultScanning()
            },
        )
        .around(rule)

    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Before
    fun readableBillInFrontOfTheExtractor() = installBillExtractor(readableBill())

    private fun reachTheValueScreen() {
        rule.pickMake("Hyundai")
        rule.pickModel("Creta")
        rule.pickYearAndFuel(2022, "PETROL")
        rule.saveTheCar()
        rule.waitUntilPresent(Copy.VALUE_SKIP, 10_000)
    }

    @Test
    fun aBillSavedFromTheFirstScan_backLandsOnHome() {
        reachTheValueScreen()
        rule.onNodeWithText(Copy.VALUE_SCAN).performClick()
        // The shutter cannot be driven; jump to the review the capture would open.
        GlobalContext.get().get<NavigationManager>()
            .navigateTo(OdoDestination.BillScanner.Review(ScanFixtures.PHOTO_KEY, firstRun = true))
        rule.waitForText(ScanCopy.REVIEW_TITLE, 10_000)
        rule.waitUntilGone(ScanCopy.READING)
        rule.onNodeWithText(ScanCopy.REVIEW_SAVE).performClick()
        rule.waitForText(ScanCopy.SAVE_TITLE, 10_000)

        Espresso.pressBack()

        rule.waitForText(HomeCopy.HEALTH_SCORE, 10_000)
        rule.onNodeWithText(Copy.VALUE_SKIP).assertDoesNotExist()
        rule.onNodeWithText(V2Copy.YEAR_FUEL_TITLE).assertDoesNotExist()
    }

    @Test
    fun skip_landsOnHome() {
        reachTheValueScreen()

        rule.onNodeWithText(Copy.VALUE_SKIP).performClick()

        rule.waitForText(HomeCopy.HEALTH_SCORE, 10_000)
    }

    @Test
    fun turnOnAutoOdometer_thenBack_landsOnHome() {
        reachTheValueScreen()

        rule.onNodeWithText(V2Copy.TURN_ON).performClick()
        rule.waitUntilGone(Copy.VALUE_SKIP)
        Espresso.pressBack()

        rule.waitForText(HomeCopy.HEALTH_SCORE, 10_000)
        rule.onNodeWithText(Copy.VALUE_SKIP).assertDoesNotExist()
    }
}
