package com.hopcape.odo.feature.advisory.domain.checklist

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.advisory.matching.BillLineMatcher
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.BenchmarkScope
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.schedule.ServiceInterval
import com.hopcape.odo.core.domain.schedule.ServiceIntervalRepository
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What the screen is handed, and what it is not.
 *
 * The cost line is the part worth pinning down: the reference tables are half entered, so a
 * total that quietly covered three of five due jobs would be read at a counter as covering
 * all five.
 */
class ReadServiceChecklistUseCaseTest {

    private val schedule = mapOf(
        "engine_oil" to ServiceInterval("engine_oil", "Engine oil + filter", km = 10_000),
        "air_filter" to ServiceInterval("air_filter", "Air filter", km = 20_000),
    )

    @Test
    fun theCostCoversOnlyTheDueJobsAPriceWasFoundForAndSaysHowMany() = runTest {
        val result = useCase(bands = { query -> if (query.categorySlug == "engine_oil") BAND else null }).read()

        val cost = result.getOrNull()?.cost
        assertEquals(2, result.getOrNull()?.checklist?.due?.size)
        assertEquals(1, cost?.pricedItems)
        assertEquals(2, cost?.dueItems)
        assertEquals(400_000, cost?.range?.low?.paise)
        assertEquals(600_000, cost?.range?.high?.paise)
    }

    @Test
    fun noPricedJobLeavesNoCostLineRatherThanAZeroOne() = runTest {
        val cost = useCase(bands = { null }).read().getOrNull()?.cost

        assertNull(cost)
    }

    @Test
    fun noCityMeansNoBandCanBeAskedForAndTheChecklistSurvivesAnyway() = runTest {
        val result = useCase(city = null).read().getOrNull()

        assertNull(result?.cost)
        assertEquals(2, result?.checklist?.due?.size)
    }

    @Test
    fun aScheduleThatCouldNotBeReadLeavesTheChecklistEmptyRatherThanWrong() = runTest {
        val result = useCase(intervals = DomainError.LookupUnavailable.left()).read().getOrNull()

        // The upsells are all that is left, and every one of them says "not in the schedule".
        assertNull(result?.checklist?.due?.firstOrNull())
        assertEquals(3, result?.checklist?.notYet?.size)
    }

    @Test
    fun withNoCarThereIsNoServiceToPrepareFor() = runTest {
        val result = useCase(car = null).read()

        assertIs<DomainError.CarNotFound>(result.leftOrNull())
    }

    private fun useCase(
        car: Car? = CAR,
        city: String? = "Mumbai",
        intervals: Either<DomainError, Map<String, ServiceInterval>> = schedule.right(),
        bands: (PriceBandQuery) -> PriceBand? = { BAND },
    ) = ReadServiceChecklistUseCase(
        cars = FakeCars(car),
        logs = FakeLogs,
        odometers = object : CurrentOdometerProvider {
            override fun observeCurrent(carId: CarId): Flow<Distance?> = flowOf(Distance.of(42_000).getOrNull())
        },
        schedule = ServiceIntervalRepository { intervals },
        bands = PriceBandRepository { bands(it).right() },
        cities = CurrentCityProvider { city },
        questionnaire = FakeQuestionnaire,
        builder = PreServiceChecklistBuilder(BillLineMatcher()),
        clock = FIXED_CLOCK,
    )

    private class FakeCars(private val car: Car?) : CarRepository {
        override fun observePrimaryCar(): Flow<Car?> = flowOf(car)
        override fun observe(id: CarId): Flow<Car?> = flowOf(car)
        override suspend fun add(car: Car) = car.right()
        override suspend fun update(car: Car) = car.right()
        override suspend fun softDelete(id: CarId) = Unit.right()
    }

    private object FakeLogs : ServiceLogRepository {
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(emptyList())
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
        override suspend fun add(entry: ServiceLogEntry) = entry.right()
        override suspend fun update(entry: ServiceLogEntry) = entry.right()
        override suspend fun softDelete(id: ServiceLogId) = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = emptyList()
        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = flowOf(emptyList())
    }

    private object FakeQuestionnaire : QuestionnaireRepository {
        override suspend fun save(key: QuestionKey, values: Set<String>) = Unit.right()
        override fun observe(): Flow<List<QuestionAnswer>> = flowOf(emptyList())
        override suspend fun answersFor(key: QuestionKey) = emptyList<QuestionAnswer>().right()
    }

    private companion object {

        val FIXED_CLOCK = object : Clock {
            override fun now(): Instant = Instant.parse("2026-09-05T09:00:00Z")
        }

        val BAND = PriceBand(
            low = Amount.of(400_000).getOrNull()!!,
            typical = Amount.of(500_000).getOrNull()!!,
            high = Amount.of(600_000).getOrNull()!!,
            sampleSize = 0,
            scope = BenchmarkScope.MODELLED,
            basis = BenchmarkBasis.MODELLED,
        )

        val CAR = Car.create(
            id = CarId("car-1"),
            ownerId = OwnerId("owner-1"),
            make = "Hyundai",
            model = "i20",
            variant = null,
            year = 2023,
            fuelType = FuelType.PETROL,
            registrationNumber = null,
            odometerKm = 42_000,
            purchaseYear = 2023,
            nickname = null,
            isPrimary = true,
        ).getOrNull()!!
    }
}
