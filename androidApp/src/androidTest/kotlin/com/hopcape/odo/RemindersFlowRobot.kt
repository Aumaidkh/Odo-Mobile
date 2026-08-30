package com.hopcape.odo

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTestTags
import com.hopcape.odo.feature.reminders.presentation.RemindersTestTags
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The words the reminders screens put on screen, mirrored from the feature's
 * `strings.xml`. Copied rather than read, for the same reason as [VaultCopy]: Compose
 * Resources keeps a feature's generated `Res` internal to its own module. Typographic
 * apostrophes (’) come straight from the resource file.
 */
internal object RemindersCopy {
    const val TITLE = "Reminders"
    const val MANAGE = "Manage"

    const val CAUGHT_UP_TITLE = "You’re all caught up"
    const val ATTENTION_BODY = "Tap a reminder below to act on it."

    const val SECTION_THIS_WEEK = "THIS WEEK"
    const val SECTION_UPCOMING = "SET UP REMINDERS"

    const val PILL_ON_TRACK = "On track"
    const val REMIND_ME = "Remind me"

    /* Rows. */
    const val ROW_INSURANCE = "Insurance renewal"
    const val ROW_SERVICE = "Service due"
    const val DUE_TODAY_PLAIN = "Due today"

    /* Suggestions (titles double as the created reminders' names). */
    const val SUGGEST_AIR = "Air pressure check"
    const val SUGGEST_COOLANT = "Coolant top-up"
    const val SUGGEST_TYRE = "Tyre rotation"
    const val SUGGESTED_EVERY_15 = "Suggested · every 15 days"
    const val SUGGESTED_EVERY_10K = "Suggested · every 10,000 km"

    /* Actions sheet. */
    const val SHEET_RESCHEDULE = "Reschedule reminder"
    const val SHEET_SKIP = "Skip this one"
    const val SHEET_TURN_OFF = "Turn off this reminder"

    /* Create/edit form. */
    const val NEW_TITLE = "New reminder"
    const val EDIT_TITLE = "Edit reminder"
    const val CHIP_COOLANT = "Coolant"
    const val CHIP_MONTHLY = "Monthly"
    const val SAVE = "Save reminder"
    const val ERROR_NAME_BLANK = "Give it a name"
    const val EDITED_NAME = "Air filter check"

    /* Settings. */
    const val SETTINGS_TITLE = "Reminder settings"
    const val SETTINGS_WHATSAPP_SOON = "Coming soon"
    const val SETTINGS_CUSTOM = "Custom reminders"
    const val SETTINGS_EMAIL = "Email"
    const val SETTINGS_BEFORE = "REMIND ME BEFORE"

    /** `"%1$d reminder needs attention"` / plural. */
    fun attentionTitle(count: Int) =
        if (count == 1) "$count reminder needs attention" else "$count reminders need attention"

    /** `"Due in %1$d days · %2$s"` — a document renewal's line. */
    fun dueInDays(days: Int, date: LocalDate) = "Due in $days days · ${formatted(date)}"

    /** `"%1$d days overdue"` — the service row past its interval. */
    fun serviceOverdueDays(days: Long) = "$days days overdue"

    /** `"At %1$s"` — a distance target's line, in the app's km format. */
    fun atKm(km: Int) = "At ${grouped(km)} km"

    /** The app's own date format ("3 Jul 2027"), mirrored from `core.domain.shared.formatDate`. */
    private fun formatted(date: LocalDate) = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"

    /** Indian digit grouping as the distance format prints it ("50,000"). */
    private fun grouped(km: Int): String {
        val digits = km.toString()
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        return rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
    }

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}

/**
 * The seeded rows the reminder tests stand on.
 *
 * Dates are **relative to today**: the feed resolves urgency against the device clock, so
 * a fixed date would drift into a different bucket and start failing on its own.
 */
internal object ReminderFixtures {
    const val INSURANCE_ID = "doc-rem-insurance"

    /** Inside the renewal window and inside the week — the this-week row. */
    const val INSURANCE_DAYS_LEFT = 5
    val INSURANCE_EXPIRY: LocalDate get() = LocalDate.now().plusDays(INSURANCE_DAYS_LEFT.toLong())

    /** Far enough back that the six-month interval has lapsed. */
    const val OVERDUE_LOG_ID = "log-rem-overdue"
    val OVERDUE_SERVICE_DATE: LocalDate get() = LocalDate.now().minusMonths(7)

    /** How overdue that makes the car, computed the way ServiceIntervalPolicy computes it. */
    val OVERDUE_DAYS: Long
        get() = ChronoUnit.DAYS.between(OVERDUE_SERVICE_DATE.plusMonths(6), LocalDate.now())

    /** The seeded car's baseline reading plus the tyre preset's 10,000 km step. */
    const val TYRE_TARGET_KM = LogFixtures.CAR_ODOMETER + 10_000
}

private typealias RemindersTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database seeding ------------------------------ */

private fun reminderDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty everything the reminder flows read or write: the owner's tables ([resetOwnerData]),
 * the vault's documents (renewals derive from them), the local `reminders` mirror, and the
 * device's `app_settings` (the settings screen edits it, and one test's toggle must not
 * leak into the next).
 */
internal fun resetReminders() {
    resetVault()
    with(reminderDriver()) {
        execute(null, "DELETE FROM reminders", 0)
        execute(null, "DELETE FROM app_settings", 0)
        notifyListeners("reminders")
        notifyListeners("app_settings")
    }
}

/**
 * One old service entry, dated relative to today so the interval stays lapsed. Written
 * straight to the database — the service-log flow has its own end-to-end test, and this
 * suite only needs the state it leaves behind.
 */
internal fun seedOverdueService() {
    with(reminderDriver()) {
        execute(
            null,
            "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, " +
                "total_amount_paise, source, created_at, updated_at, sync_status) VALUES " +
                "('${ReminderFixtures.OVERDUE_LOG_ID}', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
                "'${ReminderFixtures.OVERDUE_SERVICE_DATE}', 42000, 250000, 'MANUAL', " +
                "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 'PENDING')",
            0,
        )
        notifyListeners("service_logs")
    }
}

/** One `app_settings` flag, read straight from the row the settings screen writes. */
internal fun settingsFlag(column: String): Long? = reminderDriver().executeQuery(
    null,
    "SELECT $column FROM app_settings WHERE id = 1",
    { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
    0,
).value

/* ------------------------------ Navigation ------------------------------ */

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val REMINDERS_START_UP_TIMEOUT_MILLIS = 20_000L

/**
 * Walk from Home to reminders the way an owner reaches them: the bell in the header.
 * Reminders are not a bar tab on purpose (the destination doc says why), so the bell is
 * part of what this suite proves.
 */
internal fun RemindersTestRule.openReminders() {
    waitUntil(REMINDERS_START_UP_TIMEOUT_MILLIS) {
        onAllNodes(hasTestTag(HomeTestTags.BELL_BUTTON)).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag(HomeTestTags.BELL_BUTTON).performClick()
    awaitText(RemindersCopy.TITLE)
}

/** Open the settings screen from the home's "Manage". */
internal fun RemindersTestRule.openReminderSettings() {
    onNodeWithText(RemindersCopy.MANAGE).performClick()
    awaitText(RemindersCopy.SETTINGS_TITLE)
}

/** Tap a this-week card by its title and wait for its actions sheet. */
internal fun RemindersTestRule.openActionsFor(title: String) {
    onNodeWithText(title).performClick()
    awaitText(RemindersCopy.SHEET_TURN_OFF)
}

/** Tap one suggestion's "Remind me", named by its preset. Scrolled to first: the
 *  suggestions sit at the bottom of the upcoming card. */
internal fun RemindersTestRule.tapRemindMeOn(presetName: String) {
    onNodeWithTag(RemindersTestTags.remindMe(presetName)).performScrollTo().performClick()
}
