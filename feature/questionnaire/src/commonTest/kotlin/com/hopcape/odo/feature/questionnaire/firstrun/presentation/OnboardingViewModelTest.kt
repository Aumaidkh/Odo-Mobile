package com.hopcape.odo.feature.questionnaire.firstrun.presentation

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.performance.api.APM
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.UnlistedVehicleReporter
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.CompleteOnboardingUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.ReportUnlistedVehicleUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.SaveCarUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.Loadable
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.feature.questionnaire.odoQuestions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.PlateLookup
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.PlateLookupError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

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
    fun anEightCharacterPlate_isAcceptedRatherThanTreatedAsHalfTyped() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // JK192976 is a real plate — an older one, issued with no letter series at all. The
        // floor used to be nine, so Continue stayed dead until a ninth character was added.
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged("JK192976"))
        viewModel.onEvent(OnboardingEvent.Car.MatchRejected)
        viewModel.onEvent(OnboardingEvent.Details.MakeSelected("Honda"))
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Details.ModelSelected(CarModel("City", "VX")))
        viewModel.onEvent(OnboardingEvent.Details.YearSelected(2019))
        viewModel.onEvent(OnboardingEvent.Details.FuelSelected(FuelType.PETROL))
        viewModel.onEvent(OnboardingEvent.OdometerChanged(54_000))

        assertTrue(viewModel.state.value.canContinue)
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
    fun theManualRoute_stillNeedsARegistrationNumber() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Straight to the form without typing a plate — the way somebody who does not want a
        // lookup gets here.
        viewModel.onEvent(OnboardingEvent.Car.MatchRejected)
        viewModel.onEvent(OnboardingEvent.Details.MakeSelected("Honda"))
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Details.ModelSelected(CarModel("City", "VX")))
        viewModel.onEvent(OnboardingEvent.Details.YearSelected(2019))
        viewModel.onEvent(OnboardingEvent.Details.FuelSelected(FuelType.PETROL))
        viewModel.onEvent(OnboardingEvent.OdometerChanged(54_000))

        // Every picker answered and the odometer given, and it is still not enough: the car
        // would be saved without the number every bill and document identifies it by.
        assertFalse(viewModel.state.value.canContinue)

        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(HONDA_PLATE))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canContinue)
        // And typing it here does not drag the owner back to the lookup they opted out of.
        assertTrue(viewModel.state.value.manualEntry)
        assertEquals(PlateLookup.Idle, viewModel.state.value.car.lookup)
    }

    /** The manual route's own path to a car outside the catalog — "not listed" free text. */
    @Test
    fun theManualRoute_reportsACarTheCatalogDoesNotHave() = runTest(dispatcher) {
        val reporter = FakeUnlistedVehicleReporter()
        val viewModel = viewModel(unlistedReporter = reporter)
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Car.MatchRejected)
        viewModel.onEvent(OnboardingEvent.Details.MakeSelected("Rare Motors"))
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Details.ModelSelected(CarModel("Concept One", "Turbo")))
        viewModel.onEvent(OnboardingEvent.Details.YearSelected(2019))
        viewModel.onEvent(OnboardingEvent.Details.FuelSelected(FuelType.PETROL))
        viewModel.onEvent(OnboardingEvent.OdometerChanged(54_000))
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(HONDA_PLATE))
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        val (make, model, variant) = reporter.reports.single()
        assertEquals("Rare Motors", make)
        assertEquals("Concept One", model)
        assertEquals("Turbo", variant)
    }

    /** The registry's own claim is never compared against the catalog — there is nothing to. */
    @Test
    fun thePlateRoute_neverReportsEvenWhenTheCatalogDoesNotListTheMatch() = runTest(dispatcher) {
        val reporter = FakeUnlistedVehicleReporter()
        val viewModel = viewModel(unlistedReporter = reporter)
        advanceUntilIdle()

        completeTheWholeFlow(viewModel)

        assertTrue(reporter.reports.isEmpty())
    }

    @Test
    fun theRegistrationNumber_survivesSwitchingBetweenTheTwoRoutes() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Car.MatchRejected)
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(HONDA_PLATE))
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Details.TryAutoFillClicked)

        // One plate for the car, whichever route it was typed on.
        assertEquals(HONDA_PLATE, viewModel.state.value.car.plate.value)
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

        // Continue awaits the step's write, so the step only moves once the save lands.
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()
        assertEquals(OnboardingStep.PROFILE, viewModel.state.value.step)

        viewModel.onEvent(OnboardingEvent.Profile.NameChanged("Rahul"))
        viewModel.onEvent(OnboardingEvent.Profile.GoalToggled("TRACK_COSTS"))
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()
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
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Profile.NameChanged(" "))
        viewModel.onEvent(OnboardingEvent.Profile.GoalToggled("SELL_SOON"))
        assertFalse(viewModel.state.value.canContinue)

        viewModel.onEvent(OnboardingEvent.Profile.NameChanged("Rahul"))
        assertTrue(viewModel.state.value.canContinue)
    }

    /** Goals are multi-select now, so the whole set has to reach the answers table. */
    @Test
    fun theProfileStep_storesEveryGoalPicked() = runTest(dispatcher) {
        val answers = FakeAnswers()
        val viewModel = viewModel(answers = answers)
        viewModel.answerCarStep()
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Profile.NameChanged("Rahul"))
        viewModel.onEvent(OnboardingEvent.Profile.GoalToggled("TRACK_COSTS"))
        viewModel.onEvent(OnboardingEvent.Profile.GoalToggled("SELL_SOON"))
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        assertEquals(setOf("TRACK_COSTS", "SELL_SOON"), answers.saved[QuestionKeys.Goal])
    }

    @Test
    fun tappingAPickedGoalAgainRemovesIt() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEvent(OnboardingEvent.Profile.GoalToggled("TRACK_COSTS"))

        viewModel.onEvent(OnboardingEvent.Profile.GoalToggled("TRACK_COSTS"))

        assertFalse(viewModel.state.value.canContinue)
    }

    /* ------------------------------ Finishing ------------------------------ */

    /**
     * The goal no longer picks a surface. It used to name one of three, and all three
     * resolved to the dashboard, so the choice was never real — the owner's answer is now
     * stored and read by whoever wants it rather than spent on routing.
     */
    @Test
    fun skippingTheScan_finishesRegardlessOfTheGoal() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEvent(OnboardingEvent.Profile.GoalToggled("SELL_SOON"))

        viewModel.onEvent(OnboardingEvent.Scan.SkipClicked)

        assertEquals(OnboardingEffect.Finish(signInFirst = true), viewModel.effects.first())
    }

    @Test
    fun finishing_withoutAGoal_stillLandsSomewhere() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(OnboardingEvent.Scan.SkipClicked)

        // A missing goal is no reason to strand the owner at the end of setup.
        assertEquals(OnboardingEffect.Finish(signInFirst = true), viewModel.effects.first())
    }

    @Test
    fun finishing_withASession_doesNotAskForSignIn() = runTest(dispatcher) {
        val viewModel = viewModel(signedIn = true)

        viewModel.onEvent(OnboardingEvent.Scan.SkipClicked)

        val effect = viewModel.effects.first()
        assertEquals(OnboardingEffect.Finish(signInFirst = false), effect)
    }

    @Test
    fun scanning_finishesSetupAndAsksTheScannerToOpen() = runTest(dispatcher) {
        val viewModel = viewModel(signedIn = true)

        viewModel.onEvent(OnboardingEvent.Scan.ScanClicked)

        // The camera button ends setup like Skip does. Handing off to the scanner with the
        // flow still open is what let an owner reach the fairness report, and the profile
        // editor behind it, without ever passing the sign-in offer.
        assertEquals(
            OnboardingEffect.Finish(signInFirst = false, openScanner = true),
            viewModel.effects.first(),
        )
    }

    @Test
    fun scanning_withoutASession_asksForSignInBeforeTheScanner() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onEvent(OnboardingEvent.Scan.ScanClicked)

        assertEquals(
            OnboardingEffect.Finish(signInFirst = true, openScanner = true),
            viewModel.effects.first(),
        )
    }

    @Test
    fun skipping_doesNotOpenTheScanner() = runTest(dispatcher) {
        val viewModel = viewModel(signedIn = true)

        viewModel.onEvent(OnboardingEvent.Scan.SkipClicked)

        assertEquals(false, (viewModel.effects.first() as OnboardingEffect.Finish).openScanner)
    }

    /* ------------------------------ Persistence ------------------------------ */

    @Test
    fun continuingTheCarStep_storesTheCar() = runTest(dispatcher) {
        val cars = FakeCarRepository()
        val viewModel = viewModel(cars = cars)
        viewModel.answerCarStep()
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        // The car is stored when its step is answered, not at the end of the flow, so
        // abandoning setup halfway still leaves the owner with their car.
        val car = cars.added.single()
        assertEquals("Maruti", car.make)
        assertEquals("Swift", car.model)
        assertEquals(54_000, car.odometer.km)
        assertEquals(FOUND_PLATE, car.registrationNumber?.value)
        assertTrue(car.isPrimary, "the car named during setup is the owner's primary")
        assertEquals(OnboardingStep.PROFILE, viewModel.state.value.step)
    }

    @Test
    fun steppingBackAndForward_editsTheSameCar() = runTest(dispatcher) {
        val cars = FakeCarRepository()
        val viewModel = viewModel(cars = cars)
        viewModel.answerCarStep()
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.BackClicked)
        viewModel.onEvent(OnboardingEvent.OdometerChanged(61_500))
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        // Correcting a detail must not leave a second car in the garage.
        assertEquals(1, cars.added.size)
        assertEquals(1, cars.updated.size)
        assertEquals(cars.added.single().id, cars.updated.single().id)
        assertEquals(61_500, cars.updated.single().odometer.km)
    }

    @Test
    fun aFailedCarSave_keepsTheOwnerOnTheStep() = runTest(dispatcher) {
        val viewModel = viewModel(cars = FakeCarRepository(failing = true))
        viewModel.answerCarStep()
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        assertEquals(OnboardingStep.CAR, viewModel.state.value.step)
    }

    /**
     * A car id can arrive at this flow with real history already on it — from a prior
     * session, or pulled down by sync. Saving a lower baseline against it must surface a
     * visible error rather than silently succeeding or crashing.
     */
    @Test
    fun anOdometerRegression_surfacesAVisibleErrorRatherThanSilentlySucceeding() = runTest(dispatcher) {
        val logs = FakeServiceLogRepository(
            readings = listOf(
                OdometerReading(
                    logId = null,
                    date = LocalDate(2026, 1, 1),
                    odometer = Distance.of(45_000).getOrElse { error("bad km") },
                ),
            ),
        )
        val viewModel = viewModel(logs = logs)
        viewModel.onEvent(OnboardingEvent.Car.PlateChanged(FOUND_PLATE))
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.OdometerChanged(500))

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        // Refused, not silently applied: the step does not advance…
        assertEquals(OnboardingStep.CAR, viewModel.state.value.step)
        // …and the owner is told, rather than the failure being swallowed.
        assertTrue(viewModel.effects.first() is OnboardingEffect.SaveFailed)
    }

    @Test
    fun continuingTheProfileStep_storesACompletedProfile() = runTest(dispatcher) {
        val profiles = FakeProfileRepository()
        val viewModel = viewModel(profiles = profiles)
        reachProfileStep(viewModel)

        viewModel.answerProfileStep()
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        // Completion is stamped here rather than at the end, so a drop-off on the scan
        // step still counts as onboarded — which is what stops the flow reappearing.
        val profile = profiles.saved.single()
        assertEquals("Rahul", profile.name?.value)
        assertTrue(profile.hasCompletedOnboarding)
        assertEquals(OnboardingStep.FIRST_SCAN, viewModel.state.value.step)
    }

    @Test
    fun aFailedProfileSave_keepsTheOwnerOnTheStepAndSaysWhy() = runTest(dispatcher) {
        val viewModel = viewModel(profiles = FakeProfileRepository(failing = true))
        reachProfileStep(viewModel)
        viewModel.answerProfileStep()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        assertEquals(OnboardingStep.PROFILE, viewModel.state.value.step)
        // No field owns a storage failure, so it is reported as a one-shot message.
        assertTrue(viewModel.effects.first() is OnboardingEffect.SaveFailed)
    }

    /* ------------------------------ Telemetry ------------------------------ */

    @Test
    fun theFunnel_isTrackedFromTheFirstStepToCompletion() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()

        completeTheWholeFlow(viewModel)

        assertEquals(
            listOf(
                SetupTelemetry.Event.STARTED,
                SetupTelemetry.Event.CAR_SAVED,
                SetupTelemetry.Event.STEP_ADVANCED,
                SetupTelemetry.Event.GOAL_SELECTED,
                SetupTelemetry.Event.PROFILE_SAVED,
                SetupTelemetry.Event.STEP_ADVANCED,
                SetupTelemetry.Event.FIRST_SCAN_SKIPPED,
                SetupTelemetry.Event.COMPLETED,
            ),
            analytics.names.filterNot { it == SetupTelemetry.Event.PLATE_LOOKUP },
        )
    }

    @Test
    fun aFailedSave_isTrackedByTheTypeOfWhatFailed() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(cars = FakeCarRepository(failing = true), analytics = analytics)
        viewModel.answerCarStep()
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        val failure = analytics.events.last { it.first == SetupTelemetry.Event.SAVE_FAILED }
        assertEquals(OnboardingStep.CAR.name, failure.second[SetupTelemetry.Key.STEP])
        // The *type* that failed, never the answer that failed it.
        assertEquals("PersistenceFailure", failure.second[SetupTelemetry.Key.ERRORS])
    }

    @Test
    fun noEvent_carriesThePlateOrTheOwnersName() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()

        completeTheWholeFlow(viewModel)

        // A plate identifies a person's car and a name identifies the person; neither belongs
        // in an analytics warehouse, however convenient it would be for debugging.
        val emitted = analytics.propertyValues() + analytics.names
        assertTrue(emitted.none { it.contains(FOUND_PLATE, ignoreCase = true) }, "a plate was tracked: $emitted")
        assertTrue(emitted.none { it.contains(NAME, ignoreCase = true) }, "a name was tracked: $emitted")
    }

    /* ------------------------------ Helpers ------------------------------ */

    private fun viewModel(
        catalog: VehicleCatalog = FakeCatalog(),
        registry: VehicleRegistryLookup = FakeRegistry(),
        cars: FakeCarRepository = FakeCarRepository(),
        logs: ServiceLogRepository = FakeServiceLogRepository(),
        profiles: FakeProfileRepository = FakeProfileRepository(),
        signedIn: Boolean = false,
        analytics: AnalyticsTracker = RecordingAnalytics(),
        unlistedReporter: FakeUnlistedVehicleReporter = FakeUnlistedVehicleReporter(),
        answers: FakeAnswers = FakeAnswers(),
    ) = OnboardingViewModel(
        questions = odoQuestions(),
        answers = answers,
        loadCatalog = LoadVehicleCatalogUseCase(catalog),
        loadModels = LoadCarModelsUseCase(catalog),
        lookupPlate = LookupPlateUseCase(registry),
        saveCar = SaveCarUseCase(
            cars = cars,
            logs = logs,
            idGenerator = IdGenerator { "car-1" },
            clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
            timeZone = TimeZone.UTC,
        ),
        reportUnlisted = ReportUnlistedVehicleUseCase(unlistedReporter),
        completeOnboarding = CompleteOnboardingUseCase(profiles, CurrentOwnerProvider { OWNER }),
        currentOwner = CurrentOwnerProvider { OWNER },
        sessionStatus = SessionStatusProvider { signedIn },
        // The real telemetry, so its own code runs under test; only the tracker is a fake. The
        // logger and tracer facades no-op until the app bootstrap configures them.
        telemetry = SetupTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            tracer = APM.asTracer(),
            ids = IdGenerator { "trace-1" },
        ),
    )

    /**
     * Walk every step the way an owner would, ending on the skipped first scan.
     *
     * Each step's write has to finish before the next tap: a Continue while a save is in flight
     * is deliberately ignored, so tapping straight through would leave the flow on step one.
     */
    private fun TestScope.completeTheWholeFlow(viewModel: OnboardingViewModel) {
        viewModel.answerCarStep()
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Profile.NameChanged(NAME))
        viewModel.onEvent(OnboardingEvent.Profile.GoalToggled("TRACK_COSTS"))
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Scan.SkipClicked)
        advanceUntilIdle()
    }

    /** Captures what was tracked, so the funnel and the PII guard can both be asserted. */
    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()

        val names: List<String> get() = events.map { it.first }

        fun propertyValues(): List<String> = events.flatMap { (_, props) -> props.values.map { it.toString() } }

        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += eventName to properties
        }

        override fun identify(traits: UserTraits) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }

    /** Records what the flow stored, and can be told to fail like a full disk would. */
    private class FakeCarRepository(var failing: Boolean = false) : CarRepository {
        val added = mutableListOf<Car>()
        val updated = mutableListOf<Car>()

        override suspend fun add(car: Car): Either<DomainError, Car> =
            if (failing) DomainError.PersistenceFailure("disk full").left() else car.right().also { added += car }

        override suspend fun update(car: Car): Either<DomainError, Car> =
            if (failing) DomainError.PersistenceFailure("disk full").left() else car.right().also { updated += car }

        override fun observePrimaryCar(): Flow<Car?> = flowOf(added.lastOrNull())

        override fun observe(id: CarId): Flow<Car?> = flowOf(added.lastOrNull { it.id == id })

        override suspend fun softDelete(id: CarId): Either<DomainError, Unit> {
            added.removeAll { it.id == id }
            return Unit.right()
        }
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    /** Records what the goal step stored, so a test can assert the whole set arrived. */
    private class FakeAnswers : QuestionnaireRepository {
        val saved = mutableMapOf<QuestionKey, Set<String>>()

        override suspend fun save(key: QuestionKey, values: Set<String>): Either<DomainError, Unit> {
            saved[key] = values
            return Unit.right()
        }

        override fun observe(): Flow<List<QuestionAnswer>> = flowOf(emptyList())

        override suspend fun answersFor(key: QuestionKey): Either<DomainError, List<QuestionAnswer>> =
            emptyList<QuestionAnswer>().right()
    }

    /** Only the odometer timeline matters to this flow's tests, so the rest answers emptily. */
    private class FakeServiceLogRepository(
        private val readings: List<OdometerReading>? = null,
    ) : ServiceLogRepository {
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(emptyList())
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? = readings
        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> =
            flowOf(readings.orEmpty())
    }

    private class FakeProfileRepository(var failing: Boolean = false) : OwnerProfileRepository {
        val saved = mutableListOf<OwnerProfile>()

        override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> =
            if (failing) {
                DomainError.PersistenceFailure("disk full").left()
            } else {
                profile.right().also { saved += profile }
            }

        override fun observe(): Flow<OwnerProfile?> = flowOf(saved.lastOrNull())
        override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber): Either<DomainError, Unit> =
            Unit.right()

        override suspend fun delete(): Either<DomainError, Unit> = Unit.right().also { saved.clear() }
    }


    /** Get the car step to [OnboardingUiState.canContinue] the short way. */
    private fun OnboardingViewModel.answerCarStep() {
        onEvent(OnboardingEvent.Car.PlateChanged(FOUND_PLATE))
        onEvent(OnboardingEvent.OdometerChanged(54_000))
    }

    /** Answer and leave the car step, so the flow is sitting on the profile step. */
    private fun TestScope.reachProfileStep(viewModel: OnboardingViewModel) {
        viewModel.answerCarStep()
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()
    }

    private fun OnboardingViewModel.answerProfileStep() {
        onEvent(OnboardingEvent.Profile.NameChanged("Rahul"))
        onEvent(OnboardingEvent.Profile.GoalToggled("TRACK_COSTS"))
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

    private class FakeUnlistedVehicleReporter : UnlistedVehicleReporter {
        val reports = mutableListOf<Triple<String, String, String?>>()
        override suspend fun report(make: String, model: String, variant: String?) {
            reports += Triple(make, model, variant)
        }
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
        val OWNER = OwnerId("owner-1")

        /** A plate the registry resolves to a car whose make the **catalog does not** list. */
        const val FOUND_PLATE = "MH12AB1234"

        /** The owner's name, so the PII guard can look for exactly what was entered. */
        const val NAME = "Rahul"

        /** A plate resolving to a car the catalog knows, so the prefill has something to seed. */
        const val HONDA_PLATE = "MH12CD5678"

        val SWIFT = RegisteredVehicle(
            make = "Maruti",
            model = "Swift",
            variant = "VXI",
            year = ModelYear.of(2020).getOrNull()!!,
            fuelType = FuelType.PETROL,
            source = VehicleSource.OWN_RECORD,
        )

        val HONDA_CITY = RegisteredVehicle(
            make = "Honda",
            model = "City",
            variant = "VX",
            year = ModelYear.of(2020).getOrNull()!!,
            fuelType = FuelType.PETROL,
            source = VehicleSource.OWN_RECORD,
        )
    }
}
