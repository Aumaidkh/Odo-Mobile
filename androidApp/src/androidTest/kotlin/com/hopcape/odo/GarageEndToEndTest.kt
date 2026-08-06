package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.garage.presentation.GarageTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Every garage flow an owner can reach, driven against the real app: the real Koin graph,
 * the real SQLite database, the real navigation graph and the real ViewModels.
 *
 * The garage is the app's aggregator — it reads the car, its documents and its service log
 * together, and hands off to three other features — so its bugs live in seams no unit test
 * spans: a route Koin cannot build, a snapshot whose three reads disagree, a sheet that
 * navigates to a destination that no longer exists.
 *
 * **What is seeded and why.** The car, its logs and its documents are written straight to
 * the database. The garage does not create any of them (a service is logged in the service
 * log, a document in the vault), so seeding is how a *populated* garage exists at all. The
 * flows the garage does own — updating the odometer, adding, editing and removing a car,
 * and every jump out of it — are driven by tapping.
 *
 * **What is deliberately not covered:** a failed database read (there is no way to break
 * SQLite from a test without breaking the whole app with it, and the ViewModel's failure
 * branch is unit-tested), and the paywall's own screen beyond the fact that export reaches
 * it — that feature has not been built.
 */
@RunWith(AndroidJUnit4::class)
class GarageEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start every test from a set-up device with a car and nothing else.
     *
     * The activity is recreated because the rule launches it before this runs, so it may
     * have already read a previous test's data.
     */
    @Before
    fun startFromASetUpDevice() {
        resetGarage()
        seedOnboardedOwner()
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------------ The home base ------------------------------ */

    @Test
    fun aGarageWithNoCarAsksForOne() {
        resetGarage()
        seedProfileOnly()
        rule.activityRule.scenario.recreate()
        rule.openGarage()

        rule.awaitText(GarageCopy.ADD_CAR_TITLE)
        rule.onNodeWithText(GarageCopy.EMPTY_TITLE).assertIsDisplayed()
        // The two things worth doing before there is any history.
        rule.onNodeWithText(GarageCopy.EMPTY_ADD_DOCS).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.EMPTY_LOG_SERVICE).assertIsDisplayed()
        // Nothing that would describe a car it does not have.
        rule.onNodeWithTag(GarageTestTags.CAR_CARD).assertDoesNotExist()
    }

    @Test
    fun aGarageWithACarLeadsWithIt() {
        rule.openGarage()

        rule.awaitText(LogFixtures.CAR_NAME)
        rule.onNodeWithText(GarageCopy.carMeta(GarageFixtures.CAR_YEAR, GarageFixtures.CAR_PLATE_SHOWN))
            .assertIsDisplayed()
        rule.onNodeWithTag(GarageTestTags.ODOMETER).assertTextEquals(GarageFixtures.CAR_ODOMETER_SHOWN)
        rule.onNodeWithText(GarageCopy.UPDATE).assertIsDisplayed()
    }

    @Test
    fun theDocumentsSectionSaysWhichPapersAreMissing() {
        seedDocument(
            id = GarageFixtures.INSURANCE_ID,
            type = DocumentType.INSURANCE,
            expiresOn = LocalDate.now().plusDays(200),
        )
        seedDocument(id = GarageFixtures.RC_ID, type = DocumentType.RC)
        rule.openGarage()

        rule.awaitText(GarageCopy.DOCUMENTS)
        // An RC never lapses, so it is simply on file.
        rule.assertDocumentRowShows(DocumentType.RC, GarageCopy.DOC_ON_FILE)
        // The PUC was never uploaded, so its row offers to add one.
        rule.assertDocumentRowShows(DocumentType.PUC, GarageCopy.DOC_ADD)
        rule.assertDocumentRowShows(DocumentType.INSURANCE, GarageCopy.DOC_INSURANCE)
    }

    @Test
    fun anExpiringDocumentIsShownAsSuchOnTheOverview() {
        seedDocument(
            id = GarageFixtures.INSURANCE_ID,
            type = DocumentType.INSURANCE,
            expiresOn = LocalDate.now().plusDays(7),
        )
        rule.openGarage()

        rule.awaitText(GarageCopy.DOCUMENTS)
        rule.assertDocumentRowShows(DocumentType.INSURANCE, "Expires in 7 days")
    }

    @Test
    fun theHistoryShowsEveryLoggedServiceAndWhatTheyCost() {
        seedServiceHistory()
        rule.openGarage()

        rule.awaitText(GarageCopy.HISTORY)
        // Rs. 6,400 + Rs. 4,800 + Rs. 3,200.
        rule.onNodeWithText(GarageCopy.entriesSummary(3, "Rs. 14,400")).assertIsDisplayed()

        // A row is headed by its tags, and says where and when the work was done.
        rule.assertServiceRowShows(LogFixtures.FLAGGED_ID, GarageCopy.CAT_BRAKES)
        rule.assertServiceRowShows(
            LogFixtures.FLAGGED_ID,
            GarageCopy.entryMeta("2 Mar 2026", LogFixtures.FLAGGED_WORKSHOP),
        )
        // A bill photo is what earns the badge; the entry without one says so.
        rule.assertServiceRowShows(LogFixtures.FLAGGED_ID, GarageCopy.BADGE_VERIFIED)
        rule.assertServiceRowShows(LogFixtures.SELF_REPORTED_ID, GarageCopy.BADGE_SELF_REPORTED)
    }

    @Test
    fun aChipNarrowsTheHistoryAndItsTotal() {
        seedServiceHistory()
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)

        rule.scrollToText(GarageCopy.FILTER_TYRES)
        rule.onNodeWithText(GarageCopy.FILTER_TYRES).performClick()

        // Nothing on file was tyre work, so the chip empties the list rather than lying.
        rule.awaitText(GarageCopy.NO_ENTRIES)
        rule.onNodeWithText(GarageCopy.entriesSummary(0, "Rs. 0")).assertIsDisplayed()

        rule.onNodeWithText(GarageCopy.FILTER_SERVICE).performClick()
        rule.awaitGone(GarageCopy.NO_ENTRIES)
        // Brakes, a general service and an oil change are all routine workshop work.
        rule.onNodeWithText(GarageCopy.entriesSummary(3, "Rs. 14,400")).assertIsDisplayed()
    }

    @Test
    fun aGarageWithNoServicesSaysSo() {
        rule.openGarage()

        rule.awaitText(GarageCopy.HISTORY)
        rule.onNodeWithText(GarageCopy.NO_ENTRIES).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.entriesSummary(0, "Rs. 0")).assertIsDisplayed()
    }

    /**
     * The reported bug: after logging a service the card kept its previous reading, running
     * one service behind. The new reading must be on the card as soon as the garage is back
     * on screen.
     */
    @Test
    fun loggingAServiceMovesTheCardsReadingAtOnce() {
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)
        rule.onNodeWithTag(GarageTestTags.ODOMETER).assertTextEquals(GarageFixtures.CAR_ODOMETER_SHOWN)

        rule.openAddToHistory()
        rule.onNodeWithText(GarageCopy.ADD_HISTORY_MANUAL).performClick()
        rule.awaitText(LogCopy.FORM_TITLE_ADD)
        rule.fillServiceForm(odometer = "41000", date = todayTyped())
        rule.saveServiceLog()

        // Saving may land on the new entry rather than straight back on the garage.
        rule.waitUntil(10_000) {
            rule.textCount(GarageCopy.HISTORY) > 0 || rule.textCount(LogCopy.ADD_BILL_TO_VERIFY) > 0
        }
        if (rule.textCount(GarageCopy.HISTORY) == 0) androidx.test.espresso.Espresso.pressBack()
        rule.awaitText(GarageCopy.HISTORY)

        rule.onNodeWithTag(GarageTestTags.ODOMETER).assertTextEquals("41,000 km")
    }

    private fun todayTyped(): String {
        val today = LocalDate.now()
        return "%02d%02d%04d".format(today.monthValue, today.dayOfMonth, today.year)
    }

    /* ------------------------------ The odometer ------------------------------ */

    @Test
    fun aNewReadingIsStoredAndShownOnTheCard() {
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)

        rule.openOdometerSheet()
        // The sheet opens on what is already on record, dated from when it was written.
        rule.onNodeWithText(
            GarageCopy.lastRecorded(GarageFixtures.CAR_ODOMETER_SHOWN, "1 Jan 2024"),
        ).assertIsDisplayed()

        rule.saveOdometer(GarageFixtures.HIGHER_READING)

        rule.awaitGone(GarageCopy.ODOMETER_SAVE)
        rule.onNodeWithTag(GarageTestTags.ODOMETER)
            .assertTextEquals(GarageFixtures.HIGHER_READING_SHOWN)
        assertEquals("88888", storedCarValue("current_odometer_km"))
    }

    /**
     * The reading is typed on the phone's own number pad, not an in-app keypad. The field
     * behind the drums takes focus as soon as the sheet opens — that is what raises the
     * keyboard — what is typed is cleaned as it lands (a leading zero is dropped, a
     * seventh digit has nowhere to go), and the keyboard's done key saves like the button.
     */
    @Test
    fun theReadingIsTypedOnTheSystemKeyboard() {
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)
        rule.openOdometerSheet()

        rule.odometerEntry().assertIsFocused()

        rule.dialOdometer("")
        rule.typeOdometer("0" + GarageFixtures.FULL_READING)
        rule.typeOdometer("9")
        rule.odometerEntry().performImeAction()

        rule.awaitGone(GarageCopy.ODOMETER_SAVE)
        rule.onNodeWithTag(GarageTestTags.ODOMETER)
            .assertTextEquals(GarageFixtures.FULL_READING_SHOWN)
        assertEquals(GarageFixtures.FULL_READING, storedCarValue("current_odometer_km"))
    }

    /**
     * An odometer only counts up. The reading is what per-km cost, the health score and the
     * resale record are computed from, so a lower one is refused rather than warned about —
     * and the sheet stays open with the number still there to correct.
     */
    @Test
    fun aReadingBelowTheLastOneIsRefusedWithoutClosingTheSheet() {
        seedServiceHistory()
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)
        rule.openOdometerSheet()

        rule.saveOdometer(GarageFixtures.LOWER_READING)

        rule.awaitTextStartingWith(GarageCopy.ERROR_ODO_BACK_PREFIX)
        rule.onNodeWithText(GarageCopy.ODOMETER_SAVE).assertIsDisplayed()
        // The car keeps the reading it had.
        assertEquals(LogFixtures.CAR_ODOMETER.toString(), storedCarValue("current_odometer_km"))
    }

    /* ------------------------------ Car actions ------------------------------ */

    @Test
    fun theCarMenuOffersOnlyWhatOneCarCanDo() {
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)

        rule.openCarMenu()

        rule.onNodeWithText(GarageCopy.ACTION_EDIT).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.ACTION_EXPORT).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.ACTION_REMOVE).assertIsDisplayed()
        // Multi-car is Phase 2, so the menu does not offer a choice the app cannot honour.
        rule.onNodeWithText("Switch car").assertDoesNotExist()
        rule.onNodeWithText("Add another car").assertDoesNotExist()
    }

    @Test
    fun editingTheCarStoresTheChangeAndLeavesTheOdometerAlone() {
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)
        rule.openCarMenu()
        rule.onNodeWithText(GarageCopy.ACTION_EDIT).performClick()

        rule.awaitText(GarageCopy.EDIT_SAVE)
        // The form opens on the stored car, and says where the reading is changed instead.
        rule.onNodeWithText(GarageCopy.EDIT_ODO_NOTE).assertIsDisplayed()
        rule.typeInto(GarageTestTags.NICKNAME_FIELD, GarageFixtures.NICKNAME)
        rule.onNodeWithText(GarageCopy.EDIT_SAVE).performClick()

        // The nickname is what the car is called from here on.
        rule.awaitText(GarageFixtures.NICKNAME)
        assertEquals(GarageFixtures.NICKNAME, storedCarValue("nickname"))
        assertEquals(LogFixtures.CAR_ODOMETER.toString(), storedCarValue("current_odometer_km"))
    }

    @Test
    fun closingTheEditorLeavesTheCarAsItWas() {
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)
        rule.openCarMenu()
        rule.onNodeWithText(GarageCopy.ACTION_EDIT).performClick()
        rule.awaitText(GarageCopy.EDIT_SAVE)

        rule.typeInto(GarageTestTags.NICKNAME_FIELD, GarageFixtures.NICKNAME)
        rule.onNodeWithLabel(GarageCopy.CLOSE_LABEL).performClick()

        rule.awaitText(LogFixtures.CAR_NAME)
        assertNull(storedCarValue("nickname"))
    }

    /* ------------------------------ Export ------------------------------ */

    @Test
    fun exportSaysWhatIsOnFileAndLeadsToPro() {
        seedServiceHistory()
        seedDocument(id = GarageFixtures.RC_ID, type = DocumentType.RC)
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)
        rule.openCarMenu()

        rule.onNodeWithText(GarageCopy.ACTION_EXPORT).performClick()

        rule.awaitText(GarageCopy.EXPORT_TITLE)
        rule.onNodeWithText(GarageCopy.exportServices(3)).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.exportDocs(1)).assertIsDisplayed()
        // The record itself is the paid Resale Passport, and the sheet says so.
        rule.onNodeWithText(GarageCopy.EXPORT_PRO_NOTE).assertIsDisplayed()

        rule.onNodeWithText(GarageCopy.EXPORT_PDF).performClick()

        rule.awaitGone(GarageCopy.EXPORT_TITLE)
    }

    /* ------------------------------ Removing the car ------------------------------ */

    @Test
    fun removingTheCarSpellsOutWhatGoesWithIt() {
        seedServiceHistory()
        seedDocument(id = GarageFixtures.RC_ID, type = DocumentType.RC)
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)
        rule.openCarMenu()

        rule.onNodeWithText(GarageCopy.ACTION_REMOVE).performClick()

        rule.awaitText(GarageCopy.removeTitle(LogFixtures.CAR_NAME))
        rule.onNodeWithText(GarageCopy.removeBody(services = 3, documents = 1)).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.REMOVE_EXPORT_FIRST).assertIsDisplayed()
    }

    @Test
    fun cancellingTheRemovalKeepsTheCar() {
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)
        rule.openCarMenu()
        rule.onNodeWithText(GarageCopy.ACTION_REMOVE).performClick()
        rule.awaitText(GarageCopy.REMOVE_CANCEL)

        rule.onNodeWithText(GarageCopy.REMOVE_CANCEL).performClick()

        rule.awaitText(LogFixtures.CAR_NAME)
        assertEquals(1L, liveRowCount("cars"))
    }

    /**
     * The car's logs and documents go with it, in one write. They are only reachable through
     * the car, and a document left behind would still count against the free tier's cap.
     */
    @Test
    fun removingTheCarTakesItsHistoryAndItsPapersWithIt() {
        seedServiceHistory()
        seedDocument(id = GarageFixtures.RC_ID, type = DocumentType.RC)
        rule.openGarage()
        rule.awaitText(LogFixtures.CAR_NAME)
        rule.openCarMenu()
        rule.onNodeWithText(GarageCopy.ACTION_REMOVE).performClick()
        rule.awaitText(GarageCopy.REMOVE_CONFIRM)

        rule.onNodeWithText(GarageCopy.REMOVE_CONFIRM).performClick()

        // The garage falls back to asking for a car, without being told to.
        rule.awaitText(GarageCopy.ADD_CAR_TITLE)
        assertEquals(0L, liveRowCount("cars"))
        assertEquals(0L, liveRowCount("service_logs"))
        assertEquals(0L, liveRowCount("documents"))
    }

    /* ------------------------------ Adding a car ------------------------------ */

    @Test
    fun aCarCanBeDescribedAndAdded() {
        resetGarage()
        seedProfileOnly()
        rule.activityRule.scenario.recreate()
        rule.openGarage()
        rule.awaitText(GarageCopy.ADD_CAR_TITLE)

        rule.onNodeWithText(GarageCopy.ADD_CAR_BUTTON).performClick()
        rule.awaitText(GarageCopy.ADD_SUBMIT)
        rule.describeNewCar()
        rule.onNodeWithText(GarageCopy.ADD_SUBMIT).performClick()

        // The garage now leads with the car that was just described.
        rule.awaitText(GarageFixtures.NEW_CAR_NAME)
        assertEquals(GarageFixtures.NEW_MAKE, storedCarValue("make"))
        assertEquals(GarageFixtures.NEW_MODEL, storedCarValue("model"))
        assertEquals(GarageFixtures.NEW_PLATE_TYPED, storedCarValue("registration_number"))
        assertEquals(GarageFixtures.NEW_ODOMETER, storedCarValue("current_odometer_km"))
    }

    /**
     * There is no registry behind the lookup port, so the form says so and keeps its fields
     * on screen. A form that hides itself waiting for a match that never comes is a dead end.
     */
    @Test
    fun theAddFormSaysTheRegistryCannotAnswerYet() {
        resetGarage()
        seedProfileOnly()
        rule.activityRule.scenario.recreate()
        rule.openGarage()
        rule.awaitText(GarageCopy.ADD_CAR_TITLE)
        rule.onNodeWithText(GarageCopy.ADD_CAR_BUTTON).performClick()
        rule.awaitText(GarageCopy.ADD_SUBMIT)

        rule.typeInto(GarageTestTags.REGISTRATION_FIELD, GarageFixtures.NEW_PLATE_TYPED)

        rule.awaitText(GarageCopy.ADD_LOOKUP_UNAVAILABLE)
        rule.onNodeWithTag(GarageTestTags.MAKE_FIELD).assertIsDisplayed()
    }

    @Test
    fun anEmptyAddFormRefusesToSaveAndMarksWhatIsMissing() {
        resetGarage()
        seedProfileOnly()
        rule.activityRule.scenario.recreate()
        rule.openGarage()
        rule.awaitText(GarageCopy.ADD_CAR_TITLE)
        rule.onNodeWithText(GarageCopy.ADD_CAR_BUTTON).performClick()
        rule.awaitText(GarageCopy.ADD_SUBMIT)

        rule.onNodeWithText(GarageCopy.ADD_SUBMIT).performClick()

        // The fields that failed say so, rather than one banner over a form of five inputs.
        rule.awaitText(GarageCopy.ERROR_FIELD_MAKE)
        rule.onNodeWithText(GarageCopy.ERROR_FIELD_MODEL).assertIsDisplayed()
        assertEquals(0L, liveRowCount("cars"))
    }

    /* ------------------------------ Handing off ------------------------------ */

    @Test
    fun manageOpensTheDocumentVault() {
        rule.openGarage()
        rule.awaitText(GarageCopy.MANAGE)

        rule.onNodeWithText(GarageCopy.MANAGE).performClick()

        rule.awaitText(VaultCopy.ADD_DOCUMENT_BAR)
    }

    @Test
    fun theAddToHistorySheetOffersEveryWayIn() {
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)

        rule.openAddToHistory()

        rule.onNodeWithText(GarageCopy.ADD_HISTORY_SCAN).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.ADD_HISTORY_MANUAL).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.ADD_HISTORY_DOC).assertIsDisplayed()
        rule.onNodeWithText(GarageCopy.ADD_HISTORY_VIEW_ALL).assertIsDisplayed()
    }

    /** The garage's history is a summary; the full record is the service log's own screen. */
    @Test
    fun viewAllRecordsOpensTheServiceLog() {
        seedServiceHistory()
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)
        rule.openAddToHistory()

        rule.onNodeWithText(GarageCopy.ADD_HISTORY_VIEW_ALL).performClick()

        rule.awaitText(LogCopy.LIST_TITLE)
    }

    @Test
    fun tappingAServiceOpensItsRecord() {
        seedServiceHistory()
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)

        rule.onNodeWithTag(GarageTestTags.serviceRow(LogFixtures.FLAGGED_ID))
            .performScrollTo()
            .performClick()

        rule.awaitText(LogFixtures.FLAGGED_WORKSHOP)
    }

    @Test
    fun addingADocumentFromTheEmptyGarageOpensTheVault() {
        resetGarage()
        seedProfileOnly()
        rule.activityRule.scenario.recreate()
        rule.openGarage()
        rule.awaitText(GarageCopy.EMPTY_ADD_DOCS)

        rule.onNodeWithText(GarageCopy.EMPTY_ADD_DOCS).performClick()

        rule.awaitText(VaultCopy.ADD_TITLE)
    }
}
