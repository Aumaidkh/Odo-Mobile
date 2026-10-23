package com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2

import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.platform.notification.EngagementNudge
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.ReportUnlistedVehicleUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.SaveCarUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.SetupTelemetry
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.Loadable
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupV2ViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opensOnModel_withTheMakeAndNothingPreselected() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SetupV2Step.MODEL, state.step)
        assertEquals("Hyundai", state.make)
        assertNull(state.model)
        assertNull(state.year)
        assertNull(state.fuel)
    }

    @Test
    fun theTilesAreTheBaseModels_andSearchHasEveryTrim() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val catalog = (viewModel.state.value.catalog as Loadable.Ready).value
        assertEquals(listOf(CarModel("Creta"), CarModel("Venue")), catalog.baseModels)
        assertEquals(3, catalog.models.size)
        assertEquals((2026 downTo 2019).toList(), catalog.recentYears)
    }

    @Test
    fun fiveTaps_saveTheCarWithTheOdometerPending() = runTest(dispatcher) {
        val cars = V2Cars()
        val nudges = V2Nudges()
        val viewModel = viewModel(cars = cars, nudges = nudges)
        advanceUntilIdle()

        // Tap 1 was the Hyundai tile on Welcome.
        viewModel.onEvent(SetupV2Event.ModelChosen(CarModel("Creta")))
        assertEquals(SetupV2Step.YEAR_FUEL, viewModel.state.value.step)
        viewModel.onEvent(SetupV2Event.YearChosen(2022))
        viewModel.onEvent(SetupV2Event.FuelChosen(FuelType.PETROL))
        viewModel.onEvent(SetupV2Event.SaveClicked)
        advanceUntilIdle()

        assertEquals(SetupV2Effect.ShowValue, viewModel.effects.first())
        val car = cars.added.single()
        assertEquals("Hyundai", car.make)
        assertEquals("Creta", car.model)
        assertTrue(car.isOdometerPending)
        assertEquals(listOf(EngagementNudge.FIRST_SCORE), nudges.scheduled)
    }

    @Test
    fun saveIsDisabledWithOnlyAYear() = runTest(dispatcher) {
        val cars = V2Cars()
        val viewModel = viewModel(cars = cars)
        advanceUntilIdle()
        viewModel.onEvent(SetupV2Event.ModelChosen(CarModel("Creta")))
        viewModel.onEvent(SetupV2Event.YearChosen(2022))

        assertFalse(viewModel.state.value.canSave)
        viewModel.onEvent(SetupV2Event.SaveClicked)
        advanceUntilIdle()
        assertTrue(cars.added.isEmpty())
    }

    @Test
    fun aFreeTextModel_savesLikeAListedOne_andIsReported() = runTest(dispatcher) {
        val cars = V2Cars()
        val unlisted = V2Unlisted()
        val viewModel = viewModel(cars = cars, unlisted = unlisted)
        advanceUntilIdle()

        viewModel.answer(CarModel("Alcazar", "Signature"))
        advanceUntilIdle()

        assertEquals("Alcazar", cars.added.single().model)
        assertEquals(listOf(Triple<String, String, String?>("Hyundai", "Alcazar", "Signature")), unlisted.reports)
    }

    @Test
    fun aFailedSave_keepsTheOwnerOnTheStep() = runTest(dispatcher) {
        val viewModel = viewModel(cars = V2Cars(failing = true))
        advanceUntilIdle()

        viewModel.answer(CarModel("Creta"))
        advanceUntilIdle()

        assertIs<SetupV2Effect.SaveFailed>(viewModel.effects.first() as SetupV2Effect)
        assertEquals(SetupV2Step.YEAR_FUEL, viewModel.state.value.step)
        assertFalse(viewModel.state.value.saving)
    }

    @Test
    fun saveAgainAfterBackFromValue_editsTheSameCar() = runTest(dispatcher) {
        val cars = V2Cars()
        val viewModel = viewModel(cars = cars)
        advanceUntilIdle()
        viewModel.answer(CarModel("Creta"))
        advanceUntilIdle()

        viewModel.onEvent(SetupV2Event.YearChosen(2021))
        viewModel.onEvent(SetupV2Event.SaveClicked)
        advanceUntilIdle()

        assertEquals(1, cars.added.size)
        assertEquals(1, cars.updated.size)
    }

    @Test
    fun backFromYearAndFuel_returnsToModel_andBackFromModelLeaves() = runTest(dispatcher) {
        val analytics = V2Analytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()
        viewModel.onEvent(SetupV2Event.ModelChosen(CarModel("Creta")))

        viewModel.onEvent(SetupV2Event.BackClicked)
        assertEquals(SetupV2Step.MODEL, viewModel.state.value.step)

        viewModel.onEvent(SetupV2Event.BackClicked)
        assertEquals(SetupV2Effect.NavigateBack, viewModel.effects.first())
        val abandoned = analytics.last(SetupTelemetry.Event.ABANDONED)
        assertEquals("model", abandoned[SetupTelemetry.Key.STEP])
    }

    @Test
    fun changeMake_leavesForWelcome() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(SetupV2Event.ChangeMakeClicked)

        assertEquals(SetupV2Effect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun theFunnelReportsV2StepNames() = runTest(dispatcher) {
        val analytics = V2Analytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()

        viewModel.answer(CarModel("Creta"))
        advanceUntilIdle()

        val answered = analytics.events.filter { it.first == SetupTelemetry.Event.FIELD_ANSWERED }
            .map { it.second[SetupTelemetry.Key.FIELD] }
        assertEquals(listOf("make", "model", "year", "fuel"), answered)
        val advanced = analytics.events.filter { it.first == SetupTelemetry.Event.STEP_ADVANCED }
            .map { it.second[SetupTelemetry.Key.STEP] }
        assertEquals(listOf("model", "year_fuel"), advanced)
        assertTrue(SetupTelemetry.Event.CAR_SAVED in analytics.names)
        assertFalse(SetupTelemetry.Event.ODOMETER_SKIPPED in analytics.names)
    }

    @Test
    fun aFailedCatalogRead_offersARetry() = runTest(dispatcher) {
        val catalog = V2Catalog(failing = true)
        val viewModel = viewModel(catalog = catalog)
        advanceUntilIdle()
        assertIs<Loadable.Failed>(viewModel.state.value.catalog)

        catalog.failing = false
        viewModel.onEvent(SetupV2Event.CatalogRetried)
        advanceUntilIdle()

        assertIs<Loadable.Ready<SetupV2Catalog>>(viewModel.state.value.catalog)
    }

    private fun SetupV2ViewModel.answer(model: CarModel) {
        onEvent(SetupV2Event.ModelChosen(model))
        onEvent(SetupV2Event.YearChosen(2022))
        onEvent(SetupV2Event.FuelChosen(FuelType.PETROL))
        onEvent(SetupV2Event.SaveClicked)
    }

    private fun viewModel(
        catalog: V2Catalog = V2Catalog(),
        cars: V2Cars = V2Cars(),
        nudges: V2Nudges = V2Nudges(),
        unlisted: V2Unlisted = V2Unlisted(),
        analytics: V2Analytics = V2Analytics(),
    ) = SetupV2ViewModel(
        make = "Hyundai",
        loadCatalog = LoadVehicleCatalogUseCase(catalog),
        loadModels = LoadCarModelsUseCase(catalog),
        saveCar = SaveCarUseCase(
            cars = cars,
            logs = V2Logs(),
            idGenerator = IdGenerator { "car-1" },
            clock = V2Clock(),
            timeZone = TimeZone.UTC,
        ),
        reportUnlisted = ReportUnlistedVehicleUseCase(unlisted),
        currentOwner = CurrentOwnerProvider { OwnerId("owner-1") },
        telemetry = SetupTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            tracer = APM.asTracer(),
            ids = IdGenerator { "trace-1" },
        ),
        nudges = nudges,
    )
}
