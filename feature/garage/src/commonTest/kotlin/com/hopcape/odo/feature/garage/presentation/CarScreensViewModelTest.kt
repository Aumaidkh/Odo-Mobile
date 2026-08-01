package com.hopcape.odo.feature.garage.presentation

import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.garage.domain.usecase.AddCarUseCase
import com.hopcape.odo.feature.garage.domain.usecase.FakeCarRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeVehicleCatalog
import com.hopcape.odo.feature.garage.domain.usecase.FakeVehicleRegistryLookup
import com.hopcape.odo.feature.garage.domain.usecase.FixedClock
import com.hopcape.odo.feature.garage.domain.usecase.FixedIdGenerator
import com.hopcape.odo.feature.garage.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ObserveGarageUseCase
import com.hopcape.odo.feature.garage.domain.usecase.RemoveCarUseCase
import com.hopcape.odo.feature.garage.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.garage.domain.usecase.UpdateCarDetailsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ownerProvider
import com.hopcape.odo.feature.garage.domain.usecase.testCar
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsEffect
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsEvent
import com.hopcape.odo.feature.garage.presentation.sheets.CarActionsViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.ExportEffect
import com.hopcape.odo.feature.garage.presentation.sheets.ExportEvent
import com.hopcape.odo.feature.garage.presentation.sheets.ExportViewModel
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarEffect
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarEvent
import com.hopcape.odo.feature.garage.presentation.sheets.RemoveCarViewModel
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.valueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    private fun garage(cars: FakeCarRepository) = ObserveGarageUseCase(
        cars = cars,
        documents = FakeDocumentRepository(),
        logs = FakeServiceLogRepository(),
        clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
        timeZone = TimeZone.UTC,
    )

    /* ------------------------------ Add car ------------------------------ */

    private fun addCarViewModel(
        cars: FakeCarRepository = FakeCarRepository(),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = AddCarViewModel(
        addCar = AddCarUseCase(cars, FixedIdGenerator("new-car"), ownerProvider()),
        loadCatalog = LoadVehicleCatalogUseCase(FakeVehicleCatalog()),
        loadModels = LoadCarModelsUseCase(FakeVehicleCatalog()),
        lookupPlate = LookupPlateUseCase(FakeVehicleRegistryLookup()),
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

    private fun editCarViewModel(cars: FakeCarRepository) = EditCarViewModel(
        activeCar = FakeActiveCar(TEST_CAR),
        observeGarage = garage(cars),
        updateDetails = UpdateCarDetailsUseCase(cars),
        loadCatalog = LoadVehicleCatalogUseCase(FakeVehicleCatalog()),
        loadModels = LoadCarModelsUseCase(FakeVehicleCatalog()),
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

    /** Export is the Resale Passport's job, and that is paid — both actions go to the paywall. */
    @Test
    fun exportingLeadsToThePaywall() = runTest {
        val analytics = RecordingAnalytics()
        val viewModel = ExportViewModel(
            activeCar = FakeActiveCar(TEST_CAR),
            observeGarage = garage(FakeCarRepository(testCar())),
            telemetry = testTelemetry(analytics),
        )

        viewModel.onEvent(ExportEvent.PdfTapped)

        val effect = assertIs<ExportEffect.OpenPaywall>(viewModel.effects.first())
        assertEquals("EXPORT", effect.trigger)
        assertTrue(analytics.events.any { it.first == GarageTelemetry.Event.EXPORT_REQUESTED })
    }
}
