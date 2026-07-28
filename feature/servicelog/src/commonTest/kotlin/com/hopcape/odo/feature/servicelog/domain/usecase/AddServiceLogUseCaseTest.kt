package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
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

    /** [mostRecent] is the reference reading the regression check compares against. */
    private class FakeServiceLogRepository(private val mostRecent: Int?) : ServiceLogRepository {
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
        override suspend fun mostRecentOdometerKm(carId: CarId): Int? = mostRecent
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

    private fun useCase(mostRecent: Int?) = AddServiceLogUseCase(
        logs = FakeServiceLogRepository(mostRecent),
        idGenerator = FixedIdGenerator("log-1"),
        clock = clock,
        timeZone = TimeZone.UTC,
    )

    private fun command(odometerKm: Int? = 60_000, serviceDate: LocalDate? = LocalDate(2026, 6, 1)) =
        AddServiceLogCommand(
            serviceDate = serviceDate,
            odometerKm = odometerKm,
            totalAmountPaise = 280_000,
            workshopName = "Sharma Motors",
        )

    @Test
    fun forwardReading_persistsManualEntry() = runTest {
        val repo = FakeServiceLogRepository(mostRecent = 50_000)
        val useCase = AddServiceLogUseCase(repo, FixedIdGenerator("log-1"), clock, TimeZone.UTC)

        val result = useCase(command(odometerKm = 60_000), carId, ownerId)

        assertTrue(result.isRight(), "expected Right but was $result")
        val entry = result.getOrNull()!!
        assertEquals("log-1", entry.id.value)
        assertEquals(ownerId, entry.ownerId)
        assertEquals(LogSource.MANUAL, entry.source)
        assertEquals(1, repo.addCount)
    }

    @Test
    fun equalReading_isAccepted() = runTest {
        val result = useCase(mostRecent = 50_000)(command(odometerKm = 50_000), carId, ownerId)
        assertTrue(result.isRight(), "equal odometer must be allowed: $result")
    }

    @Test
    fun backwardsReading_isRejectedAndNotPersisted() = runTest {
        val repo = FakeServiceLogRepository(mostRecent = 50_000)
        val useCase = AddServiceLogUseCase(repo, FixedIdGenerator("log-1"), clock, TimeZone.UTC)

        val result = useCase(command(odometerKm = 40_000), carId, ownerId)

        val error = result.leftOrNull()!!.head
        assertIs<DomainError.OdometerRegression>(error)
        assertEquals(50_000, error.previousKm)
        assertEquals(40_000, error.attemptedKm)
        assertEquals(0, repo.addCount)
    }

    @Test
    fun noBaseline_isCarNotFound() = runTest {
        val result = useCase(mostRecent = null)(command(), carId, ownerId)
        assertTrue(result.leftOrNull()!!.contains(DomainError.CarNotFound))
    }

    @Test
    fun invalidFields_areRejectedBeforeRegression() = runTest {
        val repo = FakeServiceLogRepository(mostRecent = 50_000)
        val useCase = AddServiceLogUseCase(repo, FixedIdGenerator("log-1"), clock, TimeZone.UTC)

        val result = useCase(command(odometerKm = null), carId, ownerId)

        assertTrue(result.leftOrNull()!!.contains(DomainError.MissingOdometer))
        assertEquals(0, repo.addCount)
    }

    @Test
    fun futureServiceDate_isRejected() = runTest {
        val result = useCase(mostRecent = 50_000)(
            command(serviceDate = LocalDate(2026, 7, 4)),
            carId,
            ownerId,
        )
        assertTrue(result.leftOrNull()!!.contains(DomainError.ServiceDateInFuture))
    }
}
