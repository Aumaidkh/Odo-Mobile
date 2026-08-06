package com.hopcape.odo.feature.reminders.presentation.actions

import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.feature.reminders.FakeActiveCarProvider
import com.hopcape.odo.feature.reminders.FakeAppSettingsRepository
import com.hopcape.odo.feature.reminders.FakeReminderRepository
import com.hopcape.odo.feature.reminders.RecordingScheduler
import com.hopcape.odo.feature.reminders.TEST_TODAY
import com.hopcape.odo.feature.reminders.customReminder
import com.hopcape.odo.feature.reminders.domain.usecase.DismissReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.SetReminderPausedUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.silentRemindersTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderActionsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        kind: String,
        dueOn: String?,
        customId: String?,
        val reminders: FakeReminderRepository = FakeReminderRepository(),
        val settings: FakeAppSettingsRepository = FakeAppSettingsRepository(AppSettings()),
    ) {
        val viewModel = ReminderActionsViewModel(
            args = ReminderActionsArgs(kind = kind, dueOn = dueOn, customId = customId),
            activeCar = FakeActiveCarProvider(),
            dismissReminder = DismissReminderUseCase(reminders),
            setPaused = SetReminderPausedUseCase(reminders, RecordingScheduler()),
            observeSettings = ObserveReminderSettingsUseCase(settings),
            updateSettings = UpdateReminderSettingsUseCase(settings),
            telemetry = silentRemindersTelemetry(),
        )
    }

    @Test
    fun snoozeRecordsTheOccurrenceAndCloses() = runTest {
        val fixture = Fixture(kind = "INSURANCE_EXPIRY", dueOn = TEST_TODAY.toString(), customId = null)

        fixture.viewModel.onEvent(ReminderActionsEvent.SnoozeTapped)

        val dismissal = fixture.reminders.recordedDismissals.single()
        assertEquals(ReminderKind.INSURANCE_EXPIRY, dismissal.kind)
        assertEquals(TEST_TODAY, dismissal.dueOn)
        assertIs<ReminderActionsEffect.Close>(fixture.viewModel.effects.firstOrNull())
    }

    @Test
    fun snoozingACustomOccurrenceNamesItsReminder() = runTest {
        val fixture = Fixture(kind = "CUSTOM", dueOn = TEST_TODAY.toString(), customId = "rem-1")

        fixture.viewModel.onEvent(ReminderActionsEvent.SnoozeTapped)

        assertEquals("rem-1", fixture.reminders.recordedDismissals.single().customId?.value)
    }

    @Test
    fun turnOffPausesACustomReminder() = runTest {
        val fixture = Fixture(
            kind = "CUSTOM",
            dueOn = TEST_TODAY.toString(),
            customId = "rem-1",
            reminders = FakeReminderRepository(listOf(customReminder(id = "rem-1"))),
        )

        fixture.viewModel.onEvent(ReminderActionsEvent.TurnOffTapped)

        assertTrue(fixture.reminders.customs.single().paused)
        assertIs<ReminderActionsEffect.Close>(fixture.viewModel.effects.firstOrNull())
    }

    @Test
    fun turnOffFlipsTheTopicForADerivedKind() = runTest {
        val fixture = Fixture(kind = "PUC_EXPIRY", dueOn = TEST_TODAY.toString(), customId = null)

        fixture.viewModel.onEvent(ReminderActionsEvent.TurnOffTapped)

        assertTrue(!fixture.settings.settings.notifications.documentExpiry)
        // Turning off a document nudge must not touch the service topic.
        assertTrue(fixture.settings.settings.notifications.serviceDue)
    }

    @Test
    fun rescheduleOpensTheEditFormForACustomReminder() = runTest {
        val fixture = Fixture(kind = "CUSTOM", dueOn = null, customId = "rem-1")

        fixture.viewModel.onEvent(ReminderActionsEvent.RescheduleTapped)

        val effect = fixture.viewModel.effects.first()
        assertIs<ReminderActionsEffect.OpenEdit>(effect)
        assertEquals("rem-1", effect.reminderId)
    }

    @Test
    fun theSheetKnowsWhichRowsToShow() {
        // A derived reminder with a date: snooze yes, reschedule no.
        val derived = Fixture(kind = "SERVICE_DUE_TIME", dueOn = TEST_TODAY.toString(), customId = null)
        assertTrue(derived.viewModel.canSnooze)
        assertTrue(!derived.viewModel.canReschedule)

        // A distance-target custom: no date to snooze, but a schedule to move.
        val distance = Fixture(kind = "CUSTOM", dueOn = null, customId = "rem-1")
        assertTrue(!distance.viewModel.canSnooze)
        assertTrue(distance.viewModel.canReschedule)
    }
}
