package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SaveCarUseCaseTest {

    /** Midday UTC on 28 Jul 2026, read in UTC so the date cannot drift. */
    private val clock = FixedClock(Instant.parse("2026-07-28T12:00:00Z"))

    private class FakeCarRepository : CarRepository {
        var addCount = 0
        var updateCount = 0
        var lastAdded: Car? = null
        override suspend fun add(car: Car): Either<DomainError, Car> {
            addCount++
            lastAdded = car
            return car.right()
        }

        override suspend fun update(car: Car): Either<DomainError, Car> {
            updateCount++
            lastAdded = car
            return car.right()
        }

        override fun observePrimaryCar(): Flow<Car?> = flowOf(lastAdded)

        override fun observe(id: CarId): Flow<Car?> = flowOf(lastAdded?.takeIf { it.id == id })

        override suspend fun softDelete(id: CarId): Either<DomainError, Unit> {
            lastAdded = lastAdded?.takeIf { it.id != id }
            return Unit.right()
        }
    }

    private class FixedIdGenerator(private val id: String) : IdGenerator {
        override fun newId(): String = id
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    /** Only the odometer timeline matters here, so the rest answers emptily. */
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

    private val ownerId = OwnerId("owner-1")

    private fun validCommand() = SaveCarCommand(
        make = "Maruti",
        model = "Swift",
        year = 2020,
        fuelType = FuelType.PETROL,
        odometerKm = 45_000,
    )

    /** A reading already on file, dated when it was written down. */
    private fun reading(date: LocalDate, km: Int) = OdometerReading(
        logId = null,
        date = date,
        odometer = Distance.of(km).getOrElse { error("bad km=$km") },
    )

    private fun useCase(
        repo: CarRepository,
        idGenerator: IdGenerator = FixedIdGenerator("car-1"),
        logs: ServiceLogRepository = FakeServiceLogRepository(),
    ) = SaveCarUseCase(cars = repo, logs = logs, idGenerator = idGenerator, clock = clock, timeZone = TimeZone.UTC)

    @Test
    fun validCommand_persistsAndReturnsCar() = runTest {
        val repo = FakeCarRepository()
        val result = useCase(repo)(validCommand(), ownerId)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("car-1", result.getOrNull()?.id?.value)
        assertEquals(1, repo.addCount)
    }

    @Test
    fun invalidCommand_doesNotPersist() = runTest {
        val repo = FakeCarRepository()

        val result = useCase(repo)(
            SaveCarCommand(make = null, model = null, year = null, fuelType = null, odometerKm = null),
            ownerId,
        )

        assertTrue(result.isLeft())
        assertEquals(0, repo.addCount)
    }

    @Test
    fun withoutAnExistingId_theCarIsInserted() = runTest {
        val repo = FakeCarRepository()

        val result = useCase(repo)(validCommand(), ownerId)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(1, repo.addCount)
        assertEquals(0, repo.updateCount)
    }

    @Test
    fun withAnExistingId_theSameCarIsUpdated() = runTest {
        // Stepping back to fix a detail must edit the stored car, not add a second one.
        val repo = FakeCarRepository()

        val result = useCase(repo, idGenerator = FixedIdGenerator("unused"))(
            command = validCommand(),
            ownerId = ownerId,
            existing = CarId("car-1"),
        )

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("car-1", result.getOrNull()?.id?.value)
        assertEquals(1, repo.updateCount)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun updatingAnUnknownCar_reportsTheRepositoryFailure() = runTest {
        val repo = object : CarRepository by FakeCarRepository() {
            override suspend fun update(car: Car): Either<DomainError, Car> =
                DomainError.CarNotFound.left()
        }

        val result = useCase(repo, idGenerator = FixedIdGenerator("unused"))(
            command = validCommand(),
            ownerId = ownerId,
            existing = CarId("ghost"),
        )

        assertEquals(listOf(DomainError.CarNotFound), result.leftOrNull()?.toList())
    }

    /**
     * A freshly generated id has no history at all — [ServiceLogRepository.odometerReadings]
     * answers `null` for it, the same as it would for any car that does not exist yet. The
     * check must treat that as "nothing to compare against", not as a failure, or no
     * brand-new car could ever be onboarded.
     */
    @Test
    fun aBrandNewCar_savesAtAnyNonNegativeValue_regardlessOfHistory() = runTest {
        val repo = FakeCarRepository()
        val logs = FakeServiceLogRepository(readings = null)

        val result = useCase(repo, logs = logs)(validCommand().copy(odometerKm = 500), ownerId)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(500, result.getOrNull()?.odometer?.km)
        assertEquals(1, repo.addCount)
    }

    /**
     * An edit can target an id that already has real service-log history — from a prior
     * session, or pulled down by sync. Saving a lower baseline through this path must be
     * refused exactly the way `:feature:garage`'s `UpdateOdometerUseCase` already refuses it.
     */
    @Test
    fun anEditAgainstKnownHigherHistory_isRejected() = runTest {
        val repo = FakeCarRepository()
        val logs = FakeServiceLogRepository(readings = listOf(reading(LocalDate(2026, 1, 1), 45_000)))

        val result = useCase(repo, logs = logs)(
            command = validCommand().copy(odometerKm = 500),
            ownerId = ownerId,
            existing = CarId("car-1"),
        )

        assertEquals(
            listOf(DomainError.OdometerRegression(previousKm = 45_000, attemptedKm = 500)),
            result.leftOrNull()?.toList(),
        )
        assertEquals(0, repo.updateCount)
    }

    @Test
    fun anEditAtOrAboveKnownHistory_isSaved() = runTest {
        val repo = FakeCarRepository()
        val logs = FakeServiceLogRepository(readings = listOf(reading(LocalDate(2026, 1, 1), 45_000)))

        val result = useCase(repo, logs = logs)(
            command = validCommand().copy(odometerKm = 45_000),
            ownerId = ownerId,
            existing = CarId("car-1"),
        )

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(1, repo.updateCount)
    }
}
