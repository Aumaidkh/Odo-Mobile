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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.DomainError
import org.junit.rules.ExternalResource
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags

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
    const val WELCOME_CTA = "Get started"
    const val WELCOME_SIGN_IN = "Already using Odo? Sign in"
    const val DETAILS_TITLE = "Your car’s details"
    const val VALUE_SCAN = "Scan your first bill"
    const val VALUE_SKIP = "Not now — let me look around"

    /** What a refused save says. Its whole job is to not be silence. */
    const val SAVE_ERROR = "Couldn’t save that on your phone. Please try again."
    const val ODOMETER_SAVE = "Save reading"
    /** Retired copy, kept so a test can assert the button did not come back. */
    const val ODOMETER_UNKNOWN = "I don\u2019t know it right now"

    /** What replaced it: the reading is optional, so the step says so rather than asking. */
    const val ODOMETER_LATER = "You can add that later"
    const val ODOMETER_BUMP = "+1,000"
    const val GOAL_COSTS = "Stop overpaying"
    const val WORKSHOP_AUTHORISED = "Company service centre"
    const val LAST_SERVICE_FORGOT = "Don’t remember"
    const val LAST_SERVICE_DATE_MISSING = "Add the month of that service, or tick “Don’t remember”."
    const val LAST_SERVICE_ODOMETER_MISSING =
        "Add the reading from that service, or tick “Don’t remember”."
    const val SCAN_CTA = "Photograph the old bill"
    const val SKIP = "Skip"
    const val CHOOSE = "Choose"
    const val CONTINUE = "Continue"
    const val DONE = "Done"
    const val BACK = "Back"

    /** The payoff screen setup ends on — proof the flow finished somewhere new. */
    const val CAR_VALUE_TITLE = "My car’s value"
    const val AUTH_TITLE = "What’s your number?"

    /** Home's own copy, not the flow's — proof the gate landed somewhere else entirely. */
    /**
     * What Home shows a car that has just been set up. Not the health card: nothing has
     * been logged or filed yet, so Home offers the checklist that earns a score instead.
     */
    /* A day-one dashboard landmark: the tile that needs nothing from the owner. */
    const val HOME_RESALE = "Resale value"
}

/** The car every setup test names, and the owner who names it. */
internal object Fixtures {
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

/**
 * Put the device in a known state **before** the activity launches.
 *
 * Where the app opens is decided once per launch and then held, so a `@Before` cannot
 * arrange it: the rule has already started the activity by the time `@Before` runs. Chain
 * this outside the compose rule instead, and the first frame is drawn against the state the
 * test asked for:
 *
 * ```
 * private val rule = createAndroidComposeRule<MainActivity>()
 *
 * @get:Rule
 * val chain: RuleChain = RuleChain.outerRule(DeviceState { clearTheOwnersRows() }).around(rule)
 * ```
 *
 * This replaces the older habit of calling `scenario.recreate()` from `@Before`. That worked
 * while the start destination lived in a plain `remember`; it stopped working when the gate's
 * answer and the Nav3 back stack were both moved into saved state, because `recreate()` is a
 * configuration change and a configuration change is now — correctly — the one kind of
 * rebuild that puts the owner back exactly where they were.
 */
internal class DeviceState(private val prepare: () -> Unit) : ExternalResource() {
    override fun before() = prepare()
}

/**
 * Start the app again from nothing, and hand back the scenario so the caller can close it.
 *
 * The distinction this exists for: `scenario.recreate()` rebuilds the activity *from saved
 * state*, which restores the back stack and the answer the start-destination gate gave last
 * time. It is the right model for a rotation and the wrong one for "what would the app do on
 * the next launch?" — the gate is never re-read, so the question is never actually asked.
 * Launching a new activity leaves `savedInstanceState` null, which is what a cold start does.
 *
 * A true process death is still only verified by hand; this shares the process, so anything
 * held in a singleton survives. What it does prove is the part that broke in the field: the
 * gate reading persisted state and opening somewhere else.
 */
internal fun OdoTestRule.relaunchTheApp(): ActivityScenario<MainActivity> {
    activityRule.scenario.close()
    return ActivityScenario.launch(MainActivity::class.java)
}

/** The app opens on the pitch when nothing has been set up; step into the flow from there. */
internal fun OdoTestRule.startFromWelcome() {
    waitForText(Copy.WELCOME_HEADLINE, START_DESTINATION_TIMEOUT_MILLIS)
    onNodeWithText(Copy.WELCOME_CTA).performClick()
    waitForText(Copy.DETAILS_TITLE)
}

/**
 * Name the car with the four pickers, and give a reading.
 *
 * The whole of step 1 now: setup asks for no registration number, so there is nothing to
 * type here at all.
 */
internal fun OdoTestRule.answerTheCarStep() {
    nameTheCar()
    setOdometer()
}

/** The four pickers alone — the way somebody away from their car answers the step. */
internal fun OdoTestRule.nameTheCar() {
    pick(OnboardingTestTags.MAKE_FIELD, Fixtures.MAKE)
    pick(OnboardingTestTags.MODEL_FIELD, Fixtures.MODEL)
    confirmYear()
    // Fuel is chips now, not a field that opens a sheet — the answer is the tap.
    onNodeWithText(Fixtures.FUEL).performClick()
}

/**
 * Welcome, the car step, and the value screen that pays for it — first run up to its last
 * screen, which is where a test about what setup *produces* wants to start.
 */
internal fun OdoTestRule.reachTheValueScreen() {
    startFromWelcome()
    answerTheCarStep()
    onNodeWithText(Copy.CONTINUE).performClick()
    waitUntilPresent(Copy.VALUE_SKIP)
}

/** All of first run, declining the scan — the quickest honest route to Home. */
internal fun OdoTestRule.finishSetupToHome() {
    reachTheValueScreen()
    onNodeWithText(Copy.VALUE_SKIP).performClick()
}

/**
 * Open the odometer sheet and put a reading in it.
 *
 * The reading is entered with the sheet's own "+1,000" shortcut rather than by typing: it
 * needs no keyboard on screen, and the shortcut is what most owners will tap anyway.
 */
internal fun OdoTestRule.setOdometer(
    thousands: Int = 5,
    fieldTag: String = OnboardingTestTags.ODOMETER_FIELD,
) {
    onNodeWithTag(fieldTag).performClick()
    waitForText(Copy.ODOMETER_SAVE)
    repeat(thousands) { onNodeWithText(Copy.ODOMETER_BUMP).performClick() }
    onNodeWithText(Copy.ODOMETER_SAVE).performClick()
    waitUntil(DEFAULT_TIMEOUT_MILLIS) { onAllNodesWithTextCount(Copy.ODOMETER_SAVE) == 0 }
}

/**
 * Pick the first day of the month the date picker opens on.
 *
 * A specific date would mean scrolling the picker, which is brittle and beside the point.
 * The first of the current month is always in the past, which is what the entry's own
 * validation cares about. Tapping a day matters: the dialog confirms nothing while its
 * selection is empty, so "Choose" alone would close it having set no date at all.
 */
internal fun OdoTestRule.pickFirstOfTheMonth() {
    onNodeWithTag(OnboardingTestTags.LAST_SERVICE_DATE_FIELD).performClick()
    waitForText(Copy.CHOOSE)
    // Unmerged: a day cell wraps its number in a node carrying the spoken date ("Tuesday,
    // September 1, 2026"), and the merged tree hands back that description instead of "1".
    // A day cell clears its children's semantics and states the whole date instead
    // ("Tuesday, September 1, 2026"), so the digit alone matches nothing. " 1," picks the
    // first without also matching the 11th or the 21st.
    onAllNodesWithText(FIRST_OF_MONTH, substring = true).onFirst().performClick()
    onNodeWithText(Copy.CHOOSE).performClick()
    waitUntil(DEFAULT_TIMEOUT_MILLIS) { onAllNodesWithTextCount(Copy.CHOOSE) == 0 }
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

/** The day-of-month fragment of a date picker cell's spoken description. */
private const val FIRST_OF_MONTH = " 1,"

/** Long enough for a debounced lookup or a sheet animation, short enough to fail fast. */
private const val DEFAULT_TIMEOUT_MILLIS = 5_000L

/** The first frame waits on a database read, and on a cold start also on the catalog seed. */
internal const val START_DESTINATION_TIMEOUT_MILLIS = 20_000L

/* ------------------------------ Refused save ------------------------------ */

/**
 * Makes storing the car fail, so the step's refusal path can be driven.
 *
 * Wraps the real repository rather than replacing it: every other read the flow makes still
 * answers normally, and only `add` refuses — which is the shape of the failure the owner
 * actually hits, not a graph with a hole in it.
 */
internal fun installRefusingCarStore() {
    val real = GlobalContext.get().get<CarRepository>()
    GlobalContext.get().loadModules(
        listOf(module { single<CarRepository> { RefusingCarStore(real) } }),
        allowOverride = true,
    )
}

private class RefusingCarStore(private val real: CarRepository) : CarRepository by real {
    override suspend fun add(car: Car): Either<DomainError, Car> =
        DomainError.PersistenceFailure().left()
}

/**
 * Drives the car form to the point where Continue is live and the save will be refused.
 *
 * Needs [installRefusingCarStore] to have run before the launch. Shared by the behaviour test
 * and the screenshot one so both photograph and assert the same moment.
 */
internal fun OdoTestRule.reachARefusedCarSave() {
    startFromWelcome()
    answerTheCarStep()
}
