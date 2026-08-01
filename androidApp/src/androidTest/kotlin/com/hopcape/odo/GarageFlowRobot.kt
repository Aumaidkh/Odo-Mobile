package com.hopcape.odo

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.garage.presentation.GarageTestTags
import org.koin.core.context.GlobalContext

/**
 * The words the garage puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason as [VaultCopy]: Compose Resources keeps a
 * feature's generated `Res` internal to its own module, so `:androidApp` cannot reach it.
 * Asserting on the copy an owner actually reads is the point.
 *
 * Typographic apostrophes (’) come straight from the resource files; an ASCII quote would
 * never match.
 */
internal object GarageCopy {
    const val TAB = "Garage"
    const val TITLE = "Garage"

    /* Empty state. */
    const val EMPTY_TITLE = "Nothing here yet"
    const val ADD_CAR_TITLE = "Add your car"
    const val ADD_CAR_BUTTON = "Add"
    const val EMPTY_ADD_DOCS = "Add insurance, PUC or RC"
    const val EMPTY_LOG_SERVICE = "Log your first service"

    /* Car card. */
    const val ODOMETER_LABEL = "ODOMETER"
    const val UPDATE = "Update"
    const val NO_PLATE = "No plate on record"
    const val CAR_MENU_LABEL = "Car options"

    /* Documents section. */
    const val DOCUMENTS = "DOCUMENTS"
    const val MANAGE = "Manage"
    const val DOC_INSURANCE = "Insurance"
    const val DOC_PUC = "Pollution (PUC)"
    const val DOC_RC = "Registration (RC)"
    const val DOC_ADD = "+ Add"
    const val DOC_ON_FILE = "On file"

    /* Service history. */
    const val HISTORY = "SERVICE HISTORY"
    const val NO_ENTRIES = "No entries in this category yet"
    const val ADD_SERVICE_LABEL = "Add service"
    const val FILTER_ALL = "All"
    const val FILTER_SERVICE = "Service"
    const val FILTER_TYRES = "Tyres"
    const val FILTER_BATTERY = "Battery"
    const val BADGE_VERIFIED = "Verified"
    const val BADGE_SELF_REPORTED = "Self-reported"
    const val CAT_BRAKES = "Brakes"
    const val CAT_TYRES = "Tyres"
    const val CAT_GENERAL = "General service"
    const val ENTRY_UNTAGGED = "Service"

    /* Car actions sheet. */
    const val ACTION_EDIT = "Edit car details"
    const val ACTION_EXPORT = "Export car record"
    const val ACTION_REMOVE = "Remove car"

    /* Add-to-history sheet. */
    const val ADD_HISTORY_TITLE = "Add to service history"
    const val ADD_HISTORY_SCAN = "Scan a bill"
    const val ADD_HISTORY_MANUAL = "Enter manually"
    const val ADD_HISTORY_DOC = "Add a document"
    const val ADD_HISTORY_VIEW_ALL = "View all records"

    /* Update-odometer sheet. */
    const val ODOMETER_TITLE = "Update odometer"
    const val ODOMETER_SAVE = "Save reading"

    /* Add car. */
    const val ADD_TITLE = "Add a car"
    const val ADD_SUBMIT = "Add to Garage"
    const val ADD_REG_LABEL = "Registration number"
    const val ADD_LOOKUP_UNAVAILABLE = "Registry lookup isn’t available yet — fill in the details below."
    const val ADD_ODOMETER_LABEL = "Current odometer"
    const val FIELD_MAKE = "Make"
    const val FIELD_MODEL = "Model"
    const val FIELD_YEAR = "Year"
    const val FIELD_FUEL = "Fuel"
    const val CHOOSE = "Choose"
    const val DONE = "Done"
    const val FUEL_PETROL = "Petrol"

    /* Each picker sheet's own heading — what says which one is open. */
    const val MAKE_SHEET_TITLE = "Pick your car’s make"
    const val MODEL_SHEET_TITLE = "Pick the model"
    const val FUEL_SHEET_TITLE = "What does it run on?"

    /** The odometer keypad's backspace, announced for screen readers and matched by it here. */
    const val ODOMETER_BACKSPACE = "Delete last digit"

    /* Edit car. */
    const val EDIT_TITLE = "Edit car"
    const val EDIT_SAVE = "Save changes"
    const val EDIT_NICKNAME = "Nickname (optional)"
    const val EDIT_ODO_NOTE = "Odometer is updated separately from the Garage card."
    const val CLOSE_LABEL = "Close"

    /* Export sheet. */
    const val EXPORT_TITLE = "Export car record"
    const val EXPORT_PRO_NOTE = "Exporting your record is part of Odo Pro."
    const val EXPORT_PDF = "PDF"
    const val EXPORT_SHARE = "Share"
    const val EXPORT_SERVICE = "Service history"
    const val EXPORT_DOCS = "Documents"

    /* Remove sheet. */
    const val REMOVE_CONFIRM = "Remove car"
    const val REMOVE_CANCEL = "Cancel"
    const val REMOVE_EXPORT_FIRST = "Export the record first?"

    /* Failures. */
    const val ERROR_ODO_BACK_PREFIX = "Your last recorded reading was"
    const val ERROR_FIELD_MAKE = "Pick the make"
    const val ERROR_FIELD_MODEL = "Pick the model"

    /** `"Remove %1$s?"` — the sheet leads with the car's own name. */
    fun removeTitle(car: String) = "Remove $car?"

    /** `"This deletes %1$d service entries and %2$d documents for this car."` */
    fun removeBody(services: Int, documents: Int) =
        "This deletes $services service entries and $documents documents for this car. It can’t be undone."

    /** `"%1$d entries · %2$s total"` — the history section's summary line. */
    fun entriesSummary(count: Int, total: String) = "$count entries · $total total"

    /** `"%1$d · %2$s"` — model year and plate, grouped the way the plate field groups it. */
    fun carMeta(year: Int, plate: String) = "$year · $plate"

    /** `"%1$s · %2$s"` — a history row's date and workshop. */
    fun entryMeta(date: String, workshop: String) = "$date · $workshop"

    /** `"Last recorded %1$s on %2$s"` — the odometer sheet's footer. */
    fun lastRecorded(reading: String, date: String) = "Last recorded $reading on $date"

    /** `"%1$d files"` / `"%1$d entries"` — what the export sheet says is on file. */
    fun exportDocs(count: Int) = "$count files"
    fun exportServices(count: Int) = "$count entries"
}

/**
 * The car and history each test drives against.
 *
 * The seeded car is [LogFixtures.CAR] — the same one the service-log and vault suites use,
 * so a garage test and a vault test are looking at the same owner's data.
 */
internal object GarageFixtures {
    /** The plate as the card renders it: normalized on the way in, grouped on the way out. */
    const val CAR_PLATE_SHOWN = "MH 12 AB 1234"
    const val CAR_YEAR = 2020

    /** The odometer the seeded car carries, as the card writes it. */
    const val CAR_ODOMETER_SHOWN = "40,000 km"

    /** Above the seeded baseline and every seeded log — a reading that has to be accepted. */
    const val HIGHER_READING = "88888"
    const val HIGHER_READING_SHOWN = "88,888 km"

    /** Below the baseline, dated before today — the reading the timeline has to refuse. */
    const val LOWER_READING = "1000"

    /* The car the add-car flow describes. */
    const val NEW_MAKE = "Hyundai"
    const val NEW_MODEL = "Creta"
    const val NEW_PLATE_TYPED = "JK03N3089"
    const val NEW_ODOMETER = "12345"
    const val NEW_CAR_NAME = "Hyundai Creta"

    const val NICKNAME = "Chhoti"

    /* Documents seeded onto the car, one per state the overview can show. */
    const val INSURANCE_ID = "garage-doc-insurance"
    const val RC_ID = "garage-doc-rc"
}

private typealias GarageTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database ------------------------------ */

private fun garageDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty everything the garage reads: the profile, the car, its logs and its documents.
 *
 * The garage aggregates all three tables, so a leftover row from another class's test would
 * show up in its counts.
 */
internal fun resetGarage() {
    resetOwnerData()
    garageDriver().execute(null, "DELETE FROM documents", 0)
    garageDriver().notifyListeners("documents")
}

/**
 * A finished setup with **no car** — the precondition for the empty garage and the add-car
 * flow, which [seedOnboardedOwner] cannot give because it always stores one.
 */
internal fun seedProfileOnly() {
    seedOnboardedOwner()
    garageDriver().execute(null, "DELETE FROM cars", 0)
    garageDriver().notifyListeners("cars")
}

/** Read a car column straight back, to check what a flow actually stored. */
internal fun storedCarValue(column: String): String? = garageDriver().executeQuery(
    identifier = null,
    sql = "SELECT $column FROM cars WHERE deleted_at IS NULL LIMIT 1",
    mapper = { cursor ->
        app.cash.sqldelight.db.QueryResult.Value(
            if (cursor.next().value) cursor.getString(0) else null,
        )
    },
    parameters = 0,
).value

/** How many live rows [table] holds — what a removal is asserted against. */
internal fun liveRowCount(table: String): Long = garageDriver().executeQuery(
    identifier = null,
    sql = "SELECT COUNT(*) FROM $table WHERE deleted_at IS NULL",
    mapper = { cursor ->
        app.cash.sqldelight.db.QueryResult.Value(
            if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L,
        )
    },
    parameters = 0,
).value

/* ------------------------------ Navigation ------------------------------ */

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val GARAGE_START_UP_TIMEOUT_MILLIS = 20_000L

/** Open the Garage tab from wherever the app started. */
internal fun GarageTestRule.openGarage() {
    awaitText(GarageCopy.TAB, GARAGE_START_UP_TIMEOUT_MILLIS)
    onNodeWithText(GarageCopy.TAB).performClick()
    awaitText(GarageCopy.TITLE)
}

/** Open the ⋮ menu on the car card. */
internal fun GarageTestRule.openCarMenu() {
    onNodeWithLabel(GarageCopy.CAR_MENU_LABEL).performClick()
    awaitText(GarageCopy.ACTION_REMOVE)
}

/** Open the update-odometer sheet from the card's "Update". */
internal fun GarageTestRule.openOdometerSheet() {
    onNodeWithText(GarageCopy.UPDATE).performClick()
    awaitText(GarageCopy.ODOMETER_SAVE)
}

/** Open the "add to service history" sheet from the history section's + button. */
internal fun GarageTestRule.openAddToHistory() {
    onNodeWithLabel(GarageCopy.ADD_SERVICE_LABEL).performClick()
    awaitText(GarageCopy.ADD_HISTORY_TITLE)
}

/**
 * Clear the drums and dial [digits] in on the keypad.
 *
 * The drums start on the reading already on record, so every digit of it is deleted first —
 * the keypad's backspace is the only way to clear it, and a reading is at most six digits.
 *
 * A digit is matched on the **last** node showing it: the drums render the current reading
 * as text too, and they sit above the keypad in the tree.
 */
internal fun GarageTestRule.dialOdometer(digits: String) {
    repeat(ODOMETER_DIGITS) { onNodeWithLabel(GarageCopy.ODOMETER_BACKSPACE).performClick() }
    digits.forEach { digit -> onAllNodesWithText(digit.toString()).onLast().performClick() }
}

/** Dial [digits] and save the reading. */
internal fun GarageTestRule.saveOdometer(digits: String) {
    dialOdometer(digits)
    onNodeWithText(GarageCopy.ODOMETER_SAVE).performClick()
}

/** How many digits a reading can hold — what a full clear has to delete. */
private const val ODOMETER_DIGITS = 6

/* ------------------------------ Assertions ------------------------------ */

/**
 * Assert that one document row says [text] — its name, its badge or its "+ Add".
 *
 * Matched against the unmerged tree: the row is clickable and merges its children away, so
 * only the unmerged tree still holds the badge as its own node.
 */
internal fun GarageTestRule.assertDocumentRowShows(type: DocumentType, text: String) {
    onNode(
        hasTestTag(GarageTestTags.documentRow(type)) and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    ).assertExists()
}

/** Assert that one history row says [text] — its heading, its badge or its amount. */
internal fun GarageTestRule.assertServiceRowShows(logId: String, text: String) {
    onNode(
        hasTestTag(GarageTestTags.serviceRow(logId)) and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    ).assertExists()
}

/** Scroll a node into view before tapping it — the garage scrolls past a full screen. */
internal fun GarageTestRule.scrollToText(text: String) {
    onNodeWithText(text).performScrollTo()
}

/**
 * Wait for a line that *starts with* [prefix].
 *
 * The odometer refusal names the reading it conflicts with, and that number comes from the
 * seeded history rather than the copy — matching the whole sentence would pin the test to a
 * fixture it does not care about.
 */
internal fun GarageTestRule.awaitTextStartingWith(prefix: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(prefix, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Open the picker tagged [fieldTag] and choose the option that reads [option].
 *
 * The fields collapse to the word "Choose", which every one of them says, so the tag is what
 * names which picker is being opened. The option inside is picked by its own copy, which is
 * what an owner reads.
 *
 * [sheetTitle] is waited for on the way in and on the way out, so a failure says whether the
 * sheet never opened or the option was never in it.
 */
internal fun GarageTestRule.pickFromSheet(
    fieldTag: String,
    sheetTitle: String,
    option: String,
    searchable: Boolean = false,
) {
    onNodeWithTag(fieldTag).performScrollTo().performClick()
    awaitText(sheetTitle)
    // A long list is lazy, so a model thirteen rows down is never composed and never enters
    // the semantics tree. The sheet's own search box is how it gets there — which is also
    // how an owner would find it.
    if (searchable) {
        onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(option)
    }
    awaitText(option)
    // A *row*, not the search box that now holds the same text — the rows are the only
    // selectable nodes in the sheet. The first match, because a model is offered without a
    // trim before its variants, and that is the one being described here.
    onAllNodes(hasText(option) and isSelectable()).onFirst().performClick()
    awaitGone(sheetTitle)
    // The sheet is its own window, and it stays alive for a frame after its content goes.
    // Without settling here, the *next* picker's tap is swallowed by a sheet that has
    // already visually closed.
    waitForIdle()
}

/**
 * Commit whatever year the wheel is centred on.
 *
 * Named apart from onboarding's equivalent on purpose: both robots extend the same rule
 * type, so two extensions of one name are one ambiguous call.
 *
 * The wheel has no way to be driven to a specific year without simulating a scroll, and the
 * year itself is not what any of these tests are about — that a year gets chosen, and that
 * the save then goes through, is.
 */
internal fun GarageTestRule.confirmModelYear() {
    onNodeWithTag(GarageTestTags.YEAR_FIELD).performScrollTo().performClick()
    awaitText(GarageCopy.DONE)
    onNodeWithText(GarageCopy.DONE).performClick()
    awaitGone(GarageCopy.DONE)
}

/**
 * Fill in the whole add-car form with a car that is not the seeded one.
 *
 * The keyboard is dismissed after the plate: it covers the lower half of the form, and a
 * tap aimed at a picker underneath it lands on a key instead.
 */
internal fun GarageTestRule.describeNewCar() {
    typeInto(GarageTestTags.REGISTRATION_FIELD, GarageFixtures.NEW_PLATE_TYPED)
    Espresso.closeSoftKeyboard()
    pickFromSheet(GarageTestTags.MAKE_FIELD, GarageCopy.MAKE_SHEET_TITLE, GarageFixtures.NEW_MAKE, searchable = true)
    pickFromSheet(GarageTestTags.MODEL_FIELD, GarageCopy.MODEL_SHEET_TITLE, GarageFixtures.NEW_MODEL, searchable = true)
    confirmModelYear()
    pickFromSheet(GarageTestTags.FUEL_FIELD, GarageCopy.FUEL_SHEET_TITLE, GarageCopy.FUEL_PETROL)
    onNodeWithTag(GarageTestTags.ODOMETER_FIELD).performScrollTo().performClick()
    awaitText(GarageCopy.ODOMETER_SAVE)
    saveOdometer(GarageFixtures.NEW_ODOMETER)
}
