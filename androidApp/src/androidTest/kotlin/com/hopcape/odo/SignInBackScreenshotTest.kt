package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The Before/After picture for issue #442 — where the system back from sign-in lands.
 *
 * One shot, taken after the gesture. There is nothing to see on the sign-in screen itself:
 * the change is entirely which surface the owner arrives at, so the picture has to be of the
 * destination.
 *
 * Run it and collect the file with the commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class SignInBackScreenshotTest {

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
    fun capturesWhereTheSystemBackFromSignInLands() {
        rule.reachTheLastServiceStep()
        rule.onNodeWithText(Copy.SCAN_CTA).performClick()
        rule.waitForText(Copy.AUTH_TITLE)

        Espresso.pressBack()

        // No wait on a specific screen: the point is to photograph wherever this lands, which
        // is the whole disagreement. Idle plus the capture's own settle is enough.
        rule.waitForIdle()
        rule.captureScreen("sign-in-system-back")
    }

    /** Everything the owner has, and nothing that was seeded as reference data. */
    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
    }
}
