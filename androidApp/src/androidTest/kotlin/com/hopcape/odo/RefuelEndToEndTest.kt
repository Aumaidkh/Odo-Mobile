package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTestTags
import com.hopcape.odo.feature.timeline.presentation.TimelineTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smart refuel's funnel, driven against the real app: the real Koin graph, the real SQLite
 * database, the real navigation graph and the real ViewModels.
 *
 * The claim this suite exists to check is the feature's whole reason to exist — that logging
 * a fill costs the owner one number. That claim spans three modules (the dashboard's shortcut,
 * refuel's form and confirm step, the fill in `:core:data`) and cannot be checked in any one
 * of them: a prefill that silently produces nothing, a confirm step that writes the number the
 * owner did not type, or a rate carried from the wrong place all look fine in isolation.
 *
 * **What is seeded and why.** The car, its service and its previous fill are written straight
 * to the database — refuel creates none of them, and a prefill with no history to prefill from
 * is a different screen. The owner's fuel rate is seeded too, so the litres the screen computes
 * do not depend on Odo's seeded city prices, which are reference data any release may correct.
 *
 * **What is deliberately not covered.** The pump-display reader and the payment-notification
 * listener: one needs a camera pointed at a lit pump, the other a permission this build does
 * not declare (see `FeatureConfig.refuelDetectEnabled`). Their parsers are unit-tested, and
 * the funnel they hand to is exactly what these tests drive.
 *
 * **Before running:** the database gained a migration for `entry_source`, so an install
 * carrying an older database upgrades on first launch. Clear the app's data if a run looks
 * confused about which columns exist.
 */
@RunWith(AndroidJUnit4::class)
class RefuelEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start every test from a set-up device with a car, a reading and one previous fill.
     *
     * The activity is recreated because the rule launches it before this runs, so it may
     * have already read a previous test's data.
     */
    @Before
    fun startFromACarWithAFillBehindIt() {
        resetRefuel()
        seedRefuelOwner()
        seedRefuelHistory()
        seedRefuelOwnerRate()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test
    fun theOwnerTypesOneNumberAndTheFillIsLogged() {
        openLogFill()

        // Everything except the amount is already filled in — that is the whole claim.
        rule.onNodeWithText(RefuelCopy.PREFILL_NOTE).assertIsDisplayed()
        rule.onNodeWithText(RefuelFixtures.LAST_FILL_STATION).assertIsDisplayed()
        rule.onNodeWithText(RefuelCopy.STATION_NOTE).assertIsDisplayed()
        rule.onNodeWithText(RefuelCopy.RATE_CARRIED).assertIsDisplayed()

        typeAmountAndContinue()

        rule.onNodeWithText(RefuelCopy.CONFIRM_TITLE).assertIsDisplayed()
        rule.onNodeWithTag(refuelTag("confirm_button")).performScrollTo().performClick()
        rule.waitForIdle()

        val fills = refuelFills()
        assertEquals("the fill should have been written", 2, fills.size)
        val logged = fills.first { it.id != RefuelFixtures.LAST_FILL_ID }
        assertEquals(150_000L, logged.amountPaise)
        // Rs. 1,500 at the owner's Rs. 100 a litre is exactly 15 litres, worked out for them.
        assertEquals(15_000L, logged.quantityMilli)
        assertEquals("PREFILLED", logged.entrySource)
        assertEquals(RefuelFixtures.LAST_FILL_STATION, logged.stationName)
    }

    @Test
    fun theOdometerIsPredictedAndSaidToBeAPrediction() {
        openLogFill()

        // 3,000 km over the forty days between the baseline and the service is 75 a day, and
        // the service was twenty days ago — so the drum opens ahead of the last real reading.
        rule.onNodeWithText(RefuelCopy.ODOMETER_PREDICTED).assertIsDisplayed()

        typeAmountAndContinue()

        // And the confirm step says so again, louder, because this is the number the owner is
        // being asked to agree to.
        rule.onNodeWithText(RefuelCopy.ODOMETER_WARNING).assertIsDisplayed()

        rule.onNodeWithTag(refuelTag("confirm_button")).performScrollTo().performClick()
        rule.waitForIdle()

        val logged = refuelFills().first { it.id != RefuelFixtures.LAST_FILL_ID }
        assertTrue(
            "a predicted odometer should sit ahead of the last recorded reading",
            (logged.odometerKm ?: 0) > RefuelFixtures.SERVICE_KM,
        )
    }

    @Test
    fun theSuccessScreenReportsWhatWasActuallyStored() {
        openLogFill()
        typeAmountAndContinue()
        rule.onNodeWithTag(refuelTag("confirm_button")).performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText(RefuelCopy.LOGGED_TITLE).assertIsDisplayed()
        rule.onNodeWithText(RefuelCopy.LOGGED_FUEL_ADDED).assertIsDisplayed()
        // The channel is on the record, not just in the analytics.
        rule.onNodeWithText(RefuelCopy.SOURCE_PREFILLED).assertIsDisplayed()
    }

    @Test
    fun rejectingACaptureWritesNothing() {
        openLogFill()
        typeAmountAndContinue()

        rule.onNodeWithText(RefuelCopy.CONFIRM_REJECT).performScrollTo().performClick()
        rule.waitForIdle()

        // Only the seeded fill remains: "this wasn't fuel" has to mean nothing was recorded.
        assertEquals(listOf(RefuelFixtures.LAST_FILL_ID), refuelFills().map { it.id })
    }

    @Test
    fun aLoggedFillShowsUpOnTheTimeline() {
        openLogFill()
        typeAmountAndContinue()
        rule.onNodeWithTag(refuelTag("confirm_button")).performScrollTo().performClick()
        rule.waitForIdle()

        // The success screen's own way through, which is how an owner would get there.
        rule.onNodeWithText(RefuelCopy.LOGGED_VIEW_TIMELINE).performScrollTo().performClick()
        rule.waitForIdle()

        // The row states the tank, not a service: 15 litres at the station carried forward.
        rule.onNodeWithTag(TimelineTestTags.FUEL_ROW, useUnmergedTree = true)
            .assertIsDisplayed()
        rule.onNodeWithText(
            "${RefuelFixtures.EXPECTED_LITRES} L at ${RefuelFixtures.LAST_FILL_STATION} · Rs. 1,500",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun theScannerOffersThePumpMode() {
        // The capture channel that works in every market, reachable from the form itself.
        openLogFill()

        rule.onNodeWithText(RefuelCopy.SCAN_PUMP_CHIP).performScrollTo().assertIsDisplayed()
    }

    private fun openLogFill() {
        rule.onNodeWithTag(HomeTestTags.LOG_FILL_BUTTON).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(RefuelCopy.LOG_TITLE).assertIsDisplayed()
    }

    private fun typeAmountAndContinue() {
        rule.onNodeWithTag(refuelTag("log_amount_field"))
            .performScrollTo()
            .performTextInput(RefuelFixtures.AMOUNT_TYPED)
        rule.waitForIdle()
        rule.onNodeWithTag(refuelTag("log_done_button")).performScrollTo().performClick()
        rule.waitForIdle()
    }

    /**
     * Refuel's test tags, by their literal values.
     *
     * The feature keeps `RefuelTestTags` internal, like every other feature's, and this module
     * is not inside it. Naming them here is the same trade the copy constants make: a rename
     * on the other side fails this suite, which is where it should fail.
     */
    private fun refuelTag(name: String): String = "refuel_$name"
}
