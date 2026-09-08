package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.Assert.assertThrows

/**
 * Where back lands once the owner has come through the pitch's sign-in.
 *
 * The pitch is first run. Signing in from it is a way past first run, so once the owner is on
 * the dashboard there is nothing behind them — back leaves the app. It used to return them to
 * the pitch they had already answered, and from there the two screens ping-ponged.
 */
@RunWith(AndroidJUnit4::class)
class PitchBackStackTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState { resetGarage(); installStubVehicleRegistry() })
        .around(rule)

    @Test
    fun backFromTheDashboardAfterThePitchSignInLeavesTheApp() {
        rule.waitForText(Copy.WELCOME_HEADLINE)
        rule.onNodeWithText(Copy.WELCOME_SIGN_IN).performClick()
        rule.waitForText(Copy.AUTH_TITLE)

        // Declining is a back, and it lands on the dashboard exactly where verifying does —
        // the same `next`, the same pop. So the stack under it is the same either way.
        Espresso.pressBack()
        rule.onNodeWithTag(HomeTestTags.NO_CAR).assertExists()

        // Nothing is behind the dashboard now. Going back to the pitch would ask the owner
        // something they have already answered.
        assertThrows(NoActivityResumedException::class.java) { Espresso.pressBack() }
    }
}
