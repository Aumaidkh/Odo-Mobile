package com.hopcape.odo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags

/** Drives the v2 first run by its test tags. Copy lives in [V2Copy]. */
private typealias V2Rule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

internal fun V2Rule.waitForTag(tag: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
}

internal fun V2Rule.waitUntilGone(text: String, timeoutMillis: Long = 10_000L) {
    waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
}

internal fun V2Rule.tapTag(tag: String) {
    waitForTag(tag)
    onNodeWithTag(tag).performScrollTo().performClick()
}

internal fun V2Rule.pickMake(make: String) {
    waitForText(V2Copy.WELCOME_TITLE, START_DESTINATION_TIMEOUT_MILLIS)
    tapTag(V2Copy.tile(make))
}

internal fun V2Rule.pickModel(model: String) {
    waitForText(V2Copy.MODEL_TITLE)
    tapTag(OnboardingTestTags.modelTile(model))
}

internal fun V2Rule.pickYearAndFuel(year: Int, fuel: String) {
    waitForText(V2Copy.YEAR_FUEL_TITLE)
    tapTag(OnboardingTestTags.yearChip(year))
    tapTag(OnboardingTestTags.fuelChip(fuel))
}

/** The pinned action sits outside the scrolling body, so it is tapped without scrolling. */
internal fun V2Rule.saveTheCar() {
    waitForTag(OnboardingTestTags.SAVE_CAR)
    onNodeWithTag(OnboardingTestTags.SAVE_CAR).performClick()
}
