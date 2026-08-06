package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every service-log flow an owner can reach today, driven against the real app: the real Koin
 * graph, the real SQLite database, the real navigation graph and the real ViewModels.
 *
 * The unit-level pieces each pass on their own, which is exactly why this exists — the seams
 * between them are where this feature has actually broken. Both bugs these tests now guard
 * were invisible to every unit test: the garage handed the list a car id that did not exist,
 * and the graph could not build a repository at all because a module was never registered.
 *
 * **What is seeded and why.** History is written straight to the database, because the states
 * that matter most cannot be produced through the UI: an entry is Verified only with a bill
 * attached (M2's scanner) and Flagged only with a stored fairness verdict (a city pool the
 * MVP has no data for). Everything else — adding, validating, filtering, opening, reporting —
 * is driven by tapping.
 *
 * **What is deliberately not covered**, because the product has no affordance for it yet:
 * editing an entry and deleting one (the ViewModel and contract support both, but the detail
 * screen renders neither), attaching a bill, the advanced-filters sheet, and the passport
 * link on the share sheet. Adding a test that reached those through anything but the UI would
 * be testing the test, not the product.
 */
@RunWith(AndroidJUnit4::class)
class ServiceLogEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start every test from a set-up device with an empty log.
     *
     * The activity is recreated because the rule launches it before this runs, so it may have
     * already read a previous test's data and resolved a different start destination.
     */
    @Before
    fun startFromASetUpDeviceWithNoServices() {
        resetOwnerData()
        seedOnboardedOwner()
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------------ The list ------------------------------ */

    @Test
    fun anEmptyLogOffersBothWaysToStartOne() {
        rule.openServiceLog()

        rule.awaitText(LogCopy.EMPTY_TITLE)
        // The scan is the product's North Star, so it leads; typing it in is always offered
        // beside it, because a bill is not always to hand.
        rule.onNodeWithText(LogCopy.EMPTY_SCAN).assertIsDisplayed()
        rule.onNodeWithText(LogCopy.EMPTY_MANUAL).performClick()

        rule.awaitText(LogCopy.FORM_TITLE_ADD)
    }

    @Test
    fun aSeededHistoryShowsItsRowsAndItsTotals() {
        seedServiceHistory()
        rule.openServiceLog()

        // Every row, and the header the three of them add up to (6,400 + 4,800 + 3,200).
        rule.awaitText(LogFixtures.FAIR_WORKSHOP)
        rule.onNodeWithText(LogFixtures.FLAGGED_WORKSHOP).assertIsDisplayed()
        rule.onNodeWithText(LogFixtures.SELF_REPORTED_WORKSHOP).assertIsDisplayed()
        rule.onNodeWithText(LogCopy.TOTAL_SPENT).assertIsDisplayed()
        rule.onNodeWithText("Rs. 14,400").assertIsDisplayed()

        // The badges are the trust model on screen: a bill earns Verified, and the entry
        // without one is asked for one rather than judged.
        rule.onNodeWithText(LogCopy.ADD_BILL_TO_VERIFY).assertIsDisplayed()
        rule.onNodeWithText(LogCopy.over(LogFixtures.FLAGGED_OVER)).assertIsDisplayed()
        rule.onNodeWithText(LogCopy.FAIR_PRICE).assertIsDisplayed()
    }

    @Test
    fun theFiltersNarrowTheListToWhatEachChipCounts() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.FAIR_WORKSHOP)

        // Counts are over the whole log, not the visible rows — two bills, one of them over.
        rule.onNodeWithText("Verified · 2").assertIsDisplayed()
        rule.onNodeWithText("Flagged · 1").performClick()

        // Flagged is the one entry judged over; the fair and self-reported ones go.
        rule.awaitGone(LogFixtures.FAIR_WORKSHOP)
        rule.onNodeWithText(LogFixtures.FLAGGED_WORKSHOP).assertIsDisplayed()
        rule.onNodeWithText(LogFixtures.SELF_REPORTED_WORKSHOP).assertDoesNotExist()

        // Verified keeps both bill-backed entries and drops the self-reported one.
        rule.onNodeWithText("Verified · 2").performClick()
        rule.awaitText(LogFixtures.FAIR_WORKSHOP)
        rule.onNodeWithText(LogFixtures.SELF_REPORTED_WORKSHOP).assertDoesNotExist()

        rule.onNodeWithText(LogCopy.FILTER_ALL).performClick()
        rule.awaitText(LogFixtures.SELF_REPORTED_WORKSHOP)
    }

    @Test
    fun theDirectionToggleSwapsTheListWithoutLosingTheCar() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.FAIR_WORKSHOP)

        rule.onNodeWithText(LogCopy.TIMELINE).performClick()

        // The timeline shows the same services, keyed by odometer rather than by spend — and
        // the filter chips belong to the ledger, so they go with it.
        rule.awaitText("54,000 km")
        rule.onNodeWithText(LogCopy.FILTER_ALL).assertDoesNotExist()

        rule.onNodeWithText(LogCopy.LEDGER).performClick()
        rule.awaitText(LogCopy.FILTER_ALL)
    }

    /* ------------------------------ Adding a service ------------------------------ */

    @Test
    fun addingAServiceStoresItAndTheListShowsIt() {
        rule.openServiceLog()
        rule.awaitText(LogCopy.EMPTY_TITLE)
        rule.onNodeWithText(LogCopy.EMPTY_MANUAL).performClick()
        rule.awaitText(LogCopy.FORM_TITLE_ADD)

        rule.fillServiceForm(
            workshop = LogFixtures.NEW_WORKSHOP,
            odometer = LogFixtures.NEW_ODOMETER,
            amount = LogFixtures.NEW_AMOUNT,
            date = LogFixtures.NEW_DATE_TYPED,
        )
        rule.saveServiceLog()

        // The form pops itself and the list re-reads the repository it was already observing —
        // nothing refreshes it by hand.
        rule.awaitText(LogFixtures.NEW_WORKSHOP)
        rule.onNodeWithText(LogCopy.LIST_TITLE).assertIsDisplayed()
        // Shown twice, and rightly: on the card, and as the header total this one entry now
        // makes up on its own.
        rule.awaitText(LogFixtures.NEW_AMOUNT_SHOWN)
        rule.onNodeWithText(LogFixtures.NEW_DATE_SHOWN, substring = true).assertIsDisplayed()

        // A manually logged service has no bill, so it is self-reported and unjudged.
        rule.onNodeWithText(LogCopy.ADD_BILL_TO_VERIFY).assertIsDisplayed()
    }

    @Test
    fun theFormWillNotSaveWithoutTheOdometer() {
        rule.openServiceLog()
        rule.openAddForm()

        // The field opens at the car's latest known reading, so Save is offered right away.
        rule.awaitText(LogFixtures.CAR_ODOMETER.toString())
        rule.onNodeWithTag(ServiceLogTestTags.SAVE).assertIsEnabled()

        // Odo's core number: without it there is no ₹/km, no health score and no anomaly
        // check, so clearing the field closes Save.
        rule.replaceInto(ServiceLogTestTags.ODOMETER_FIELD, "")
        rule.onNodeWithTag(ServiceLogTestTags.SAVE).assertIsNotEnabled()
    }

    @Test
    fun aMissingDateIsReportedOnTheDateFieldAndClearsWhenAnswered() {
        rule.openServiceLog()
        rule.openAddForm()

        // Save is offered — the odometer is answered — but the domain still refuses, and the
        // refusal has to land on the field that owns it rather than as a general failure.
        rule.fillServiceForm(workshop = LogFixtures.NEW_WORKSHOP, odometer = LogFixtures.NEW_ODOMETER)
        rule.saveServiceLog()
        rule.awaitText(LogCopy.ERROR_DATE_REQUIRED)

        // Answering the field drops its error without another save attempt.
        rule.setServiceDate(LogFixtures.NEW_DATE_TYPED)
        rule.awaitGone(LogCopy.ERROR_DATE_REQUIRED)
        rule.onNodeWithText(LogFixtures.NEW_DATE_SHOWN).assertIsDisplayed()
    }

    @Test
    fun anOdometerAboveALaterReadingIsRefusedWithTheReadingItCrossed() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.FAIR_WORKSHOP)
        rule.openAddForm()

        // Backdated to before the flagged entry, but reading higher than it: the odometer
        // only counts up, so this is impossible however it is dated.
        rule.fillServiceForm(
            workshop = LogFixtures.NEW_WORKSHOP,
            odometer = "60000",
            date = "01012026",
        )
        rule.saveServiceLog()

        // Named with the reading it crossed, so the owner can see which entry disagrees.
        rule.awaitText(LogCopy.odometerAheadOf(LogFixtures.CROSSED_READING))
        rule.onNodeWithText(LogCopy.FORM_TITLE_ADD).assertIsDisplayed()
    }

    /* ------------------------------ One entry ------------------------------ */

    @Test
    fun aVerifiedOverchargeShowsItsProofItsVerdictAndBothActions() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.FLAGGED_WORKSHOP)
        rule.openEntry(LogFixtures.FLAGGED_ID)

        // A bill makes it proof at resale, and makes its price judgeable at all.
        rule.onNodeWithText(LogCopy.DETAIL_RESALE).assertIsDisplayed()
        rule.awaitText("Rs. 1,100 over the city average")
        // The evidence travels with the verdict — never a bare number (PRD: no false precision).
        rule.awaitText("Pune average is Rs. 3,700 — based on 240 verified bills.")

        rule.onNodeWithText(LogCopy.DETAIL_SHARE).assertIsDisplayed()
        rule.onNodeWithText(LogCopy.DETAIL_REPORT).assertIsDisplayed()
    }

    @Test
    fun aSelfReportedEntryOffersNeitherProofNorAReport() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.SELF_REPORTED_WORKSHOP)
        rule.openEntry(LogFixtures.SELF_REPORTED_ID)

        // No bill: nothing to show a buyer, and nothing the fairness pool may be asked about.
        rule.onNodeWithText(LogCopy.DETAIL_RESALE).assertDoesNotExist()
        rule.onNodeWithText(LogCopy.DETAIL_SHARE).assertDoesNotExist()
        rule.onNodeWithText(LogCopy.DETAIL_REPORT).assertDoesNotExist()
        rule.onNodeWithText(LogCopy.DETAIL_TOTAL_PAID).assertIsDisplayed()
    }

    @Test
    fun aFairEntryIsNotOfferedAReport() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.FAIR_WORKSHOP)
        rule.openEntry(LogFixtures.FAIR_ID)

        // Verified, so it is shareable proof — but the price was fair, so there is nothing
        // to report and the action is absent rather than disabled.
        rule.onNodeWithText(LogCopy.DETAIL_SHARE).assertIsDisplayed()
        rule.onNodeWithText(LogCopy.DETAIL_REPORT).assertDoesNotExist()
    }

    @Test
    fun anEntryThatIsNoLongerThereSaysSo() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.FLAGGED_WORKSHOP)
        rule.openEntry(LogFixtures.FLAGGED_ID)

        // Deleted from under an open screen — the entry keeps flowing while it is on show, so
        // the screen finds out rather than going on displaying something that is gone.
        softDeleteEntry(LogFixtures.FLAGGED_ID)
        rule.awaitText(LogCopy.DETAIL_NOT_FOUND)
    }

    /* ------------------------------ Reporting an overcharge ------------------------------ */

    @Test
    fun reportingAnOverchargeNeedsAReasonAndConfirmsWhenFiled() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.FLAGGED_WORKSHOP)
        rule.openEntry(LogFixtures.FLAGGED_ID)
        rule.onNodeWithText(LogCopy.DETAIL_REPORT).performClick()

        rule.awaitText(LogCopy.REPORT_TITLE)
        // The header restates what is being reported, using the same figure the list showed.
        rule.onNodeWithText(LogCopy.REPORT_QUESTION).assertIsDisplayed()

        // A report without a reason teaches the fairness pool nothing, so it cannot be filed.
        rule.onNodeWithText(LogCopy.REPORT_SUBMIT).assertIsNotEnabled()
        rule.onNodeWithText(LogCopy.REPORT_REASON_ABOVE_MARKET).performClick()
        rule.onNodeWithText(LogCopy.REPORT_SUBMIT).assertIsEnabled().performClick()

        rule.awaitText(LogCopy.REPORT_SUCCESS)
        rule.onNodeWithText(LogCopy.REPORT_DONE).performClick()

        // Done leaves the report behind and lands back on the entry it was about.
        rule.awaitText(LogCopy.DETAIL_TOTAL_PAID)
    }

    /* ------------------------------ Sharing the record ------------------------------ */

    @Test
    fun theShareSheetSummarisesHowMuchOfTheRecordIsProven() {
        seedServiceHistory()
        rule.openServiceLog()
        rule.awaitText(LogFixtures.FAIR_WORKSHOP)

        // Sharing belongs to the timeline direction — the resale-proof view.
        rule.onNodeWithText(LogCopy.TIMELINE).performClick()
        rule.awaitLabel(LogCopy.SHARE_RECORD)
        rule.onNodeWithLabel(LogCopy.SHARE_RECORD).performClick()

        rule.awaitText(LogCopy.shareSummary(LogFixtures.CAR_NAME, verified = 2, total = 3))
        rule.onNodeWithText(LogCopy.SHARE_PDF).assertIsDisplayed()

        // No passport link exists yet, so the sheet shows no link to copy rather than an
        // empty row or a URL that resolves to nothing.
        rule.onNodeWithText(LogCopy.SHARE_COPY).assertDoesNotExist()
    }
}
