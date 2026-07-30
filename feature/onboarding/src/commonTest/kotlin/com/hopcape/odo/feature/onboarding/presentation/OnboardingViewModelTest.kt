package com.hopcape.odo.feature.onboarding.presentation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.onboarding.presentation.state.Loadable
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingGoalOption
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.state.PlateLookup
import com.hopcape.odo.feature.onboarding.presentation.state.PlateLookupError
import com.hopcape.odo.feature.onboarding.presentation.state.StartDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Driving virtual time (setMain / advanceUntilIdle) is still an experimental coroutines API.
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        // viewModelScope dispatches on Dispatchers.Main; pointing it at the test scheduler is
        // what lets the debounce and the loads be driven by virtual time.
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /* ------------------------------ Catalog ------------------------------ */

    @Test
    fun catalog_isLoadedOnInit() = runTest(dispatcher) {
        val viewModel = viewModel()

        advanceUntilIdle()

        val options = assertReady(viewModel.state.value.details.catalog)
        assertEquals(listOf("Maruti Suzuki", "Honda"), options.makes)
        assertEquals(listOf("Maruti Suzuki"), options.popularMakes)
        // The wheel takes a range, so the catalog's newest-first list becomes its bounds.
        assertEquals(2024..2026, options.years)
    }

    @Test
    fun catalog_thatFailsToRead_offersARetryThatCanSucceed() = runTest(dispatcher) {
        val catalog = FakeCatalog(failing = true)
        val viewModel = viewModel(catalog = catalog)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.details.catalog is Loadable.Failed)

        catalog.failing = false
        viewModel.onEvent(OnboardingEvent.Details.CatalogRetried)
        advanceUntilIdle()

        assertReady(viewModel.state.value.details.catalog)
    }

    @Test
    fun makeSelected_dropsTheModelChosenUnderTheOldMake_andLoadsTheNewOne() = runTest(dispatcher) {
        val catalog = FakeCatalog()
        val viewModel = viewModel(catalog = catalog)
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Details.MakeSelected("Honda"))
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Details.ModelSelected(CarModel("City", "VX")))

        assertEquals(CarModel("City", "VX"), viewModel.state.value.details.model.value)

        viewModel.onEvent(OnboardingEvent.Details.MakeSelected("Maruti Suzuki"))

        // The old model can't survive a new brand — and neither can the list it came from.
        assertNull(viewModel.state.value.details.model.value)
        assertEquals(emptyList(), viewModel.state.value.details.models)

        advanceUntilIdle()
        assertEquals("Maruti Suzuki", catalog.lastMake)
    }

    /* ------------------------------ Plate lookup ------------------------------ */

    @Test
    fun plate_isNotLookedUpUntilItIsLongEnough() = runTest(dispatcher) {
        val registry = FakeRegistry()
        val viewModel = viewModel(registry = registry)

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged("MH12"))
        advanceUntilIdle()

        assertEquals(PlateLookup.Idle, viewModel.state.value.car.lookup)
        assertEquals(0, registry.callCount)
    }

    @Test
    fun plate_thatStopsChanging_isLookedUp() = runTest(dispatcher) {
        val registry = FakeRegistry()
        val viewModel = viewModel(registry = registry)

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(FOUND_PLATE))
        advanceUntilIdle()

        val lookup = viewModel.state.value.car.lookup
        assertTrue(lookup is PlateLookup.Found)
        assertEquals("Maruti Swift VXI", lookup.match.title)
        assertEquals(1, registry.callCount)
    }

    @Test
    fun plate_stillBeingTyped_neverSpendsARoundTrip() = runTest(dispatcher) {
        val registry = FakeRegistry()
        val viewModel = viewModel(registry = registry)

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged("MH12AB1234"))
        advanceTimeBy(100)
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged("MH12AB12345"))
        advanceUntilIdle()

        // Only the plate the owner settled on is asked about.
        assertEquals(1, registry.callCount)
        assertEquals("MH12AB12345", registry.lastPlate?.value)
    }

    @Test
    fun editingThePlate_dropsTheMatchItResolvedTo() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(FOUND_PLATE))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.car.lookup is PlateLookup.Found)

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged("MH12AB12"))

        // A match must never outlive the plate it was found for.
        assertEquals(PlateLookup.Idle, viewModel.state.value.car.lookup)
    }

    @Test
    fun lookupFailures_keepTheirReason() = runTest(dispatcher) {
        val registry = FakeRegistry(failure = DomainError.RegistrationNotFound)
        val viewModel = viewModel(registry = registry)

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged("MH12AB1234"))
        advanceUntilIdle()

        assertEquals(
            PlateLookup.Failed(PlateLookupError.NOT_FOUND),
            viewModel.state.value.car.lookup,
        )
    }

    @Test
    fun anUnavailableRegistry_readsAsRetryableRatherThanAsNoSuchCar() = runTest(dispatcher) {
        // The MVP's adapter: no registry integration exists, so it reports unavailable.
        // "Couldn't check right now" is truthful; "no record for this plate" would not be.
        val registry = FakeRegistry(failure = DomainError.LookupUnavailable)
        val viewModel = viewModel(registry = registry)

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged("MH12AB1234"))
        advanceUntilIdle()

        assertEquals(
            PlateLookup.Failed(PlateLookupError.SERVICE),
            viewModel.state.value.car.lookup,
        )
    }

    @Test
    fun retry_asksAgainForTheSamePlate() = runTest(dispatcher) {
        val registry = FakeRegistry(failure = DomainError.LookupUnavailable)
        val viewModel = viewModel(registry = registry)
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged("MH12AB1234"))
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Car.LookupRetried)
        advanceUntilIdle()

        assertEquals(2, registry.callCount)
    }

    /* ------------------------------ Prefill from a match ------------------------------ */

    @Test
    fun aFoundCar_prefillsTheManualForm() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(HONDA_PLATE))
        advanceUntilIdle()

        // "Not your car?" must open a form that already describes the car we found, so the
        // owner corrects one field instead of re-answering all four.
        val details = viewModel.state.value.details
        assertEquals("Honda", details.make.value)
        assertEquals(CarModel("City", "VX"), details.model.value)
        assertEquals(2020, details.year.value)
        assertEquals(FuelType.PETROL, details.fuel.value)
        // …and the picker behind the seeded model is populated, not empty.
        assertEquals(listOf(CarModel("City"), CarModel("City", "VX")), details.models)
    }

    @Test
    fun aPrefilledForm_answersTheStepOnceTheOdometerIsGiven() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(HONDA_PLATE))
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Car.MatchRejected)
        assertFalse(viewModel.state.value.canContinue)

        viewModel.onEvent(OnboardingEvent.OdometerChanged(54_000))

        assertTrue(viewModel.state.value.canContinue)
    }

    @Test
    fun theOdometer_survivesSwitchingBetweenTheTwoRoutes() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(OnboardingEvent.OdometerChanged(54_000))
        viewModel.onEvent(OnboardingEvent.Car.MatchRejected)

        // One odometer for the car, whichever route named it — retyping it would be absurd.
        assertEquals(54_000L, viewModel.state.value.odometer.value)

        viewModel.onEvent(OnboardingEvent.Details.TryAutoFillClicked)

        assertEquals(54_000L, viewModel.state.value.odometer.value)
    }

    @Test
    fun aMakeTheCatalogDoesNotKnow_isNotPrefilled() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // The registry says "Maruti"; the catalog says "Maruti Suzuki". Seeding the registry's
        // spelling would show a make the picker behind it doesn't list — and would key this
        // car's fairness benchmarks to a bucket of its own.
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(FOUND_PLATE))
        advanceUntilIdle()

        val details = viewModel.state.value.details
        assertNull(details.make.value)
        assertNull(details.model.value)
        // Year and fuel can't disagree with a catalog, so they are seeded regardless.
        assertEquals(2020, details.year.value)
        assertEquals(FuelType.PETROL, details.fuel.value)
    }

    @Test
    fun anUnknownTrim_fallsBackToTheModelWithoutOne() = runTest(dispatcher) {
        val registry = FakeRegistry(vehicles = mapOf(HONDA_PLATE to HONDA_CITY.copy(variant = "ZX Turbo")))
        val viewModel = viewModel(registry = registry)
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(HONDA_PLATE))
        advanceUntilIdle()

        // A wrong trim is worse than no trim: it silently feeds ₹/km and every benchmark.
        assertEquals(CarModel("City"), viewModel.state.value.details.model.value)
    }

    @Test
    fun aPrefilledFormKeepsManualCorrections() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(HONDA_PLATE))
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Car.MatchRejected)
        viewModel.onEvent(OnboardingEvent.Details.ModelSelected(CarModel("City")))

        assertEquals(CarModel("City"), viewModel.state.value.details.model.value)
    }

    /* ------------------------------ Steps ------------------------------ */

    @Test
    fun theCarStep_needsBothAMatchAndAnOdometer() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(FOUND_PLATE))
        advanceUntilIdle()

        // The registry never knows the odometer, so a match alone can't answer the step.
        assertFalse(viewModel.state.value.canContinue)

        viewModel.onEvent(OnboardingEvent.OdometerChanged(54_000))

        assertTrue(viewModel.state.value.canContinue)
    }

    @Test
    fun manualEntry_isAModeOfTheCarStep_thatBackLeavesFirst() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(OnboardingEvent.Car.MatchRejected)
        assertTrue(viewModel.state.value.manualEntry)

        viewModel.onEvent(OnboardingEvent.BackClicked)

        // Back returns to the plate rather than leaving the flow.
        assertFalse(viewModel.state.value.manualEntry)
        assertEquals(OnboardingStep.CAR, viewModel.state.value.step)
    }

    @Test
    fun back_onTheFirstStep_leavesTheFlow() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(OnboardingEvent.BackClicked)

        assertEquals(OnboardingEffect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun continue_isRefusedWhileTheStepIsUnanswered() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)

        assertEquals(OnboardingStep.CAR, viewModel.state.value.step)
    }

    @Test
    fun continue_walksTheStepsInOrder() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.answerCarStep()
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        assertEquals(OnboardingStep.PROFILE, viewModel.state.value.step)

        viewModel.onEvent(OnboardingEvent.Profile.NameChanged("Rahul"))
        viewModel.onEvent(OnboardingEvent.Profile.GoalSelected(OnboardingGoalOption.STOP_OVERPAYING))
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        assertEquals(OnboardingStep.FIRST_SCAN, viewModel.state.value.step)

        viewModel.onEvent(OnboardingEvent.BackClicked)
        assertEquals(OnboardingStep.PROFILE, viewModel.state.value.step)
    }

    @Test
    fun theProfileStep_needsARealNameAndAGoal() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.answerCarStep()
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.ContinueClicked)

        viewModel.onEvent(OnboardingEvent.Profile.NameChanged(" "))
        viewModel.onEvent(OnboardingEvent.Profile.GoalSelected(OnboardingGoalOption.SELL_FOR_MORE))
        assertFalse(viewModel.state.value.canContinue)

        viewModel.onEvent(OnboardingEvent.Profile.NameChanged("Rahul"))
        assertTrue(viewModel.state.value.canContinue)
    }

    /* ------------------------------ Finishing ------------------------------ */

    @Test
    fun skippingTheScan_finishesOnTheSurfaceTheGoalEarned() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEvent(OnboardingEvent.Profile.GoalSelected(OnboardingGoalOption.SELL_FOR_MORE))

        viewModel.onEvent(OnboardingEvent.Scan.SkipClicked)

        assertEquals(
            OnboardingEffect.Finish(start = StartDestination.RESALE_PASSPORT, signInFirst = true),
            viewModel.effects.first(),
        )
    }

    @Test
    fun finishing_withoutAGoal_stillLandsSomewhere() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(OnboardingEvent.Scan.SkipClicked)

        // The goal is a routing hint; missing it is no reason to strand the owner.
        assertEquals(
            OnboardingEffect.Finish(start = StartDestination.DASHBOARD, signInFirst = true),
            viewModel.effects.first(),
        )
    }

    @Test
    fun finishing_withASession_doesNotAskForSignIn() = runTest(dispatcher) {
        val viewModel = viewModel(signedIn = true)

        viewModel.onEvent(OnboardingEvent.Scan.SkipClicked)

        val effect = viewModel.effects.first()
        assertEquals(OnboardingEffect.Finish(StartDestination.DASHBOARD, signInFirst = false), effect)
    }

    @Test
    fun scanning_handsOffToTheBillScanner() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(OnboardingEvent.Scan.ScanClicked)

        assertEquals(OnboardingEffect.OpenBillScanner, viewModel.effects.first())
    }

    /* ------------------------------ Helpers ------------------------------ */

    private fun viewModel(
        catalog: VehicleCatalog = FakeCatalog(),
        registry: VehicleRegistryLookup = FakeRegistry(),
        signedIn: Boolean = false,
    ) = OnboardingViewModel(
        loadCatalog = LoadVehicleCatalogUseCase(catalog),
        loadModels = LoadCarModelsUseCase(catalog),
        lookupPlate = LookupPlateUseCase(registry),
        sessionStatus = SessionStatusProvider { signedIn },
    )

    /** Get the car step to [OnboardingUiState.canContinue] the short way. */
    private fun OnboardingViewModel.answerCarStep() {
        onEvent(OnboardingEvent.Car.PlateChanged(FOUND_PLATE))
        onEvent(OnboardingEvent.OdometerChanged(54_000))
    }

    private fun <T> assertReady(loadable: Loadable<T>): T {
        assertTrue(loadable is Loadable.Ready, "expected Ready but was $loadable")
        return loadable.value
    }

    private class FakeCatalog(var failing: Boolean = false) : VehicleCatalog {
        var lastMake: String? = null

        override suspend fun makes(): List<String> = read { listOf("Maruti Suzuki", "Honda") }
        override suspend fun popularMakes(): List<String> = read { listOf("Maruti Suzuki") }

        override suspend fun models(make: String): List<CarModel> = read {
            lastMake = make
            if (make == "Honda") listOf(CarModel("City"), CarModel("City", "VX")) else emptyList()
        }

        override fun years(): List<Int> = listOf(2026, 2025, 2024)
        override fun fuelTypes(): List<FuelType> = FuelType.entries

        private fun <T> read(value: () -> T): T =
            if (failing) throw IllegalStateException("catalog unavailable") else value()
    }

    private class FakeRegistry(
        private val failure: DomainError? = null,
        private val vehicles: Map<String, RegisteredVehicle> = mapOf(
            FOUND_PLATE to SWIFT,
            HONDA_PLATE to HONDA_CITY,
        ),
    ) : VehicleRegistryLookup {
        var callCount = 0
        var lastPlate: RegistrationNumber? = null

        override suspend fun lookup(
            registrationNumber: RegistrationNumber,
        ): Either<DomainError, RegisteredVehicle> {
            callCount++
            lastPlate = registrationNumber
            if (failure != null) return failure.left()
            return vehicles[registrationNumber.value]?.right() ?: DomainError.RegistrationNotFound.left()
        }
    }

    private companion object {
        /** A plate the registry resolves to a car whose make the **catalog does not** list. */
        const val FOUND_PLATE = "MH12AB1234"

        /** A plate resolving to a car the catalog knows, so the prefill has something to seed. */
        const val HONDA_PLATE = "MH12CD5678"

        val SWIFT = RegisteredVehicle(
            make = "Maruti",
            model = "Swift",
            variant = "VXI",
            year = ModelYear.of(2020).getOrNull()!!,
            fuelType = FuelType.PETROL,
        )

        val HONDA_CITY = RegisteredVehicle(
            make = "Honda",
            model = "City",
            variant = "VX",
            year = ModelYear.of(2020).getOrNull()!!,
            fuelType = FuelType.PETROL,
        )
    }
}
