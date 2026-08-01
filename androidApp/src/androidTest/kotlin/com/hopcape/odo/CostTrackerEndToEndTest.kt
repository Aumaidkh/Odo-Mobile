package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.feature.costtracker.presentation.CostTrackerTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every cost-tracker flow an owner can reach, driven against the real app: the real Koin
 * graph, the real SQLite database, the real navigation graph and the real ViewModels.
 *
 * The cost tracker is arithmetic over three sources — the car's odometer, its service logs
 * and a fuel price — so its failures live where no unit test looks: a window that reads the
 * wrong slice of history, a rate the screen keeps showing after the odometer moved, an
 * owner's own price that a seeded one quietly overrules.
 *
 * **What is seeded and why.** The car and its services are written straight to the
 * database: the cost tracker creates neither (a service is logged in the service log), so
 * seeding is how a car with history exists at all. Everything the feature owns — choosing a
 * window, setting and clearing a fuel rate — is driven by tapping.
 *
 * **The figures that are asserted as literals** are the ones that do not depend on Odo's
 * seeded fuel prices: the distances, the two maintenance rows, the maintenance-only rate,
 * and the rate computed from a price the test itself sets. The seeded price is reference
 * data that any release may correct, and a suite that pins it would fail on a correction
 * rather than on a bug.
 *
 * **Before running:** the fuel-price table is new and the local database still has no
 * migrations, so an install carrying an older database has no `fuel_price` table. Clear the
 * app's data (or uninstall) first.
 *
 * **What is deliberately not covered:** a failed database read (there is no way to break
 * SQLite from a test without breaking the whole app with it, and the ViewModel's failure
 * branch is unit-tested), and the seeded price's own value.
 */
@RunWith(AndroidJUnit4::class)
class CostTrackerEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start every test from a set-up device with a car, a year of history and a city.
     *
     * The activity is recreated because the rule launches it before this runs, so it may
     * have already read a previous test's data.
     */
    @Before
    fun startFromACarWithHistory() {
        resetCostTracker()
        seedCostOwner()
        seedCostHistory()
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------------ The figures ------------------------------ */

    @Test
    fun aYearOfDrivingHasARatePerKilometre() {
        rule.openCostTracker()

        rule.awaitText(CostCopy.PER_KM_LABEL)
        val rate = rule.costPerKm()
        assertNotNull("the screen should quote a rate for a year of driving", rate)
        assertTrue("a rate reads as rupees, got $rate", rate!!.startsWith("Rs. "))
    }

    @Test
    fun theDistanceIsMeasuredFromTheReadingBeforeTheWindow() {
        rule.openCostTracker()
        rule.awaitText(CostCopy.PER_KM_LABEL)

        // 42,000 now against 32,000 the last time the car was seen before the year began —
        // the older reading anchors it even though its own service is outside the window.
        rule.awaitCostText(CostFixtures.YEAR_DISTANCE_SHOWN)
    }

    @Test
    fun theBreakdownSplitsRepairsFromRoutineWork() {
        rule.openCostTracker()
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)

        rule.assertCategoryRowShows(SpendCategory.SERVICE, CostFixtures.SERVICE_SHOWN)
        rule.assertCategoryRowShows(SpendCategory.REPAIRS, CostFixtures.REPAIR_SHOWN)
        // Fuel is estimated for a car whose owner has a city, so it earns a row too.
        rule.assertCategoryRowShows(SpendCategory.FUEL, CostCopy.CAT_FUEL)
    }

    @Test
    fun theSummaryRepeatsWhatTheFiguresWereBuiltFrom() {
        rule.openCostTracker()
        rule.awaitCostText(CostCopy.SUMMARY)

        rule.scrollToCostText(CostCopy.TOTAL_SPENT)
        rule.onNodeWithText(CostCopy.TOTAL_SPENT).assertIsDisplayed()
        rule.scrollToCostText(CostCopy.AVG_MONTH)
        rule.onNodeWithText(CostCopy.AVG_MONTH).assertIsDisplayed()
        // The distance the figures were built on, repeated where an owner looks for it.
        rule.onNodeWithText(CostFixtures.YEAR_DISTANCE_SHOWN).assertExists()
    }

    @Test
    fun theTrendComparesWithTheYearBefore() {
        rule.openCostTracker()
        rule.awaitText(CostCopy.PER_KM_LABEL)

        // The car was driven in the previous year too, so there is something to compare to.
        rule.waitUntil(TIMEOUT) { rule.tagCount(CostTrackerTestTags.TREND_BADGE) == 1 }
    }

    /* ------------------------------ The windows ------------------------------ */

    @Test
    fun choosingAQuarterNarrowsTheFiguresToIt() {
        rule.openCostTracker()
        rule.awaitCostText(CostFixtures.YEAR_DISTANCE_SHOWN)

        rule.chooseCostPeriod(CostCopy.PERIOD_3M)

        // 42,000 against the 36,000 the car last read before the quarter began.
        rule.awaitCostText(CostFixtures.QUARTER_DISTANCE_SHOWN)
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.assertCategoryRowShows(SpendCategory.SERVICE, CostFixtures.SERVICE_SHOWN)
        // The repair is eight months old, so it is not this quarter's cost.
        rule.assertNoCategoryRow(SpendCategory.REPAIRS)
    }

    @Test
    fun theChosenWindowSurvivesGoingAwayAndComingBack() {
        rule.openCostTracker()
        rule.awaitCostText(CostFixtures.YEAR_DISTANCE_SHOWN)
        rule.chooseCostPeriod(CostCopy.PERIOD_3M)
        rule.awaitCostText(CostFixtures.QUARTER_DISTANCE_SHOWN)

        rule.onNodeWithText(GarageCopy.TAB).performClick()
        rule.awaitText(GarageCopy.TITLE)
        rule.returnToCostTracker()

        rule.awaitCostText(CostFixtures.QUARTER_DISTANCE_SHOWN)
    }

    /* ------------------------------ Nothing to go on ------------------------------ */

    @Test
    fun aCarWithNoHistoryIsToldWhyThereIsNoRate() {
        resetCostTracker()
        seedCostOwner()
        rule.activityRule.scenario.recreate()
        rule.openCostTracker()

        rule.awaitText(CostCopy.NO_RATE_TITLE)
        // The car has a reading, it just has not moved on it — so the screen asks for
        // kilometres rather than for a first reading.
        rule.onNodeWithText(CostCopy.NO_RATE_DISTANCE).assertExists()
        assertNull("a car that has not moved has no rate to quote", rule.costPerKm())
        assertEquals(0, rule.tagCount(CostTrackerTestTags.TREND_BADGE))
    }

    /* ------------------------------ The fuel estimate ------------------------------ */

    @Test
    fun theFuelHalfSaysItIsAnEstimateAndWhereItCameFrom() {
        rule.openCostTracker()
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)

        rule.awaitFuelNoteStartingWith(CostCopy.FUEL_NOTE_ESTIMATED_PREFIX)
        assertTrue(
            "the note should name the city it priced fuel in, got ${rule.fuelNote()}",
            rule.fuelNote().endsWith("in Pune"),
        )
    }

    @Test
    fun anOwnerWithNoCityGetsNoFuelAndIsToldSo() {
        resetCostTracker()
        seedCostOwner(city = null)
        seedCostHistory()
        rule.activityRule.scenario.recreate()
        rule.openCostTracker()

        // Rs. 13,000 of logged work over 10,000 km, and nothing invented on top of it.
        rule.awaitCostPerKm(CostFixtures.RATE_WITHOUT_FUEL)
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.assertNoCategoryRow(SpendCategory.FUEL)
        assertEquals(CostCopy.FUEL_NOTE_MISSING, rule.fuelNote())
    }

    /* ------------------------------ The owner's own rate ------------------------------ */

    @Test
    fun theSheetOpensOnThePriceInForce() {
        rule.openCostTracker()
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.openFuelRateSheet()

        // Prefilled with Odo's own figure, so a correction is an edit rather than a re-entry.
        rule.awaitText(CostFixtures.SEEDED_PUNE_PETROL)
        // Nothing of theirs is set yet, so there is nothing to drop.
        assertEquals(0, rule.textCount(CostCopy.RATE_CLEAR))
    }

    @Test
    fun aRateTheOwnerSetsRebuildsEveryFigure() {
        rule.openCostTracker()
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.openFuelRateSheet()

        rule.saveFuelRate(CostFixtures.OWNER_RATE_TYPED)

        // The sheet closes itself and the screen behind it is already on the new price.
        rule.awaitGone(CostCopy.RATE_TITLE)
        rule.awaitCostPerKm(CostFixtures.RATE_WITH_OWNER_FUEL)
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.awaitFuelNoteStartingWith(CostCopy.FUEL_NOTE_OWNER_PREFIX)
        assertEquals(1, ownerRateCount())
    }

    @Test
    fun aRateThatCannotBeAPumpPriceIsRefused() {
        rule.openCostTracker()
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.openFuelRateSheet()

        // Rs. 9,999 a litre — a decimal point in the wrong place.
        rule.saveFuelRate("9999")

        rule.awaitText(CostCopy.RATE_ERROR_RANGE)
        rule.onNodeWithText(CostCopy.RATE_TITLE).assertIsDisplayed()
        assertEquals("nothing should have been stored", 0, ownerRateCount().toInt())
    }

    @Test
    fun textThatIsNotAPriceIsRefusedTheSameWay() {
        rule.openCostTracker()
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.openFuelRateSheet()

        rule.saveFuelRate("abc")

        rule.awaitText(CostCopy.RATE_ERROR_RANGE)
        assertEquals(0, ownerRateCount().toInt())
    }

    @Test
    fun droppingTheirRateGoesBackToOdosEstimate() {
        rule.openCostTracker()
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.openFuelRateSheet()
        rule.saveFuelRate(CostFixtures.OWNER_RATE_TYPED)
        // The second visit waits for the first sheet to actually be gone: reopening while it
        // is still animating out lands the tap on a sheet that is on its way off screen.
        rule.awaitGone(CostCopy.RATE_TITLE)
        rule.awaitCostPerKm(CostFixtures.RATE_WITH_OWNER_FUEL)

        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.openFuelRateSheet()
        rule.clearFuelRate()

        rule.waitUntil(TIMEOUT) { ownerRateCount() == 0L }
        rule.awaitGone(CostCopy.RATE_TITLE)
        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.awaitFuelNoteStartingWith(CostCopy.FUEL_NOTE_ESTIMATED_PREFIX)
    }

    /**
     * The rate is not tied to a city on purpose: an owner who never set one has no other way
     * to get a fuel figure at all.
     */
    @Test
    fun anOwnerWithNoCityCanStillStateTheirRate() {
        resetCostTracker()
        seedCostOwner(city = null)
        seedCostHistory()
        rule.activityRule.scenario.recreate()
        rule.openCostTracker()
        rule.awaitCostPerKm(CostFixtures.RATE_WITHOUT_FUEL)

        rule.scrollToCostText(CostCopy.WHERE_IT_GOES)
        rule.openFuelRateSheet()
        rule.saveFuelRate(CostFixtures.OWNER_RATE_TYPED)

        rule.awaitGone(CostCopy.RATE_TITLE)
        rule.awaitCostPerKm(CostFixtures.RATE_WITH_OWNER_FUEL)
    }

    /* ------------------------------ Across features ------------------------------ */

    /**
     * The car's own reading is changed from the garage, not from a service log. The cost
     * tracker observes the readings rather than reading them once, so the distance it is
     * built on has to move with it.
     */
    @Test
    fun anOdometerUpdateInTheGarageMovesTheDistance() {
        rule.openCostTracker()
        rule.awaitCostText(CostFixtures.YEAR_DISTANCE_SHOWN)

        rule.openGarage()
        rule.openOdometerSheet()
        rule.saveOdometer(CostFixtures.GARAGE_READING)
        rule.awaitGone(GarageCopy.ODOMETER_SAVE)

        rule.returnToCostTracker()
        rule.awaitCostText(CostFixtures.GARAGE_DISTANCE_SHOWN)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
