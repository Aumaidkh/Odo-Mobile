package com.hopcape.odo.feature.reminders.presentation

import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.feature.reminders.FakeActiveCarProvider
import com.hopcape.odo.feature.reminders.FakeDocumentRepository
import com.hopcape.odo.feature.reminders.FakeReminderRepository
import com.hopcape.odo.feature.reminders.FakeServiceLogRepository
import com.hopcape.odo.feature.reminders.FixedIdGenerator
import com.hopcape.odo.feature.reminders.RecordingScheduler
import com.hopcape.odo.feature.reminders.TEST_CLOCK
import com.hopcape.odo.feature.reminders.TEST_OWNER
import com.hopcape.odo.feature.reminders.TEST_TODAY
import com.hopcape.odo.feature.reminders.currentOdometerFrom
import com.hopcape.odo.feature.reminders.customReminder
import com.hopcape.odo.feature.reminders.document
import com.hopcape.odo.feature.reminders.domain.usecase.CreateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveCurrentOdometerUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveRemindersUseCase
import com.hopcape.odo.feature.reminders.presentation.state.Loadable
import com.hopcape.odo.feature.reminders.reading
import com.hopcape.odo.feature.reminders.silentRemindersTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        documents: FakeDocumentRepository = FakeDocumentRepository(),
        serviceLogs: FakeServiceLogRepository = FakeServiceLogRepository(),
        val reminders: FakeReminderRepository = FakeReminderRepository(),
    ) {
        val viewModel = RemindersViewModel(
            activeCar = FakeActiveCarProvider(),
            observeReminders = ObserveRemindersUseCase(
                documents = documents,
                serviceLogs = serviceLogs,
                reminders = reminders,
                currentOdometer = currentOdometerFrom(serviceLogs),
                clock = TEST_CLOCK,
                timeZone = TimeZone.UTC,
            ),
            observeOdometer = ObserveCurrentOdometerUseCase(currentOdometerFrom(serviceLogs)),
            createReminder = CreateCustomReminderUseCase(
                reminders = reminders,
                scheduler = RecordingScheduler(),
                idGenerator = FixedIdGenerator(),
                clock = TEST_CLOCK,
                timeZone = TimeZone.UTC,
            ),
            owners = { TEST_OWNER },
            telemetry = silentRemindersTelemetry(),
            clock = TEST_CLOCK,
            timeZone = TimeZone.UTC,
        )
    }

    private suspend fun Fixture.content(): RemindersContent =
        (viewModel.state.first { it.content is Loadable.Ready }.content as Loadable.Ready).value

    @Test
    fun anExpiringInsuranceLandsInThisWeek() = runTest {
        val fixture = Fixture(
            documents = FakeDocumentRepository(
                listOf(document(expiresOn = TEST_TODAY.plus(5, DateTimeUnit.DAY))),
            ),
        )

        val content = fixture.content()

        assertEquals(RemindersHeader.Attention(1), content.header)
        assertEquals(ReminderKind.INSURANCE_EXPIRY, content.thisWeek.single().id?.kind)
    }

    @Test
    fun anEmptyGarageIsCaughtUpWithSuggestions() = runTest {
        val content = Fixture().content()

        assertEquals(RemindersHeader.CaughtUp, content.header)
        assertTrue(content.thisWeek.isEmpty())
        // Every row is a suggestion — and the distance preset is absent, because there
        // is no odometer reading to anchor it at.
        assertTrue(content.upcoming.all { it.status is RowStatus.Suggested })
        assertTrue(content.upcoming.none { (it.status as RowStatus.Suggested).preset == ReminderPreset.TYRE_ROTATION })
    }

    @Test
    fun anOdometerReadingUnlocksTheDistanceSuggestion() = runTest {
        val fixture = Fixture(
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 42_000))),
        )

        val suggested = fixture.content().upcoming.mapNotNull { (it.status as? RowStatus.Suggested)?.preset }

        assertTrue(ReminderPreset.TYRE_ROTATION in suggested)
    }

    @Test
    fun suggestionTapCreatesTheReminderInPlace() = runTest {
        val fixture = Fixture(
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 42_000))),
        )
        fixture.content() // start the state flow so currentKm is live

        fixture.viewModel.onEvent(
            RemindersEvent.SuggestionTapped(ReminderPreset.TYRE_ROTATION, "Tyre rotation"),
        )

        val stored = fixture.reminders.customs.single()
        assertEquals("Tyre rotation", stored.title.value)
        assertEquals(ReminderPreset.TYRE_ROTATION, stored.preset)
        assertEquals(ReminderCadence.EveryDistance(10_000), stored.cadence)
        assertEquals(42_000, stored.anchorKm)
    }

    @Test
    fun tappingAThisWeekRowOpensItsActionsSheet() = runTest {
        val fixture = Fixture(
            documents = FakeDocumentRepository(
                listOf(document(expiresOn = TEST_TODAY.plus(5, DateTimeUnit.DAY))),
            ),
        )
        val row = fixture.content().thisWeek.single()

        fixture.viewModel.onEvent(RemindersEvent.ReminderTapped(row, "Insurance renewal", "Due in 5 days"))

        val effect = fixture.viewModel.effects.firstOrNull()
        assertIs<RemindersEffect.OpenActions>(effect)
        assertEquals(ReminderKind.INSURANCE_EXPIRY.name, effect.kind)
        assertEquals("Insurance renewal", effect.title)
    }

    @Test
    fun aPausedCustomStaysOffTheListButKeepsItsPresetClaimed() = runTest {
        val fixture = Fixture(
            reminders = FakeReminderRepository(
                listOf(customReminder(preset = ReminderPreset.BATTERY, paused = true)),
            ),
        )

        val content = fixture.content()

        assertTrue(content.upcoming.none { it.title is RowText.Plain })
        assertTrue(
            content.upcoming.none { (it.status as? RowStatus.Suggested)?.preset == ReminderPreset.BATTERY },
        )
    }

    @Test
    fun manageAndAddNavigate() = runTest {
        val fixture = Fixture()

        fixture.viewModel.onEvent(RemindersEvent.ManageTapped)
        assertIs<RemindersEffect.OpenSettings>(fixture.viewModel.effects.firstOrNull())

        fixture.viewModel.onEvent(RemindersEvent.AddTapped)
        assertIs<RemindersEffect.OpenNew>(fixture.viewModel.effects.firstOrNull())
    }
}
