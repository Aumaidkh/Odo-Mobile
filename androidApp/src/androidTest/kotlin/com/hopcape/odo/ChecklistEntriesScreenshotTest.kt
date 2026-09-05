package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.test.performScrollTo
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Takes the screenshots the entry-points PR carries.
 *
 * The seed is the case the conditional card exists for: a lapsed PUC and an overdue service
 * at once. The attention picker returns the paper — driving on a lapsed PUC is an offence, so
 * it outranks everything — which is exactly when the checklist would otherwise be unreachable
 * from Home.
 *
 * Run it and collect the files with the two commands in `.github/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class ChecklistEntriesScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                resetHome()
                seedHomeOwner()
                // Long enough ago that the interval has run out on both time and distance.
                seedHomeService(daysAgo = 400, odometerKm = 28_000)
                seedHomeDocument("puc-lapsed", DocumentType.PUC, expiresInDays = -12)
                seedHomeDocument("insurance", DocumentType.INSURANCE, expiresInDays = 300)
            },
        )
        .around(rule)

    @Test
    fun capturesTheHomeCardAndTheGarageRow() {
        rule.awaitText(HOME_TAB)
        // The health coach mark is granted on the first scored dashboard and draws a scrim
        // over everything. Tapped away first, so the picture is the dashboard rather than
        // a dimmed one.
        rule.dismissCoachMark()
        // Home scrolls, and the card sits under the attention row it exists to sit beside.
        rule.onNodeWithTag(HomeTestTags.CHECKLIST_OFFER).performScrollTo()
        rule.captureScreen("home-checklist-card")

        rule.openGarage()
        rule.openCarMenu()
        rule.awaitText(GARAGE_ROW)
        rule.captureScreen("garage-checklist-row")
    }

    private companion object {
        const val HOME_TAB = "Home"
        const val GARAGE_ROW = "Before you go in"
    }
}

/** Tap a coach mark away if one is up. There may be none, and that is not a failure. */
private fun ChecklistTestRule.dismissCoachMark() {
    waitForIdle()
    if (onAllNodesWithText(COACH_MARK_DISMISS).fetchSemanticsNodes().isEmpty()) return
    onNodeWithText(COACH_MARK_DISMISS).performClick()
    waitForIdle()
}

private typealias ChecklistTestRule =
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

private const val COACH_MARK_DISMISS = "Got it"
