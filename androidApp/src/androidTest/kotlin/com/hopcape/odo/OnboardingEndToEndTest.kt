package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The whole of first-run setup, driven the way an owner drives it, against the real app: the
 * real Koin graph, the real SQLite database, the real navigation graph and the real
 * start-destination gate. Nothing is faked — the plate lookup answers from the development
 * stub the app itself ships with.
 *
 * It exists because every part of this flow passed its own tests while the product was still
 * broken: the ViewModel stored nothing and setup reappeared on every launch. The seam that
 * was missing sat *between* the units, which is exactly what no unit test can see.
 *
 * **On "relaunch":** an instrumented test shares a process with the app, so `recreate()` is
 * an Activity relaunch rather than process death. It proves the gate reads persisted state
 * and opens elsewhere, which is the behaviour under test; a true cold start is verified by
 * hand.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start every test from a device that has never been set up.
     *
     * The rows go rather than the file: the database is a process-wide singleton with an
     * open connection, so deleting the file underneath it would leave that connection
     * writing to an unlinked inode while reads still served stale data. The seeded catalog
     * is left alone — it is reference data, not the owner's.
     */
    @Before
    fun startFromAnUnsetUpDevice() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
        // The rule launches the activity *before* this runs, so by now it may already have
        // read a previous test's data and opened on Home. Recreating it re-runs the
        // start-destination gate against the tables we just emptied.
        rule.activityRule.scenario.recreate()
    }

    @Test
    fun plateRoute_setsUpTheCarAndNeverAsksAgain() {
        rule.startFromWelcome()

        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)

        // A named car still isn't an answered step. Odo can't compute ₹/km, the health score
        // or a km anomaly without the reading, so Continue stays shut until it is set.
        rule.onNodeWithText(Copy.CONTINUE).assertIsNotEnabled()
        rule.setOdometer()
        rule.onNodeWithText(Copy.CONTINUE).assertIsEnabled().performClick()

        rule.waitForText(Copy.PROFILE_TITLE)
        rule.onNodeWithText(Copy.CONTINUE).assertIsNotEnabled()
        rule.typeInto(OnboardingTestTags.NAME_FIELD, Fixtures.OWNER_NAME)
        rule.onNodeWithText(Copy.GOAL_COSTS).performClick()
        rule.onNodeWithText(Copy.CONTINUE).assertIsEnabled().performClick()

        // Skipping the first scan still finishes setup.
        rule.waitForText(Copy.SCAN_TITLE)
        rule.onNodeWithText(Copy.SCAN_SKIP).performClick()

        // With no session yet, the sign-in offer comes first — the one place Odo asks,
        // because by now there is something concrete worth backing up.
        rule.waitForText(Copy.AUTH_TITLE)

        // The point of the whole flow: it does not happen twice.
        rule.activityRule.scenario.recreate()
        rule.waitForText(Copy.HOME_SCORE_WAITING)
        rule.onNodeWithText(Copy.WELCOME_HEADLINE).assertDoesNotExist()
        rule.onNodeWithText(Copy.CAR_TITLE).assertDoesNotExist()
    }

    @Test
    fun unknownPlate_saysSoAndOffersTheManualForm() {
        rule.startFromWelcome()

        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.UNKNOWN_PLATE)
        rule.waitForText(Copy.LOOKUP_NOT_FOUND)

        // "No record" is permanent, so the way forward is the form, not a retry.
        rule.onNodeWithText(Copy.ENTER_MANUALLY).performClick()
        rule.waitForText(Copy.DETAILS_TITLE)
    }

    @Test
    fun manualRoute_setsUpTheCarByHand() {
        rule.startFromWelcome()
        rule.onNodeWithText(Copy.ENTER_MANUALLY).performClick()
        rule.waitForText(Copy.DETAILS_TITLE)

        rule.onNodeWithText(Copy.CONTINUE).assertIsNotEnabled()
        rule.pick(OnboardingTestTags.MAKE_FIELD, Fixtures.MAKE)
        rule.pick(OnboardingTestTags.MODEL_FIELD, Fixtures.MODEL)
        rule.confirmYear()
        rule.pick(OnboardingTestTags.FUEL_FIELD, Fixtures.FUEL)
        rule.setOdometer()

        rule.onNodeWithText(Copy.CONTINUE).assertIsEnabled().performClick()
        rule.waitForText(Copy.PROFILE_TITLE)
    }

    @Test
    fun backFromTheManualForm_returnsToThePlate() {
        rule.startFromWelcome()
        rule.onNodeWithText(Copy.ENTER_MANUALLY).performClick()
        rule.waitForText(Copy.DETAILS_TITLE)

        // Manual entry is a mode of the car step, not a step of its own, so back leaves the
        // mode and stays in the flow.
        rule.onNodeWithLabel(Copy.BACK).performClick()
        rule.waitForText(Copy.CAR_TITLE)
    }

    @Test
    fun theFlowIsNotSkippableWithoutAnswers() {
        rule.startFromWelcome()

        // Nothing typed: the car step is unanswered and says so.
        rule.onNodeWithText(Copy.CONTINUE).assertIsNotEnabled()

        // A plate alone isn't an answer either — the odometer is never optional.
        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)
        rule.onNodeWithText(Copy.CONTINUE).assertIsNotEnabled()
        rule.onNodeWithText(Copy.CAR_TITLE).assertIsDisplayed()
    }
}
