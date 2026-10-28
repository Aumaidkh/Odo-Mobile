package com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2

import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.setup.SetupDraft
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
    fun eachStepIsReportedAsShown_includingOnTheWayBack() = runTest(dispatcher) {
        val analytics = V2Analytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()

        viewModel.onEvent(SetupV2Event.ModelChosen(CarModel("Creta")))
        advanceUntilIdle()
        viewModel.onEvent(SetupV2Event.BackClicked)
        advanceUntilIdle()

        assertEquals(listOf("model", "year_fuel", "model"), analytics.shownSteps())
    }

    @Test
    fun aResume_reportsOnlyTheStepItOpensOn() = runTest(dispatcher) {
        val analytics = V2Analytics()
        viewModel(resume = true, drafts = V2Drafts(draft(model = "Creta")), analytics = analytics)
        advanceUntilIdle()

        assertEquals(listOf("year_fuel"), analytics.shownSteps())
    }

    @Test
    fun aModelPick_saysWhereItCameFrom() = runTest(dispatcher) {
        fun sourceOf(event: SetupV2Event): Any? {
            val analytics = V2Analytics()
            val viewModel = viewModel(analytics = analytics)
            advanceUntilIdle()
            viewModel.onEvent(event)
            return analytics.events.last { it.first == SetupTelemetry.Event.MODEL_CHOSEN }.second[SetupTelemetry.Key.SOURCE]
        }

        assertEquals("grid", sourceOf(SetupV2Event.ModelChosen(CarModel("Creta"))))
        assertEquals("search", sourceOf(SetupV2Event.ModelChosen(CarModel("Creta", "SX"), fromSearch = true)))
        assertEquals("typed", sourceOf(SetupV2Event.ModelChosen(CarModel("Stargazer"), fromSearch = true)))
    }

    @Test
    fun theModelList_countsAClose_butNotTheCloseAfterAPick() = runTest(dispatcher) {
        val analytics = V2Analytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()

        viewModel.onEvent(SetupV2Event.ModelSearchOpened)
        viewModel.onEvent(SetupV2Event.ModelSearchDismissed)
        viewModel.onEvent(SetupV2Event.ModelSearchOpened)
        viewModel.onEvent(SetupV2Event.ModelChosen(CarModel("Creta", "SX"), fromSearch = true))
        viewModel.onEvent(SetupV2Event.ModelSearchDismissed)

        assertEquals(2, analytics.events.count { it.first == SetupTelemetry.Event.MODEL_SEARCH_OPENED })
        assertEquals(1, analytics.events.count { it.first == SetupTelemetry.Event.MODEL_SEARCH_DISMISSED })
        assertFalse(viewModel.state.value.searchOpen)
    }

    @Test
    fun yearAndFuel_reportTheirSource_andAChangedAnswer() = runTest(dispatcher) {
        val analytics = V2Analytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()
        viewModel.onEvent(SetupV2Event.ModelChosen(CarModel("Creta")))

        viewModel.onEvent(SetupV2Event.YearChosen(2022))
        viewModel.onEvent(SetupV2Event.OlderYearsOpened)
        viewModel.onEvent(SetupV2Event.YearChosen(2012, fromOlder = true))
        viewModel.onEvent(SetupV2Event.FuelChosen(FuelType.PETROL))
        viewModel.onEvent(SetupV2Event.FuelChosen(FuelType.DIESEL))

        val years = analytics.events.filter { it.first == SetupTelemetry.Event.YEAR_CHOSEN }.map { it.second }
        assertEquals(listOf("chip", "older"), years.map { it[SetupTelemetry.Key.SOURCE] })
        assertEquals(listOf(false, true), years.map { it[SetupTelemetry.Key.CHANGED] })
        val fuels = analytics.events.filter { it.first == SetupTelemetry.Event.FUEL_CHOSEN }.map { it.second }
        assertEquals(listOf(false, true), fuels.map { it[SetupTelemetry.Key.CHANGED] })
        assertTrue(SetupTelemetry.Event.YEAR_OLDER_OPENED in analytics.names)
        assertFalse(viewModel.state.value.olderOpen)
    }

    @Test
    fun changeMake_isReported() = runTest(dispatcher) {
        val analytics = V2Analytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()

        viewModel.onEvent(SetupV2Event.ChangeMakeClicked)

        assertTrue(SetupTelemetry.Event.CHANGE_MAKE_CLICKED in analytics.names)
    }

    private fun V2Analytics.shownSteps(): List<Any?> =
        events.filter { it.first == SetupTelemetry.Event.STEP_SHOWN }.map { it.second[SetupTelemetry.Key.STEP] }

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

    @Test
    fun everyAnswerIsKeptInTheDraft() = runTest(dispatcher) {
        val drafts = V2Drafts()
        val viewModel = viewModel(drafts = drafts)
        advanceUntilIdle()

        viewModel.onEvent(SetupV2Event.ModelChosen(CarModel("Creta")))
        advanceUntilIdle()
        assertEquals("Creta", drafts.current?.modelName)
        viewModel.onEvent(SetupV2Event.YearChosen(2022))
        advanceUntilIdle()
        assertEquals(2022, drafts.current?.year)
        viewModel.onEvent(SetupV2Event.FuelChosen(FuelType.DIESEL))
        advanceUntilIdle()

        assertEquals(
            SetupDraft("Hyundai", "Creta", null, 2022, FuelType.DIESEL, V2Clock().now()),
            drafts.current,
        )
    }

    @Test
    fun savingTheCar_clearsTheDraft() = runTest(dispatcher) {
        val drafts = V2Drafts()
        val viewModel = viewModel(drafts = drafts)
        advanceUntilIdle()

        viewModel.answer(CarModel("Creta"))
        advanceUntilIdle()

        assertEquals(SetupV2Effect.ShowValue, viewModel.effects.first())
        assertNull(drafts.current)
        assertEquals(1, drafts.clears)
    }

    @Test
    fun resumeWithOnlyAMake_opensOnModel() = runTest(dispatcher) {
        val viewModel = viewModel(resume = true, drafts = V2Drafts(draft()))
        advanceUntilIdle()

        assertEquals(SetupV2Step.MODEL, viewModel.state.value.step)
    }

    @Test
    fun resumeWithAModel_opensOnYearAndFuel() = runTest(dispatcher) {
        val viewModel = viewModel(resume = true, drafts = V2Drafts(draft(model = "Creta")))
        advanceUntilIdle()

        assertEquals(SetupV2Step.YEAR_FUEL, viewModel.state.value.step)
        assertEquals(CarModel("Creta"), viewModel.state.value.model)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun resumeWithEveryAnswer_opensOnYearAndFuelReadyToSave() = runTest(dispatcher) {
        val analytics = V2Analytics()
        val viewModel = viewModel(
            resume = true,
            drafts = V2Drafts(draft(model = "Creta", year = 2022, fuel = FuelType.PETROL)),
            analytics = analytics,
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SetupV2Step.YEAR_FUEL, state.step)
        assertEquals(2022, state.year)
        assertEquals(FuelType.PETROL, state.fuel)
        assertTrue(state.canSave)

        viewModel.onEvent(SetupV2Event.SaveClicked)
        advanceUntilIdle()
        assertEquals(true, analytics.last(SetupTelemetry.Event.CAR_SAVED)[SetupTelemetry.Key.RESUMED])
    }

    @Test
    fun aFreshSetup_doesNotReadTheDraft() = runTest(dispatcher) {
        val viewModel = viewModel(resume = false, drafts = V2Drafts(draft(model = "Venue", year = 2020)))
        advanceUntilIdle()

        assertNull(viewModel.state.value.model)
        assertEquals(SetupV2Step.MODEL, viewModel.state.value.step)
    }

    private fun draft(model: String? = null, year: Int? = null, fuel: FuelType? = null) =
        SetupDraft(make = "Hyundai", modelName = model, year = year, fuel = fuel, updatedAt = V2Clock().now())

    private fun viewModel(
        resume: Boolean = false,
        drafts: V2Drafts = V2Drafts(),
        catalog: V2Catalog = V2Catalog(),
        cars: V2Cars = V2Cars(),
        nudges: V2Nudges = V2Nudges(),
        unlisted: V2Unlisted = V2Unlisted(),
        analytics: V2Analytics = V2Analytics(),
    ) = SetupV2ViewModel(
        make = "Hyundai",
        resume = resume,
        drafts = drafts,
        clock = V2Clock(),
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
