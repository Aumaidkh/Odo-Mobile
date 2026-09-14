package com.hopcape.odo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Setup has to survive being re-entered with its own car already stored (#454).
 *
 * The car step stores the car on Continue and the profile step is what marks setup finished, so
 * a run that ends between the two leaves a car and no profile. The next launch opens setup again
 * — and `savedCarId` is a ViewModel field, so the flow has forgotten the car it stored. Inserting
 * a second live primary car is refused by `uq_cars_one_primary` on every attempt, which left the
 * owner unable to finish setup at all.
 *
 * Driven rather than seeded: the state is made the way an owner makes it, and the relaunch is
 * what takes `savedCarId` away.
 */
@RunWith(AndroidJUnit4::class)
class SetupReentryTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                clearTheOwnersRows()
                installStubVehicleRegistry()
            },
        )
        .around(rule)

    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
    }

    @Test
    fun setupReenteredWithItsOwnCarStored_stillGetsPast() {
        // First pass: name the car and move on, which is what stores it.
        rule.startFromWelcome()
        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)
        rule.setOdometer()
        rule.onNodeWithText(Copy.CONTINUE).assertIsEnabled().performClick()
        rule.waitForText(Copy.PROFILE_TITLE)

        // Leave before the profile step writes anything. A new activity is a new ViewModel, so
        // the id of the car just stored is gone — exactly what a killed process does.
        rule.relaunchTheApp()

        // No profile, so first run starts over — from the pitch, on a database that already
        // holds the car.
        rule.startFromWelcome()
        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)
        rule.setOdometer()

        // The second pass has to edit the stored car. Adding a twin is refused by the unique
        // index, and the owner would never leave this step.
        rule.onNodeWithText(Copy.CONTINUE).assertIsEnabled().performClick()
        rule.waitForText(Copy.PROFILE_TITLE)
    }
}
