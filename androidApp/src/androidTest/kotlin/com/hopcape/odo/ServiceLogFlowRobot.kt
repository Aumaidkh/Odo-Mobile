package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTestTags
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * The words the service log puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason as [Copy]: Compose Resources keeps a feature's
 * generated `Res` internal to its own module, so `:androidApp` cannot reach it. Asserting on
 * the copy an owner actually reads is the point — a reworded screen *should* make these tests
 * speak up rather than pass over changed words.
 *
 * Typographic apostrophes (’) come straight from the resource files; an ASCII quote would
 * never match.
 */
internal object LogCopy {
    const val LIST_TITLE = "Service Log"
    const val EMPTY_TITLE = "No services logged yet"
    const val EMPTY_SCAN = "Scan a bill"
    const val EMPTY_MANUAL = "Enter manually"

    const val LEDGER = "Ledger"
    const val TIMELINE = "Timeline"
    const val TOTAL_SPENT = "TOTAL SPENT"
    const val FILTER_ALL = "All"

    const val FORM_TITLE_ADD = "Log a service"
    const val ATTACH_BILL = "Attach bill to verify this entry"
    const val ERROR_DATE_REQUIRED = "Service date is required"

    const val VERIFIED = "Verified"
    const val SELF_REPORTED = "Self-reported"
    const val FAIR_PRICE = "Fair price"
    const val ADD_BILL_TO_VERIFY = "Add bill to verify"

    const val DETAIL_TOTAL_PAID = "Total paid"
    const val DETAIL_RESALE = "Counts as resale proof"
    const val DETAIL_SHARE = "Share verified record"
    const val DETAIL_REPORT = "Report this overcharge"
    const val DETAIL_NOT_FOUND = "This service is no longer available."

    const val REPORT_TITLE = "Report overcharge"
    const val REPORT_QUESTION = "What went wrong?"
    const val REPORT_REASON_ABOVE_MARKET = "Charged above market rate"
    const val REPORT_SUBMIT = "Submit report"
    const val REPORT_SUCCESS = "Report submitted"
    const val REPORT_DONE = "Done"

    const val SHARE_TITLE = "Share verified record"
    const val SHARE_PDF = "Download as PDF"
    const val SHARE_COPY = "Copy"

    /** Icon-only affordances carry their label as a content description. */
    const val ADD_SERVICE = "Add service"
    const val SHARE_RECORD = "Share record"
    const val BACK_LABEL = "Back"

    /** The garage is the only route into the service log today, so its copy is on the path. */
    const val GARAGE_TAB = "Garage"
    const val GARAGE_VIEW_ALL = "View all records"

    /** `"%1$s over"` — the ledger's overcharge pill and the report header. */
    fun over(rupees: String) = "$rupees over"

    /** `"%1$s · %2$d of %3$d services verified"` — the share sheet's summary. */
    fun shareSummary(car: String, verified: Int, total: Int) = "$car · $verified of $total services verified"

    /**
     * `"Odometer can’t be above a later reading (48,500 km)"`.
     *
     * The reading is written the way every other reading in the app is — grouped, and in
     * the owner's distance unit — because the message carries it as a distance rather than
     * as a bare number.
     */
    fun odometerAheadOf(km: Int) = "Odometer can’t be above a later reading (${grouped(km)} km)"

    /** Indian digit grouping — last three, then twos, as the domain formats a reading. */
    private fun grouped(value: Int): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        return rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
    }
}

/**
 * The seeded car and its history.
 *
 * Rows are written straight to the database rather than driven through the UI, because the
 * states that matter most cannot be reached through it: an entry is **Verified** only with a
 * bill attached (M2's scanner) and **Flagged** only with a stored fairness verdict (a city
 * pool the MVP has no data for). Seeding is what lets the filters, the fairness section and
 * the whole report flow be tested at all.
 */
internal object LogFixtures {
    const val OWNER = "local-owner"
    const val CAR = "test-car"
    const val CAR_NAME = "Maruti Suzuki Swift VXI"

    /** The car's baseline reading, dated by its `created_at` — the timeline starts here. */
    const val CAR_CREATED = "2024-01-01T00:00:00Z"
    const val CAR_ODOMETER = 40_000

    const val FLAGGED_ID = "log-flagged"
    const val FLAGGED_WORKSHOP = "AutoCare Pune"
    const val FLAGGED_OVER = "Rs. 1,100"

    const val FAIR_ID = "log-fair"
    const val FAIR_WORKSHOP = "Sharma Motors"

    const val SELF_REPORTED_ID = "log-self"
    const val SELF_REPORTED_WORKSHOP = "Speed Garage"

    /** A new service after every seeded one, and not in the future. */
    const val NEW_WORKSHOP = "Bombay Motors"
    const val NEW_DATE_TYPED = "07152026"
    const val NEW_DATE_SHOWN = "15 Jul 2026"
    const val NEW_ODOMETER = "56000"
    const val NEW_AMOUNT = "2500"
    const val NEW_AMOUNT_SHOWN = "Rs. 2,500"

    /** The reading a backdated entry would have to cross — the flagged entry's. */
    const val CROSSED_READING = 48_500
}

private typealias LogTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database seeding ------------------------------ */

private fun driver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty the owner's tables.
 *
 * The rows go rather than the file: the database is a process-wide singleton with an open
 * connection, so deleting the file underneath it would leave that connection writing to an
 * unlinked inode. The seeded vehicle catalog is reference data and is left alone.
 */
internal fun resetOwnerData() = with(driver()) {
    execute(null, "DELETE FROM service_log_categories", 0)
    execute(null, "DELETE FROM service_logs", 0)
    execute(null, "DELETE FROM overcharge_reports", 0)
    execute(null, "DELETE FROM cars", 0)
    execute(null, "DELETE FROM profiles", 0)
    announceSeededWrites()
}

/**
 * A finished setup: an onboarded profile and one primary car.
 *
 * Written directly so every test starts at the service log rather than re-walking first-run
 * setup — that flow has its own end-to-end test, and repeating it here would make these
 * slower and give them a second reason to fail.
 */
internal fun seedOnboardedOwner() = with(driver()) {
    execute(
        null,
        "INSERT INTO profiles (id, full_name, onboarding_goal, onboarding_completed_at, city, " +
            "created_at, updated_at, sync_status) VALUES ('${LogFixtures.OWNER}', 'Rahul', " +
            "'SAVE_MONEY', '$SEEDED_AT', 'Pune', '$SEEDED_AT', '$SEEDED_AT', 'PENDING')",
        0,
    )
    execute(
        null,
        "INSERT INTO cars (id, owner_id, make, model, variant, year, fuel_type, registration_number, " +
            "current_odometer_km, is_primary, created_at, updated_at, sync_status) VALUES " +
            "('${LogFixtures.CAR}', '${LogFixtures.OWNER}', 'Maruti Suzuki', 'Swift', 'VXI', 2020, " +
            "'PETROL', 'MH12AB1234', ${LogFixtures.CAR_ODOMETER}, 1, '${LogFixtures.CAR_CREATED}', " +
            "'$SEEDED_AT', 'PENDING')",
        0,
    )
    announceSeededWrites()
}

/**
 * Three entries covering every badge the list can show: verified-and-over, verified-and-fair,
 * and self-reported. Their readings rise with their dates, so they form a timeline a real
 * owner could have produced — which is what lets a new entry be added on top of them.
 */
internal fun seedServiceHistory() {
    insertLog(
        id = LogFixtures.SELF_REPORTED_ID,
        date = "2025-12-18",
        odometerKm = 43_200,
        amountPaise = 640_000,
        workshop = LogFixtures.SELF_REPORTED_WORKSHOP,
        category = "GENERAL_SERVICE",
    )
    insertLog(
        id = LogFixtures.FLAGGED_ID,
        date = "2026-03-02",
        odometerKm = LogFixtures.CROSSED_READING,
        amountPaise = 480_000,
        workshop = LogFixtures.FLAGGED_WORKSHOP,
        category = "BRAKES",
        billPhotoPath = "/bills/flagged.jpg",
        // Paid Rs. 4,800 against a Rs. 3,700 city average — past the 10% band, on a sample big
        // enough to say so. That verdict is the report flow's precondition.
        fairness = fairnessJson("BRAKES", paidPaise = 480_000, averagePaise = 370_000, sampleSize = 240),
    )
    insertLog(
        id = LogFixtures.FAIR_ID,
        date = "2026-06-12",
        odometerKm = 54_000,
        amountPaise = 320_000,
        workshop = LogFixtures.FAIR_WORKSHOP,
        category = "OIL_CHANGE",
        billPhotoPath = "/bills/fair.jpg",
        // Inside the band, so the same evidence produces a fair verdict instead.
        fairness = fairnessJson("OIL_CHANGE", paidPaise = 320_000, averagePaise = 330_000, sampleSize = 120),
    )
    announceSeededWrites()
}

/**
 * Tell SQLDelight that these tables changed.
 *
 * Seeds are hand-written SQL, which goes in behind SQLDelight's back: it re-runs a query only
 * when the write went through one of its own, so without this an already-collecting screen —
 * or the process-lifetime `ActiveCarProvider` — would keep serving what it read before the
 * seed. That is invisible when a test class runs alone (nothing has read yet) and fails the
 * moment another class has already opened the app, which is exactly the kind of order
 * dependence a suite must not have.
 *
 * The keys are the table names SQLDelight generates its notifications against.
 */
private fun announceSeededWrites() = driver().notifyListeners(
    "cars",
    "profiles",
    "service_logs",
    "service_log_categories",
    "overcharge_reports",
)

private fun insertLog(
    id: String,
    date: String,
    odometerKm: Int,
    amountPaise: Long,
    workshop: String,
    category: String,
    billPhotoPath: String? = null,
    fairness: String? = null,
) = with(driver()) {
    execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, " +
            "workshop_name, notes, source, bill_id, bill_photo_path, fairness_snapshot, created_at, " +
            "updated_at, sync_status) VALUES ('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
            "'$date', $odometerKm, $amountPaise, '$workshop', NULL, 'MANUAL', NULL, " +
            "${billPhotoPath.sqlOrNull()}, ${fairness.sqlOrNull()}, '$SEEDED_AT', '$SEEDED_AT', 'PENDING')",
        0,
    )
    execute(null, "INSERT INTO service_log_categories (service_log_id, category) VALUES ('$id', '$category')", 0)
}

/**
 * A stored fairness snapshot, in the shape the data layer reads back.
 *
 * The verdict itself is absent on purpose: the column stores the *evidence* and lets
 * `FairnessReport.of` derive the verdict, so a snapshot written here is judged by the very
 * rule the app would apply to one it wrote itself.
 */
private fun fairnessJson(category: String, paidPaise: Long, averagePaise: Long, sampleSize: Int): String =
    """{"city":"Pune","checked_at":"$SEEDED_AT","items":[{"category":"$category",""" +
        """"amount_paise":$paidPaise,"city_average_paise":$averagePaise,"sample_size":$sampleSize}]}"""

/**
 * Soft-delete an entry through the repository, standing in for a delete made on another
 * surface (or arriving on a sync pull).
 *
 * Through the repository rather than as raw SQL, and that distinction is the whole point:
 * SQLDelight only re-runs a query when the write went through SQLDelight, so a hand-written
 * UPDATE changes the row without any open screen ever hearing about it. Driving the app's
 * own write path is what makes this a test of the screen observing its entry.
 */
internal fun softDeleteEntry(id: String) = runBlocking {
    GlobalContext.get().get<ServiceLogRepository>().softDelete(ServiceLogId(id))
}

/** SQL literal or NULL — these seeds are hand-written SQL, so nullability is explicit. */
private fun String?.sqlOrNull(): String = this?.let { "'${it.replace("'", "''")}'" } ?: "NULL"

/** A fixed timestamp for every seeded row; nothing under test reads it. */
private const val SEEDED_AT = "2026-07-01T00:00:00Z"

/* ------------------------------ Navigation ------------------------------ */

/**
 * Walk from Home to the service log the way an owner reaches it: the Garage tab, its
 * "add to history" sheet, then "View all records".
 *
 * Driving that path is part of what these tests prove — including that the garage hands the
 * service log a car that actually exists, which is what the active-car port fixed.
 */
internal fun LogTestRule.openServiceLog() {
    awaitText(LogCopy.GARAGE_TAB, START_UP_TIMEOUT_MILLIS)
    onNodeWithText(LogCopy.GARAGE_TAB).performClick()
    awaitLabel(LogCopy.ADD_SERVICE)
    onNodeWithLabel(LogCopy.ADD_SERVICE).performClick()
    awaitText(LogCopy.GARAGE_VIEW_ALL)
    onNodeWithText(LogCopy.GARAGE_VIEW_ALL).performClick()
    awaitText(LogCopy.LIST_TITLE)
}

/** Open the add form from the list's floating action button. */
internal fun LogTestRule.openAddForm() {
    onNodeWithLabel(LogCopy.ADD_SERVICE).performClick()
    awaitText(LogCopy.FORM_TITLE_ADD)
}

/** Open one seeded entry's detail by id, in whichever direction the list is showing. */
internal fun LogTestRule.openEntry(logId: String) {
    onNodeWithTag(ServiceLogTestTags.card(logId)).performClick()
    awaitText(LogCopy.DETAIL_TOTAL_PAID)
}

/* ------------------------------ The form ------------------------------ */

/**
 * Fill the add form. Every argument is optional so a test can leave exactly the field it is
 * about unanswered — an absent date is how the missing-date guard gets exercised.
 */
internal fun LogTestRule.fillServiceForm(
    workshop: String? = null,
    odometer: String? = null,
    amount: String? = null,
    date: String? = null,
) {
    workshop?.let { typeInto(ServiceLogTestTags.WORKSHOP_FIELD, it) }
    odometer?.let { typeInto(ServiceLogTestTags.ODOMETER_FIELD, it) }
    amount?.let { typeInto(ServiceLogTestTags.AMOUNT_FIELD, it) }
    date?.let { setServiceDate(it) }
}

/**
 * Set the service date through the picker's keyboard-entry mode.
 *
 * The calendar grid is a Material component whose day cells are only reachable by scrolling a
 * month into view — brittle to automate and beside the point. Its text field commits the same
 * value, and the value is what this screen is being tested on.
 *
 * The editable node is matched *inside the dialog*, because the form's own fields are still
 * in the semantics tree behind it.
 */
internal fun LogTestRule.setServiceDate(mmddyyyy: String) {
    onNodeWithTag(ServiceLogTestTags.DATE_FIELD).performClick()
    awaitText(PICKER_OK)
    onNodeWithLabel(PICKER_TEXT_MODE).performClick()
    onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(mmddyyyy)
    onNodeWithText(PICKER_OK).performClick()
    awaitGone(PICKER_OK)
}

internal fun LogTestRule.saveServiceLog() = onNodeWithTag(ServiceLogTestTags.SAVE).performClick()

/* ------------------------------ Waiting ------------------------------ */

/**
 * Wait until [text] is on screen. Unlike the onboarding robot's equivalent this tolerates
 * repeats: a workshop name shows on the card *and* in the detail's collapsing title, and both
 * are on screen mid-transition.
 */
internal fun LogTestRule.awaitText(text: String, timeoutMillis: Long = LOG_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) { textCount(text) > 0 }
    onAllNodesWithText(text).onFirst().assertIsDisplayed()
}

internal fun LogTestRule.awaitLabel(label: String, timeoutMillis: Long = LOG_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) { labelCount(label) > 0 }
}

internal fun LogTestRule.awaitGone(text: String, timeoutMillis: Long = LOG_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) { textCount(text) == 0 }
}

internal fun LogTestRule.textCount(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes().size

internal fun LogTestRule.labelCount(label: String): Int =
    onAllNodesWithContentDescription(label).fetchSemanticsNodes().size

/** Long enough for a database read and a screen transition, short enough to fail fast. */
private const val LOG_TIMEOUT_MILLIS = 5_000L

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val START_UP_TIMEOUT_MILLIS = 20_000L

/* Material's date picker: its own copy, not the feature's. */
private const val PICKER_OK = "OK"
private const val PICKER_TEXT_MODE = "Switch to text input mode"
