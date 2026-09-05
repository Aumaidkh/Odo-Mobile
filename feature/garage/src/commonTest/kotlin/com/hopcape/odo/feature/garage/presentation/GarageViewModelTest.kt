package com.hopcape.odo.feature.garage.presentation

import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import com.hopcape.odo.feature.garage.domain.usecase.FakeCarRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeChallanRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeTripRepository
import com.hopcape.odo.feature.garage.domain.usecase.FakeTripTracker
import com.hopcape.odo.feature.garage.domain.usecase.FakeVehicleBondStore
import com.hopcape.odo.feature.garage.domain.usecase.currentOdometerFrom
import com.hopcape.odo.feature.garage.domain.usecase.FixedClock
import com.hopcape.odo.feature.garage.domain.usecase.ObserveAutoOdometerCardState
import com.hopcape.odo.feature.garage.domain.usecase.ObserveGarageUseCase
import com.hopcape.odo.feature.garage.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.garage.domain.usecase.testCar
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class GarageViewModelTest {

    private class FakeActiveCar(carId: CarId?) : ActiveCarProvider {
        private val _id = MutableStateFlow(carId)
        override val activeCarId: StateFlow<CarId?> = _id
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun featureConfig(challans: Boolean) = object : FeatureConfig {
        override val autoOdometerEnabled = true
        override val refuelDetectEnabled = true
        override val challanEnabled = challans
        override val plateLookupEnabled = false
        override val advisoryClassifierEnabled = false
    }

    private fun viewModel(
        carId: CarId? = TEST_CAR,
        cars: CarRepository = FakeCarRepository(testCar()),
        logs: FakeServiceLogRepository = FakeServiceLogRepository(),
        challansEnabled: Boolean = true,
    ) = GarageViewModel(
        activeCar = FakeActiveCar(carId),
        observeGarage = ObserveGarageUseCase(
            cars = cars,
            documents = FakeDocumentRepository(),
            logs = logs,
            currentOdometer = currentOdometerFrom(logs),
            clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
            timeZone = TimeZone.UTC,
        ),
        // Hidden by default (no readings, no bond) — the auto-odometer slot isn't what
        // these tests are about.
        observeAutoOdometerCard = ObserveAutoOdometerCardState(
            serviceLogs = FakeServiceLogRepository(readings = emptyList()),
            bonds = FakeVehicleBondStore(),
            tracker = FakeTripTracker(enabled = false),
            trips = FakeTripRepository(),
            clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z")),
            config = featureConfig(challans = challansEnabled),
            timeZone = TimeZone.UTC,
        ),
        challans = FakeChallanRepository(),
        telemetry = testTelemetry(),
        featureConfig = featureConfig(challans = challansEnabled),
    )

    @Test
    fun theCarIsRead_andShown() = runTest {
        val state = viewModel().state.first { it.content is Loadable.Ready }

        assertEquals(TEST_CAR, state.content.valueOrNull?.car?.id)
    }

    /** No car is a loaded garage with nothing in it — the "add your car" state, not a wait. */
    @Test
    fun noActiveCar_readsAsAnEmptyGarage() = runTest {
        val state = viewModel(carId = null).state.first { it.content is Loadable.Ready }

        assertNull(state.content.valueOrNull?.car)
    }

    @Test
    fun choosingAChip_narrowsTheHistory() = runTest {
        val viewModel = viewModel()
        viewModel.state.first { it.content is Loadable.Ready }

        viewModel.onEvent(GarageEvent.FilterSelected(ServiceFacet.TYRES))

        assertEquals(ServiceFacet.TYRES, viewModel.state.value.filter)
    }

    @Test
    fun tappingTheCarMenu_opensIt() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(GarageEvent.CarMenuTapped)

        assertIs<GarageEffect.OpenCarActions>(viewModel.effects.first())
    }

    /** The service log's screens are per-car, so the effect carries the car the row belongs to. */
    @Test
    fun tappingAService_carriesTheCarWithIt() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(GarageEvent.ServiceTapped(ServiceLogId("log-1")))

        val effect = assertIs<GarageEffect.OpenService>(viewModel.effects.first())
        assertEquals(TEST_CAR.value, effect.carId)
    }

    /** Without a car there is nothing to open — opening the wrong one would be worse. */
    @Test
    fun tappingAService_withoutACar_doesNothing() = runTest {
        val viewModel = viewModel(carId = null)

        viewModel.onEvent(GarageEvent.ServiceTapped(ServiceLogId("log-1")))
        viewModel.onEvent(GarageEvent.CarMenuTapped)

        // The menu effect arriving first means no navigation was emitted for the service.
        assertIs<GarageEffect.OpenCarActions>(viewModel.effects.first())
    }

    @Test
    fun aFailedRead_saysSoRatherThanShowingAnEmptyGarage() = runTest {
        val state = viewModel(cars = FailingCarRepository()).state.first { it.content is Loadable.Failed }

        assertTrue(state.filteredHistory.isEmpty())
    }
}
