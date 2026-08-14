package com.hopcape.odo

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTestTags
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceTestTags
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.profile.presentation.EditProfileTestTags
import com.hopcape.odo.feature.profile.presentation.NotificationsTestTags
import com.hopcape.odo.feature.profile.presentation.ProfileTestTags
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsTestTags
import org.koin.core.context.GlobalContext
import app.cash.sqldelight.db.SqlDriver

/**
 * The words the profile puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read: Compose Resources keeps a feature's generated `Res` internal to
 * its own module, so `:androidApp` cannot reach it. Asserting on the copy an owner actually
 * reads is the point.
 */
internal object ProfileCopy {
    const val TITLE = "Profile"
    const val EDIT = "Edit"
    const val SAVE = "Save changes"
    const val CLOSE = "Close"

    /* Rows. */
    const val NOTIFICATIONS = "Notifications"
    const val UNITS = "Units & currency"
    const val APPEARANCE = "Appearance"
    const val SIGN_IN = "Sign in to back up your data"

    /* Prompts on the card. */
    const val CITY_MISSING = "Set your city for price checks"

    /* Edit screen. */
    const val CITY_FIELD = "City"
    const val NAME_TOO_SHORT = "Names are at least 2 characters."
    const val EMAIL_INVALID = "That doesn’t look like an email address."

    /* Delete. */
    const val DELETE = "Delete my data"
    const val DELETE_TITLE = "Delete everything on this device?"
    const val DELETE_CONFIRM = "Delete everything"

    /* Notifications screen. */
    const val NOTIF_HEALTH = "Health Score changes"
    const val NOTIF_DOC_EXPIRY = "Document expiry"
    const val NOTIFY_AT_8_AM = "8 AM"

    /* Sheets. */
    const val DONE = "Done"
    const val THEME_LIGHT = "Light"
    const val THEME_DARK = "Dark"

    /** The summary under the notifications row, e.g. "4 on". */
    fun topicsOn(count: Int) = "$count on"

    /** The summary under the units row, e.g. "km · ₹". */
    fun unitsSummary(suffix: String) = "$suffix · ₹"

    /** Where first-run setup starts, which is where deleting everything lands. */
    const val WELCOME_HEADLINE = "Know what your car really costs you."
}

private typealias ProfileTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database ------------------------------ */

private fun profileDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty everything the profile reads or writes.
 *
 * The settings row goes too: it is the one table a test can leave a theme or a unit in that
 * every later test would then inherit.
 */
internal fun resetProfile() {
    resetOwnerData()
    clearAppSettings()
}

/** Back to the defaults — dark-or-light by system, kilometres, four topics on. */
internal fun clearAppSettings() = with(profileDriver()) {
    execute(null, "DELETE FROM app_settings", 0)
    notifyListeners("app_settings")
}

/** What the settings row holds, read straight from the table the app writes. */
internal fun storedTheme(): String? = readSetting("theme")

internal fun storedDistanceUnit(): String? = readSetting("distance_unit")

internal fun storedNotificationFlag(column: String): Long? =
    readSetting(column)?.toLongOrNull()

/** One column off the single settings row, as text. */
internal fun readStoredSetting(column: String): String? = readSetting(column)

private fun readSetting(column: String): String? = with(profileDriver()) {
    executeQuery(
        identifier = null,
        sql = "SELECT $column FROM app_settings WHERE id = 1",
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) {
                    cursor.getString(0) ?: cursor.getLong(0)?.toString()
                } else {
                    null
                },
            )
        },
        parameters = 0,
    ).value
}

/** The owner's stored name and city, to prove an edit reached the database. */
internal fun storedProfileField(column: String): String? = with(profileDriver()) {
    executeQuery(
        identifier = null,
        sql = "SELECT $column FROM profiles WHERE deleted_at IS NULL LIMIT 1",
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) cursor.getString(0) else null,
            )
        },
        parameters = 0,
    ).value
}

/** Whether any live car is left — what "delete my data" has to leave behind. */
internal fun liveCarCount(): Long = with(profileDriver()) {
    executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM cars WHERE deleted_at IS NULL",
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        parameters = 0,
    ).value
}

/** Seed a stored preference before the app reads it, for the "it was already set" cases. */
internal fun seedAppSettings(
    theme: ThemePreference = ThemePreference.SYSTEM,
    distanceUnit: DistanceUnit = DistanceUnit.KILOMETRE,
    fuelEfficiencyUnit: FuelEfficiencyUnit = FuelEfficiencyUnit.DISTANCE_PER_UNIT,
) = with(profileDriver()) {
    val unit = distanceUnit.name
    execute(null, "DELETE FROM app_settings", 0)
    execute(
        null,
        "INSERT INTO app_settings (id, theme, larger_text, distance_unit, fuel_efficiency_unit, " +
            "notif_doc_expiry, notif_service_due, notif_custom, notif_overcharge, notif_monthly, " +
            "notif_health_drop, notif_push, updated_at) VALUES (1, '${theme.name}', 0, '$unit', " +
            "'${fuelEfficiencyUnit.name}', 1, 1, 0, 1, 1, 0, 1, '2026-08-01T00:00:00Z')",
        0,
    )
    notifyListeners("app_settings")
}

/* ------------------------------ Navigation ------------------------------ */

/**
 * The first frame waits on the start-destination read, and on a cold start on the seed.
 *
 * Generous because this is the first wait every suite makes, so when it is too short nothing
 * else gets a chance to run: a loaded machine once took a passing auth suite from under four
 * minutes to over nine, and every one of its tests failed here rather than on what it was
 * testing. A launch that is genuinely broken still fails, only later.
 */
private const val PROFILE_START_UP_TIMEOUT_MILLIS = 45_000L

/** Open the profile from Home's avatar, which is the only way in. */
internal fun ProfileTestRule.openProfile() {
    waitUntil(PROFILE_START_UP_TIMEOUT_MILLIS) {
        onAllNodes(hasTestTag(HomeTestTags.PROFILE_BUTTON)).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag(HomeTestTags.PROFILE_BUTTON).performClick()
    awaitText(ProfileCopy.TITLE)
}

/** Back to Home, so returning proves what the profile stored. */
internal fun ProfileTestRule.leaveProfile() {
    onNodeWithContentDescription(BACK_LABEL).performClick()
    awaitGone(ProfileCopy.TITLE)
}

internal fun ProfileTestRule.openEditProfile() {
    onNodeWithText(ProfileCopy.EDIT).performClick()
    awaitText(ProfileCopy.SAVE)
}

internal fun ProfileTestRule.closeEditProfile() {
    onNodeWithContentDescription(ProfileCopy.CLOSE).performClick()
    awaitText(ProfileCopy.TITLE)
}

internal fun ProfileTestRule.openNotifications() {
    onNodeWithTag(ProfileTestTags.NOTIFICATIONS_ROW).performClick()
    awaitText(ProfileCopy.NOTIF_DOC_EXPIRY)
}

/**
 * Tap one lead chip under "Document expiry".
 *
 * By tag rather than by text: several kinds of paper offer the same "30 days", and the row
 * they sit in is the only thing that tells them apart.
 */
internal fun ProfileTestRule.toggleLeadChip(type: DocumentType, days: Int) {
    onNodeWithTag(NotificationsTestTags.leadChip(type, days)).performScrollTo().performClick()
    waitForIdle()
}

/** Pick the hour every reminder should arrive at. */
internal fun ProfileTestRule.chooseNotifyHour(label: String) {
    onNodeWithTag(NotificationsTestTags.NOTIFY_AT_FIELD).performScrollTo().performClick()
    awaitText(label)
    onNodeWithText(label).performClick()
    waitForIdle()
}

/** The owner's stored lead days, as the column holds them. */
internal fun storedLeadDays(): String? = readStoredSetting("notif_doc_leads")

/** The stored hour every reminder fires at. */
internal fun storedNotifyHour(): Long? = readStoredSetting("notif_hour")?.toLongOrNull()

internal fun ProfileTestRule.openUnitsSheet() {
    onNodeWithTag(ProfileTestTags.UNITS_ROW).performClick()
    awaitText(ProfileCopy.DONE)
}

internal fun ProfileTestRule.openAppearanceSheet() {
    onNodeWithTag(ProfileTestTags.APPEARANCE_ROW).performClick()
    awaitText(ProfileCopy.DONE)
}

internal fun ProfileTestRule.closeSheet() {
    onNodeWithText(ProfileCopy.DONE).performClick()
    awaitGone(ProfileCopy.DONE)
}

/* ------------------------------ Actions ------------------------------ */

internal fun ProfileTestRule.typeName(name: String) = replaceField(EditProfileTestTags.NAME_FIELD, name)

internal fun ProfileTestRule.typeEmail(email: String) = replaceField(EditProfileTestTags.EMAIL_FIELD, email)

/**
 * Clear the field tagged [fieldTag] and type [text] into it.
 *
 * The tag sits on the component while the node that accepts text is the `BasicTextField`
 * inside it, so the editable node is matched within the tagged subtree — the same move
 * [typeInto] makes, with a clearance first because these fields arrive already filled.
 */
private fun ProfileTestRule.replaceField(fieldTag: String, text: String) {
    val field = onNode(hasSetTextAction() and (hasTestTag(fieldTag) or hasAnyAncestor(hasTestTag(fieldTag))))
    field.performTextClearance()
    field.performTextInput(text)
    waitForIdle()
}

/** Pick a city from the dropdown, which is how a benchmark city is set at all. */
internal fun ProfileTestRule.chooseCity(city: String) {
    onNodeWithTag(EditProfileTestTags.CITY_FIELD).performScrollTo().performClick()
    awaitText(city)
    onNodeWithText(city).performClick()
    waitForIdle()
}

internal fun ProfileTestRule.saveProfile() {
    onNodeWithText(ProfileCopy.SAVE).performClick()
    waitForIdle()
}

internal fun ProfileTestRule.chooseTheme(theme: ThemePreference) {
    onNodeWithTag(AppearanceTestTags.themeCard(theme)).performClick()
    waitForIdle()
}

internal fun ProfileTestRule.chooseDistanceUnit(unit: DistanceUnit) {
    onNodeWithTag(UnitsTestTags.distanceCard(unit)).performClick()
    waitForIdle()
}

internal fun ProfileTestRule.toggleNotificationTopic(label: String) {
    onNodeWithText(label).performScrollTo().performClick()
    waitForIdle()
}

/** Start the wipe and confirm it — the only irreversible thing the app can do. */
internal fun ProfileTestRule.deleteEverything() {
    onNodeWithText(ProfileCopy.DELETE).performClick()
    awaitText(ProfileCopy.DELETE_TITLE)
    onNodeWithTag(ProfileTestTags.DELETE_CONFIRM).performClick()
    waitForIdle()
}

/* ------------------------------ Assertions ------------------------------ */

/** The summary text under a preference row, which is what says the setting took. */
internal fun ProfileTestRule.assertRowSummary(rowTag: String, text: String) {
    onNode(
        hasTestTag(rowTag) and androidx.compose.ui.test.hasAnyDescendant(
            androidx.compose.ui.test.hasText(text),
        ),
        useUnmergedTree = true,
    ).assertExists()
}

/** The back arrow every full-screen surface carries. */
private const val BACK_LABEL = "Back"

/** Take the city off the seeded profile, for the "no benchmarks yet" case. */
internal fun clearProfileCity() = with(profileDriver()) {
    execute(null, "UPDATE profiles SET city = NULL", 0)
    notifyListeners("profiles")
}

/**
 * Wait for a node whose text *contains* [text].
 *
 * Home writes the car and its reading as one line, so the reading cannot be matched on its
 * own — and the reading is the whole point of the assertion.
 */
internal fun ProfileTestRule.awaitTextContaining(text: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
}
