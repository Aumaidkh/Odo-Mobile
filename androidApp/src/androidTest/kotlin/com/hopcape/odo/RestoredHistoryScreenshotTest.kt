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
                seedOnboardedOwner()
                seedServiceHistory()
                seedHomeValidDocuments()
            },
        )
        .around(rule)

    @Test
    fun capturesTheSheetWithAndWithoutAnOdometerCorrection() {
        // Let the dashboard settle first: the sheet rises over it, and a restore announced
        // mid-load photographs the skeleton behind the scrim.
        rule.awaitHomeLoaded()

        // The reinstall case — the owner typed 12,000 km while re-adding the car and their
        // history proves 54,000.
        setCarOdometer(12_000)
        announceRestore()
        rule.awaitText(RestoredHistoryCopy.TITLE)
        rule.captureScreen("after-odometer-correction")
        rule.dismissRestoredHistory()

        // The same restore where the car's own reading is already the highest: the sheet
        // welcomes them back and offers nothing to put right.
        setCarOdometer(92_000)
        announceRestore()
        rule.awaitText(RestoredHistoryCopy.TITLE)
        rule.captureScreen("after-inform-only")
    }
}

/** The sheet's copy, so the test waits on the shipped strings rather than a guess. */
internal object RestoredHistoryCopy {
    const val TITLE = "Purani history mil gayi"
    const val DONE = "Theek hai"

    /** The keep-my-reading button carries the number, so only its opening is fixed. */
    const val KEEP_PREFIX = "Nahi,"
}
