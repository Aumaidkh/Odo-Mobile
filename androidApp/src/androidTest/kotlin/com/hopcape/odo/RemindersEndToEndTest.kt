package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.feature.reminders.presentation.RemindersTestTags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every reminders flow an owner can reach today, driven against the real app: the real
 * Koin graph, the real SQLite database (the `reminders` mirror included), the real
 * navigation graph and the real ViewModels.
 *
 * The seams are what this suite exists for. The feed is derived across three
 * repositories, the actions sheet rebuilds its identity from navigation primitives, and
 * the settings screen shares a store with the profile — all places a unit test cannot
 * break and an owner can.
 *
 * **What is seeded and why.** Documents and service history are written straight to the
 * database — their own flows have their own end-to-end tests, and this suite only needs
 * the state they leave behind. Custom reminders are never seeded: creating them *is* a
 * flow under test, so every custom reminder here is made by tapping.
 *
 * **What is deliberately not covered:** notification delivery (the scheduler is a no-op
 * until the M4 engine) and sync (the fake remote stands in; the sync engine has its own
 * coverage).
 */
@RunWith(AndroidJUnit4::class)
class RemindersEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Start every test from a set-up device with no reminders and default settings. */
    @Before
    fun startFromASetUpDevice() {
        resetReminders()
        seedOnboardedOwner()
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------------ The list ------------------------------ */

    @Test
    fun aQuietCarIsCaughtUpAndSuggestsThePresets() {
        rule.openReminders()

        rule.awaitText(RemindersCopy.CAUGHT_UP_TITLE)
        // The five presets are offered, each with its cadence and an opt-in. The
        // distance one is here because onboarding recorded a baseline reading.
        rule.onNodeWithText(RemindersCopy.SUGGEST_AIR).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(RemindersCopy.SUGGESTED_EVERY_15).assertExists()
        rule.onNodeWithText(RemindersCopy.SUGGEST_COOLANT).assertExists()
        rule.onNodeWithText(RemindersCopy.SUGGEST_TYRE).assertExists()
        rule.onNodeWithText(RemindersCopy.SUGGESTED_EVERY_10K).assertExists()
    }

    @Test
    fun anExpiringInsuranceLandsInThisWeek() {
        seedDocument(
            id = ReminderFixtures.INSURANCE_ID,
            type = DocumentType.INSURANCE,
            expiresOn = ReminderFixtures.INSURANCE_EXPIRY,
        )
        rule.openReminders()

        rule.awaitText(RemindersCopy.attentionTitle(1))
        rule.onNodeWithText(RemindersCopy.ATTENTION_BODY).assertIsDisplayed()
        rule.onNodeWithText(RemindersCopy.SECTION_THIS_WEEK).assertIsDisplayed()
        rule.onNodeWithText(RemindersCopy.ROW_INSURANCE).assertIsDisplayed()
        rule.onNodeWithText(
            RemindersCopy.dueInDays(ReminderFixtures.INSURANCE_DAYS_LEFT, ReminderFixtures.INSURANCE_EXPIRY),
        ).assertIsDisplayed()
    }

    @Test
    fun aLapsedServiceIntervalSaysHowFarOverdue() {
        seedOverdueService()
        rule.openReminders()

        rule.awaitText(RemindersCopy.attentionTitle(1))
        rule.onNodeWithText(RemindersCopy.ROW_SERVICE).assertIsDisplayed()
        rule.onNodeWithText(RemindersCopy.serviceOverdueDays(ReminderFixtures.OVERDUE_DAYS))
            .assertIsDisplayed()
    }

    /* ------------------------------ The actions sheet ------------------------------ */

    @Test
    fun skippingANudgeHidesItUntilTheNextOne() {
        seedDocument(
            id = ReminderFixtures.INSURANCE_ID,
            type = DocumentType.INSURANCE,
            expiresOn = ReminderFixtures.INSURANCE_EXPIRY,
        )
        rule.openReminders()
        rule.awaitText(RemindersCopy.ROW_INSURANCE)

        rule.openActionsFor(RemindersCopy.ROW_INSURANCE)

        // A derived reminder's date comes from the document; there is nothing to move.
        rule.onNodeWithText(RemindersCopy.SHEET_RESCHEDULE).assertDoesNotExist()

        rule.onNodeWithText(RemindersCopy.SHEET_SKIP).performClick()

        // The dismissal is a stored row keyed on this occurrence; the feed looks past it.
        rule.awaitText(RemindersCopy.CAUGHT_UP_TITLE)
        rule.onNodeWithText(RemindersCopy.ROW_INSURANCE).assertDoesNotExist()
    }

    @Test
    fun turningOffADerivedKindFlipsItsTopicAndTheListStaysHonest() {
        seedDocument(
            id = ReminderFixtures.INSURANCE_ID,
            type = DocumentType.INSURANCE,
            expiresOn = ReminderFixtures.INSURANCE_EXPIRY,
        )
        rule.openReminders()
        rule.awaitText(RemindersCopy.ROW_INSURANCE)

        rule.openActionsFor(RemindersCopy.ROW_INSURANCE)
        rule.onNodeWithText(RemindersCopy.SHEET_TURN_OFF).performClick()

        // No per-reminder mute exists, so "turn off" is the document-expiry topic — the
        // same switch the settings screen shows.
        rule.awaitText(RemindersCopy.TITLE)
        assertEquals(0L, settingsFlag("notif_doc_expiry"))
        // The list keeps showing the truth: the policy still lapses in five days. Only
        // the pushes stop.
        rule.onNodeWithText(RemindersCopy.ROW_INSURANCE).assertIsDisplayed()
    }

    /* ------------------------------ Suggestions ------------------------------ */

    @Test
    fun remindMeCreatesThePresetInPlace() {
        rule.openReminders()
        rule.awaitText(RemindersCopy.SUGGEST_AIR)

        rule.tapRemindMeOn(ReminderPreset.AIR_PRESSURE.name)

        // The new reminder starts today, so it lands in this week — and the suggestion
        // it came from is gone, because the preset is now taken.
        rule.awaitText(RemindersCopy.SECTION_THIS_WEEK)
        rule.onNodeWithText(RemindersCopy.DUE_TODAY_PLAIN).assertExists()
        rule.onNodeWithText(RemindersCopy.SUGGESTED_EVERY_15).assertDoesNotExist()
    }

    @Test
    fun theDistancePresetAnchorsAtTheCarsReading()  {
        rule.openReminders()
        rule.awaitText(RemindersCopy.SUGGEST_TYRE)

        rule.tapRemindMeOn(ReminderPreset.TYRE_ROTATION.name)

        // Anchored at the seeded 40,000 km baseline plus the preset's 10,000 km step;
        // 10,000 km out is comfortably on track.
        rule.awaitText(RemindersCopy.atKm(ReminderFixtures.TYRE_TARGET_KM))
        rule.onNodeWithText(RemindersCopy.PILL_ON_TRACK).assertExists()
    }

    /* ------------------------------ The create form ------------------------------ */

    @Test
    fun addingACustomReminderFromTheForm() {
        rule.openReminders()
        rule.awaitText(RemindersCopy.TITLE)

        rule.onNodeWithTag(RemindersTestTags.ADD_FAB).performClick()
        rule.awaitText(RemindersCopy.NEW_TITLE)

        // Picking a topic prefills the name; the cadence is the owner's choice.
        rule.onNodeWithText(RemindersCopy.CHIP_COOLANT).performClick()
        rule.onNodeWithText(RemindersCopy.CHIP_MONTHLY).performClick()
        rule.onNodeWithText(RemindersCopy.SAVE).performClick()

        // Started today, so it is due today, on the list the owner lands back on.
        rule.awaitText(RemindersCopy.SUGGEST_COOLANT)
        rule.onNodeWithText(RemindersCopy.DUE_TODAY_PLAIN).assertExists()
    }

    @Test
    fun aNamelessReminderIsRefusedOnTheField() {
        rule.openReminders()
        rule.awaitText(RemindersCopy.TITLE)
        rule.onNodeWithTag(RemindersTestTags.ADD_FAB).performClick()
        rule.awaitText(RemindersCopy.NEW_TITLE)

        // The form opens with no name typed; saving without one is the mistake.
        rule.onNodeWithText(RemindersCopy.SAVE).performClick()

        rule.awaitText(RemindersCopy.ERROR_NAME_BLANK)
        rule.onNodeWithText(RemindersCopy.NEW_TITLE).assertIsDisplayed()
    }

    /* ------------------------------ Editing ------------------------------ */

    @Test
    fun rescheduleOpensTheEditFormAndSavesInPlace() {
        rule.openReminders()
        rule.awaitText(RemindersCopy.SUGGEST_AIR)
        rule.tapRemindMeOn(ReminderPreset.AIR_PRESSURE.name)
        rule.awaitText(RemindersCopy.SECTION_THIS_WEEK)

        rule.openActionsFor(RemindersCopy.SUGGEST_AIR)
        rule.onNodeWithText(RemindersCopy.SHEET_RESCHEDULE).performClick()

        // The form opens in edit mode, prefilled with the reminder being moved.
        rule.awaitText(RemindersCopy.EDIT_TITLE)
        rule.onNodeWithText(RemindersCopy.SUGGEST_AIR).assertExists()

        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput(RemindersCopy.EDITED_NAME)
        rule.onNodeWithText(RemindersCopy.SAVE).performClick()

        // Same reminder, new name — an edit, not a second row.
        rule.awaitText(RemindersCopy.EDITED_NAME)
        rule.onNodeWithText(RemindersCopy.SUGGEST_AIR).assertDoesNotExist()
    }

    @Test
    fun turningOffACustomReminderPausesItWithoutReSuggestingIt() {
        rule.openReminders()
        rule.awaitText(RemindersCopy.SUGGEST_AIR)
        rule.tapRemindMeOn(ReminderPreset.AIR_PRESSURE.name)
        rule.awaitText(RemindersCopy.SECTION_THIS_WEEK)

        rule.openActionsFor(RemindersCopy.SUGGEST_AIR)
        rule.onNodeWithText(RemindersCopy.SHEET_TURN_OFF).performClick()

        // Paused, not deleted: the row leaves the list, but the preset stays claimed —
        // suggesting it again would argue with a decision the owner just made.
        rule.awaitGone(RemindersCopy.SUGGEST_AIR)
        rule.onNodeWithText(RemindersCopy.SUGGESTED_EVERY_15).assertDoesNotExist()
    }

    /* ------------------------------ Settings ------------------------------ */

    @Test
    fun settingsShowOnlyWhatIsRealAndPersistWhatIsToggled() {
        rule.openReminders()
        rule.awaitText(RemindersCopy.TITLE)
        rule.openReminderSettings()

        // WhatsApp waits, disabled, and says so. Email and the global lead time are gone
        // — one was cut with the profile work, the other is per-type policy, not a knob.
        rule.onNodeWithText(RemindersCopy.SETTINGS_WHATSAPP_SOON).assertIsDisplayed()
        rule.onNodeWithText(RemindersCopy.SETTINGS_EMAIL).assertDoesNotExist()
        rule.onNodeWithText(RemindersCopy.SETTINGS_BEFORE).assertDoesNotExist()
        rule.onNodeWithText(RemindersCopy.SETTINGS_CUSTOM).assertExists()

        rule.onNodeWithTag(RemindersTestTags.settingsToggle("SERVICE")).performScrollTo().performClick()

        // The toggle wrote through to the same device store the profile screen edits.
        rule.waitUntil { settingsFlag("notif_service_due") == 0L }

        // Still off after the process would have re-read it — the row, not memory.
        rule.activityRule.scenario.recreate()
        rule.openReminders()
        rule.awaitText(RemindersCopy.TITLE)
        rule.openReminderSettings()
        rule.onNodeWithTag(RemindersTestTags.settingsToggle("SERVICE")).performScrollTo().assertIsOff()
    }
}
