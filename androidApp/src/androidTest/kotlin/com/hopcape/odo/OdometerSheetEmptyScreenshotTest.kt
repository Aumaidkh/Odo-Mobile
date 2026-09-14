package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * The Before/After picture for the odometer sheet on a car with nothing recorded.
 *
 * The car is real and on screen; it just has no reading yet, which is what the car step
 * offers. The sheet used to refuse to open at all.
 *
 * Run it and collect the file with the commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class OdometerSheetEmptyScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                resetGarage()
                installStubVehicleRegistry()
                silenceTheCoachMarks()
            },
        )
        .around(rule)

    /** Coach marks would cover the garage; this is a picture of the sheet, not of them. */
    private fun silenceTheCoachMarks() {
        val store = GlobalContext.get().get<ShowcaseSeenStore>()
        runBlocking { ShowcaseHookId.entries.forEach { store.markSeen(it) } }
    }

    private companion object {
        const val VALUE_TITLE = "Odo needs your odometer"
    }

    @Test
    fun capturesTheSheetWithNothingRecorded() {
        rule.startFromWelcome()
        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)
        rule.onNodeWithText(Copy.ODOMETER_UNKNOWN).performClick()
        rule.finishSetupFromTheProfileStep()

        // Setup lands on the value screen over Home; step back before reaching for a tab.
        rule.waitForText(VALUE_TITLE)
        rule.onNodeWithLabel(Copy.BACK).performClick()

        rule.openGarage()
        rule.waitForText(GarageCopy.ODOMETER_LABEL)
        rule.onNodeWithText(GarageCopy.UPDATE).performClick()

        rule.captureScreen("odometer-sheet-nothing-recorded")
    }
}
