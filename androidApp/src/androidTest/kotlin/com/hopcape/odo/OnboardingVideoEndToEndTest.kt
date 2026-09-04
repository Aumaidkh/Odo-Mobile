package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import org.koin.core.context.GlobalContext

/**
 * The video intro variant of first run, against the real app.
 *
 * **Why it is its own class.** The variant is chosen by `onboarding_video_enabled` before
 * the first frame, so it cannot be a test inside [OnboardingEndToEndTest] — every test in a
 * class shares one launch policy. Pinning the flag on for this class and leaving it at its
 * compiled default everywhere else is what keeps both intros covered at once.
 *
 * **No clip is expected to play.** The URLs stay at their compiled default of blank, which
 * is what a build with no clips configured has and what a first launch with no network gets
 * either way. That is deliberate: the video is decoration, and the thing worth testing is
 * that an owner reaches car setup without one. A test that streamed a real clip would be
 * asserting on a CDN.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingVideoEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(PinnedConfig(VIDEO_ENABLED, value = "true", compiledDefault = "false"))
        .around(DeviceState {
            clearTheOwnersRows()
            installStubVehicleRegistry()
        })
        .around(rule)

    @Test
    fun aNewInstallOpensOnTheIntro_notTheWelcomePage() {
        rule.waitForText(VideoCopy.REFUEL_TITLE, START_DESTINATION_TIMEOUT_MILLIS)

        // The variant replaces the pitch rather than sitting in front of it.
        rule.onNodeWithText(Copy.WELCOME_HEADLINE).assertDoesNotExist()
    }

    /**
     * The regression this class was written for.
     *
     * The clip was given a fixed 0.65 of the screen height, so on a short phone the copy,
     * the dots and the button needed more room than the remaining 0.35 and the button was
     * laid out past the bottom edge. Nothing crashed and nothing looked broken — the intro
     * simply had no way out, on the one screen every new install has to get through.
     *
     * `assertIsDisplayed` is the assertion that catches it: a node clipped out of the window
     * still exists and is still clickable in a test, and only being *displayed* is the
     * question an owner is asking.
     */
    @Test
    fun everyPageKeepsItsButtonOnScreen() {
        rule.waitForText(VideoCopy.REFUEL_TITLE, START_DESTINATION_TIMEOUT_MILLIS)

        rule.onNodeWithText(VideoCopy.NEXT).assertIsDisplayed()
        rule.onNodeWithText(VideoCopy.SKIP).assertIsDisplayed()

        rule.onNodeWithText(VideoCopy.NEXT).performClick()
        rule.waitForText(VideoCopy.SCANNER_TITLE)

        // The last page swaps the label, and it has to be as reachable as the first one's.
        rule.onNodeWithText(VideoCopy.CTA).assertIsDisplayed()
        rule.onNodeWithText(VideoCopy.SKIP).assertIsDisplayed()
    }

    @Test
    fun theLastPage_leadsIntoCarSetup() {
        rule.waitForText(VideoCopy.REFUEL_TITLE, START_DESTINATION_TIMEOUT_MILLIS)
        rule.onNodeWithText(VideoCopy.NEXT).performClick()
        rule.waitForText(VideoCopy.SCANNER_TITLE)

        rule.onNodeWithText(VideoCopy.CTA).performClick()

        rule.waitForText(Copy.CAR_TITLE)
    }

    /**
     * Skipping the intro is not skipping onboarding.
     *
     * Both buttons lead to the same place on purpose: there is no version of first run that
     * does not set up a car, and a Skip that reached Home would leave the app with no car to
     * show — the state every screen behind Home assumes cannot happen.
     */
    @Test
    fun skip_leavesTheClipsAndNotTheFlow() {
        rule.waitForText(VideoCopy.REFUEL_TITLE, START_DESTINATION_TIMEOUT_MILLIS)

        rule.onNodeWithText(VideoCopy.SKIP).performClick()

        rule.waitForText(Copy.CAR_TITLE)
        rule.onNodeWithText(Copy.HOME_SCORE_WAITING).assertDoesNotExist()
    }

    /** Setup still only happens once, whichever intro led into it. */
    @Test
    fun onceTheCarIsSetUp_theIntroIsNotShownAgain() {
        rule.waitForText(VideoCopy.REFUEL_TITLE, START_DESTINATION_TIMEOUT_MILLIS)
        rule.onNodeWithText(VideoCopy.SKIP).performClick()
        rule.waitForText(Copy.CAR_TITLE)

        rule.typeInto(OnboardingTestTags.PLATE_FIELD, Fixtures.KNOWN_PLATE)
        rule.waitForText(Fixtures.MATCHED_CAR)
        rule.setOdometer()
        rule.onNodeWithText(Copy.CONTINUE).performClick()

        rule.waitForText(Copy.PROFILE_TITLE)
        rule.typeInto(OnboardingTestTags.NAME_FIELD, Fixtures.OWNER_NAME)
        rule.onNodeWithText(Copy.GOAL_COSTS).performClick()
        rule.onNodeWithText(Copy.CONTINUE).performClick()

        rule.waitForText(Copy.WORKSHOP_TITLE)
        rule.onNodeWithText(Copy.WORKSHOP_AUTHORISED).performClick()
        rule.onNodeWithText(Copy.CONTINUE).performClick()

        rule.waitForText(Copy.LAST_SERVICE_TITLE)
        rule.onNodeWithText(Copy.SKIP).performClick()
        rule.waitForText(Copy.AUTH_TITLE)

        // The flag is still on, so this is the variant's own answer and not the usual flow's:
        // an owner who has set up a car never sees either intro again.
        rule.relaunchTheApp().use {
            rule.waitForText(Copy.HOME_SCORE_WAITING, START_DESTINATION_TIMEOUT_MILLIS)
            rule.onNodeWithText(VideoCopy.REFUEL_TITLE).assertDoesNotExist()
        }
    }

    private fun clearTheOwnersRows() {
        val driver = GlobalContext.get().get<SqlDriver>()
        driver.execute(null, "DELETE FROM cars", 0)
        driver.execute(null, "DELETE FROM profiles", 0)
    }

    private companion object {
        const val VIDEO_ENABLED = "onboarding_video_enabled"
    }
}

/**
 * The intro's copy, mirrored from `:feature:onboarding`'s `strings.xml` for the same reason
 * [Copy] is — the generated `Res` class is internal to the module that owns it.
 */
internal object VideoCopy {
    const val REFUEL_TITLE = "Odo logs your fuel for you"
    const val SCANNER_TITLE = "Snap a bill, keep the record"
    const val NEXT = "Next"
    const val CTA = "Get started"
    const val SKIP = "Skip"
}
