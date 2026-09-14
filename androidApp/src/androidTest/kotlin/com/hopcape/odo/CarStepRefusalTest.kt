package com.hopcape.odo

import androidx.compose.ui.test.assertIsEnabled
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
 * A car the app could not store has to say so.
 *
 * Setup refuses the step when the save fails and emits the reason, but the route dropped it —
 * so Continue stayed lit, the tap registered, and nothing moved or spoke. The twin of #440 on
 * the step that has no field to hang an error on.
 *
 * Driven through the real flow because the state under test only exists after a save is
 * actually refused.
 */
@RunWith(AndroidJUnit4::class)
class CarStepRefusalTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * The refusal is installed before the launch, like the rest of the graph: the setup
     * ViewModel resolves its repository when the step is first composed, and an override
     * loaded after that would arrive too late to be the one it holds.
     */
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
    fun aRefusedCarSaveSaysSoRatherThanDoingNothing() {
        rule.reachARefusedCarSave()

        // The form is complete, so the button is live and the tap is real. What follows is
        // the save being refused, which is a different thing from an unanswered step.
        rule.onNodeWithText(Copy.CONTINUE).assertIsEnabled().performClick()

        // Staying put is correct — the car was not stored, and moving on would be a lie.
        rule.waitForText(Copy.DETAILS_TITLE)
        // Staying put in silence is not. This is the whole bug.
        rule.waitForText(Copy.SAVE_ERROR)
    }
}
