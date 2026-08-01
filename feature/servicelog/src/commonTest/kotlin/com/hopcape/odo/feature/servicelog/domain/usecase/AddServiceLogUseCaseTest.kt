package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
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

class AddServiceLogUseCaseTest {

    /** [readings] is the car's odometer timeline; `null` = no such car. */
    private class FakeServiceLogRepository(private val readings: List<OdometerReading>?) : ServiceLogRepository {
        var addCount = 0
        var lastAdded: ServiceLogEntry? = null
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(listOfNotNull(lastAdded))
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(lastAdded)
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> {
            addCount++
            lastAdded = entry
            return entry.right()
        }
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? = readings
        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> =
            flowOf(readings.orEmpty())
    }

    private class FixedIdGenerator(private val id: String) : IdGenerator {
        override fun newId(): String = id
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    // 2026-07-03 in UTC.
    private val clock = FixedClock(Instant.parse("2026-07-03T10:00:00Z"))

    /** A reading on the car's timeline — `id` null stands for the onboarding baseline. */
    private fun reading(km: Int, date: LocalDate, id: String? = null) = OdometerReading(
        logId = id?.let(::ServiceLogId),
        date = date,
        odometer = Distance.of(km).getOrElse { error("test fixture km=$km") },
    )

    /** The car was onboarded at 50,000 km on 1 Jan 2026. */
    private val baseline = listOf(reading(km = 50_000, date = LocalDate(2026, 1, 1)))

    private fun repo(readings: List<OdometerReading>? = baseline) = FakeServiceLogRepository(readings)

    private fun useCase(repo: FakeServiceLogRepository) =
        AddServiceLogUseCase(repo, FixedIdGenerator("log-1"), clock, TimeZone.UTC)

    private fun command(odometerKm: Int? = 60_000, serviceDate: LocalDate? = LocalDate(2026, 6, 1)) =
        AddServiceLogCommand(
            serviceDate = serviceDate,
            odometerKm = odometerKm,
            totalAmountPaise = 280_000,
            workshopName = "Sharma Motors",
        )

    @Test
    fun forwardReading_persistsManualEntry() = runTest {
        val repo = repo()

        val result = useCase(repo)(command(odometerKm = 60_000), carId, ownerId)

        assertTrue(result.isRight(), "expected Right but was $result")
        val entry = result.getOrNull()!!
        assertEquals("log-1", entry.id.value)
        assertEquals(ownerId, entry.ownerId)
        assertEquals(LogSource.MANUAL, entry.source)
        assertEquals(1, repo.addCount)
    }

    @Test
    fun equalReading_isAccepted() = runTest {
        val result = useCase(repo())(command(odometerKm = 50_000), carId, ownerId)
        assertTrue(result.isRight(), "equal odometer must be allowed: $result")
    }

    @Test
    fun backwardsReading_isRejectedAndNotPersisted() = runTest {
        val repo = repo()

        val result = useCase(repo)(command(odometerKm = 40_000), carId, ownerId)

        val error = assertIs<DomainError.OdometerRegression>(result.leftOrNull()!!.head)
        assertEquals(50_000, error.previousKm)
        assertEquals(40_000, error.attemptedKm)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun backdatedHistory_belowTheBaseline_isPersisted() = runTest {
        // Building the service history after onboarding: the car joined Odo at 50,000 km
        // in January, and its 2024 service (30,000 km) is being logged now. Nothing about
        // that is a regression — it simply happened earlier.
        val repo = repo()

        val result = useCase(repo)(
            command(odometerKm = 30_000, serviceDate = LocalDate(2024, 3, 12)),
            carId,
            ownerId,
        )

        assertTrue(result.isRight(), "backdated history must be loggable: $result")
        assertEquals(1, repo.addCount)
    }

    @Test
    fun backdatedReading_aboveALaterReading_isRejected() = runTest {
        val repo = repo()

        // 65,000 km in 2024 is impossible when the car read 50,000 km in January 2026.
        val result = useCase(repo)(
            command(odometerKm = 65_000, serviceDate = LocalDate(2024, 3, 12)),
            carId,
            ownerId,
        )

        val error = assertIs<DomainError.OdometerAheadOfLaterEntry>(result.leftOrNull()!!.head)
        assertEquals(50_000, error.nextKm)
        assertEquals(65_000, error.attemptedKm)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun noBaseline_isCarNotFound() = runTest {
        val result = useCase(repo(readings = null))(command(), carId, ownerId)
        assertTrue(result.leftOrNull()!!.contains(DomainError.CarNotFound))
    }

    @Test
    fun invalidFields_areRejectedBeforeTheTimelineCheck() = runTest {
        val repo = repo()

        val result = useCase(repo)(command(odometerKm = null), carId, ownerId)

        assertTrue(result.leftOrNull()!!.contains(DomainError.MissingOdometer))
        assertEquals(0, repo.addCount)
    }

    @Test
    fun futureServiceDate_isRejected() = runTest {
        val result = useCase(repo())(command(serviceDate = LocalDate(2026, 7, 4)), carId, ownerId)
        assertTrue(result.leftOrNull()!!.contains(DomainError.ServiceDateInFuture))
    }
}
