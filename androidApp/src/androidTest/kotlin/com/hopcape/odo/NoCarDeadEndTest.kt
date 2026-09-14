package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.advisory.presentation.CarValueTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * What the app offers an owner who has no car yet.
 *
 * Both surfaces named one thing to do — add a car — and neither could do it. The value screen
 * said so with no button at all, and the garage's "log a service" opened a sheet whose rows
 * were wired to `carId?.let { }`, so a tap on them did nothing: no move, no message, not even
 * the sheet closing.
 */
@RunWith(AndroidJUnit4::class)
class NoCarDeadEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState { resetGarage(); seedProfileOnly() })
        .around(rule)

    @Test
    fun loggingAServiceWithNoCarLeadsToAddingOne() {
        rule.openGarage()
        rule.awaitText(GarageCopy.ADD_CAR_TITLE)

        rule.onNodeWithText(GarageCopy.EMPTY_LOG_SERVICE).performClick()
        rule.awaitText(SHEET_TITLE)

        rule.onNodeWithText(MANUAL_ROW).performClick()

        // A car is the one thing missing, so that is where the row has to lead. Doing nothing
        // leaves the owner tapping a live row on an open sheet.
        rule.awaitText(ADD_CAR_SCREEN)
    }

    @Test
    fun theValueScreenWithNoCarOffersAWayToAddOne() {
        rule.awaitText(GarageCopy.TAB)
        rule.runOnUiThread {
            GlobalContext.get().get<NavigationManager>().navigateTo(OdoDestination.CarValue)
        }
        rule.awaitText(VALUE_TITLE)

        // The screen asks for a car. It has to offer the way to add one.
        rule.onNodeWithTag(CarValueTestTags.ADD_CAR).performClick()
        rule.awaitText(ADD_CAR_SCREEN)
    }

    private companion object {
        const val SHEET_TITLE = "Add to service history"
        const val MANUAL_ROW = "Enter manually"
        const val ADD_CAR_SCREEN = "Add a car"
        const val VALUE_TITLE = "My car’s value"
    }
}
