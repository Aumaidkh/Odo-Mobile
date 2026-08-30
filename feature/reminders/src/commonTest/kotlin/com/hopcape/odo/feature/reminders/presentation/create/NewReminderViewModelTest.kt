package com.hopcape.odo.feature.reminders.presentation.create

import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.feature.reminders.FakeActiveCarProvider
import com.hopcape.odo.feature.reminders.FakeReminderRepository
import com.hopcape.odo.feature.reminders.FakeServiceLogRepository
import com.hopcape.odo.feature.reminders.FixedIdGenerator
import com.hopcape.odo.feature.reminders.RecordingScheduler
import com.hopcape.odo.feature.reminders.TEST_CLOCK
import com.hopcape.odo.feature.reminders.TEST_OWNER
import com.hopcape.odo.feature.reminders.TEST_TODAY
import com.hopcape.odo.feature.reminders.currentOdometerFrom
import com.hopcape.odo.feature.reminders.customReminder
import com.hopcape.odo.feature.reminders.domain.usecase.CreateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveCurrentOdometerUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.ObserveCustomReminderUseCase
import com.hopcape.odo.feature.reminders.domain.usecase.UpdateCustomReminderUseCase
import com.hopcape.odo.feature.reminders.reading
import com.hopcape.odo.feature.reminders.silentRemindersTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NewReminderViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        reminderId: String? = null,
        suggestedPreset: String? = null,
        suggestedName: String? = null,
        val reminders: FakeReminderRepository = FakeReminderRepository(),
        serviceLogs: FakeServiceLogRepository = FakeServiceLogRepository(),
    ) {
        val viewModel = NewReminderViewModel(
            args = NewReminderArgs(reminderId, suggestedPreset, suggestedName),
            activeCar = FakeActiveCarProvider(),
            owners = { TEST_OWNER },
            observeOdometer = ObserveCurrentOdometerUseCase(currentOdometerFrom(serviceLogs)),
            observeReminder = ObserveCustomReminderUseCase(reminders),
            createReminder = CreateCustomReminderUseCase(
                reminders = reminders,
                scheduler = RecordingScheduler(),
                idGenerator = FixedIdGenerator(),
                clock = TEST_CLOCK,
                timeZone = kotlinx.datetime.TimeZone.UTC,
            ),
            updateReminder = UpdateCustomReminderUseCase(
                reminders = reminders,
                scheduler = RecordingScheduler(),
                clock = TEST_CLOCK,
                timeZone = kotlinx.datetime.TimeZone.UTC,
            ),
            telemetry = silentRemindersTelemetry(),
            clock = TEST_CLOCK,
            timeZone = kotlinx.datetime.TimeZone.UTC,
        )
    }

    @Test
    fun startsOnTodayWithTheFirstPreset() = runTest {
        val state = Fixture().viewModel.state.value

        assertEquals(TEST_TODAY.toEpochDays() * 86_400_000L, state.startMillis)
        assertEquals(ReminderPreset.AIR_PRESSURE, state.preset)
        assertTrue(!state.editing)
    }

    @Test
    fun savingAValidFormStoresAndCloses() = runTest {
        val fixture = Fixture()
        with(fixture.viewModel) {
            onEvent(NewReminderEvent.PresetSelected(ReminderPreset.COOLANT, "Coolant top-up"))
            onEvent(NewReminderEvent.RepeatChanged(ReminderRepeat.MONTHLY))
            onEvent(NewReminderEvent.TimeChanged(hour = 18, minute = 30))
            onEvent(NewReminderEvent.SaveTapped)
        }

        val stored = fixture.reminders.customs.single()
        assertEquals("Coolant top-up", stored.title.value)
        assertEquals(ReminderCadence.Monthly, stored.cadence)
        assertEquals(18, stored.at.hour)
        assertIs<NewReminderEffect.Close>(fixture.viewModel.effects.firstOrNull())
    }

    @Test
    fun aBlankNameLandsOnTheNameFieldAndStoresNothing() = runTest {
        val fixture = Fixture()
        with(fixture.viewModel) {
            onEvent(NewReminderEvent.NameChanged(""))
            onEvent(NewReminderEvent.SaveTapped)
        }

        assertNotNull(fixture.viewModel.state.value.nameError)
        assertTrue(fixture.reminders.customs.isEmpty())
        assertTrue(!fixture.viewModel.state.value.saving)
    }

    @Test
    fun byDistanceIsRefusedWithoutAReading() = runTest {
        val fixture = Fixture()

        fixture.viewModel.onEvent(NewReminderEvent.RepeatChanged(ReminderRepeat.BY_DISTANCE))

        assertEquals(ReminderRepeat.EVERY_15_DAYS, fixture.viewModel.state.value.repeat)
    }

    @Test
    fun byDistanceAnchorsAtTheCurrentReading() = runTest {
        val fixture = Fixture(
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 42_000))),
        )
        with(fixture.viewModel) {
            onEvent(NewReminderEvent.PresetSelected(ReminderPreset.TYRE_ROTATION, "Tyre rotation"))
            onEvent(NewReminderEvent.RepeatChanged(ReminderRepeat.BY_DISTANCE))
            onEvent(NewReminderEvent.SaveTapped)
        }

        val stored = fixture.reminders.customs.single()
        assertEquals(ReminderCadence.EveryDistance(10_000), stored.cadence)
        assertEquals(42_000, stored.anchorKm)
    }

    @Test
    fun distanceStepDefaultsToThePresetsOwnStep_andCanBeOverridden() = runTest {
        val fixture = Fixture(
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 42_000))),
        )
        with(fixture.viewModel) {
            onEvent(NewReminderEvent.PresetSelected(ReminderPreset.TYRE_ROTATION, "Tyre rotation"))
            onEvent(NewReminderEvent.RepeatChanged(ReminderRepeat.BY_DISTANCE))
        }
        assertEquals(10_000, fixture.viewModel.state.value.distanceStepKm)

        fixture.viewModel.onEvent(NewReminderEvent.DistanceStepChanged(5_000))
        fixture.viewModel.onEvent(NewReminderEvent.SaveTapped)

        assertEquals(ReminderCadence.EveryDistance(5_000), fixture.reminders.customs.single().cadence)
    }

    @Test
    fun distanceStepDefaultsToTheGenericStep_forATopicWithNoDistanceCadenceOfItsOwn() = runTest {
        val fixture = Fixture(
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 42_000))),
        )
        with(fixture.viewModel) {
            onEvent(NewReminderEvent.CustomLabelSaved("Wheel alignment"))
            onEvent(NewReminderEvent.RepeatChanged(ReminderRepeat.BY_DISTANCE))
        }

        assertEquals(NewReminderUiState.DEFAULT_DISTANCE_STEP_KM, fixture.viewModel.state.value.distanceStepKm)
    }

    @Test
    fun aNonPositiveDistanceStepIsRefusedAndStoresNothing() = runTest {
        val fixture = Fixture(
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 42_000))),
        )
        with(fixture.viewModel) {
            onEvent(NewReminderEvent.PresetSelected(ReminderPreset.TYRE_ROTATION, "Tyre rotation"))
            onEvent(NewReminderEvent.RepeatChanged(ReminderRepeat.BY_DISTANCE))
            onEvent(NewReminderEvent.DistanceStepChanged(0))
            onEvent(NewReminderEvent.SaveTapped)
        }

        assertNotNull(fixture.viewModel.state.value.distanceStepError)
        assertTrue(fixture.reminders.customs.isEmpty())
    }

    @Test
    fun editingADistanceReminderPrefillsItsStep() = runTest {
        val existing = customReminder(
            id = "rem-1",
            cadence = ReminderCadence.EveryDistance(7_500),
            anchorKm = 40_000,
        )
        val fixture = Fixture(
            reminderId = "rem-1",
            reminders = FakeReminderRepository(listOf(existing)),
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 40_000))),
        )

        val prefilled = fixture.viewModel.state.value
        assertEquals(ReminderRepeat.BY_DISTANCE, prefilled.repeat)
        assertEquals(7_500, prefilled.distanceStepKm)
    }

    @Test
    fun tappingASuggestionRowPrefillsExactlyWhatOneTapCreateWouldHaveUsed() = runTest {
        val fixture = Fixture(
            suggestedPreset = ReminderPreset.TYRE_ROTATION.name,
            suggestedName = "Tyre rotation",
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 42_000))),
        )

        val prefilled = fixture.viewModel.state.value
        assertTrue(!prefilled.editing)
        assertEquals(ReminderPreset.TYRE_ROTATION, prefilled.preset)
        assertEquals("Tyre rotation", prefilled.name)
        assertEquals(ReminderRepeat.BY_DISTANCE, prefilled.repeat)
        assertEquals(10_000, prefilled.distanceStepKm)

        // Nothing is created merely by opening the form — only saving does.
        assertTrue(fixture.reminders.customs.isEmpty())
    }

    @Test
    fun aSuggestionPrefillCanBeAdjustedBeforeSaving() = runTest {
        val fixture = Fixture(
            suggestedPreset = ReminderPreset.TYRE_ROTATION.name,
            suggestedName = "Tyre rotation",
            serviceLogs = FakeServiceLogRepository(readings = listOf(reading(km = 42_000))),
        )

        with(fixture.viewModel) {
            onEvent(NewReminderEvent.DistanceStepChanged(5_000))
            onEvent(NewReminderEvent.SaveTapped)
        }

        val stored = fixture.reminders.customs.single()
        assertEquals(ReminderCadence.EveryDistance(5_000), stored.cadence)
    }

    @Test
    fun aNonDistancePresetSuggestionPrefillsItsOwnCadence() = runTest {
        val fixture = Fixture(
            suggestedPreset = ReminderPreset.COOLANT.name,
            suggestedName = "Coolant top-up",
        )

        assertEquals(ReminderRepeat.MONTHLY, fixture.viewModel.state.value.repeat)
    }

    @Test
    fun editPrefillsAndUpdatesInPlace() = runTest {
        val existing = customReminder(id = "rem-1", cadence = ReminderCadence.Monthly)
        val fixture = Fixture(reminderId = "rem-1", reminders = FakeReminderRepository(listOf(existing)))

        val prefilled = fixture.viewModel.state.value
        assertTrue(prefilled.editing)
        assertEquals("Air pressure check", prefilled.name)
        assertEquals(ReminderRepeat.MONTHLY, prefilled.repeat)

        with(fixture.viewModel) {
            onEvent(NewReminderEvent.NameChanged("Air pressure + tread"))
            onEvent(NewReminderEvent.SaveTapped)
        }

        val stored = fixture.reminders.customs.single()
        assertEquals("rem-1", stored.id.value)
        assertEquals("Air pressure + tread", stored.title.value)
    }

    @Test
    fun editingAVanishedReminderJustCloses() = runTest {
        val fixture = Fixture(reminderId = "rem-gone")

        assertIs<NewReminderEffect.Close>(fixture.viewModel.effects.firstOrNull())
        assertNull(fixture.viewModel.state.value.nameError)
    }
}
