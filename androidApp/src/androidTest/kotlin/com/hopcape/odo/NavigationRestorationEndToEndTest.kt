package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The owner stays where they were when the activity is rebuilt under them.
 *
 * Android destroys and recreates the activity on any configuration change — the OS dark/light
 * switch, a rotation, a system font-size or locale change — and again after a background kill.
 * The Nav3 back stack used to live in a plain `remember`, so all of it went with the
 * composition and the app came back on its start destination. Whatever screen was open was
 * gone, and so was everything behind it.
 *
 * `ActivityScenario.recreate()` is the same destroy-save-restore cycle the OS performs, which
 * makes it the honest way to test this: it exercises the real saved-state write and read
 * rather than a stand-in for them.
 *
 * These cover the app shell rather than any one feature, which is why they are their own class
 * — the destination used is only a means of being somewhere that is not Home.
 */
@RunWith(AndroidJUnit4::class)
class NavigationRestorationEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    companion object {
        /**
         * The car exists before anything launches an activity.
         *
         * Where the app opens is read once per launch and the rule starts the activity before
         * `@Before` runs, so an unseeded profile would put the first test on the welcome
         * carousel instead of the dashboard.
         */
        @JvmStatic
        @BeforeClass
        fun seedTheCarOnce() {
            resetTimeline()
            seedTimelineOwner()
        }
    }

    @Before
    fun startFromACarWithNoHistory() {
        clearTimelineData()
        resetTimelineFilter()
    }

    @Test
    fun theOpenTabSurvivesTheActivityBeingRebuilt() {
        rule.openTimeline()

        rule.activityRule.scenario.recreate()

        // Not Home. The selected tab has no state of its own — it is read off the top of the
        // back stack — so a tab that came back selected is a back stack that came back.
        rule.awaitTimelineLoaded()
    }

    @Test
    fun aPushedScreenAndEverythingUnderItSurviveTheRebuild() {
        // Home → Garage → Documents: a stack three deep, so this proves more than the top
        // entry being restored.
        rule.openVault()

        rule.activityRule.scenario.recreate()

        rule.awaitText(VaultCopy.TITLE)
        // Back has somewhere to go, and it is the screen the owner came from — not the exit.
        Espresso.pressBack()
        rule.awaitText(GarageCopy.TITLE)
    }
}
