package com.hopcape.odo

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.feature.profile.presentation.ProfileTestTags
import org.koin.core.context.GlobalContext

/**
 * The words the Privacy & permissions screen puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason [ProfileCopy] is: Compose Resources keeps a
 * feature's generated `Res` internal to its own module. Asserting on the copy an owner
 * actually reads is the point, and on this screen the copy *is* the product — a privacy
 * notice that says the wrong thing is the bug.
 */
internal object PrivacyCopy {
    const val TITLE = "Privacy & permissions"

    /* Device access. Read-only rows — Android owns these switches. */
    const val CAMERA = "Camera"
    const val LOCATION = "Location"
    const val NOTIFICATIONS = "Notifications"
    const val FILES = "Files"
    const val FILES_STATE = "Asked each time"
    const val MANAGED = "Managed by Android. Change these in system settings."

    /* The three switches. */
    const val SHARE_PRICES = "Share prices anonymously"
    const val KEEP_ROUTES = "Keep trip routes"
    const val USAGE_ANALYTICS = "Usage analytics"
    const val ROUTES_OFF = "Off — only distance is stored"
    const val ROUTES_ON = "On — start and end points are stored on this phone"

    /* Footer. */
    const val POLICY = "Privacy policy"
    const val DELETE_ACCOUNT = "Delete my account & data"

    /* The policy screen behind it. */
    const val POLICY_SHORT_VERSION = "The short version"
    const val POLICY_READ_FULL = "Read the full policy"

    /* The delete flow. */
    const val DELETE_HEADING = "This cannot be undone"
    const val DELETE_ACTION = "Delete everything"
    const val DELETE_CANCEL = "Cancel"

    /** What has to be typed before the delete button unlocks. */
    const val DELETE_PHRASE = "Delete my account"
}

private typealias PrivacyTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database ------------------------------ */

private fun privacyDriver(): SqlDriver = GlobalContext.get().get()

/**
 * What a privacy switch actually stored, read straight from the table the app writes.
 *
 * The whole point of an end-to-end test here: a switch that moves on screen and does not
 * reach the database is exactly the failure this screen cannot have.
 */
internal fun storedPrivacyFlag(column: String): Long? = with(privacyDriver()) {
    executeQuery(
        identifier = null,
        sql = "SELECT $column FROM app_settings WHERE id = 1",
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) cursor.getLong(0) else null,
            )
        },
        parameters = 0,
    ).value
}

/** Whether the owner's prices may feed the city benchmark — stored on the profile, not settings. */
internal fun storedSharesPrices(): Long? = with(privacyDriver()) {
    executeQuery(
        identifier = null,
        sql = "SELECT shares_prices FROM profiles WHERE deleted_at IS NULL LIMIT 1",
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) cursor.getLong(0) else null,
            )
        },
        parameters = 0,
    ).value
}

/**
 * Empty the two tables the routes switch touches.
 *
 * `resetProfile()` does not reach these — trips outlive a profile wipe in the app itself, so
 * a suite that seeded one would leak it into the next test's counts.
 */
internal fun resetTrips() = with(privacyDriver()) {
    execute(null, "DELETE FROM trips", 0)
    execute(null, "DELETE FROM parked_locations", 0)
    notifyListeners("trips", "parked_locations")
}

/**
 * A finished trip with both coordinates, plus the parked location it left behind.
 *
 * What "Keep trip routes" has to erase. Written directly rather than driven through the
 * tracker: that needs GPS, a foreground service and several minutes of simulated driving, and
 * none of it is what this test is about.
 */
internal fun seedTripWithRoute() = with(privacyDriver()) {
    execute(
        null,
        "INSERT INTO trips (id, car_id, owner_id, started_at, ended_at, distance_m, estimated_m, " +
            "mode, status, start_lat, start_lon, end_lat, end_lon, created_at, updated_at, sync_status) " +
            "VALUES ('$TRIP_ID', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
            "'2026-08-10T09:00:00Z', '2026-08-10T09:20:00Z', $TRIP_DISTANCE_M, $TRIP_DISTANCE_M, " +
            "'BT_VERIFIED', 'COUNTED', 18.52, 73.85, 18.60, 73.90, " +
            "'2026-08-10T09:20:00Z', '2026-08-10T09:20:00Z', 'PENDING')",
        0,
    )
    execute(
        null,
        "INSERT OR REPLACE INTO parked_locations (car_id, lat, lon, recorded_at) " +
            "VALUES ('${LogFixtures.CAR}', 18.60, 73.90, '2026-08-10T09:20:00Z')",
        0,
    )
    notifyListeners("trips", "parked_locations")
}

/** How many stored trips still carry a start coordinate. */
internal fun tripsWithCoordinates(): Long = countOf(
    "SELECT COUNT(*) FROM trips WHERE start_lat IS NOT NULL OR end_lat IS NOT NULL",
)

/** How many parked locations are on file — a coordinate the app kept, so the switch clears them. */
internal fun parkedLocationCount(): Long = countOf("SELECT COUNT(*) FROM parked_locations")

/** The distance on the seeded trip, which the purge must leave alone. */
internal fun seededTripDistance(): Long = countOf("SELECT distance_m FROM trips WHERE id = '$TRIP_ID'")

/** Whether any live profile row is left — what a full delete has to leave behind. */
internal fun liveProfileCount(): Long =
    countOf("SELECT COUNT(*) FROM profiles WHERE deleted_at IS NULL")

private fun countOf(sql: String): Long = with(privacyDriver()) {
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L,
            )
        },
        parameters = 0,
    ).value
}

private const val TRIP_ID = "trip-with-route"
private const val TRIP_DISTANCE_M = 12_000L

/* ------------------------------ Navigation ------------------------------ */

internal fun PrivacyTestRule.openPrivacy() {
    onNodeWithTag(ProfileTestTags.PRIVACY_ROW).performScrollTo().performClick()
    awaitText(PrivacyCopy.TITLE)
}

internal fun PrivacyTestRule.openPrivacyPolicy() {
    onNodeWithText(PrivacyCopy.POLICY).performScrollTo().performClick()
    awaitText(PrivacyCopy.POLICY_SHORT_VERSION)
}

internal fun PrivacyTestRule.openDeleteAccount() {
    onNodeWithTag(ProfileTestTags.PRIVACY_DELETE_ACCOUNT).performScrollTo().performClick()
    awaitText(PrivacyCopy.DELETE_HEADING)
}

/* ------------------------------ Actions ------------------------------ */

/**
 * Move one of the three switches.
 *
 * By tag, not by label: the switch is a `toggleable` row whose text sits in a sibling node,
 * so clicking the label would sometimes hit the text and sometimes the row.
 */
internal fun PrivacyTestRule.togglePrivacySwitch(tag: String) {
    onNodeWithTag(tag).performScrollTo().performClick()
    waitForIdle()
}

/**
 * Type the confirmation phrase into the delete screen's field.
 *
 * The tag sits on the component while the node that accepts text is the `BasicTextField`
 * inside it, so the editable node is matched within the tagged subtree.
 */
internal fun PrivacyTestRule.typeDeleteConfirmation(phrase: String) {
    val field = onNode(
        hasSetTextAction() and
            (
                hasTestTag(ProfileTestTags.DELETE_ACCOUNT_PHRASE) or
                    hasAnyAncestor(hasTestTag(ProfileTestTags.DELETE_ACCOUNT_PHRASE))
                ),
    )
    field.performTextClearance()
    field.performTextInput(phrase)
    waitForIdle()
}

/**
 * Type the phrase and press delete — the whole irreversible act.
 *
 * On a device with no session this wipes immediately; there is no code step, because there is
 * no account to prove anything against.
 */
internal fun PrivacyTestRule.deleteAccountForReal() {
    typeDeleteConfirmation(PrivacyCopy.DELETE_PHRASE)
    onNodeWithTag(ProfileTestTags.DELETE_ACCOUNT_CONFIRM).performClick()
    waitForIdle()
}

/* ------------------------------ Assertions ------------------------------ */

/**
 * A device-access row shows [state].
 *
 * Matched inside the tagged row rather than anywhere on screen, because "Allowed" legitimately
 * appears on three rows at once and a bare text assertion would pass on the wrong one.
 * `useUnmergedTree` because the row merges its children into one semantics node.
 */
internal fun PrivacyTestRule.assertAccessRow(rowTag: String, state: String) {
    onNode(hasTestTag(rowTag) and hasAnyDescendant(hasText(state)), useUnmergedTree = true)
        .assertExists()
}
