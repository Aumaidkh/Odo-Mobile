package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The Before/After picture for issue #462 — today's value stated as a band.
 *
 * Run it and collect the file with the commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class CarValueBandScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState { resetGarage(); seedOnboardedOwner() })
        .around(rule)

    @Test
    fun capturesTheValueScreen() {
        rule.waitForText(GarageCopy.TAB, START_DESTINATION_TIMEOUT_MILLIS)
        rule.runOnUiThread {
            GlobalContext.get().get<NavigationManager>().navigateTo(OdoDestination.CarValue)
        }
        rule.waitForText("My car’s value")
        rule.captureScreen("car-value-band")
    }
}
