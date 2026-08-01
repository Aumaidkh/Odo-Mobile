package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.feature.timeline.presentation.TimelineTestTags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every timeline flow an owner can reach, driven against the real app: the real Koin graph,
 * the real SQLite database, the real navigation graph and the real ViewModels.
 *
 * The feed is four sources merged — services, documents, score snapshots and the car itself
 * — and its failures live where no unit test looks: a tab that keeps yesterday's rows after a
 * service is logged, a filter that narrows the feed but not the counts beside it, a card that
 * opens someone else's entry.
 *
 * **What is seeded and why.** The car, its services, its papers and its scores are written
 * straight to the database: the timeline creates none of them, it only shows them. The
 * flagged entry carries a stored fairness verdict because the check that would produce one
 * needs a city pool the MVP has no data for, and the bill photos are seeded because the
 * scanner that would attach one is M2.
 *
 * **Before running:** the local database still has no migrations, so an install carrying an
 * older database has no `health_scores` table. Clear the app's data (or uninstall) first.
 *
 * **What is deliberately not covered:** a failed database read (there is no way to break
 * SQLite from a test without breaking the whole app with it, and the ViewModel's failure
 * branch is unit-tested), and the score events' rule-version guard (it needs two releases'
 * worth of rules — the builder's own tests cover it).
 */
@RunWith(AndroidJUnit4::class)
class TimelineEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    companion object {
        /**
         * The car exists before anything launches an activity.
         *
         * Where the app opens is read once per launch, and the rule starts the activity
         * before `@Before` runs — so seeding the profile per test would leave the first test
         * of the class driving the welcome carousel. Each test then clears only the events.
         */
        @JvmStatic
        @BeforeClass
        fun seedTheCarOnce() {
            resetTimeline()
            seedTimelineOwner()
        }
    }

    /** Start every test from a car with nothing logged against it. */
    @Before
    fun startFromACarWithNoHistory() {
        clearTimelineData()
        resetTimelineFilter()
    }

    /* ------------------------------ The feed ------------------------------ */

    @Test
    fun theTabShowsEveryKindOfEventTheCarHas() {
        startCarWithAHistory()

        rule.openTimeline()

        // Scrolled to rather than counted: the feed is lazy, so a row below the fold is not
        // composed and counting would only ever see the top of the record.
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.SELF_REPORTED_ID))
        rule.awaitRow(TimelineTestTags.SCORE_ROW)
        rule.awaitRow(TimelineTestTags.DOCUMENT_ROW_PREFIX)
        rule.awaitRow(TimelineTestTags.MILESTONE_ROW)
    }

    @Test
    fun theHeaderCountsTheWholeRecord() {
        startCarWithAHistory()

        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        rule.assertSubtitle(
            TimelineCopy.subtitle(
                car = TimelineFixtures.CAR_NAME,
                events = TimelineFixtures.TOTAL_EVENTS,
                sinceYear = TimelineFixtures.CAR_ADDED_YEAR,
            ),
        )
    }

    @Test
    fun aBillBackedEntryReadsVerifiedAndAnUnbackedOneOffersToFixThat() {
        startCarWithAHistory()

        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        rule.assertTimelineServiceShows(TimelineFixtures.VERIFIED_ID, TimelineCopy.VERIFIED)
        rule.assertTimelineServiceShows(TimelineFixtures.SELF_REPORTED_ID, TimelineCopy.SELF_REPORTED)
        rule.assertTimelineServiceShows(TimelineFixtures.SELF_REPORTED_ID, TimelineCopy.ADD_BILL)
    }

    @Test
    fun anOverchargedEntryShowsWhatItWasOverBy() {
        startCarWithAHistory()

        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.FLAGGED_ID))

        // The verdict frozen when the entry was checked, not a fresh one.
        rule.assertTimelineServiceShows(
            TimelineFixtures.FLAGGED_ID,
            TimelineCopy.flaggedOver(TimelineFixtures.FLAGGED_OVER),
        )
    }

    @Test
    fun theServiceCardIsTitledByWhatWasDone() {
        startCarWithAHistory()

        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        rule.assertTimelineServiceShows(TimelineFixtures.VERIFIED_ID, "Oil change + filter")
    }

    @Test
    fun theFirstDocumentOfATypeIsAddedAndTheNextIsARenewal() {
        startCarWithAHistory()

        rule.openTimeline()

        // Two policies: last year's was the first, this year's replaced it.
        rule.awaitRow(TimelineTestTags.documentRow("PUC"))
        rule.onNodeWithText(TimelineCopy.documentAdded("PUC", "30 Nov 2026")).assertIsDisplayed()
        rule.awaitRow(TimelineTestTags.documentRow("INSURANCE"))
        rule.onNodeWithText(TimelineCopy.documentRenewed("Insurance", "1 Jun 2027")).assertIsDisplayed()
    }

    @Test
    fun aScoreThatMovedReadsAsTheMoveItMade() {
        startCarWithAHistory()

        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.SCORE_ROW)

        rule.onNodeWithText(
            TimelineCopy.scoreRose(TimelineFixtures.SCORE_BEFORE, TimelineFixtures.SCORE_AFTER),
        ).assertIsDisplayed()
    }

    @Test
    fun aDayOfScoreMovesIsOneRowNotThree() {
        // An afternoon of uploading documents: three scores taken on one day, plus the one
        // the day opened from. Nothing else is seeded, so the whole feed is on screen and
        // the rows can honestly be counted.
        seedTimelineScores(beforeDaysAgo = 2, afterDaysAgo = 1, before = 62, after = 66)
        seedTimelineScores(beforeDaysAgo = 1, afterDaysAgo = 1, before = 68, after = 74, idPrefix = "same-day")

        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.SCORE_ROW)

        assertEquals(1, rule.rowCount(TimelineTestTags.SCORE_ROW))
    }

    @Test
    fun theMilestoneIsTheOldestThingOnTheFeed() {
        startCarWithAHistory()

        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.MILESTONE_ROW)

        rule.onNodeWithText(TimelineCopy.milestone(TimelineFixtures.CAR_NAME)).assertIsDisplayed()
    }

    /* ------------------------------ Living data ------------------------------ */

    @Test
    fun aServiceLoggedWhileTheTabIsOpenAppearsOnIt() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        addServiceNow(
            id = "timeline-log-new",
            date = "2026-07-30",
            odometerKm = 55_000,
            amountPaise = 150_000L,
            notes = "Air filter",
        )

        rule.awaitRow(TimelineTestTags.serviceRow("timeline-log-new"))
    }

    /* ------------------------------ The new user ------------------------------ */

    @Test
    fun aCarWithNoHistoryIsOfferedTheFirstScan() {
        rule.openTimeline()

        rule.awaitRow(TimelineTestTags.MILESTONE_ROW)
        rule.onNodeWithText(TimelineCopy.EMPTY_TITLE).assertIsDisplayed()
        rule.onNodeWithText(TimelineCopy.newUserSubtitle(TimelineFixtures.CAR_NAME)).assertIsDisplayed()
    }

    /* ------------------------------ The filter ------------------------------ */

    @Test
    fun theSheetCountsEachKindOfEventTheCarHas() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        rule.openTimelineFilter()

        rule.onNodeWithText(TimelineCopy.showEvents(TimelineFixtures.TOTAL_EVENTS))
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText(TimelineCopy.FILTER_SERVICES).assertIsDisplayed()
        rule.onNodeWithText(TimelineCopy.FILTER_DOCUMENTS).assertIsDisplayed()
    }

    @Test
    fun untickingACategoryDropsItFromTheFeed() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.documentRow("PUC"))

        rule.openTimelineFilter()
        rule.toggleFilterCategory(TimelineTestTags.FILTER_ROW_DOCUMENTS)
        rule.dismissTimelineFilter()

        rule.assertRowAbsent(TimelineTestTags.DOCUMENT_ROW_PREFIX)
        // The services are untouched, and the header says what it is hiding.
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))
        rule.assertSubtitle(
            TimelineCopy.filteredSubtitle(
                TimelineFixtures.CAR_NAME,
                shown = TimelineFixtures.EVENTS_WITHOUT_DOCUMENTS,
                total = TimelineFixtures.TOTAL_EVENTS,
            ),
        )
    }

    @Test
    fun onlyFlaggedNarrowsToTheEntriesWorthArguingAbout() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        rule.openTimelineFilter()
        rule.toggleOnlyFlagged()
        rule.dismissTimelineFilter()

        rule.assertRowAbsent(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))
        rule.assertRowAbsent(TimelineTestTags.serviceRow(TimelineFixtures.SELF_REPORTED_ID))
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.FLAGGED_ID))
    }

    @Test
    fun aFilterThatHidesEverythingSaysSoRatherThanLookingEmpty() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        rule.openTimelineFilter()
        TimelineTestTags.FILTER_ROWS.forEach { rule.toggleFilterCategory(it) }
        rule.dismissTimelineFilter()

        rule.onNodeWithText(TimelineCopy.FILTERED_EMPTY_TITLE).assertIsDisplayed()
        // Not the new-user message: this car has a history, it is just hidden.
        assertEquals(0, rule.textCount(TimelineCopy.EMPTY_TITLE))
    }

    @Test
    fun theFilterSurvivesLeavingTheTabAndComingBack() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.documentRow("PUC"))
        rule.openTimelineFilter()
        rule.toggleFilterCategory(TimelineTestTags.FILTER_ROW_DOCUMENTS)
        rule.dismissTimelineFilter()
        rule.assertRowAbsent(TimelineTestTags.DOCUMENT_ROW_PREFIX)

        rule.leaveForHomeTab()
        rule.returnToTimeline()

        // In memory for the session, so the tab comes back the way it was left.
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))
        rule.assertRowAbsent(TimelineTestTags.DOCUMENT_ROW_PREFIX)
    }

    /* ------------------------------ Where rows lead ------------------------------ */

    @Test
    fun aServiceCardOpensThatEntrysDetail() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        rule.openServiceFromTimeline(TimelineFixtures.VERIFIED_ID)

        rule.awaitText(TimelineCopy.DETAIL_TOTAL_PAID)
    }

    @Test
    fun addBillOnAnUnbackedEntryReachesTheScanner() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.SELF_REPORTED_ID))

        rule.tapAddBill(TimelineFixtures.SELF_REPORTED_ID)

        rule.awaitText(TimelineCopy.SCANNER_TITLE)
    }

    @Test
    fun scanFirstBillReachesTheScannerToo() {
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.MILESTONE_ROW)

        rule.scrollFeedTo(TimelineTestTags.EMPTY_CTA)
        rule.onNodeWithTag(TimelineTestTags.EMPTY_CTA).performClick()

        rule.awaitText(TimelineCopy.SCANNER_TITLE)
    }

    @Test
    fun theHeaderSharesTheCarsRecord() {
        startCarWithAHistory()
        rule.openTimeline()
        rule.awaitRow(TimelineTestTags.serviceRow(TimelineFixtures.VERIFIED_ID))

        rule.shareFromTimeline()

        rule.awaitText(TimelineCopy.SHARE_SHEET_TITLE)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    /** A car with three services, three papers and a score that moved. */
    private fun startCarWithAHistory() {
        seedTimelineHistory()
        seedTimelineDocuments()
        seedTimelineScores()
    }
}
