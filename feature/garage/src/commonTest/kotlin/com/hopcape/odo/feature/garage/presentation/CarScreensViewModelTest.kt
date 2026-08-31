package com.hopcape.odo.feature.garage.presentation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.cost.fuel.FuelPriceProvider
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.feature.garage.domain.usecase.AddCarUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ObserveCarDetailsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.FakeCarRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeUnlistedVehicleReporter
import com.hopcape.odo.feature.garage.domain.usecase.FakeVehicleCatalog
import com.hopcape.odo.feature.garage.domain.usecase.currentOdometerFrom
import com.hopcape.odo.feature.garage.domain.usecase.FakeVehicleRegistryLookup
import com.hopcape.odo.feature.garage.domain.usecase.FixedClock
import com.hopcape.odo.feature.garage.domain.usecase.FixedIdGenerator
import com.hopcape.odo.feature.garage.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ObserveGarageUseCase
import com.hopcape.odo.feature.garage.domain.usecase.RemoveCarUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ReportUnlistedVehicleUseCase
import com.hopcape.odo.feature.garage.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.garage.domain.usecase.UpdateCarDetailsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ownerProvider
import com.hopcape.odo.feature.garage.domain.usecase.testCar
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsEffect
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsEvent
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.ExportEffect
import com.hopcape.odo.feature.garage.presentation.sheets.ExportEvent
import com.hopcape.odo.feature.garage.presentation.sheets.ExportProgress
import com.hopcape.odo.feature.garage.presentation.sheets.ExportViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.ExportVia
import com.hopcape.odo.feature.garage.presentation.sheets.pdf.CarDetailsPrintable
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarEffect
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarEvent
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarViewModel
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.valueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class CarScreensViewModelTest {

    private class FakeActiveCar(carId: CarId?) : ActiveCarProvider {
        private val _id = MutableStateFlow(carId)
        override val activeCarId: StateFlow<CarId?> = _id
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun garage(cars: FakeCarRepository): ObserveGarageUseCase {
        val logs = FakeServiceLogRepository()
        return ObserveGarageUseCase(
            cars = cars,
            documents = FakeDocumentRepository(),
            logs = logs,
            currentOdometer = currentOdometerFrom(logs),
            clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
            timeZone = TimeZone.UTC,
        )
    }

    /* ------------------------------ Add car ------------------------------ */

    private fun addCarViewModel(
        cars: FakeCarRepository = FakeCarRepository(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
        reporter: FakeUnlistedVehicleReporter = FakeUnlistedVehicleReporter(),
    ) = AddCarViewModel(
        addCar = AddCarUseCase(cars, FixedIdGenerator("new-car"), ownerProvider()),
        loadCatalog = LoadVehicleCatalogUseCase(FakeVehicleCatalog()),
        loadModels = LoadCarModelsUseCase(FakeVehicleCatalog()),
        lookupPlate = LookupPlateUseCase(FakeVehicleRegistryLookup()),
        reportUnlisted = ReportUnlistedVehicleUseCase(reporter),
        telemetry = testTelemetry(analytics),
    )

    @Test
    fun theCatalogFillsThePickers() = runTest {
        val state = addCarViewModel().state.value

        assertEquals(listOf("Maruti Suzuki", "Hyundai", "Tata"), state.options.makes)
        assertEquals(FuelType.entries, state.options.fuelTypes)
    }

    @Test
    fun answeringTheFormAndTapping_storesTheCar() = runTest {
        val cars = FakeCarRepository()
        val viewModel = addCarViewModel(cars)

        viewModel.onEvent(AddCarEvent.MakeSelected("Maruti Suzuki"))
        viewModel.onEvent(AddCarEvent.ModelSelected(CarModel("Swift", "VXI")))
        viewModel.onEvent(AddCarEvent.YearSelected(2020))
        viewModel.onEvent(AddCarEvent.FuelSelected(FuelType.PETROL))
        viewModel.onEvent(AddCarEvent.OdometerChanged(45_000))
        viewModel.onEvent(AddCarEvent.AddTapped)

        assertIs<AddCarEffect.Added>(viewModel.effects.first())
        val car = cars.added.single()
        assertEquals("Maruti Suzuki", car.make)
        assertEquals("VXI", car.variant)
        assertEquals(45_000, car.odometer.km)
    }

    @Test
    fun savingACatalogCar_neverReportsIt() = runTest {
        val reporter = FakeUnlistedVehicleReporter()
        val viewModel = addCarViewModel(reporter = reporter)

        viewModel.onEvent(AddCarEvent.MakeSelected("Maruti Suzuki"))
        viewModel.onEvent(AddCarEvent.ModelSelected(CarModel("Swift", "VXI")))
        viewModel.onEvent(AddCarEvent.YearSelected(2020))
        viewModel.onEvent(AddCarEvent.FuelSelected(FuelType.PETROL))
        viewModel.onEvent(AddCarEvent.OdometerChanged(45_000))
        viewModel.onEvent(AddCarEvent.AddTapped)
        assertIs<AddCarEffect.Added>(viewModel.effects.first())

        assertTrue(reporter.reports.isEmpty())
    }

    /** The "not listed" free-text row (and any other path to a value outside the catalog). */
    @Test
    fun savingACarNotInTheCatalog_reportsItInTheBackground() = runTest {
        val reporter = FakeUnlistedVehicleReporter()
        val viewModel = addCarViewModel(reporter = reporter)

        viewModel.onEvent(AddCarEvent.MakeSelected("Rare Motors"))
        viewModel.onEvent(AddCarEvent.ModelSelected(CarModel("Concept One", "Turbo")))
        viewModel.onEvent(AddCarEvent.YearSelected(2020))
        viewModel.onEvent(AddCarEvent.FuelSelected(FuelType.PETROL))
        viewModel.onEvent(AddCarEvent.OdometerChanged(45_000))
        viewModel.onEvent(AddCarEvent.AddTapped)
        assertIs<AddCarEffect.Added>(viewModel.effects.first())

        val (make, model, variant) = reporter.reports.single()
        assertEquals("Rare Motors", make)
        assertEquals("Concept One", model)
        assertEquals("Turbo", variant)
    }

    /** A rejected save marks the fields that caused it, not a lone banner over a valid form. */
    @Test
    fun anIncompleteForm_marksTheFieldsThatFailed() = runTest {
        val cars = FakeCarRepository()
        val viewModel = addCarViewModel(cars)

        viewModel.onEvent(AddCarEvent.AddTapped)

        val fields = viewModel.state.value.fields
        assertTrue(fields.make.error != null, "make should be marked")
        assertTrue(fields.model.error != null, "model should be marked")
        assertTrue(viewModel.state.value.odometer.error != null, "the odometer should be marked")
        assertTrue(cars.added.isEmpty())
    }

    /** Choosing a different brand drops the model — a Hyundai trim under "Tata" is nonsense. */
    @Test
    fun choosingAnotherMake_clearsTheModel() = runTest {
        val viewModel = addCarViewModel()
        viewModel.onEvent(AddCarEvent.MakeSelected("Maruti Suzuki"))
        viewModel.onEvent(AddCarEvent.ModelSelected(CarModel("Swift")))

        viewModel.onEvent(AddCarEvent.MakeSelected("Hyundai"))

        assertEquals(null, viewModel.state.value.fields.model.value)
    }

    /** There is no registry wired, so a whole plate still leaves the manual path in place. */
    @Test
    fun aWholePlate_isLookedUpAndComesBackEmpty() = runTest {
        val analytics = RecordingAnalytics()
        val viewModel = addCarViewModel(analytics = analytics)

        viewModel.onEvent(AddCarEvent.PlateChanged("MH12AB1234"))

        assertEquals(null, viewModel.state.value.match)
        assertTrue(analytics.events.any { it.first == GarageTelemetry.Event.PLATE_LOOKED_UP })
    }

    @Test
    fun aPartialPlate_isNotLookedUp() = runTest {
        val analytics = RecordingAnalytics()
        val viewModel = addCarViewModel(analytics = analytics)

        viewModel.onEvent(AddCarEvent.PlateChanged("MH12"))

        assertTrue(
            analytics.events.none { it.first == GarageTelemetry.Event.PLATE_LOOKED_UP },
            "half a plate is a round trip with a guaranteed answer",
        )
    }

    /* ------------------------------ Edit car ------------------------------ */

    private fun editCarViewModel(
        cars: FakeCarRepository,
        reporter: FakeUnlistedVehicleReporter = FakeUnlistedVehicleReporter(),
    ) = EditCarViewModel(
        activeCar = FakeActiveCar(TEST_CAR),
        observeGarage = garage(cars),
        updateDetails = UpdateCarDetailsUseCase(cars),
        loadCatalog = LoadVehicleCatalogUseCase(FakeVehicleCatalog()),
        loadModels = LoadCarModelsUseCase(FakeVehicleCatalog()),
        reportUnlisted = ReportUnlistedVehicleUseCase(reporter),
        telemetry = testTelemetry(),
    )

    @Test
    fun theEditFormOpensOnTheStoredCar() = runTest {
        val viewModel = editCarViewModel(FakeCarRepository(testCar(nickname = "Chhoti")))

        val form = assertIs<Loadable.Ready<CarFormFields>>(viewModel.state.value.form)
        assertEquals("Maruti Suzuki", form.value.make.value)
        assertEquals("Chhoti", form.value.nickname.value)
        assertEquals("MH12AB1234", form.value.registration.value)
    }

    /** The reading moves through the update sheet only, so an edit carries it over as it was. */
    @Test
    fun savingAnEdit_leavesTheOdometerAlone() = runTest {
        val cars = FakeCarRepository(testCar(odometerKm = 45_000))
        val viewModel = editCarViewModel(cars)

        viewModel.onEvent(EditCarEvent.NicknameChanged("Chhoti"))
        viewModel.onEvent(EditCarEvent.SaveTapped)

        assertIs<EditCarEffect.Saved>(viewModel.effects.first())
        val saved = cars.updated.single()
        assertEquals("Chhoti", saved.nickname)
        assertEquals(45_000, saved.odometer.km)
    }

    /* ------------------------------ Car sheets ------------------------------ */

    @Test
    fun theActionsSheetNamesTheCar() = runTest {
        val viewModel = CarActionsViewModel(
            activeCar = FakeActiveCar(TEST_CAR),
            observeGarage = garage(FakeCarRepository(testCar())),
            telemetry = testTelemetry(),
        )

        assertEquals("Maruti Suzuki Swift VXI", viewModel.state.value.car.valueOrNull?.displayName)
    }

    @Test
    fun theActionsSheetRoutesOnward() = runTest {
        val viewModel = CarActionsViewModel(
            activeCar = FakeActiveCar(TEST_CAR),
            observeGarage = garage(FakeCarRepository(testCar())),
            telemetry = testTelemetry(),
        )

        viewModel.onEvent(CarActionsEvent.RemoveTapped)

        assertIs<CarActionsEffect.OpenRemove>(viewModel.effects.first())
    }

    @Test
    fun removingTheCar_deletesItAndCloses() = runTest {
        val cars = FakeCarRepository(testCar())
        val analytics = RecordingAnalytics()
        val viewModel = RemoveCarViewModel(
            activeCar = FakeActiveCar(TEST_CAR),
            observeGarage = garage(cars),
            removeCar = RemoveCarUseCase(cars),
            telemetry = testTelemetry(analytics),
        )

        viewModel.onEvent(RemoveCarEvent.RemoveTapped)

        assertIs<RemoveCarEffect.Removed>(viewModel.effects.first())
        assertEquals(listOf(TEST_CAR), cars.deleted)
        assertTrue(analytics.events.any { it.first == GarageTelemetry.Event.CAR_REMOVED })
    }

    /* ------------------------------ Export ------------------------------ */

    private class FakeHealthScores : HealthScoreRepository {
        override suspend fun latest(carId: CarId): HealthSnapshot? = null
        override suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot? = null
        override fun observeHistory(carId: CarId): Flow<List<HealthSnapshot>> = flowOf(emptyList())
        override suspend fun record(snapshot: HealthSnapshot): Either<DomainError, HealthSnapshot> = snapshot.right()
    }

    private class FakeOwners : OwnerProfileRepository {
        override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> = profile.right()
        override fun observe(): Flow<OwnerProfile?> = flowOf(null)
        override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber): Either<DomainError, Unit> =
            Unit.right()

        override suspend fun delete(): Either<DomainError, Unit> = Unit.right()
    }

    private class NoFuelPrices : FuelPriceProvider {
        override suspend fun priceFor(city: String?, fuelType: FuelType): FuelPrice? = null
        override fun priceChanges(): Flow<Unit> = flowOf(Unit)
    }

    /** Records what was written, and can be told to fail the way a full disk would. */
    private class RecordingFileStore : PlatformFileStore {
        val written = mutableListOf<Pair<String, Int>>()

        override suspend fun save(pickedRef: String, directory: String, fileName: String): Either<DomainError, String> =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun delete(storageKey: String) = Unit
        override suspend fun exists(storageKey: String): Boolean = written.any { it.first == storageKey }
        override suspend fun bytes(storageKey: String): Either<DomainError, ByteArray> =
            DomainError.PersistenceFailure("unused").left()

        override suspend fun write(storageKey: String, bytes: ByteArray): Either<DomainError, String> {
            written += storageKey to bytes.size
            return storageKey.right()
        }
    }

    private fun exportViewModel(
        activeCar: ActiveCarProvider = FakeActiveCar(TEST_CAR),
        files: RecordingFileStore = RecordingFileStore(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = ExportViewModel(
        activeCar = activeCar,
        observeDetails = ObserveCarDetailsUseCase(
            cars = FakeCarRepository(testCar()),
            logs = FakeServiceLogRepository(),
            documents = FakeDocumentRepository(),
            scores = FakeHealthScores(),
            owners = FakeOwners(),
            city = CurrentCityProvider { "Pune" },
            fuelPrices = NoFuelPrices(),
            clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
            timeZone = TimeZone.UTC,
        ),
        documents = { details -> CarDetailsPrintable("<!doctype html>${details.record.carName}", "${details.record.carName} car details") },
        files = files,
        telemetry = testTelemetry(analytics),
    )

    @Test
    fun tappingExport_asksTheHostToRenderTheCarDetails() = runTest {
        val analytics = RecordingAnalytics()
        val viewModel = exportViewModel(analytics = analytics)
        val effects = mutableListOf<ExportEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(ExportEvent.PdfTapped)

        val render = assertIs<ExportEffect.RenderDocument>(effects.single())
        assertEquals("<!doctype html>Maruti Suzuki Swift VXI", render.html)
        assertEquals(ExportVia.PDF, render.via)
        assertTrue(viewModel.state.value.isBusy, "both buttons hold still while the document is laid out")
        assertTrue(analytics.events.any { it.first == GarageTelemetry.Event.EXPORT_REQUESTED })
    }

    @Test
    fun aRenderedDocument_isWrittenBesideTheCarAndShared() = runTest {
        val files = RecordingFileStore()
        val viewModel = exportViewModel(files = files)
        val effects = mutableListOf<ExportEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(ExportEvent.ShareTapped)
        viewModel.onEvent(ExportEvent.Rendered("%PDF-1.4 fake".encodeToByteArray(), ExportVia.SHARE))

        val share = assertIs<ExportEffect.ShareFile>(effects.last())
        assertEquals("exports/${TEST_CAR.value}/car-details.pdf", share.storageKey)
        assertEquals(listOf(share.storageKey), files.written.map { it.first })
        assertTrue(share.title.isNotBlank(), "the share sheet offers the file under a name")
    }

    @Test
    fun aRenderThatProducedNothing_isReportedAsAFailure() = runTest {
        val files = RecordingFileStore()
        val viewModel = exportViewModel(files = files)
        val effects = mutableListOf<ExportEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(ExportEvent.PdfTapped)
        viewModel.onEvent(ExportEvent.Rendered(bytes = null, via = ExportVia.PDF))

        assertEquals(ExportProgress.Failed, viewModel.state.value.export)
        assertTrue(effects.none { it is ExportEffect.ShareFile }, "there is no file to share")
        assertTrue(files.written.isEmpty())
    }

    @Test
    fun exportWithoutACar_offersNothing() = runTest {
        val viewModel = exportViewModel(activeCar = FakeActiveCar(null))
        val effects = mutableListOf<ExportEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(ExportEvent.PdfTapped)

        assertIs<Loadable.Failed>(viewModel.state.value.car)
        assertTrue(effects.isEmpty(), "there is no car to describe")
    }
}
