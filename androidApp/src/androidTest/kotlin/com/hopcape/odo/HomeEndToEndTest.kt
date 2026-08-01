package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTestTags
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every Home flow an owner can reach, driven against the real app: the real Koin graph, the
 * real SQLite database, the real navigation graph and the real ViewModels.
 *
 * Home is six shared rules read together — the score, the running cost, the overcharge
 * total, the attention picker, the insight picker and the activity feed — and its failures
 * live where no unit test looks: a card that says a paper has lapsed above a score that
 * already counted the renewal, a checklist that stays on after the first bill lands, an
 * attention card whose tap goes to the wrong screen.
 *
 * **What is seeded and why.** The car, its services and its papers are written straight to
 * the database: Home creates none of them, it only reads them. Bill photos are seeded
 * because the scanner that would attach one is M2, and the fairness verdict is seeded
 * because the check that would produce one needs a city pool the MVP has no data for.
 *
 * Dates are relative to today, not fixed. Home is a dashboard of deadlines, so "lapsed" and
 * "overdue" have to stay true whenever the suite is run.
 *
 * **Before running:** the local database still has no migrations, so an install carrying an
 * older database has no `health_scores` table. Clear the app's data (or uninstall) first.
 *
 * **What is deliberately not covered:** the no-car state (setup never finishing leaves the
 * app opening on the welcome carousel, so Home is not reachable to assert on — the
 * ViewModel's own test covers it), and a failed database read (there is no way to break
 * SQLite from a test without breaking the whole app with it).
 */
@RunWith(AndroidJUnit4::class)
class HomeEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    companion object {
        /**
         * The car exists before anything launches an activity.
         *
         * Where the app opens is read once per launch, and the rule starts the activity
         * before `@Before` runs — so seeding the profile per test would leave the first test
         * of the class driving the welcome carousel.
         */
        @JvmStatic
        @BeforeClass
        fun seedTheCarOnce() {
            resetHome()
            seedHomeOwner()
        }
    }

    /** Start every test from a car with nothing logged or filed against it. */
    @Before
    fun startFromACarWithNoRecord() {
        clearHomeData()
    }

    /* ------------------------------ The new-user path ------------------------------ */

    @Test
    fun aCarWithNothingOnItGetsTheChecklistRatherThanAScore() {
        rule.openHome()

        rule.awaitCardShows(HomeTestTags.SCORE_WAITING, HomeCopy.SCORE_WAITING)
        rule.assertCardShows(HomeTestTags.CHECKLIST_CAR, HomeCopy.SETUP_CAR)
        rule.assertCardShows(HomeTestTags.CHECKLIST_BILL, HomeCopy.SETUP_BILL)
        rule.assertCardShows(HomeTestTags.CHECKLIST_DOCS, HomeCopy.SETUP_DOCS)
    }

    /** One document is all it takes for the score to mean something. */
    @Test
    fun theFirstDocumentEndsTheChecklistAndStartsTheScore() {
        rule.openHome()
        rule.awaitCardShows(HomeTestTags.SCORE_WAITING, HomeCopy.SCORE_WAITING)

        seedHomeValidDocuments()

        rule.awaitCardShows(HomeTestTags.HEALTH_CARD, HomeCopy.HEALTH_SCORE)
    }

    @Test
    fun theChecklistCountsWhatIsActuallyDone() {
        rule.openHome()

        rule.awaitCardShows(HomeTestTags.CHECKLIST_CAR, HomeCopy.SETUP_CAR)
        rule.assertCardShows(HomeTestTags.SCREEN, HomeCopy.setUp(done = 1))
    }

    @Test
    fun theFirstScanButtonReachesTheScanner() {
        rule.openHome()
        rule.awaitCardShows(HomeTestTags.SCORE_WAITING, HomeCopy.SCORE_WAITING)

        rule.tapScanFirstBill()

        rule.awaitText(TimelineCopy.SCANNER_TITLE)
    }

    @Test
    fun theChecklistsDocumentRowReachesTheVault() {
        rule.openHome()
        rule.awaitCardShows(HomeTestTags.CHECKLIST_DOCS, HomeCopy.SETUP_DOCS)

        rule.tapChecklistDocuments()

        rule.awaitText(VaultCopy.ADD_TITLE)
    }

    /* ------------------------------ The scored dashboard ------------------------------ */

    @Test
    fun aCarWithARecordShowsItsScoreAndItsCosts() {
        seedHomeValidDocuments()
        seedHomeService()

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.HEALTH_CARD, HomeCopy.HEALTH_SCORE)
        rule.assertCardShows(HomeTestTags.HEALTH_CARD, HomeCopy.SEE_BREAKDOWN)
        rule.assertCardShows(HomeTestTags.COST_CARD, HomeCopy.RUNNING_COST)
        rule.assertCardShows(HomeTestTags.OVERCHARGE_CARD, HomeCopy.OVERCHARGE_CAUGHT)
    }

    /**
     * With no month-old snapshot to compare against, the card says so instead of showing a
     * reassuring zero.
     */
    @Test
    fun aScoreWithNoHistoryBehindItClaimsNoMovement() {
        seedHomeValidDocuments()

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.HEALTH_CARD, HomeCopy.FIRST_MONTH)
    }

    @Test
    fun anOverchargedBillIsCountedAndTheRestAreNot() {
        seedHomeValidDocuments()
        seedHomeService(
            id = HomeFixtures.FLAGGED_SERVICE_ID,
            daysAgo = HomeFixtures.FLAGGED_SERVICE_DAYS_AGO,
            odometerKm = HomeFixtures.FLAGGED_SERVICE_KM,
            amountPaise = HomeFixtures.FLAGGED_SERVICE_PAISE,
            notes = "Front brake pads",
            overcharged = true,
        )
        seedHomeService()

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.OVERCHARGE_CARD, HomeFixtures.FLAGGED_OVER)
        rule.assertCardShows(HomeTestTags.OVERCHARGE_CARD, HomeCopy.ONE_BILL_FLAGGED)
    }

    /** A car whose bills were all fair reads zero rather than hiding the card. */
    @Test
    fun aCarWithNothingCaughtSaysSoRatherThanHidingTheCard() {
        seedHomeValidDocuments()
        seedHomeService()

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.OVERCHARGE_CARD, HomeCopy.NO_OVERCHARGES)
    }

    /* ------------------------------ The attention card ------------------------------ */

    @Test
    fun aLapsedPaperIsWhatTheAttentionCardRaises() {
        seedHomeService()
        seedHomeDocument(HomeFixtures.INSURANCE_ID, DocumentType.INSURANCE, expiresInDays = 300)
        seedHomeDocument(
            HomeFixtures.PUC_ID,
            DocumentType.PUC,
            expiresInDays = -HomeFixtures.PUC_LAPSED_DAYS_AGO,
        )

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.ATTENTION_CARD, HomeCopy.PUC_EXPIRED)
    }

    @Test
    fun aPaperInsideItsRenewalWindowIsRaisedWithTheDaysLeft() {
        seedHomeService()
        seedHomeDocument(HomeFixtures.PUC_ID, DocumentType.PUC, expiresInDays = 120)
        seedHomeDocument(
            HomeFixtures.INSURANCE_ID,
            DocumentType.INSURANCE,
            expiresInDays = HomeFixtures.INSURANCE_EXPIRING_DAYS,
        )

        rule.openHome()

        rule.awaitCardShows(
            HomeTestTags.ATTENTION_CARD,
            HomeCopy.expiresIn("Insurance", HomeFixtures.INSURANCE_EXPIRING_DAYS.toInt()),
        )
    }

    @Test
    fun aServicePastItsIntervalIsRaisedWhenNoPaperIsDue() {
        seedHomeValidDocuments()
        seedHomeService(
            id = HomeFixtures.OVERDUE_SERVICE_ID,
            daysAgo = HomeFixtures.OVERDUE_SERVICE_DAYS_AGO,
            odometerKm = HomeFixtures.OVERDUE_SERVICE_KM,
            notes = "Wheel alignment",
        )

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.ATTENTION_CARD, HomeCopy.SERVICE_OVERDUE)
    }

    @Test
    fun aCarWithNothingDueSaysExactlyThat() {
        seedHomeValidDocuments()
        seedHomeService()

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.ATTENTION_CARD, HomeCopy.NOTHING_DUE)
        rule.assertCardShows(HomeTestTags.ATTENTION_CARD, HomeCopy.NOTHING_DUE_SUB)
    }

    /** A paper is renewed in the vault, so that is where its card has to lead. */
    @Test
    fun tappingADocumentAlertOpensTheVault() {
        seedHomeService()
        seedHomeDocument(
            HomeFixtures.PUC_ID,
            DocumentType.PUC,
            expiresInDays = -HomeFixtures.PUC_LAPSED_DAYS_AGO,
        )

        rule.openHome()
        rule.awaitCardShows(HomeTestTags.ATTENTION_CARD, HomeCopy.PUC_EXPIRED)
        rule.tapAttention()

        rule.awaitText(VaultCopy.TITLE)
    }

    /** A service is dealt with in the log, which is where the next entry gets added. */
    @Test
    fun tappingAServiceAlertOpensTheServiceLog() {
        seedHomeValidDocuments()
        seedHomeService(
            id = HomeFixtures.OVERDUE_SERVICE_ID,
            daysAgo = HomeFixtures.OVERDUE_SERVICE_DAYS_AGO,
            odometerKm = HomeFixtures.OVERDUE_SERVICE_KM,
            notes = "Wheel alignment",
        )

        rule.openHome()
        rule.awaitCardShows(HomeTestTags.ATTENTION_CARD, HomeCopy.SERVICE_OVERDUE)
        rule.tapAttention()

        rule.awaitText(LogCopy.LIST_TITLE)
    }

    /* ------------------------------ The insight card ------------------------------ */

    @Test
    fun aRecordWithNoBillsBehindItIsToldWhatThatCosts() {
        seedHomeValidDocuments()
        seedHomeService(withBill = false)
        seedHomeService(
            id = "home-log-second",
            daysAgo = 90,
            odometerKm = 48_000,
            notes = "Wheel alignment",
            withBill = false,
        )

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.INSIGHT_CARD, HomeCopy.INSIGHT_EYEBROW)
        rule.assertCardShows(HomeTestTags.INSIGHT_CARD, HomeCopy.noBills(2))
    }

    @Test
    fun aFullyVerifiedRecordIsCalledResaleReady() {
        seedHomeValidDocuments()
        seedHomeService()
        seedHomeService(id = "home-log-b", daysAgo = 90, odometerKm = 48_000, notes = "Brake pads")
        seedHomeService(id = "home-log-c", daysAgo = 150, odometerKm = 45_000, notes = "Tyres")

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.INSIGHT_CARD, HomeCopy.RESALE_EYEBROW)
        rule.assertCardShows(HomeTestTags.INSIGHT_CARD, HomeCopy.resaleReady(3))
    }

    /* ------------------------------ Recent activity ------------------------------ */

    @Test
    fun theRecentRowShowsTheNewestThingThatHappened() {
        seedHomeValidDocuments()
        seedHomeService()

        rule.openHome()

        rule.awaitCardShows(HomeTestTags.RECENT_ROW, HomeFixtures.RECENT_SERVICE_NOTE)
        rule.assertCardShows(HomeTestTags.RECENT_ROW, HomeCopy.VERIFIED)
    }

    @Test
    fun theRecentRowOpensTheServiceItIsAbout() {
        seedHomeValidDocuments()
        seedHomeService()

        rule.openHome()
        rule.awaitCardShows(HomeTestTags.RECENT_ROW, HomeFixtures.RECENT_SERVICE_NOTE)
        rule.tapRecent()

        rule.awaitText(LogCopy.DETAIL_TOTAL_PAID)
    }

    /**
     * The dashboard is a live read, not a snapshot taken when the tab opened: a service
     * logged while Home is on screen has to reach the recent row.
     */
    @Test
    fun theDashboardFollowsTheRecordWhileItIsOnScreen() {
        seedHomeValidDocuments()

        rule.openHome()
        rule.awaitCardShows(HomeTestTags.HEALTH_CARD, HomeCopy.HEALTH_SCORE)

        seedHomeService()

        rule.awaitCardShows(HomeTestTags.RECENT_ROW, HomeFixtures.RECENT_SERVICE_NOTE)
    }

    /* ------------------------------ Where the links go ------------------------------ */

    @Test
    fun seeBreakdownOpensTheHealthScore() {
        seedHomeValidDocuments()
        seedHomeService()

        rule.openHome()
        rule.awaitCardShows(HomeTestTags.HEALTH_CARD, HomeCopy.SEE_BREAKDOWN)
        rule.tapSeeBreakdown()

        rule.awaitText(HealthCopy.TITLE)
    }

    @Test
    fun theTimelineLinkOpensTheTimelineTab() {
        seedHomeValidDocuments()
        seedHomeService()

        rule.openHome()
        rule.awaitCardShows(HomeTestTags.RECENT_ROW, HomeFixtures.RECENT_SERVICE_NOTE)
        rule.tapTimelineLink()

        // The feed itself, not the tab: both are called "Timeline", and only the feed
        // appearing proves the link went somewhere.
        rule.awaitTimelineLoaded()
    }

    /** Coming back to the tab re-reads the record rather than serving what it last drew. */
    @Test
    fun leavingAndReturningShowsTheRecordAsItStandsNow() {
        // Insurance only: a valid PUC would go on outranking the lapsed one added below,
        // because a renewal is what silences the paper it replaced.
        seedHomeDocument(HomeFixtures.INSURANCE_ID, DocumentType.INSURANCE, expiresInDays = 300)

        rule.openHome()
        rule.awaitCardShows(HomeTestTags.ATTENTION_CARD, HomeCopy.NOTHING_DUE)

        seedHomeDocument(
            HomeFixtures.PUC_ID,
            DocumentType.PUC,
            expiresInDays = -HomeFixtures.PUC_LAPSED_DAYS_AGO,
        )
        rule.openTimeline()
        rule.returnToHome()

        rule.awaitCardShows(HomeTestTags.ATTENTION_CARD, HomeCopy.PUC_EXPIRED)
    }
}
