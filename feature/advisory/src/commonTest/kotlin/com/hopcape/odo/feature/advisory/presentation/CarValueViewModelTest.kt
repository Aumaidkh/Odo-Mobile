package com.hopcape.odo.feature.advisory.presentation

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.city.City
import com.hopcape.odo.core.domain.city.CityCatalog
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.advisory.domain.ObserveCarValueUseCase
import com.hopcape.performance.api.APM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CarValueViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun firstRunWithAutoOdometerOn_offersTheCardOnce() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(firstRun = true, autoOdometer = true, analytics = analytics)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.offerAutoOdometer)
        assertEquals(1, analytics.names.count { it == AdvisoryTelemetry.Event.AUTO_ODO_OFFERED })
    }

    @Test
    fun autoOdometerOff_noCardAndNoOffer() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(firstRun = true, autoOdometer = false, analytics = analytics)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.offerAutoOdometer)
        assertFalse(AdvisoryTelemetry.Event.AUTO_ODO_OFFERED in analytics.names)
    }

    @Test
    fun theGarageArrival_neverOffersTheCard() = runTest(dispatcher) {
        val viewModel = viewModel(firstRun = false, autoOdometer = true)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.offerAutoOdometer)
    }

    @Test
    fun turnOn_opensAutoOdometer() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(firstRun = true, autoOdometer = true, analytics = analytics)
        advanceUntilIdle()

        viewModel.onEvent(CarValueEvent.TurnOnAutoOdometerClicked)

        assertEquals(CarValueEffect.OpenAutoOdometer, viewModel.effects.first())
        assertTrue(AdvisoryTelemetry.Event.AUTO_ODO_OFFER_ACCEPTED in analytics.names)
    }

    @Test
    fun theAutoOdometerCard_isCountedSeenOnce() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(firstRun = true, autoOdometer = true, analytics = analytics)
        advanceUntilIdle()

        repeat(3) { viewModel.onEvent(CarValueEvent.AutoOdometerCardSeen) }

        assertEquals(1, analytics.names.count { it == AdvisoryTelemetry.Event.AUTO_ODO_CARD_SEEN })
    }

    @Test
    fun whyARange_reportsOpenAndClose() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(firstRun = true, analytics = analytics)
        advanceUntilIdle()

        viewModel.onEvent(CarValueEvent.WhyRangeToggled(expanded = true))
        viewModel.onEvent(CarValueEvent.WhyRangeToggled(expanded = false))

        assertEquals(2, analytics.names.count { it == AdvisoryTelemetry.Event.WHY_RANGE_TOGGLED })
    }

    @Test
    fun aFirstRunScan_isMarkedFirstRun() = runTest(dispatcher) {
        val firstRun = viewModel(firstRun = true)
        firstRun.onEvent(CarValueEvent.ScanClicked)
        assertEquals(CarValueEffect.OpenScanner(firstRun = true), firstRun.effects.first())

        val garage = viewModel(firstRun = false)
        garage.onEvent(CarValueEvent.ScanClicked)
        assertEquals(CarValueEffect.OpenScanner(firstRun = false), garage.effects.first())
    }

    @Test
    fun skip_finishesFirstRun() = runTest(dispatcher) {
        val viewModel = viewModel(firstRun = true)

        viewModel.onEvent(CarValueEvent.SkipClicked)

        assertEquals(CarValueEffect.FinishFirstRun, viewModel.effects.first())
    }

    @Test
    fun backInFirstRun_finishesFirstRunAndCountsAsSkip() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val firstRun = viewModel(firstRun = true, analytics = analytics)
        firstRun.onEvent(CarValueEvent.BackClicked)
        assertEquals(CarValueEffect.FinishFirstRun, firstRun.effects.first())
        assertTrue(AdvisoryTelemetry.Event.FIRST_RUN_SKIPPED in analytics.names)

        val garage = viewModel(firstRun = false)
        garage.onEvent(CarValueEvent.BackClicked)
        assertEquals(CarValueEffect.NavigateBack, garage.effects.first())
    }

    @Test
    fun eachFirstRunExit_finishesFirstRunOnceWithItsVia() = runTest(dispatcher) {
        val exits = mapOf(
            CarValueEvent.ScanClicked to "scan",
            CarValueEvent.SkipClicked to "skip",
            CarValueEvent.BackClicked to "back",
            CarValueEvent.TurnOnAutoOdometerClicked to "auto_odometer",
        )
        exits.forEach { (exit, via) ->
            val analytics = RecordingAnalytics()
            val viewModel = viewModel(firstRun = true, analytics = analytics)
            viewModel.onEvent(exit)
            viewModel.onEvent(CarValueEvent.SkipClicked)
            assertEquals(listOf<Any?>(via), analytics.finishedVia(), "for $exit")
        }
    }

    @Test
    fun theGarageArrival_neverFinishesFirstRun() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(firstRun = false, analytics = analytics)
        viewModel.onEvent(CarValueEvent.ScanClicked)
        viewModel.onEvent(CarValueEvent.BackClicked)
        assertTrue(analytics.finishedVia().isEmpty())
    }

    @Test
    fun aScanClick_saysWhetherItCameFromFirstRun() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        viewModel(firstRun = true, analytics = analytics).onEvent(CarValueEvent.ScanClicked)
        val (_, fields) = analytics.events.first { it.first == AdvisoryTelemetry.Event.SCAN_CLICKED }
        assertEquals(true, fields[AdvisoryTelemetry.Key.FIRST_RUN])
    }

    private fun viewModel(
        firstRun: Boolean,
        autoOdometer: Boolean = true,
        analytics: AnalyticsTracker = RecordingAnalytics(),
    ) = CarValueViewModel(
        observeCarValue = ObserveCarValueUseCase(
            cars = OneCar(),
            logs = NoLogs(),
            profiles = NoProfile(),
            cities = object : CityCatalog { override suspend fun cities(): List<City> = emptyList() },
            clock = object : Clock { override fun now(): Instant = Instant.parse("2026-10-24T10:00:00Z") },
            timeZone = TimeZone.UTC,
        ),
        telemetry = AdvisoryTelemetry(
            logger = HLogger.asLogger(),
            analytics = analytics,
            tracer = APM.asTracer(),
            ids = IdGenerator { "trace-1" },
        ),
        firstRun = firstRun,
        firstRunSteps = 3,
        autoOdometerEnabled = autoOdometer,
    )

    private class OneCar : CarRepository {
        private val car: Car = Car.create(
            id = CarId("car-1"),
            ownerId = OwnerId("owner-1"),
            make = "Hyundai",
            model = "Creta",
            year = 2022,
            fuelType = FuelType.PETROL,
            odometerKm = null,
            odometerPending = true,
            isPrimary = true,
        ).getOrElse { error("invalid fixture: $it") }

        override suspend fun add(car: Car): Either<DomainError, Car> = car.right()
        override suspend fun update(car: Car): Either<DomainError, Car> = car.right()
        override fun observePrimaryCar(): Flow<Car?> = flowOf(car)
        override fun observe(id: CarId): Flow<Car?> = flowOf(car)
        override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = Unit.right()
    }

    private class NoLogs : ServiceLogRepository {
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(emptyList())
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = emptyList()
        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = flowOf(emptyList())
    }

    private class NoProfile : OwnerProfileRepository {
        override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> = profile.right()
        override fun observe(): Flow<OwnerProfile?> = flowOf(null)
        override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber): Either<DomainError, Unit> = Unit.right()
        override suspend fun delete(): Either<DomainError, Unit> = Unit.right()
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val names get() = events.map { it.first }
        fun finishedVia() = events.filter { it.first == AdvisoryTelemetry.Event.FIRST_RUN_FINISHED }
            .map { it.second[AdvisoryTelemetry.Key.VIA] }
        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += eventName to properties
        }
        override fun identify(traits: UserTraits) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }
}
