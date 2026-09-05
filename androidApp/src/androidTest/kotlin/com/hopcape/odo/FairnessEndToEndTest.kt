package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessTestTags
import com.hopcape.odo.feature.profile.presentation.EditProfileTestTags
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fairness check driven against the real app: the real Koin graph, the real benchmark
 * table, the real navigation graph and the real ViewModels.
 *
 * What these tests are really about is the **four outcomes**. A verdict, a fair price, a pool
 * too thin to judge, and no city data at all are four different answers, and the screen used
 * to draw the last two as the reassuring green one. That is the exact failure the PRD's
 * no-false-precision rule exists to prevent, and it is invisible to a unit test of the
 * ViewModel — it lives in which card the composable picks.
 *
 * **What is seeded and why.** Entries are written straight to the database: an entry is
 * checkable only once a bill is attached, and attaching one needs the system file picker.
 * Everything after that — opening the check, reading the verdict, filing a report — is driven
 * by tapping.
 *
 * The one flow driven through a stub is attaching a bill: the picker is another app's
 * activity, so the result is answered with a real file on disk and everything after it — the
 * copy into app storage, the verified badge, the verdict — is the app's own work.
 */
@RunWith(AndroidJUnit4::class)
class FairnessEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Put the device in the state each test needs **before** the activity launches.
     *
     * Where the app opens is decided once per launch and then held in saved state, so a
     * `@Before` is too late — the rule has already drawn a first frame against the previous
     * test's data. [DeviceState] runs outside the compose rule, so the seed lands first.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        // The runner pins every key to its compiled default, and the bill check ships
        // closed. This suite drives it, so it says so in its own file.
        .outerRule(PinnedConfig("bill_check_enabled", value = "true", compiledDefault = "false"))
        .around(DeviceState { startFromASetUpDeviceWithAnEmptyLog() })
        .around(rule)

    private fun startFromASetUpDeviceWithAnEmptyLog() {
        Intents.init()
        resetOwnerData()
        seedOnboardedOwner()
        installFairnessBenchmarks()
    }

    @After
    fun tearDown() = Intents.release()

    /* ------------------------------ The four outcomes ------------------------------ */

    @Test
    fun payingOverTheCityAverage_isCalledAnOvercharge() {
        seedFairnessEntries()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.OVER_ID, FairnessFixtures.OVER_WORKSHOP)

        rule.runFairnessCheck()

        rule.awaitFairnessTag(FairnessTestTags.HERO_OVER)
        rule.onNodeWithText(FairnessCopy.OVER_LABEL).assertIsDisplayed()
        // Rs. 5,000 against a Rs. 3,400 average.
        rule.onNodeWithText("Rs. 1,600 over").assertIsDisplayed()
    }

    @Test
    fun payingTheCityAverage_isCalledFair() {
        seedFairnessEntries()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.FAIR_ID, FairnessFixtures.FAIR_WORKSHOP)

        rule.runFairnessCheck()

        rule.awaitFairnessTag(FairnessTestTags.HERO_FAIR)
        rule.onNodeWithText(FairnessCopy.FAIR_LABEL).assertIsDisplayed()
        assertEquals(0, rule.fairnessTagCount(FairnessTestTags.REPORT_BUTTON))
    }

    @Test
    fun aPoolTooThinToJudge_saysSoInsteadOfSayingFair() {
        // Three bills for AC work in this city. The PRD forbids a verdict on that, and the
        // green card would be one.
        seedFairnessEntries()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.THIN_ID, FairnessFixtures.THIN_WORKSHOP)

        rule.runFairnessCheck()

        rule.awaitFairnessTag(FairnessTestTags.HERO_THIN)
        rule.onNodeWithText(FairnessCopy.THIN_LABEL).assertIsDisplayed()
        rule.onNodeWithText(FairnessCopy.THIN_HEADLINE).performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.fairnessTagCount(FairnessTestTags.HERO_FAIR))
    }

    @Test
    fun aThinPool_showsTheRangeItDoesHave() {
        seedFairnessEntries()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.THIN_ID, FairnessFixtures.THIN_WORKSHOP)

        rule.runFairnessCheck()

        // The percentiles are the only figure three data points can support.
        rule.awaitFairnessTag(FairnessTestTags.THIN_RANGE)
        rule.onNodeWithTag(FairnessTestTags.THIN_RANGE).assertIsDisplayed()
    }

    @Test
    fun withNoCityDataAtAll_theScreenClaimsNothing() {
        seedFairnessEntries()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.NO_DATA_ID, FairnessFixtures.NO_DATA_WORKSHOP)

        rule.runFairnessCheck()

        rule.awaitFairnessTag(FairnessTestTags.HERO_NO_BENCHMARK)
        rule.onNodeWithText(FairnessCopy.NO_DATA_HEADLINE).assertIsDisplayed()
        // Two bars would draw the bill as exactly average when nothing was compared.
        assertEquals(0, rule.fairnessTagCount(FairnessTestTags.COMPARISON))
        assertEquals(0, rule.fairnessTagCount(FairnessTestTags.REPORT_BUTTON))
    }

    /* ------------------------------ What follows a verdict ------------------------------ */

    @Test
    fun anOverchargeCanBeReported() {
        seedFairnessEntries()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.OVER_ID, FairnessFixtures.OVER_WORKSHOP)
        rule.runFairnessCheck()
        rule.awaitFairnessTag(FairnessTestTags.REPORT_BUTTON)

        rule.onNodeWithTag(FairnessTestTags.REPORT_BUTTON).performClick()

        // The report is filed against the entry the check was about, which is why the ids
        // travel on the navigation key at all.
        rule.awaitFairnessText(FairnessCopy.REPORT_QUESTION)
        rule.onNodeWithText(FairnessCopy.REPORT_QUESTION).assertIsDisplayed()
    }

    @Test
    fun doneLeavesTheReportAndReturnsToTheEntry() {
        seedFairnessEntries()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.FAIR_ID, FairnessFixtures.FAIR_WORKSHOP)
        rule.runFairnessCheck()

        rule.onNodeWithTag(FairnessTestTags.DONE_BUTTON).performClick()

        // Back on the entry, which now carries the verdict the check just took.
        rule.awaitFairnessText(FairnessFixtures.FAIR_WORKSHOP)
    }

    /* ------------------------------ Preconditions ------------------------------ */

    @Test
    fun aSelfReportedEntryIsAskedForABill_notForAVerdict() {
        // No bill means no proof, and the product does not grade an unproven number.
        seedSelfReportedEntry()
        rule.openServiceLog()

        rule.openEntryDetail(FairnessFixtures.SELF_REPORTED_ID, FairnessFixtures.SELF_REPORTED_WORKSHOP)

        rule.awaitFairnessText(FairnessCopy.ATTACH_BILL)
        rule.onNodeWithText(FairnessCopy.ATTACH_BILL).assertIsDisplayed()
        assertEquals(0, rule.fairnessTextCount(FairnessCopy.CHECK_FAIRNESS))
    }

    @Test
    fun attachingABill_verifiesTheEntryAndChecksItsPrice() {
        // The whole point of the bill: it is what turns a number the owner typed into
        // something the product will vouch for and benchmark.
        seedSelfReportedEntry()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.SELF_REPORTED_ID, FairnessFixtures.SELF_REPORTED_WORKSHOP)

        rule.attachABill()

        rule.awaitFairnessText(FairnessCopy.VERIFIED_BADGE)
        // Rs. 3,000 against a Rs. 3,400 average: verified, and judged in the same breath.
        rule.awaitFairnessText(FairnessCopy.DETAIL_FAIR_HEADLINE)
    }

    @Test
    fun withNoCityOnTheProfile_theCheckAsksForOneInsteadOfGuessing() {
        seedFairnessEntries()
        clearOwnerCity()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.OVER_ID, FairnessFixtures.OVER_WORKSHOP)

        rule.runFairnessCheck()

        rule.awaitFairnessTag(FairnessTestTags.NO_CITY)
        rule.onNodeWithText(FairnessCopy.NO_CITY_TITLE).assertIsDisplayed()
        rule.onNodeWithText(FairnessCopy.SET_CITY).assertIsDisplayed()
        assertEquals(0, rule.fairnessTagCount(FairnessTestTags.HERO_FAIR))
    }

    @Test
    fun settingTheCityFromTheDeadEnd_opensTheEditorThatHoldsTheField() {
        seedFairnessEntries()
        clearOwnerCity()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.OVER_ID, FairnessFixtures.OVER_WORKSHOP)
        rule.runFairnessCheck()
        rule.awaitFairnessTag(FairnessTestTags.SET_CITY_BUTTON)

        rule.onNodeWithTag(FairnessTestTags.SET_CITY_BUTTON).performClick()

        // The editor, not the profile root: the button named one field, and the root only
        // holds a row that leads to the screen holding it.
        rule.awaitFairnessText(ProfileCopy.SAVE)
        rule.onNodeWithTag(EditProfileTestTags.CITY_FIELD).assertExists()
    }

    @Test
    fun settingTheCity_comesBackToAReportThatUsesIt() {
        seedFairnessEntries()
        clearOwnerCity()
        rule.openServiceLog()
        rule.openEntryDetail(FairnessFixtures.OVER_ID, FairnessFixtures.OVER_WORKSHOP)
        rule.runFairnessCheck()
        rule.awaitFairnessTag(FairnessTestTags.SET_CITY_BUTTON)
        rule.onNodeWithTag(FairnessTestTags.SET_CITY_BUTTON).performClick()
        rule.awaitFairnessText(ProfileCopy.SAVE)

        rule.chooseCity(FairnessFixtures.CITY)
        rule.saveProfile()

        // The report is what the owner was trying to see, so it is what they come back to,
        // and the check runs again against the city they just set. That the dead end is gone
        // is the whole assertion: which verdict the pool produces is what the outcome tests
        // above are for, and this one must not depend on the benchmarks having data.
        rule.awaitFairnessGone(FairnessTestTags.NO_CITY)
        assertEquals(0, rule.fairnessTagCount(FairnessTestTags.SET_CITY_BUTTON))
    }

    @Test
    fun doneAfterAScan_leavesTheWholeErrandRatherThanTheReport() {
        seedFairnessEntries()
        rule.openGarage()
        rule.openReportAsIfScanned()

        rule.onNodeWithTag(FairnessTestTags.DONE_BUTTON).performClick()

        // Not the confirm step for a bill that is already saved, and not the viewfinder
        // behind that: the errand is over, so the owner is back where it started.
        rule.awaitFairnessText(GarageCopy.TITLE)
        assertEquals(0, rule.fairnessTextCount(ScanCopy.REVIEW_TITLE))
    }
}
