package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseSeenStore
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * The Before/After pictures for issue #429 — setup no longer waits for an odometer reading.
 *
 * Two shots, both from a fresh install walking the real flow: the car step with the escape
 * hatch under the drum, and the Home card that keeps asking afterwards. Driving it here
 * rather than by hand also proves the two ends join up, which a screenshot of either alone
 * would not.
 *
 * Run it and collect the files with the commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class PendingOdometerScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Empty the owner's rows before the launch, not after: where the app opens is decided once
     * per launch, so a reset inside the test would come too late to send it to Welcome.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                clearTheOwnersRows()
                installStubVehicleRegistry()
                silenceTheCoachMarks()
            },
        )
        .around(rule)

    @Test
    fun capturesTheCarStepAndTheHomeCardThatFollowsIt() {
        rule.startFromWelcome()
        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)

        // The car is named and the odometer is untouched — the moment the old build stopped
        // an owner who was not near their car.
        rule.captureScreen("after-car-step")

        rule.onNodeWithText(Copy.CONTINUE).performClick()
        rule.finishSetupFromTheProfileStep()

        // The costs goal lands on the value screen, which is one of the surfaces that cannot
        // work without the reading. It asks for it instead of reporting an empty estimate —
        // or, as it did before this change, claiming there is no car.
        rule.waitForText(ValueCopy.TITLE)
        rule.captureScreen("after-car-value")

        rule.onNodeWithLabel(Copy.BACK).performClick()

        // Wait on the card itself rather than on the dashboard being "loaded": a brand-new
        // owner gets the setup checklist instead of the score, and the card is what this
        // picture is of.
        rule.waitForText(NudgeCopy.TITLE)
        rule.captureScreen("after-home-nudge")
    }

    /** Everything the owner has, and nothing that was seeded as reference data. */
    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
    }

    /**
     * Mark every coach mark seen. They dim the screen behind them, and a picture of Home
     * under a scrim is a picture of the coach mark.
     */
    private fun silenceTheCoachMarks() {
        val store = GlobalContext.get().get<ShowcaseSeenStore>()
        runBlocking { ShowcaseHookId.entries.forEach { store.markSeen(it) } }
    }
}

/** The shipped copy, so each wait is on the real string rather than a guess. */
private object NudgeCopy {
    const val TITLE = "Add your odometer reading"
}

private object ValueCopy {
    const val TITLE = "Odo needs your odometer"
}
