package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Takes the screenshots the restored-history PR carries (issue #425).
 *
 * Not an assertion — nothing here can fail on the product. Photographing this by hand is not
 * possible: the sheet only appears after a sign-in sync has adopted a car the server already
 * held, which a local debug build has no Supabase session to do.
 *
 * One capture per test, each on its own activity. The sheet is shown once per restore and
 * answering it clears the marker, so driving both shapes in one run would mean re-announcing
 * onto a screen that had just dismissed one — a sequence no owner performs and not what the
 * pictures are meant to show.
 *
 * Run it and collect the files with the two commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class RestoredHistoryScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                resetProfile()
                resetOwnerData()
                resetRestorableRecords()
                clearPendingRestore()
                seedOnboardedOwner()
                seedServiceHistory()
                seedHomeValidDocuments()
            },
        )
        .around(rule)

    @Test
    fun capturesTheSheetOfferingAnOdometerCorrection() {
        // The reinstall case: the owner typed 12,000 km while re-adding the car, and the
        // history that just came back proves 54,000.
        setCarOdometer(12_000)
        showTheRestore("after-odometer-correction")
    }

    @Test
    fun capturesTheSheetWithNothingToCorrect() {
        // The same restore where the car's own reading is already the highest — a welcome
        // with no question attached.
        setCarOdometer(92_000)
        showTheRestore("after-inform-only")
    }

    /**
     * Wait for the dashboard, announce the restore, photograph the sheet.
     *
     * The dashboard first because the sheet rises over it: a restore announced mid-load
     * photographs the skeleton behind the scrim.
     */
    private fun showTheRestore(name: String) {
        rule.awaitHomeLoaded()
        announceRestore()
        rule.awaitText(RestoredHistoryCopy.TITLE)
        rule.captureScreen(name)
    }
}

/** The sheet's copy, so the test waits on the shipped strings rather than a guess. */
internal object RestoredHistoryCopy {
    const val TITLE = "Your history is back"
}
