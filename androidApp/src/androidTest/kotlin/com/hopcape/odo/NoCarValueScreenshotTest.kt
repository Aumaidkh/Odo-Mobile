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
 * The Before/After picture for the car-value screen's no-car state.
 *
 * One shot. The screen names the one thing to do — add a car — and used to offer no way to
 * do it.
 *
 * Run it and collect the file with the commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class NoCarValueScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState { resetGarage(); seedProfileOnly() })
        .around(rule)

    @Test
    fun capturesTheNoCarValueScreen() {
        rule.awaitText(GarageCopy.TAB)
        rule.runOnUiThread {
            GlobalContext.get().get<NavigationManager>().navigateTo(OdoDestination.CarValue)
        }
        rule.awaitText("My car’s value")
        rule.captureScreen("car-value-no-car")
    }
}
