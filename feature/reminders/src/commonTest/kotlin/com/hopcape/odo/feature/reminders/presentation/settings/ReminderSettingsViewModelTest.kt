package com.hopcape.odo.feature.reminders.presentation.settings

import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.feature.reminders.FakeAppSettingsRepository
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateReminderSettingsUseCase
import com.hopcape.odo.feature.reminders.presentation.state.Loadable
import com.hopcape.odo.feature.reminders.silentRemindersTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        initial: AppSettings = AppSettings(),
    ) {
        val repository = FakeAppSettingsRepository(initial)
        val viewModel = ReminderSettingsViewModel(
            observeSettings = ObserveReminderSettingsUseCase(repository),
            updateSettings = UpdateReminderSettingsUseCase(repository),
            telemetry = silentRemindersTelemetry(),
        )
    }

    private suspend fun Fixture.content(): ReminderSettingsContent =
        (viewModel.state.first { it.content is Loadable.Ready }.content as Loadable.Ready).value

    @Test
    fun projectsTheStoredPreferences() = runTest {
        val fixture = Fixture(
            AppSettings(
                notifications = NotificationPreferences(
                    documentExpiry = false,
                    customReminders = true,
                    whatsapp = true,
                ),
            ),
        )

        val content = fixture.content()

        assertTrue(!content.documents)
        assertTrue(content.custom)
        assertTrue(content.whatsapp)
    }

    @Test
    fun aToggleWritesThroughAndTheSwitchFollowsTheStore() = runTest {
        val fixture = Fixture()
        fixture.content()

        fixture.viewModel.onEvent(ReminderSettingsEvent.Toggled(ReminderToggle.SERVICE))

        assertTrue(!fixture.repository.settings.notifications.serviceDue)
        assertTrue(!fixture.content().service)
    }

    @Test
    fun togglingATopicLeavesEveryOtherSettingAlone() = runTest {
        val fixture = Fixture()
        fixture.content()

        fixture.viewModel.onEvent(ReminderSettingsEvent.Toggled(ReminderToggle.PARTNER))

        val stored = fixture.repository.settings.notifications
        assertTrue(stored.partnerOffers)
        assertEquals(NotificationPreferences().copy(partnerOffers = true), stored)
    }
}
