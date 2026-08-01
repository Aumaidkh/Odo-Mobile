package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every health-score flow an owner can reach, driven against the real app: the real Koin
 * graph, the real SQLite database, the real navigation graph and the real ViewModels.
 *
 * The score is rules over four sources — services, papers, odometer readings and fairness
 * verdicts — and its failures live where no unit test looks: a screen that keeps yesterday's
 * number after a document is added, a delta measured against last week, a snapshot written
 * on every read until the history is worthless.
 *
 * **What is seeded and why.** The car, its services and its papers are written straight to
 * the database: the health score creates none of them, so seeding is how a car with a
 * history exists at all. Every seeded service carries a bill photo, because a Verified entry
 * is what the history factor pays for and the scanner that would attach one is M2.
 *
 * **The scores are asserted as literals.** They are arithmetic over this seed and nothing
 * else — no seeded reference data, no network — so a change to the point rules is meant to
 * fail here. That is the job: `HealthScoreCalculator.RULES_VERSION` moving is the reminder
 * to come and re-read [HealthFixtures].
 *
 * **Before running:** `health_scores` is a new table and the local database still has no
 * migrations, so an install carrying an older database does not have it. Clear the app's
 * data (or uninstall) first.
 *
 * **What is deliberately not covered:** a failed database read (there is no way to break
 * SQLite from a test without breaking the whole app with it, and the ViewModel's failure
 * branch is unit-tested), and the cost-fairness factor above zero (it needs a stored
 * fairness verdict, which needs the city pool the MVP has no data for).
 */
@RunWith(AndroidJUnit4::class)
class HealthScoreEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start every test from a set-up device with a car and no score history.
     *
     * Pro is set explicitly rather than left to the shipped stub, because a test that
     * overrides it changes a definition that outlives it.
     */
    @Before
    fun startFromACarWithNoScoreYet() {
        resetHealthScore()
        setProEntitlement(isPro = true)
        seedHealthOwner()
    }

    @After
    fun restoreTheShippedEntitlement() {
        setProEntitlement(isPro = true)
    }

    /* ------------------------------ The score ------------------------------ */

    @Test
    fun homesBreakdownLinkOpensTheCarsOwnScore() {
        startWellKeptCar()

        rule.openHealthScore()

        rule.awaitHealthScore(HealthFixtures.SCORE)
        rule.onNodeWithText(HealthCopy.BAND_GOOD).assertIsDisplayed()
    }

    @Test
    fun theBreakdownShowsEveryFactorTheScoreWasBuiltFrom() {
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        rule.scrollToHealthText(HealthCopy.BREAKDOWN)
        rule.assertFactorRowShows(HealthFactorKind.MAINTENANCE, HealthCopy.FACTOR_MAINTENANCE)
        rule.assertFactorRowShows(
            HealthFactorKind.MAINTENANCE,
            HealthCopy.factorScore(HealthFixtures.MAINTENANCE_PTS, 35),
        )
        rule.assertFactorRowShows(
            HealthFactorKind.DOCUMENTATION,
            HealthCopy.factorScore(HealthFixtures.DOCUMENTATION_PTS, 30),
        )
        // Nothing has ever been benchmarked, and an unchecked bill earns nothing.
        rule.assertFactorRowShows(
            HealthFactorKind.COST_EFFICIENCY,
            HealthCopy.factorScore(HealthFixtures.COST_PTS, 20),
        )
        rule.assertFactorRowShows(
            HealthFactorKind.HISTORY,
            HealthCopy.factorScore(HealthFixtures.HISTORY_PTS, 15),
        )
    }

    @Test
    fun theBiggestOpportunityIsTheFactorWithTheMostPointsLeft() {
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        rule.scrollToHealthText(HealthCopy.OPPORTUNITY_COST)
        rule.onNodeWithText(HealthCopy.opportunityLabel(20)).assertIsDisplayed()
        rule.onNodeWithText(HealthCopy.OPPORTUNITY_COST).assertIsDisplayed()
    }

    @Test
    fun aLapsedPolicyCostsItsPointsAndTheBandWithThem() {
        startWellKeptCar(insuranceExpiresInDays = -10)
        rule.openHealthScore()

        rule.awaitHealthScore(HealthFixtures.SCORE_WITHOUT_INSURANCE)
        rule.onNodeWithText(HealthCopy.BAND_FAIR).assertIsDisplayed()
        rule.scrollToHealthText(HealthCopy.BREAKDOWN)
        rule.assertFactorRowShows(
            HealthFactorKind.DOCUMENTATION,
            HealthCopy.factorScore(HealthFixtures.DOCUMENTATION_WITHOUT_INSURANCE, 30),
        )
    }

    @Test
    fun aPaperThatWasNeverUploadedEarnsNothingEither() {
        startWellKeptCar(withPuc = false)
        rule.openHealthScore()

        rule.awaitHealthScore(HealthFixtures.SCORE_WITHOUT_PUC)
        rule.scrollToHealthText(HealthCopy.BREAKDOWN)
        rule.assertFactorRowShows(HealthFactorKind.DOCUMENTATION, HealthCopy.factorScore(20, 30))
    }

    @Test
    fun drivingPastTheServiceIntervalCostsMaintenancePoints() {
        startWellKeptCar()
        // The garage moves the car's own reading; no service is logged against it.
        driveCarTo(HealthFixtures.OVERDUE_ODOMETER_KM)
        rule.openHealthScore()

        rule.awaitHealthScore(HealthFixtures.SCORE_WHEN_OVERDUE)
        rule.scrollToHealthText(HealthCopy.BREAKDOWN)
        rule.assertFactorRowShows(
            HealthFactorKind.MAINTENANCE,
            HealthCopy.factorScore(HealthFixtures.MAINTENANCE_PTS_WHEN_OVERDUE, 35),
        )
    }

    @Test
    fun aCarWithNothingLoggedScoresNothingAndIsToldHowToStart() {
        startEmptyCar()
        rule.openHealthScore()

        // Zero because nothing is proven, not because the car is in bad shape — so the
        // screen offers a way forward instead of a movement.
        rule.awaitHealthScore(0)
        rule.onNodeWithText(HealthCopy.BAND_NEEDS_CARE).assertIsDisplayed()
        rule.onNodeWithText(HealthCopy.NOTHING_LOGGED).assertIsDisplayed()
    }

    @Test
    fun addingADocumentRescoresWithoutLeavingTheScreen() {
        startWellKeptCar(withPuc = false)
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE_WITHOUT_PUC)

        addPucNow()

        rule.awaitHealthScore(HealthFixtures.SCORE)
    }

    /* ------------------------------ The month delta ------------------------------ */

    @Test
    fun aScoreFromAMonthAgoShowsWhatChanged() {
        seedHealthSnapshot(
            daysAgo = HealthFixtures.BASELINE_SNAPSHOT_DAYS_AGO,
            score = HealthFixtures.LOWER_SNAPSHOT_SCORE,
        )
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        rule.onNodeWithText(HealthCopy.deltaUp(HealthFixtures.DELTA_POINTS)).assertIsDisplayed()
    }

    @Test
    fun aScoreThatFellSaysToTakeAction() {
        seedHealthSnapshot(
            daysAgo = HealthFixtures.BASELINE_SNAPSHOT_DAYS_AGO,
            score = HealthFixtures.HIGHER_SNAPSHOT_SCORE,
        )
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        rule.onNodeWithText(HealthCopy.deltaDown(HealthFixtures.DELTA_POINTS)).assertIsDisplayed()
    }

    @Test
    fun aScoreFromLastWeekIsNotAMonthAgo() {
        seedHealthSnapshot(daysAgo = 5, score = HealthFixtures.LOWER_SNAPSHOT_SCORE)
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        // Too recent to be "this month": the line is hidden rather than quoting a week's
        // movement as a month's.
        assertEquals(0, rule.tagCount(HealthScoreTestTags.NOTE))
    }

    /* ------------------------------ The history it keeps ------------------------------ */

    @Test
    fun openingTheScoreKeepsIt() {
        startWellKeptCar()
        assertEquals(0L, healthSnapshotCount())

        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)
        rule.waitUntil(SNAPSHOT_TIMEOUT_MILLIS) { healthSnapshotCount() == 1L }

        assertEquals(HealthFixtures.SCORE.toLong(), latestSnapshotScore())
    }

    @Test
    fun aScoreThatHasNotMovedIsNotKeptTwice() {
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)
        rule.waitUntil(SNAPSHOT_TIMEOUT_MILLIS) { healthSnapshotCount() == 1L }

        rule.leaveHealthScore()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)
        rule.waitForIdle()

        // The same score again is not history, it is noise — and the "what changed" push
        // would have nothing to point at.
        assertEquals(1L, healthSnapshotCount())
    }

    /* ------------------------------ The explainer ------------------------------ */

    @Test
    fun theExplainerListsTheWeightsAndTheBands() {
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        rule.openScoreInfo()

        // The sheet sits over the breakdown, so a factor's name is on screen twice — once
        // in the card behind it and once in the weights it lists.
        assertEquals(2, rule.textCount(HealthCopy.FACTOR_HISTORY))
        rule.onNodeWithText(HealthCopy.weight(35)).assertIsDisplayed()
        rule.onNodeWithText(HealthCopy.weight(30)).assertIsDisplayed()
        rule.onNodeWithText(HealthCopy.weight(20)).assertIsDisplayed()
        rule.onNodeWithText(HealthCopy.weight(15)).assertIsDisplayed()
        // The PRD's four bands, which the dial and the label read from too. The sheet
        // scrolls, so the last of them is reached the way an owner reaches it.
        rule.onNodeWithText(HealthCopy.INFO_RANGE_EXCELLENT).assertIsDisplayed()
        rule.onNodeWithText(HealthCopy.INFO_RANGE_GOOD).assertIsDisplayed()
        rule.scrollToHealthText(HealthCopy.INFO_RANGE_NEEDS_CARE)
        rule.onNodeWithText(HealthCopy.INFO_RANGE_FAIR).assertIsDisplayed()
        rule.onNodeWithText(HealthCopy.INFO_RANGE_NEEDS_CARE).assertIsDisplayed()
    }

    @Test
    fun theExplainerCloses() {
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        rule.openScoreInfo()
        rule.dismissScoreInfo()

        // Back on the score, not somewhere else.
        rule.onNodeWithText(HealthCopy.TITLE).assertIsDisplayed()
    }

    /* ------------------------------ The Pro gate ------------------------------ */

    @Test
    fun proOwnersSeeTheWholeBreakdownAndNoPaywall() {
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        assertEquals(0, rule.tagCount(HealthScoreTestTags.PAYWALL))
        assertEquals(1, rule.tagCount(HealthScoreTestTags.OPPORTUNITY))
    }

    @Test
    fun freeOwnersGetThePaywallInsteadOfTheNudge() {
        setProEntitlement(isPro = false)
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        // The score itself is never locked — only the breakdown behind it is.
        rule.scrollToHealthText(HealthCopy.PAYWALL_TITLE)
        rule.onNodeWithText(HealthCopy.PAYWALL_TITLE).assertIsDisplayed()
        assertEquals(0, rule.tagCount(HealthScoreTestTags.OPPORTUNITY))
    }

    @Test
    fun unlockOpensThePaywall() {
        setProEntitlement(isPro = false)
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        rule.tapUnlock()

        rule.awaitText(HealthCopy.PAYWALL_SCREEN_HEADLINE)
    }

    /* ------------------------------ Getting back ------------------------------ */

    @Test
    fun backReturnsToHome() {
        startWellKeptCar()
        rule.openHealthScore()
        rule.awaitHealthScore(HealthFixtures.SCORE)

        rule.leaveHealthScore()

        rule.awaitText(HealthCopy.HOME_SEE_BREAKDOWN)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    /**
     * A car with two bill-backed services and its three papers on file, then a fresh
     * activity: the rule launches one before the seed lands, so it may still be showing a
     * previous test's data.
     */
    private fun startWellKeptCar(
        withPuc: Boolean = true,
        insuranceExpiresInDays: Long = HealthFixtures.INSURANCE_DAYS_LEFT,
    ) {
        seedHealthHistory()
        seedHealthDocuments(withPuc = withPuc, insuranceExpiresInDays = insuranceExpiresInDays)
        rule.activityRule.scenario.recreate()
    }

    /** A car added today: nothing logged, nothing uploaded, one baseline reading. */
    private fun startEmptyCar() {
        rule.activityRule.scenario.recreate()
    }

    private companion object {
        /** The snapshot is written just after the score lands, on the same local write path. */
        const val SNAPSHOT_TIMEOUT_MILLIS = 5_000L
    }
}
