package com.hopcape.odo

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.feature.costtracker.presentation.CostTrackerTestTags
import org.koin.core.context.GlobalContext
import java.time.LocalDate

/**
 * The words the cost tracker puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason as [GarageCopy]: Compose Resources keeps a
 * feature's generated `Res` internal to its own module, so `:androidApp` cannot reach it.
 * Asserting on the copy an owner actually reads is the point.
 */
internal object CostCopy {
    const val TAB = "Costs"
    const val TITLE = "Running cost"

    /* Headline. */
    const val PER_KM_LABEL = "COST PER KILOMETRE"
    const val NO_RATE_TITLE = "Not enough to go on yet"
    const val NO_RATE_READINGS = "Add your car’s odometer reading and Odo will start tracking what it costs to run."
    const val NO_RATE_DISTANCE =
        "Odo needs about 100 km of driving before a cost per kilometre means anything. " +
            "Log your next service with its odometer reading."

    /* Period chips. */
    const val PERIOD_3M = "3M"
    const val PERIOD_6M = "6M"
    const val PERIOD_1Y = "1Y"

    /* Sections. */
    const val SPEND_BY_MONTH = "Spend by month"
    const val WHERE_IT_GOES = "WHERE IT GOES"
    const val SUMMARY = "SUMMARY"
    const val TOTAL_SPENT = "Total spent"
    const val DISTANCE_DRIVEN = "Distance driven"
    const val AVG_MONTH = "Average / month"

    /* Categories. */
    const val CAT_FUEL = "Fuel"
    const val CAT_SERVICE = "Service"
    const val CAT_REPAIRS = "Repairs"

    /* The fuel note — the estimate has to announce itself. */
    const val FUEL_NOTE_ESTIMATED_PREFIX = "Fuel estimated at"
    const val FUEL_NOTE_OWNER_PREFIX = "Fuel estimated from your rate"
    const val FUEL_NOTE_MISSING = "Fuel is not included — set your city in Profile to add it"

    /* The owner's own rate. */
    const val RATE_ACTION = "Set your rate"
    const val RATE_TITLE = "What do you pay for fuel?"
    const val RATE_SAVE = "Save rate"
    const val RATE_CLEAR = "Use Odo’s estimate instead"
    const val RATE_ERROR_RANGE = "Enter a price between Rs. 1 and Rs. 1000"
}

/**
 * The car, its history and the figures they add up to.
 *
 * Dates are relative to the day the test runs, because the screen's windows are relative to
 * today: a fixed date would drift out of the last year and take the whole suite with it.
 *
 * The distances are what make the assertions exact. Odo's own fuel prices are seeded
 * reference data and could be corrected in any release, so only the figures that do **not**
 * depend on them are asserted as literals: the distance, the two maintenance rows, and the
 * two rates computed with a fuel price the test itself sets (or with none at all).
 */
internal object CostFixtures {
    /** The car's baseline: 30,000 km, two years and change before today. */
    const val BASELINE_KM = 30_000
    const val BASELINE_DAYS_AGO = 800L

    /** Before the last year — it anchors the distance without being counted as spend. */
    const val OLD_ID = "cost-log-old"
    const val OLD_DAYS_AGO = 600L
    const val OLD_KM = 32_000
    const val OLD_PAISE = 400_000L

    /** Inside the year, a repair. */
    const val REPAIR_ID = "cost-log-repair"
    const val REPAIR_DAYS_AGO = 200L
    const val REPAIR_KM = 36_000
    const val REPAIR_PAISE = 500_000L
    const val REPAIR_SHOWN = "Rs. 5,000"

    /** Inside the year and inside the quarter, a routine service. */
    const val SERVICE_ID = "cost-log-service"
    const val SERVICE_DAYS_AGO = 30L
    const val SERVICE_KM = 42_000
    const val SERVICE_PAISE = 800_000L
    const val SERVICE_SHOWN = "Rs. 8,000"

    /** 42,000 − 32,000 over the year, and 42,000 − 36,000 over the quarter. */
    const val YEAR_DISTANCE_SHOWN = "10,000 km"
    const val QUARTER_DISTANCE_SHOWN = "6,000 km"

    /** Rs. 13,000 of logged work over 10,000 km, with no fuel in the figure. */
    const val RATE_WITHOUT_FUEL = "Rs. 1.3"

    /**
     * Rs. 120 a litre at the assumed 15 km/l is Rs. 8/km, so 10,000 km of fuel is
     * Rs. 80,000 — Rs. 93,000 all in, over 10,000 km.
     */
    const val OWNER_RATE_TYPED = "120"
    const val RATE_WITH_OWNER_FUEL = "Rs. 9.3"

    /** Odo's seeded Pune petrol price, which the sheet opens on. */
    const val SEEDED_PUNE_PETROL = "104.03"

    /** Above every reading and dated today — the reading the garage has to accept. */
    const val GARAGE_READING = "99999"
    const val GARAGE_DISTANCE_SHOWN = "67,999 km"
}

private typealias CostTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database ------------------------------ */

private fun costDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty everything the cost tracker reads, including any rate a previous test set.
 *
 * The owner's fuel rate is the one row that outlives [resetOwnerData]: it lives in the
 * reference table beside the seeded prices and is deliberately not tied to a car, so
 * without this a test that sets a rate would change the figures of every test after it.
 */
internal fun resetCostTracker() {
    resetOwnerData()
    costDriver().execute(null, "DELETE FROM fuel_price WHERE source = 'OWNER'", 0)
    costDriver().notifyListeners("fuel_price")
}

/**
 * A finished setup: an onboarded profile in [city] and one car reading 30,000 km, dated
 * far enough back that it anchors a year of driving.
 *
 * `city` is nullable because "the owner never set one" is a state the fuel estimate has to
 * handle — onboarding does not ask for a city, so it is the state most owners are in.
 */
internal fun seedCostOwner(city: String? = "Pune") = with(costDriver()) {
    val cityValue = city?.let { "'$it'" } ?: "NULL"
    execute(
        null,
        "INSERT INTO profiles (id, full_name, onboarding_goal, onboarding_completed_at, city, " +
            "created_at, updated_at, sync_status) VALUES ('${LogFixtures.OWNER}', 'Rahul', " +
            "'TRACK_COSTS', '$COST_SEEDED_AT', $cityValue, '$COST_SEEDED_AT', '$COST_SEEDED_AT', 'PENDING')",
        0,
    )
    execute(
        null,
        "INSERT INTO cars (id, owner_id, make, model, variant, year, fuel_type, registration_number, " +
            "current_odometer_km, is_primary, created_at, updated_at, sync_status) VALUES " +
            "('${LogFixtures.CAR}', '${LogFixtures.OWNER}', 'Maruti Suzuki', 'Swift', 'VXI', 2020, " +
            "'PETROL', 'MH12AB1234', ${CostFixtures.BASELINE_KM}, 1, " +
            "'${daysAgo(CostFixtures.BASELINE_DAYS_AGO)}T00:00:00Z', '$COST_SEEDED_AT', 'PENDING')",
        0,
    )
    announceCostWrites()
}

/**
 * Three services: one before the year that only anchors the distance, one repair inside it,
 * and one routine service inside the last quarter too.
 *
 * Their readings rise with their dates, so they form a timeline a real owner could have
 * produced — and the three windows the screen offers each see a different slice of it.
 */
internal fun seedCostHistory() {
    insertCostLog(
        id = CostFixtures.OLD_ID,
        daysAgo = CostFixtures.OLD_DAYS_AGO,
        odometerKm = CostFixtures.OLD_KM,
        amountPaise = CostFixtures.OLD_PAISE,
        category = "GENERAL_SERVICE",
    )
    insertCostLog(
        id = CostFixtures.REPAIR_ID,
        daysAgo = CostFixtures.REPAIR_DAYS_AGO,
        odometerKm = CostFixtures.REPAIR_KM,
        amountPaise = CostFixtures.REPAIR_PAISE,
        category = "BRAKES",
    )
    insertCostLog(
        id = CostFixtures.SERVICE_ID,
        daysAgo = CostFixtures.SERVICE_DAYS_AGO,
        odometerKm = CostFixtures.SERVICE_KM,
        amountPaise = CostFixtures.SERVICE_PAISE,
        category = "OIL_CHANGE",
    )
    announceCostWrites()
}

/** How many rates the owner has set — what a save and a clear are asserted against. */
internal fun ownerRateCount(): Long = costDriver().executeQuery(
    identifier = null,
    sql = "SELECT COUNT(*) FROM fuel_price WHERE source = 'OWNER'",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
    parameters = 0,
).value

private fun insertCostLog(
    id: String,
    daysAgo: Long,
    odometerKm: Int,
    amountPaise: Long,
    category: String,
) = with(costDriver()) {
    execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, " +
            "workshop_name, notes, source, bill_id, bill_photo_path, fairness_snapshot, created_at, " +
            "updated_at, sync_status) VALUES ('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
            "'${daysAgo(daysAgo)}', $odometerKm, $amountPaise, 'Sharma Motors', NULL, 'MANUAL', NULL, " +
            "NULL, NULL, '$COST_SEEDED_AT', '$COST_SEEDED_AT', 'PENDING')",
        0,
    )
    execute(null, "INSERT INTO service_log_categories (service_log_id, category) VALUES ('$id', '$category')", 0)
}

/**
 * Tell SQLDelight that these tables changed — seeds are hand-written SQL, which goes in
 * behind its back. See the service-log robot for why this matters between test classes.
 */
private fun announceCostWrites() = costDriver().notifyListeners(
    "cars",
    "profiles",
    "service_logs",
    "service_log_categories",
    "fuel_price",
)

/** A date [days] before today, as the `service_date` column stores it. */
private fun daysAgo(days: Long): String = LocalDate.now().minusDays(days).toString()

/** A fixed write timestamp; nothing under test reads it. */
private const val COST_SEEDED_AT = "2026-07-01T00:00:00Z"

/* ------------------------------ Navigation ------------------------------ */

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val COST_START_UP_TIMEOUT_MILLIS = 20_000L

/** Open the Costs tab from wherever the app started. */
internal fun CostTestRule.openCostTracker() {
    awaitText(CostCopy.TAB, COST_START_UP_TIMEOUT_MILLIS)
    onNodeWithText(CostCopy.TAB).performClick()
    awaitText(CostCopy.TITLE)
}

/** Come back to the Costs tab from another one. */
internal fun CostTestRule.returnToCostTracker() {
    onNodeWithText(CostCopy.TAB).performClick()
    awaitText(CostCopy.TITLE)
}

/** Switch the window the figures are computed over. */
internal fun CostTestRule.chooseCostPeriod(chip: String) {
    onNodeWithText(chip).performClick()
    waitForIdle()
}

/** Open the sheet where the owner states what they pay for fuel. */
internal fun CostTestRule.openFuelRateSheet() {
    onNodeWithText(CostCopy.RATE_ACTION).performScrollTo().performClick()
    awaitText(CostCopy.RATE_TITLE)
}

/** Replace whatever the field opened on — it is prefilled, so typing alone would append. */
internal fun CostTestRule.enterFuelRate(text: String) {
    val field = onNode(
        hasSetTextAction() and hasAnyAncestor(hasTestTag(CostTrackerTestTags.FUEL_RATE_FIELD)),
    )
    field.performTextClearance()
    field.performTextInput(text)
}

/**
 * Drop the rate the owner had set, from inside the sheet.
 *
 * The row is waited for: the sheet reads the price in force after it opens, so for a frame
 * it does not yet know there is a rate of theirs to drop.
 */
internal fun CostTestRule.clearFuelRate() {
    awaitText(CostCopy.RATE_CLEAR)
    onNodeWithText(CostCopy.RATE_CLEAR).performScrollTo().performClick()
}

/** Type [text] into the sheet's field and save it. */
internal fun CostTestRule.saveFuelRate(text: String) {
    enterFuelRate(text)
    onNodeWithText(CostCopy.RATE_SAVE).performClick()
}

/* ------------------------------ Assertions ------------------------------ */

/** The headline rate as the owner reads it, or `null` when the screen shows none. */
internal fun CostTestRule.costPerKm(): String? = onAllNodes(
    hasTestTag(CostTrackerTestTags.COST_PER_KM),
    useUnmergedTree = true,
).fetchSemanticsNodes()
    .firstOrNull()
    ?.config
    ?.getOrNull(SemanticsProperties.Text)
    ?.firstOrNull()
    ?.text

/** Wait until the headline reads [rate] — the figures land a frame after the screen does. */
internal fun CostTestRule.awaitCostPerKm(rate: String) {
    waitUntil(COST_TIMEOUT_MILLIS) { costPerKm() == rate }
}

/** Assert one "where it goes" row says [text] — its label, its per-km share or its total. */
internal fun CostTestRule.assertCategoryRowShows(category: SpendCategory, text: String) {
    onNode(
        hasTestTag(CostTrackerTestTags.categoryRow(category)) and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    ).assertExists()
}

/** Assert the breakdown has no row for [category] at all. */
internal fun CostTestRule.assertNoCategoryRow(category: SpendCategory) {
    onAllNodes(hasTestTag(CostTrackerTestTags.categoryRow(category)), useUnmergedTree = true)
        .assertCountEquals(0)
}

/** The fuel note's text — what the screen says the estimate was built on. */
internal fun CostTestRule.fuelNote(): String = onNode(
    hasTestTag(CostTrackerTestTags.FUEL_NOTE),
    useUnmergedTree = true,
).fetchSemanticsNode()
    .config[SemanticsProperties.Text]
    .first()
    .text

/** Wait for a fuel note that starts with [prefix] — the price inside it is the seed's. */
internal fun CostTestRule.awaitFuelNoteStartingWith(prefix: String) {
    waitUntil(COST_TIMEOUT_MILLIS) {
        onAllNodes(hasTestTag(CostTrackerTestTags.FUEL_NOTE), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty() && fuelNote().startsWith(prefix)
    }
}

/** How many nodes carry [tag] — how "the badge is not there" is asserted. */
internal fun CostTestRule.tagCount(tag: String): Int =
    onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

/** Scroll a line into view before reading or tapping it — the screen is taller than a phone. */
internal fun CostTestRule.scrollToCostText(text: String) {
    onAllNodesWithText(text).onFirst().performScrollTo()
}

/**
 * Wait until [text] is anywhere in the screen's tree, displayed or not.
 *
 * The running cost is one long scroll: the distance shows in the hero line *and* in the
 * summary, and the summary is below the fold. Asserting it is on screen would make the test
 * about where the page happens to end, so presence is what is waited for and a scroll is
 * what a test does when it means to look at something.
 */
internal fun CostTestRule.awaitCostText(text: String) {
    waitUntil(COST_TIMEOUT_MILLIS) { textCount(text) > 0 }
}

/** Long enough for a database read and a recomputation, short enough to fail fast. */
private const val COST_TIMEOUT_MILLIS = 5_000L
