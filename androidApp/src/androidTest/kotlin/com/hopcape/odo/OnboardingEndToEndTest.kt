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
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import org.koin.core.context.GlobalContext
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

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
 * **On "relaunch":** an instrumented test shares a process with the app, so starting a new
 * activity is a relaunch rather than process death. It proves the gate reads persisted state
 * and opens elsewhere, which is the behaviour under test; a true cold start is verified by
 * hand.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Empty the owner's rows, then launch — in that order.
     *
     * The order is the whole point. Where the app opens is decided once per launch and then
     * held in saved state, so a `@Before` is too late: the rule has already drawn a first
     * frame against the previous test's data, and only a new activity asks the question
     * again. Chaining [DeviceState] outside the compose rule moves the reset in front of the
     * launch.
     *
     * The rows go rather than the file: the database is a process-wide singleton with an
     * open connection, so deleting the file underneath it would leave that connection
     * writing to an unlinked inode while reads still served stale data. The seeded catalog
     * is left alone — it is reference data, not the owner's.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState {
            clearTheOwnersRows()
            installStubVehicleRegistry()
        })
        .around(rule)

    /** Everything the owner has, and nothing that was seeded as reference data. */
    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
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

        // The workshop tier decides the labour rate every price comparison is quoted at,
        // so its step will not pass without an answer.
        rule.waitForText(Copy.WORKSHOP_TITLE)
        rule.onNodeWithText(Copy.CONTINUE).assertIsNotEnabled()
        rule.onNodeWithText(Copy.WORKSHOP_AUTHORISED).performClick()
        rule.onNodeWithText(Copy.CONTINUE).assertIsEnabled().performClick()

        // Skipping the last service still finishes setup.
        rule.waitForText(Copy.LAST_SERVICE_TITLE)
        rule.onNodeWithText(Copy.SKIP).performClick()

        // With no session yet, the sign-in offer comes first — the one place Odo asks,
        // because by now there is something concrete worth backing up.
        rule.waitForText(Copy.AUTH_TITLE)

        // The point of the whole flow: it does not happen twice. A new activity rather than
        // recreate(), because only a launch with no saved state re-asks the gate — see
        // relaunchTheApp.
        rule.relaunchTheApp().use {
            rule.waitForText(Copy.HOME_SCORE_WAITING, START_DESTINATION_TIMEOUT_MILLIS)
            rule.onNodeWithText(Copy.WELCOME_HEADLINE).assertDoesNotExist()
            rule.onNodeWithText(Copy.CAR_TITLE).assertDoesNotExist()
        }
    }

    /**
     * Regression for the last step handing an owner straight to the scanner.
     *
     * The camera button used to open `BillScanner.Capture` with setup still running, so the
     * sign-in offer at the end of the flow was never reached. From the viewfinder the scan
     * ran on to the fairness report, and the report's "set your city" opened the profile
     * editor — a post-setup surface, reached by someone who had not been asked to sign in.
     * Scanning now finishes setup like Skip does, so the offer comes first either way.
     */
    @Test
    fun photographingTheOldBill_asksForSignInBeforeTheScanner() {
        rule.reachTheLastServiceStep()

        rule.onNodeWithText(Copy.SCAN_CTA).performClick()

        // Sign-in, not the viewfinder.
        rule.waitForText(Copy.AUTH_TITLE)
        rule.onNodeWithText(ScanCopy.SCAN_TITLE_BILL).assertDoesNotExist()
    }

    /**
     * The point of the whole step: an owner who remembers their last service leaves setup
     * with a real service log against their car.
     *
     * Asserted against the app's own repository rather than a fake, because everything
     * between the ViewModel and SQLite is what this is checking — the use case, the mapper,
     * and a `DECLARED` row surviving a column that has only ever held MANUAL or SCANNED.
     * The unit tests already cover the decision to write; only this covers the write.
     */
    @Test
    fun rememberingTheLastService_leavesARealServiceLogBehind() {
        rule.reachTheLastServiceStep()

        rule.pickFirstOfTheMonth()
        rule.setOdometer(thousands = 3, fieldTag = OnboardingTestTags.LAST_SERVICE_ODOMETER_FIELD)
        rule.onNodeWithText(Copy.DONE).performClick()

        // Setup is over either way; the row is what matters.
        rule.waitForText(Copy.AUTH_TITLE)

        val entry = runBlocking { theOwnersOnlyServiceLog() }
        assertEquals(LogSource.DECLARED, entry.source)
        assertEquals(3_000, entry.odometer.km)
        // No bill behind it, so no money. Zero is the truth, not a placeholder.
        assertEquals(0L, entry.totalAmount.paise)
    }

    /** The car setup just stored, and the single log now hanging off it. */
    private suspend fun theOwnersOnlyServiceLog(): ServiceLogEntry {
        val koin = GlobalContext.get()
        val car = koin.get<CarRepository>().observePrimaryCar().filterNotNull().first()
        return koin.get<ServiceLogRepository>().observe(car.id).first().single()
    }

    /**
     * "Don't remember" is a first-class answer, and ticking it must not leave a half-row
     * behind: a date typed before the box was ticked used to still be written on Done.
     */
    @Test
    fun theLastServiceStepAcceptsNotRemembering() {
        rule.reachTheLastServiceStep()

        rule.onNodeWithText(Copy.LAST_SERVICE_FORGOT).performClick()
        rule.onNodeWithText(Copy.DONE).assertIsEnabled().performClick()

        rule.waitForText(Copy.AUTH_TITLE)
    }

    /**
     * The door for an owner who has done this before (issue #392).
     *
     * Signing out or reinstalling clears the local rows, so a returning owner lands on
     * Welcome with an empty app and a full server. Without this they can only set the car
     * up again, which makes a second one — sync then restores everything on its own.
     */
    @Test
    fun welcome_offersSignInWithoutSettingUpACar() {
        rule.waitForText(Copy.WELCOME_HEADLINE, START_DESTINATION_TIMEOUT_MILLIS)

        rule.onNodeWithText(Copy.WELCOME_SIGN_IN).performClick()

        // Straight to the number, with no car step in between.
        rule.waitForText(Copy.AUTH_TITLE)
        rule.onNodeWithText(Copy.CAR_TITLE).assertDoesNotExist()
    }

    /**
     * The match names where it came from (issue #392, D3).
     *
     * The card used to say only what the car was. It now answers from Odo's own records
     * rather than the RTO, and an owner cannot weigh a suggestion without knowing whether
     * it is their own history or a stranger's.
     */
    @Test
    fun aMatchFromTheOwnersOwnRecords_saysSo() {
        rule.startFromWelcome()

        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)

        rule.onNodeWithText(Copy.MATCH_SOURCE_OWN).assertIsDisplayed()
    }

    /**
     * A car somebody else entered under this plate is flagged as exactly that.
     *
     * The guardrail behind the whole cross-owner tier. Cars change hands, so this is a guess
     * about the owner's car rather than something they wrote down — and a wrong car accepted
     * silently becomes the car every fairness benchmark and health score is computed from.
     * The copy has to differ, and it has to carry the nudge to check.
     */
    @Test
    fun aMatchFromAnotherOwnersRecord_asksTheOwnerToCheckIt() {
        installStubVehicleRegistry(VehicleSource.ANOTHER_RECORD)
        rule.startFromWelcome()

        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)

        rule.onNodeWithText(Copy.MATCH_SOURCE_OTHER).assertIsDisplayed()
        rule.onNodeWithText(Copy.MATCH_SOURCE_OWN).assertDoesNotExist()
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

        // Every picker answered and the odometer given, and it is still not enough: the plate
        // is required on this route too, or the car is saved without the number every bill,
        // reminder and document identifies it by.
        rule.onNodeWithText(Copy.CONTINUE).assertIsNotEnabled()
        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.UNKNOWN_PLATE)

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
