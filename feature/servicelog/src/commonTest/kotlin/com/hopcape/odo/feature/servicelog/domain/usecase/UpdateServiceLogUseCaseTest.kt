package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateServiceLogUseCaseTest {

    private class FakeServiceLogRepository(private val readings: List<OdometerReading>?) : ServiceLogRepository {
        var updateCount = 0
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(emptyList())
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> {
            updateCount++
            return entry.right()
        }
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? = readings
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    private val editedId = ServiceLogId("log-2")
    private val clock = FixedClock(Instant.parse("2026-07-03T10:00:00Z"))

    private fun reading(km: Int, date: LocalDate, id: String? = null) = OdometerReading(
        logId = id?.let(::ServiceLogId),
        date = date,
        odometer = Distance.of(km).getOrElse { error("test fixture km=$km") },
    )

    /**
     * The car was onboarded at 50,000 km in January; `log-2` was then logged on 1 June
     * with a fat-fingered 65,000 km.
     */
    private val timeline = listOf(
        reading(km = 50_000, date = LocalDate(2026, 1, 1)),
        reading(km = 65_000, date = LocalDate(2026, 6, 1), id = "log-2"),
    )

    private fun useCase(repo: FakeServiceLogRepository) = UpdateServiceLogUseCase(repo, clock, TimeZone.UTC)

    private fun command(odometerKm: Int?, serviceDate: LocalDate? = LocalDate(2026, 6, 1)) =
        UpdateServiceLogCommand(
            id = editedId,
            carId = carId,
            ownerId = ownerId,
            serviceDate = serviceDate,
            odometerKm = odometerKm,
            totalAmountPaise = 280_000,
            workshopName = "Sharma Motors",
        )

    @Test
    fun correctingTheEntrysOwnReadingDownwards_isPersisted() = runTest {
        // The typo fix: 65,000 → 56,000. Checked against its own stored value this would
        // look like a regression; the entry must be excluded from its own timeline.
        val repo = FakeServiceLogRepository(timeline)

        val result = useCase(repo)(command(odometerKm = 56_000))

        assertTrue(result.isRight(), "an entry must not be checked against itself: $result")
        assertEquals(56_000, result.getOrNull()!!.odometer.km)
        assertEquals(1, repo.updateCount)
    }

    @Test
    fun unchangedReading_isPersisted() = runTest {
        val repo = FakeServiceLogRepository(timeline)

        val result = useCase(repo)(command(odometerKm = 65_000))

        assertTrue(result.isRight(), "an unchanged reading must always pass: $result")
    }

    @Test
    fun editBelowAnEarlierReading_isRejectedAndNotPersisted() = runTest {
        val repo = FakeServiceLogRepository(timeline)

        // 45,000 in June contradicts the 50,000 the car already read in January.
        val result = useCase(repo)(command(odometerKm = 45_000))

        val error = assertIs<DomainError.OdometerRegression>(result.leftOrNull()!!.head)
        assertEquals(50_000, error.previousKm)
        assertEquals(45_000, error.attemptedKm)
        assertEquals(0, repo.updateCount)
    }

    @Test
    fun noBaseline_isCarNotFound() = runTest {
        val result = useCase(FakeServiceLogRepository(readings = null))(command(odometerKm = 56_000))
        assertTrue(result.leftOrNull()!!.contains(DomainError.CarNotFound))
    }

    @Test
    fun invalidFields_areRejectedBeforeTheTimelineCheck() = runTest {
        val repo = FakeServiceLogRepository(timeline)

        val result = useCase(repo)(command(odometerKm = null, serviceDate = null))

        val errors = result.leftOrNull()!!
        assertTrue(errors.contains(DomainError.MissingOdometer))
        assertTrue(errors.contains(DomainError.MissingServiceDate))
        assertEquals(0, repo.updateCount)
    }
}
