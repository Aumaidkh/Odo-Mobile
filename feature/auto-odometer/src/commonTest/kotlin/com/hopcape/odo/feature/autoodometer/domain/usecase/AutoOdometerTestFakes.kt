package com.hopcape.odo.feature.autoodometer.domain.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.currentReading
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.triptracker.TrackingPreconditions
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.TrackingStatus
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** Shared test fixtures and fakes for the auto-odometer feature's use-case tests. */

internal val TEST_CAR = CarId("car-1")
internal val TEST_OWNER = OwnerId("owner-1")

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** A fully-readied [TrackingReadiness] — every precondition granted. */
internal val READY = TrackingReadiness(
    fineLocation = true,
    backgroundLocation = true,
    activityRecognition = true,
    bluetoothConnect = true,
    notifications = true,
)

internal fun testTrip(
    id: String,
    carId: CarId = TEST_CAR,
    startedAt: Instant,
    endedAt: Instant,
    distanceMeters: Long,
    status: TripStatus = TripStatus.RECORDED,
    mode: TripMode = TripMode.BT_VERIFIED,
): Trip = Trip.reconstitute(
    id = TripId(id),
    carId = carId,
    ownerId = TEST_OWNER,
    startedAt = startedAt,
    endedAt = endedAt,
    distanceMeters = distanceMeters,
    estimatedMeters = 0,
    mode = mode,
    status = status,
    startLat = null,
    startLon = null,
    endLat = null,
    endLon = null,
)

internal fun testReading(date: LocalDate, km: Int, logId: ServiceLogId? = null): OdometerReading =
    OdometerReading(logId = logId, date = date, odometer = Distance.of(km).getOrElse { error("bad km $km") })

/** Records every write; a `null` [readings] baseline mirrors a car with no baseline at all. */
internal class FakeServiceLogRepository(
    entries: List<ServiceLogEntry> = emptyList(),
    readings: List<OdometerReading>? = emptyList(),
) : ServiceLogRepository {
    val entriesFlow = MutableStateFlow(entries)
    val readingsFlow = MutableStateFlow(readings.orEmpty())

    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = entriesFlow
    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
    override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? = readingsFlow.value
    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = readingsFlow
}

/** Emits a fixed [current] value for every car — a trip-aware "current odometer" double. */
internal class FakeCurrentOdometerProvider(private val current: Distance?) : CurrentOdometerProvider {
    override fun observeCurrent(carId: CarId): Flow<Distance?> = flowOf(current)
}

/**
 * A [CurrentOdometerProvider] over [logs]'s own readings, with no trips ever counted —
 * mirrors the pre-trip-aware behaviour so a test that does not care about trips can keep
 * asserting against [logs]'s latest reading.
 */
internal fun currentOdometerFrom(logs: FakeServiceLogRepository): CurrentOdometerProvider =
    object : CurrentOdometerProvider {
        override fun observeCurrent(carId: CarId): Flow<Distance?> =
            logs.readingsFlow.map { it.currentReading()?.odometer }
    }

/** Ignores [CarId] filtering — this feature is single-car (plan D-decision), same as other fakes in this repo. */
internal class FakeTripRepository(
    initial: List<Trip> = emptyList(),
) : TripRepository {
    val trips = MutableStateFlow(initial)
    val statusChanges = mutableListOf<Pair<TripId, TripStatus>>()
    val deleteAllCalls = mutableListOf<CarId>()
    var deleteAllResult: Either<DomainError, Unit> = Unit.right()
    var forgetRoutesCalls = 0
        private set

    override suspend fun add(trip: Trip): Either<DomainError, Trip> {
        trips.update { it + trip }
        return trip.right()
    }

    override suspend fun addWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation): Either<DomainError, Trip> {
        trips.update { it + listOfNotNull(trip, gap) }
        return trip.right()
    }

    override fun observe(carId: CarId): Flow<List<Trip>> = trips

    override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> = trips

    override suspend fun setStatus(id: TripId, status: TripStatus): Either<DomainError, Unit> {
        statusChanges += id to status
        trips.update { list -> list.map { if (it.id == id) it.withStatus(status) else it } }
        return Unit.right()
    }

    override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> =
        trips.value.filter { it.status.counted && it.startedAt > after }

    override suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip> =
        trips.value.filter { it.status.counted && it.startedAt >= from && it.startedAt <= to }

    override suspend fun parkedLocation(carId: CarId): ParkedLocation? = null

    override suspend fun deleteAllForCar(carId: CarId): Either<DomainError, Unit> {
        deleteAllCalls += carId
        if (deleteAllResult.isRight()) trips.value = emptyList()
        return deleteAllResult
    }

    /**
     * "Keep trip routes" turning off. Counted rather than no-op'd so a use case here that
     * starts erasing routes has to say so in a test — nothing in this feature calls it today,
     * and it should not begin to by accident.
     *
     * The trips themselves stay: this drops coordinates, not history. The fake holds no
     * coordinates to drop, which is why there is nothing else to do.
     */
    override suspend fun forgetRoutes(): Either<DomainError, Unit> {
        forgetRoutesCalls++
        return Unit.right()
    }
}

internal class FakeAppSettingsRepository(
    initial: AppSettings = AppSettings.Default,
) : AppSettingsRepository {
    val settings = MutableStateFlow(initial)
    val saved = mutableListOf<AppSettings>()
    var failing = false

    override fun observe(): Flow<AppSettings> = settings

    override suspend fun save(settings: AppSettings): Either<DomainError, AppSettings> =
        if (failing) {
            DomainError.PersistenceFailure("disk full").left()
        } else {
            saved += settings
            this.settings.value = settings
            settings.right()
        }
}

/** [throwing] simulates a broken write (e.g. `SharedPreferences` unavailable) — [saveBond] throws instead of saving. */
internal class FakeVehicleBondStore(
    initial: VehicleBond? = null,
    private val throwing: Boolean = false,
) : VehicleBondStore {
    var current: VehicleBond? = initial
    val saved = mutableListOf<VehicleBond>()

    override suspend fun bond(): VehicleBond? = current
    override suspend fun saveBond(bond: VehicleBond) {
        if (throwing) error("vehicle bond store unavailable")
        saved += bond
        current = bond
    }
    override suspend fun clearBond() {
        current = null
    }
}

internal class FakeTripTracker(enabled: Boolean = false) : TripTracker {
    val enabledFlow = MutableStateFlow(enabled)
    val statusFlow = MutableStateFlow<TrackingStatus>(TrackingStatus.Disabled)
    var pauseCalls = 0
    var resumeCalls = 0
    var discardCalls = 0
    var startIfConnectedCalls = 0

    override suspend fun setEnabled(enabled: Boolean) {
        enabledFlow.value = enabled
    }

    override val isEnabled: Flow<Boolean> get() = enabledFlow
    override val status: Flow<TrackingStatus> get() = statusFlow

    override suspend fun pauseActiveTrip() {
        pauseCalls++
    }

    override suspend fun resumeActiveTrip() {
        resumeCalls++
    }

    override suspend fun discardActiveTrip() {
        discardCalls++
    }

    override suspend fun startIfConnected() {
        startIfConnectedCalls++
    }
}

internal class FakeTrackingPreconditions(private val readiness: TrackingReadiness) : TrackingPreconditions {
    override fun status(): TrackingReadiness = readiness
}
