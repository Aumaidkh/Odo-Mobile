package com.hopcape.odo

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTestTags

/**
 * The words the onboarding flow puts on screen, mirrored from each feature's `strings.xml`.
 *
 * Copied rather than read: Compose Resources keeps the generated `Res` class internal to its
 * own module (`publicResClass = false`), so a test in `:androidApp` cannot reach the
 * feature's strings. Duplication is the price, and for an end-to-end test it is close to
 * free — asserting on the copy an owner actually reads is the point, and a reworded screen
 * *should* make this test speak up rather than pass over changed words.
 *
 * Note the typographic apostrophes (’ not '): they come from the resource files, and a plain
 * ASCII quote here would never match.
 */
internal object Copy {
    const val WELCOME_HEADLINE = "Know what your car really costs you."
    const val WELCOME_CTA = "Continue with mobile"
    const val CAR_TITLE = "Which car is yours?"
    const val DETAILS_TITLE = "Your car’s details"
    const val ENTER_MANUALLY = "Enter details manually"
    const val LOOKUP_NOT_FOUND = "No record for this plate"
    const val ODOMETER_SAVE = "Save reading"
    const val ODOMETER_BUMP = "+1,000"
    const val PROFILE_TITLE = "Last bit about you"
    const val GOAL_COSTS = "Stop overpaying"
    const val SCAN_TITLE = "Find out if you overpaid last time"
    const val SCAN_SKIP = "I’ll do this later"
    const val CONTINUE = "Continue"
    const val DONE = "Done"
    const val BACK = "Back"
    const val AUTH_TITLE = "What’s your number?"

    /** Home's own copy, not the flow's — proof the gate landed somewhere else entirely. */
    /**
     * What Home shows a car that has just been set up. Not the health card: nothing has
     * been logged or filed yet, so Home offers the checklist that earns a score instead.
     */
    const val HOME_SCORE_WAITING = "Your score is waiting"
}

/** What the development registry stub resolves, and what it does not. */
internal object Fixtures {
    const val KNOWN_PLATE = "JK03N3078"
    const val MATCHED_CAR = "Maruti Suzuki Swift VXI"
    const val UNKNOWN_PLATE = "MH12AB1234"
    const val OWNER_NAME = "Rahul"
    const val MAKE = "Maruti Suzuki"
    const val MODEL = "Swift"
    const val FUEL = "Petrol"
}

private typealias OdoTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/**
 * Wait until [text] is on screen, failing with the usual timeout message if it never is.
 *
 * Every step of this flow crosses something asynchronous — a database read, a debounced
 * lookup, a step transition that fades — so waiting on the *result* is the only stable way
 * to drive it. Sleeping for a guessed duration would be both slower and flakier.
 */
internal fun OdoTestRule.waitForText(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
    waitUntilPresent(text, timeoutMillis)
    onNodeWithText(text).assertIsDisplayed()
}

/**
 * Wait for [text] without insisting it is unique.
 *
 * The picker sheets list a make twice on purpose — once under "Popular", once under "All" —
 * so the single-node assertion in [waitForText] would fail on a screen that is behaving
 * exactly as designed.
 */
internal fun OdoTestRule.waitUntilPresent(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) { onAllNodesWithTextCount(text) > 0 }
}

private fun OdoTestRule.onAllNodesWithTextCount(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes().size

/** The app opens on the pitch when nothing has been set up; step into the flow from there. */
internal fun OdoTestRule.startFromWelcome() {
    waitForText(Copy.WELCOME_HEADLINE, START_DESTINATION_TIMEOUT_MILLIS)
    onNodeWithText(Copy.WELCOME_CTA).performClick()
    waitForText(Copy.CAR_TITLE)
}

/**
 * Open the odometer sheet and put a reading in it.
 *
 * The reading is entered with the sheet's own "+1,000" shortcut rather than by typing: the
 * drum is a custom control with no text input behind it, and the shortcut is what most
 * owners will tap anyway.
 */
internal fun OdoTestRule.setOdometer(thousands: Int = 5) {
    onNodeWithTag(OnboardingTestTags.ODOMETER_FIELD).performClick()
    waitForText(Copy.ODOMETER_SAVE)
    repeat(thousands) { onNodeWithText(Copy.ODOMETER_BUMP).performClick() }
    onNodeWithText(Copy.ODOMETER_SAVE).performClick()
    waitUntil(DEFAULT_TIMEOUT_MILLIS) { onAllNodesWithTextCount(Copy.ODOMETER_SAVE) == 0 }
}

/** Open the picker behind [fieldTag] and choose [option] from the sheet it raises. */
internal fun OdoTestRule.pick(fieldTag: String, option: String) {
    onNodeWithTag(fieldTag).performClick()
    waitUntilPresent(option)
    // First match: a popular make is listed under both "Popular" and "All", and either row
    // selects the same make.
    onAllNodesWithText(option).onFirst().performClick()
    waitUntil(DEFAULT_TIMEOUT_MILLIS) { onAllNodesWithTextCount(Copy.CONTINUE) > 0 }
}

/**
 * Confirm whatever year the wheel is showing.
 *
 * Unlike the other pickers this one is a scrolling wheel with a staged Cancel/Done commit,
 * so a specific year is only reachable by scrolling it into view — brittle to automate and
 * beside the point. What matters here is that the sheet commits a year at all, which the
 * step's Continue then proves by becoming enabled.
 */
internal fun OdoTestRule.confirmYear() {
    onNodeWithTag(OnboardingTestTags.YEAR_FIELD).performClick()
    waitForText(Copy.DONE)
    onNodeWithText(Copy.DONE).performClick()
    waitUntil(DEFAULT_TIMEOUT_MILLIS) { onAllNodesWithTextCount(Copy.DONE) == 0 }
}

/**
 * Type into the field tagged [fieldTag].
 *
 * The tag sits on the component, while the thing that actually accepts text is the
 * `BasicTextField` inside it — a plate box and a name field are both several nodes deep.
 * Matching the editable node *within* the tagged subtree keeps the tags on the components
 * the screens own, instead of pushing them down into :core:designsystem's internals.
 */
internal fun OdoTestRule.typeInto(fieldTag: String, text: String) {
    onNode(hasSetTextAction() and (hasTestTag(fieldTag) or hasAnyAncestor(hasTestTag(fieldTag))))
        .performTextInput(text)
}

/** Icon-only controls carry their label as a content description, not as text. */
internal fun OdoTestRule.onNodeWithLabel(label: String): SemanticsNodeInteraction =
    onNodeWithContentDescription(label)

/** Long enough for a debounced lookup or a sheet animation, short enough to fail fast. */
private const val DEFAULT_TIMEOUT_MILLIS = 5_000L

/** The first frame waits on a database read, and on a cold start also on the catalog seed. */
private const val START_DESTINATION_TIMEOUT_MILLIS = 20_000L
