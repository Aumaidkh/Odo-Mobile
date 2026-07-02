package com.hopcape.odo.feature.onboarding.presentation

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.car.usecase.AddCarUseCase
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEffect
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEvent
import com.hopcape.odo.feature.onboarding.presentation.contract.StartDestination
import com.hopcape.odo.feature.onboarding.presentation.contract.toStartDestination
import com.hopcape.odo.feature.onboarding.presentation.scan.HistoryScanLauncher
import com.hopcape.odo.feature.onboarding.presentation.scan.HistoryScanOutcome
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    // --- Fakes ---

    private class FakeCarRepository(
        private val onAdd: (Car) -> Either<DomainError, Car> = { it.right() },
    ) : CarRepository {
        var addCount = 0
        var lastAdded: Car? = null
        override suspend fun add(car: Car): Either<DomainError, Car> {
            addCount++
            lastAdded = car
            return onAdd(car)
        }
        override fun observePrimaryCar(): Flow<Car?> = flowOf(lastAdded)
    }

    private class FixedIdGenerator(private val id: String = "car-1") : IdGenerator {
        override fun newId(): String = id
    }

    private class FakeVehicleCatalog : VehicleCatalog {
        override suspend fun makes(): List<String> = listOf("Maruti Suzuki", "Hyundai")
        override suspend fun models(make: String): List<String> = when (make) {
            "Maruti Suzuki" -> listOf("Swift", "Baleno")
            else -> listOf("i20", "Creta")
        }
        override fun years(): List<Int> = (2025 downTo 2020).toList()
        override fun fuelTypes(): List<FuelType> = FuelType.entries.toList()
    }

    private class CountingScanLauncher : HistoryScanLauncher {
        var count = 0
        override suspend fun scan(): HistoryScanOutcome {
            count++
            return HistoryScanOutcome.NotAvailable
        }
    }

    /** Models for make "A" load slowly, so an uncancelled stale load would overwrite "B". */
    private class OrderedCatalog : VehicleCatalog {
        override suspend fun makes(): List<String> = listOf("A", "B")
        override suspend fun models(make: String): List<String> {
            if (make == "A") delay(1000)
            return listOf("model-$make")
        }
        override fun years(): List<Int> = listOf(2020)
        override fun fuelTypes(): List<FuelType> = FuelType.entries.toList()
    }

    private val owner = CurrentOwnerProvider { OwnerId("owner-1") }
    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repo: FakeCarRepository = FakeCarRepository(),
        scan: HistoryScanLauncher = CountingScanLauncher(),
        catalog: VehicleCatalog = FakeVehicleCatalog(),
    ): OnboardingViewModel {
        val ids = FixedIdGenerator()
        return OnboardingViewModel(
            addCar = AddCarUseCase(repo, ids),
            catalog = catalog,
            owner = owner,
            ids = ids,
            scanLauncher = scan,
        )
    }

    private fun OnboardingViewModel.fillValidCarDetails() {
        onEvent(OnboardingEvent.MakeChanged("Maruti Suzuki"))
        onEvent(OnboardingEvent.ModelChanged("Swift"))
        onEvent(OnboardingEvent.YearChanged(2020))
        onEvent(OnboardingEvent.FuelTypeChanged(FuelType.PETROL))
        onEvent(OnboardingEvent.OdometerChanged("45000"))
    }

    // --- Tests ---

    @Test
    fun init_loadsDropdownReferenceData() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.isCatalogLoading)
        assertTrue(state.makes.isNotEmpty())
        assertTrue(state.years.isNotEmpty())
        assertEquals(FuelType.entries.toList(), state.fuelTypes)
    }

    @Test
    fun happyPath_submitsAndEmitsNavigateToStart() = runTest(testDispatcher) {
        val repo = FakeCarRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.fillValidCarDetails()
        vm.onEvent(OnboardingEvent.CarDetailsNext)
        assertEquals(OnboardingStep.HISTORY_IMPORT, vm.state.value.step)
        assertTrue(!vm.state.value.form.hasErrors)

        vm.onEvent(OnboardingEvent.SkipHistory)
        assertEquals(OnboardingStep.GOAL_SELECTION, vm.state.value.step)

        // Start collecting the one-shot effect before triggering submit.
        val effectDeferred = async { vm.effects.first() }
        runCurrent()

        vm.onEvent(OnboardingEvent.GoalSelected(OnboardingGoal.TRACK_COSTS))
        vm.onEvent(OnboardingEvent.Finish)
        advanceUntilIdle()

        assertEquals(1, repo.addCount)
        val effect = effectDeferred.await()
        assertTrue(effect is OnboardingEffect.NavigateToStart)
        assertEquals(StartDestination.DASHBOARD, effect.destination)
        assertEquals("car-1", effect.carId)
        assertEquals(false, vm.state.value.isSubmitting)
    }

    @Test
    fun carDetailsNext_missingOdometer_setsErrorAndStays() = runTest(testDispatcher) {
        val repo = FakeCarRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onEvent(OnboardingEvent.MakeChanged("Maruti Suzuki"))
        vm.onEvent(OnboardingEvent.ModelChanged("Swift"))
        vm.onEvent(OnboardingEvent.YearChanged(2020))
        vm.onEvent(OnboardingEvent.FuelTypeChanged(FuelType.PETROL))
        // odometer left blank
        vm.onEvent(OnboardingEvent.CarDetailsNext)

        assertNotNull(vm.state.value.form.odometer.error)
        assertEquals(OnboardingStep.CAR_DETAILS, vm.state.value.step)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun carDetailsNext_negativeOdometer_setsOdometerError() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.fillValidCarDetails()
        vm.onEvent(OnboardingEvent.OdometerChanged("-5"))
        vm.onEvent(OnboardingEvent.CarDetailsNext)

        assertNotNull(vm.state.value.form.odometer.error)
        assertEquals(OnboardingStep.CAR_DETAILS, vm.state.value.step)
    }

    @Test
    fun carDetailsNext_missingYear_setsYearError() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.fillValidCarDetails()
        vm.onEvent(OnboardingEvent.YearChanged(null))
        vm.onEvent(OnboardingEvent.CarDetailsNext)

        assertNotNull(vm.state.value.form.year.error)
        assertEquals(OnboardingStep.CAR_DETAILS, vm.state.value.step)
    }

    @Test
    fun carDetailsNext_yearOutOfRange_setsYearError() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.fillValidCarDetails()
        vm.onEvent(OnboardingEvent.YearChanged(1900))
        vm.onEvent(OnboardingEvent.CarDetailsNext)

        assertNotNull(vm.state.value.form.year.error)
        assertEquals(OnboardingStep.CAR_DETAILS, vm.state.value.step)
    }

    @Test
    fun carDetailsNext_missingFuel_setsFuelError() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(OnboardingEvent.MakeChanged("Maruti Suzuki"))
        vm.onEvent(OnboardingEvent.ModelChanged("Swift"))
        vm.onEvent(OnboardingEvent.YearChanged(2020))
        vm.onEvent(OnboardingEvent.OdometerChanged("45000"))
        // fuel not selected
        vm.onEvent(OnboardingEvent.CarDetailsNext)

        assertNotNull(vm.state.value.form.fuelType.error)
        assertEquals(OnboardingStep.CAR_DETAILS, vm.state.value.step)
    }

    @Test
    fun carDetailsNext_blankMakeAndModel_setsBothErrors() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(OnboardingEvent.YearChanged(2020))
        vm.onEvent(OnboardingEvent.FuelTypeChanged(FuelType.PETROL))
        vm.onEvent(OnboardingEvent.OdometerChanged("45000"))
        // make + model never set
        vm.onEvent(OnboardingEvent.CarDetailsNext)

        assertNotNull(vm.state.value.form.make.error)
        assertNotNull(vm.state.value.form.model.error)
        assertEquals(OnboardingStep.CAR_DETAILS, vm.state.value.step)
    }

    @Test
    fun back_onFirstStep_emitsNavigateBack() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val effectDeferred = async { vm.effects.first() }
        runCurrent()

        vm.onEvent(OnboardingEvent.Back)
        advanceUntilIdle()

        // First step has nowhere to rewind to — back exits onboarding.
        assertEquals(OnboardingEffect.NavigateBack, effectDeferred.await())
        assertEquals(OnboardingStep.CAR_DETAILS, vm.state.value.step)
    }

    @Test
    fun back_onLaterStep_rewindsStepWithoutEffect() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val effects = mutableListOf<OnboardingEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }

        vm.fillValidCarDetails()
        vm.onEvent(OnboardingEvent.CarDetailsNext)
        assertEquals(OnboardingStep.HISTORY_IMPORT, vm.state.value.step)

        vm.onEvent(OnboardingEvent.Back)
        advanceUntilIdle()

        assertEquals(OnboardingStep.CAR_DETAILS, vm.state.value.step)
        assertTrue(effects.none { it is OnboardingEffect.NavigateBack })
    }

    @Test
    fun skipHistory_advancesWithoutScanning() = runTest(testDispatcher) {
        val scan = CountingScanLauncher()
        val vm = viewModel(scan = scan)
        advanceUntilIdle()

        vm.fillValidCarDetails()
        vm.onEvent(OnboardingEvent.CarDetailsNext)
        vm.onEvent(OnboardingEvent.SkipHistory)
        advanceUntilIdle()

        assertEquals(OnboardingStep.GOAL_SELECTION, vm.state.value.step)
        assertEquals(0, scan.count)
    }

    @Test
    fun rapidMakeChange_cancelsStaleModelLoad() = runTest(testDispatcher) {
        val vm = viewModel(catalog = OrderedCatalog())
        advanceUntilIdle()

        vm.onEvent(OnboardingEvent.MakeChanged("A")) // slow load — must be cancelled
        vm.onEvent(OnboardingEvent.MakeChanged("B")) // fast load — must win
        advanceUntilIdle()

        assertEquals(listOf("model-B"), vm.state.value.models)
        assertEquals("B", vm.state.value.form.make.value)
    }

    @Test
    fun goalSelected_highlightsWithoutSubmitting() = runTest(testDispatcher) {
        val repo = FakeCarRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.fillValidCarDetails()
        vm.onEvent(OnboardingEvent.CarDetailsNext)
        vm.onEvent(OnboardingEvent.SkipHistory)
        vm.onEvent(OnboardingEvent.GoalSelected(OnboardingGoal.TRACK_COSTS))
        advanceUntilIdle()

        // Selecting a goal only highlights it; nothing is persisted until Finish.
        assertEquals(OnboardingGoal.TRACK_COSTS, vm.state.value.selectedGoal)
        assertEquals(0, repo.addCount)
        assertEquals(OnboardingStep.GOAL_SELECTION, vm.state.value.step)
    }

    @Test
    fun goalSelection_mapsEachGoalToCorrectStartDestination() {
        assertEquals(StartDestination.RESALE_PASSPORT, OnboardingGoal.SELL_SOON.toStartDestination())
        assertEquals(StartDestination.DASHBOARD, OnboardingGoal.TRACK_COSTS.toStartDestination())
        assertEquals(StartDestination.DOCUMENT_VAULT, OnboardingGoal.NEVER_MISS_RENEWAL.toStartDestination())
    }

    @Test
    fun submit_persistenceFailure_setsSubmitErrorAndNoEffect() = runTest(testDispatcher) {
        val repo = FakeCarRepository(onAdd = { DomainError.PersistenceFailure("disk full").left() })
        val vm = viewModel(repo)
        advanceUntilIdle()

        val effects = mutableListOf<OnboardingEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }

        vm.fillValidCarDetails()
        vm.onEvent(OnboardingEvent.CarDetailsNext)
        vm.onEvent(OnboardingEvent.SkipHistory)
        vm.onEvent(OnboardingEvent.GoalSelected(OnboardingGoal.SELL_SOON))
        vm.onEvent(OnboardingEvent.Finish)
        advanceUntilIdle()

        assertNotNull(vm.state.value.submitError)
        assertEquals(OnboardingStep.GOAL_SELECTION, vm.state.value.step)
        assertEquals(false, vm.state.value.isSubmitting)
        assertTrue(effects.isEmpty())
        assertNull(vm.state.value.form.odometer.error)
    }
}
