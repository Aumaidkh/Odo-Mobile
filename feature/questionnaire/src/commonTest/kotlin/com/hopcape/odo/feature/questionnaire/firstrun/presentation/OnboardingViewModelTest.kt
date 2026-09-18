package com.hopcape.odo.feature.questionnaire.firstrun.presentation

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import androidx.lifecycle.ViewModelStore
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
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.CompleteOnboardingUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.RecordDeclaredServiceUseCase
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
import kotlin.test.assertNotNull
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

    /* ------------------------------ The car ------------------------------ */

    /** "Not listed" free text is the only path to a car the catalog does not have. */
    @Test
    fun aCarTheCatalogDoesNotHave_isReported() = runTest(dispatcher) {
        val reporter = FakeUnlistedVehicleReporter()
        val viewModel = viewModel(unlistedReporter = reporter)
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.Details.MakeSelected("Rare Motors"))
        advanceUntilIdle()
        viewModel.onEvent(OnboardingEvent.Details.ModelSelected(CarModel("Concept One", "Turbo")))
        viewModel.onEvent(OnboardingEvent.Details.YearSelected(2019))
        viewModel.onEvent(OnboardingEvent.Details.FuelSelected(FuelType.PETROL))
        viewModel.onEvent(OnboardingEvent.OdometerChanged(54_000))
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        val (make, model, variant) = reporter.reports.single()
        assertEquals("Rare Motors", make)
        assertEquals("Concept One", model)
        assertEquals("Turbo", variant)
    }

    /* ------------------------------ Steps ------------------------------ */

    @Test
    fun theCarStep_needsTheFourPickers_andNothingElse() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Nothing has named a car yet.
        assertFalse(viewModel.state.value.canContinue)

        viewModel.answerCarStep()
        advanceUntilIdle()

        // No registration number and no odometer: the plate is asked for later, by the
        // features that need it, and a reading is often not in the owner's head at all.
        assertTrue(viewModel.state.value.canContinue)
    }

    /**
     * The reading is optional, and saying so is a label rather than a button now — so the
     * only way past the step without one is Continue itself.
     */
    @Test
    fun theCarStepMovesOnWithNoOdometerAtAll() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.answerCarStepWithoutOdometer()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        // Nothing dialled, and the car is saved with the reading pending rather than at zero.
        assertNull(viewModel.state.value.odometer.value)
        assertEquals(OnboardingEffect.ShowValue, viewModel.effects.first())
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

    /**
     * The car step is the last one setup owns. What pays for its answers is the value
     * screen, and that is step 3 of first run rather than a screen after it — so the flow
     * is handed on rather than finished here.
     */
    @Test
    fun continuingTheCarStep_handsOffToTheValueScreen() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.answerCarStep()
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        assertEquals(OnboardingEffect.ShowValue, viewModel.effects.first())
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
        assertEquals("Honda", car.make)
        assertEquals("City", car.model)
        assertEquals(54_000, car.odometer.km)
        // Setup no longer asks for one; the column is nullable and a feature asks later.
        assertNull(car.registrationNumber)
        assertTrue(car.isPrimary, "the car named during setup is the owner's primary")
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
        advanceUntilIdle()
        viewModel.answerCarStepWithoutOdometer()
        viewModel.onEvent(OnboardingEvent.OdometerChanged(500))

        viewModel.onEvent(OnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        // Refused, not silently applied: the step does not advance…
        assertEquals(OnboardingStep.CAR, viewModel.state.value.step)
        // …and the owner is told, rather than the failure being swallowed.
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
                SetupTelemetry.Event.COMPLETED,
            ),
            analytics.names,
        )
    }

    @Test
    fun leavingWithoutPressingBack_isStillAnAbandonment() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val store = ViewModelStore()
        store.put("onboarding", viewModel(analytics = analytics))
        advanceUntilIdle()

        // What people actually do: they leave. Without this the funnel showed silence where
        // it should have shown an abandonment, so the step people give up on was invisible.
        store.clear()

        val abandoned = analytics.events.single { it.first == SetupTelemetry.Event.ABANDONED }
        assertEquals(OnboardingStep.CAR.name, abandoned.second[SetupTelemetry.Key.STEP])
    }

    @Test
    fun finishingTheFlow_isNeverAlsoCountedAsAbandoned() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        val store = ViewModelStore()
        store.put("onboarding", viewModel)
        advanceUntilIdle()

        completeTheWholeFlow(viewModel)
        store.clear()

        // The backstop must not turn every completed setup into an abandonment as well.
        assertEquals(0, analytics.events.count { it.first == SetupTelemetry.Event.ABANDONED })
    }

    @Test
    fun pressingBackOutOfTheFlow_countsOneAbandonment_notTwo() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        val store = ViewModelStore()
        store.put("onboarding", viewModel)
        advanceUntilIdle()

        viewModel.onEvent(OnboardingEvent.BackClicked)
        advanceUntilIdle()
        store.clear()

        assertEquals(1, analytics.events.count { it.first == SetupTelemetry.Event.ABANDONED })
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
    fun noEvent_carriesTheOwnersName() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics = analytics)
        advanceUntilIdle()

        completeTheWholeFlow(viewModel)

        // A name identifies the person, and it never belongs in an analytics warehouse
        // however convenient it would be for debugging. (Setup no longer takes a plate at
        // all, so there is none left to leak.)
        val emitted = analytics.propertyValues() + analytics.names
        assertTrue(emitted.none { it.contains(NAME, ignoreCase = true) }, "a name was tracked: $emitted")
    }

    /* ------------------------------ Helpers ------------------------------ */

    private fun viewModel(
        catalog: VehicleCatalog = FakeCatalog(),
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
        saveCar = SaveCarUseCase(
            cars = cars,
            logs = logs,
            idGenerator = IdGenerator { "car-1" },
            clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
            timeZone = TimeZone.UTC,
        ),
        reportUnlisted = ReportUnlistedVehicleUseCase(unlistedReporter),
        recordDeclaredService = RecordDeclaredServiceUseCase(
            logs = logs,
            idGenerator = IdGenerator { "log-1" },
            clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
            timeZone = TimeZone.UTC,
        ),
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
    private class FakeAnswers(private val failing: Boolean = false) : QuestionnaireRepository {
        val saved = mutableMapOf<QuestionKey, Set<String>>()

        override suspend fun save(key: QuestionKey, values: Set<String>): Either<DomainError, Unit> {
            if (failing) return DomainError.PersistenceFailure("disk full").left()
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
        val added = mutableListOf<ServiceLogEntry>()

        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(emptyList())
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> =
            entry.right().also { added += entry }
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = readings.orEmpty()
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
    /** The four pickers, with no reading — the way somebody away from their car answers. */
    private fun OnboardingViewModel.answerCarStepWithoutOdometer() {
        onEvent(OnboardingEvent.Details.MakeSelected(MAKE))
        onEvent(OnboardingEvent.Details.ModelSelected(CarModel("City", "VX")))
        onEvent(OnboardingEvent.Details.YearSelected(2020))
        onEvent(OnboardingEvent.Details.FuelSelected(FuelType.PETROL))
    }

    private fun OnboardingViewModel.answerCarStep() {
        onEvent(OnboardingEvent.Details.MakeSelected(MAKE))
        onEvent(OnboardingEvent.Details.ModelSelected(CarModel("City", "VX")))
        onEvent(OnboardingEvent.Details.YearSelected(2020))
        onEvent(OnboardingEvent.Details.FuelSelected(FuelType.PETROL))
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

    private class FakeUnlistedVehicleReporter : UnlistedVehicleReporter {
        val reports = mutableListOf<Triple<String, String, String?>>()
        override suspend fun report(make: String, model: String, variant: String?) {
            reports += Triple(make, model, variant)
        }
    }

    private companion object {
        val OWNER = OwnerId("owner-1")

        /** A plate the registry resolves to a car whose make the **catalog does not** list. */
        const val MAKE = "Honda"

        /** The owner's name, so the PII guard can look for exactly what was entered. */
        const val NAME = "Rahul"

        /** A plate resolving to a car the catalog knows, so the prefill has something to seed. */


    }
}
